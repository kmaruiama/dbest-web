package dbest

import com.sun.net.httpserver.HttpServer
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.file.FileAlreadyExistsException
import java.nio.file.NoSuchFileException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.Executors
import kotlin.system.exitProcess
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import org.http4k.core.ContentType
import org.http4k.core.Filter
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.then
import org.http4k.routing.ResourceLoader
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.routing.static
import org.http4k.server.HttpExchangeHandler

class NotFoundException(message: String) : RuntimeException(message)

@Serializable data class Failure(val error: String)

@Serializable private data class Bootstrap(val token: String)

@Serializable private data class ShuttingDown(val shuttingDown: Boolean)

const val JSON = "application/json; charset=utf-8"
private const val TOKEN_HEADER = "X-DBest-Token"
private const val SHUTDOWN_DELAY_MS = 300L

internal var shutdownAction: () -> Unit = { exitProcess(0) }

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8000
    val sessions = Sessions()
    restoreOpen(sessions)
    Runtime.getRuntime()
        .addShutdownHook(
            Thread({
                sessions.engine.close()
                for (workspace in sessions.workspaces.values) {
                    workspace.engineState.close()
                }
            }))
    val server = loopbackServer(router(sessions, newToken()), port)
    server.start()
    println("PORTA: http://${server.address.hostString}:${server.address.port}")
}

fun loopbackServer(handler: HttpHandler, port: Int): HttpServer {
    require(port in 0..65535, { "porta invalida" })
    val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0)
    server.createContext("/", HttpExchangeHandler(handler))
    server.executor = Executors.newFixedThreadPool(4)
    return server
}

fun router(sessions: Sessions, token: String): HttpHandler {
    val api =
        tokenFilter(token)
            .then(
                routes(
                    "/bootstrap" bind
                        Method.GET to
                        ({
                            respond(Bootstrap.serializer(), Bootstrap(token))
                                .header("Cache-Control", "no-store")
                        }),
                    "/shutdown" bind Method.POST to ({ shutdown() }),
                    catalogRoutes(),
                    workspaceRoutes(sessions),
                    nodeRoutes(sessions),
                    comparatorRoutes(sessions),
                    exportRoutes(sessions),
                    ingestRoutes(),
                ))
    val app = { request: Request ->
        val response = api(request)
        if (response.status == Status.NOT_FOUND) frontend(request) else response
    }
    return accessLog.then(errors).then(securityHeaders).then(app)
}

fun <T> respond(serializer: KSerializer<T>, value: T): Response =
    Response(Status.OK).header("Content-Type", JSON).body(wire.encodeToString(serializer, value))

fun <T> decode(request: Request, serializer: KSerializer<T>): T =
    wire.decodeFromString(serializer, request.bodyString())

fun logError(line: String, cause: Throwable?) {
    System.err.println(line)
    cause?.printStackTrace(System.err)
}

private fun newToken(): String {
    val bytes = ByteArray(32)
    SecureRandom().nextBytes(bytes)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}

private val accessLog =
    Filter({ next ->
        { request ->
            val start = System.nanoTime()
            val response = next(request)
            println(
                "${request.method} ${request.uri} ${response.status.code} ${(System.nanoTime() - start) / 1_000_000}ms")
            response
        }
    })

private val securityHeaders =
    Filter({ next ->
        { request ->
            next(request)
                .header(
                    "Content-Security-Policy",
                    "default-src 'self'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'; object-src 'none'; style-src 'self' 'unsafe-inline'")
                .header("Referrer-Policy", "no-referrer")
                .header("X-Content-Type-Options", "nosniff")
        }
    })

private val errors =
    Filter({ next ->
        { request ->
            try {
                next(request)
            } catch (failure: Exception) {
                fail(failure)
            }
        }
    })

private fun fail(failure: Exception): Response =
    when (failure) {
        is NotFoundException,
        is NoSuchFileException -> failure(Status.NOT_FOUND, failure, "nao encontrado")
        is EngineBusyException -> failure(Status.CONFLICT, failure, "ocupado")
        is FileAlreadyExistsException -> failure(Status.CONFLICT, failure, "o arquivo ja existe")
        is DialogUnavailableException ->
            failure(Status.SERVICE_UNAVAILABLE, failure, "seletor indisponivel", trace = true)
        is IllegalArgumentException,
        is SerializationException -> failure(Status.BAD_REQUEST, failure, "requisicao invalida")
        is PlanError -> failure(Status.UNPROCESSABLE_ENTITY, failure, "plano invalido")
        is StorageError ->
            failure(Status.BAD_GATEWAY, failure, "erro de storage", trace = true, expose = false)
        is EngineException ->
            failure(
                Status.INTERNAL_SERVER_ERROR,
                failure,
                "falha na engine",
                trace = true,
                expose = false)
        is IOException ->
            failure(Status.INTERNAL_SERVER_ERROR, failure, "falha de E/S", trace = true)
        else -> throw failure
    }

private fun failure(
    status: Status,
    failure: Exception,
    fallback: String,
    trace: Boolean = false,
    expose: Boolean = true
): Response {
    val detail = failure.message ?: fallback
    logError(
        "${status.code} ${failure.javaClass.simpleName}: $detail", if (trace) failure else null)
    return failureResponse(status, if (expose) detail else fallback)
}

private fun failureResponse(status: Status, message: String): Response {
    val body = wire.encodeToString(Failure.serializer(), Failure(message))
    return Response(status).header("Content-Type", JSON).body(body)
}

private fun tokenFilter(token: String): Filter =
    Filter({ next ->
        { request ->
            val writes =
                request.method in setOf(Method.POST, Method.PUT, Method.PATCH, Method.DELETE)
            if (writes && !hasValidToken(request, token)) {
                failureResponse(Status.UNAUTHORIZED, "autorizacao da aplicacao ausente ou invalida")
            } else {
                next(request)
            }
        }
    })

private fun hasValidToken(request: Request, token: String): Boolean {
    val provided = request.header(TOKEN_HEADER) ?: return false
    val expected = token.toByteArray(Charsets.UTF_8)
    return MessageDigest.isEqual(provided.toByteArray(Charsets.UTF_8), expected)
}

private val assets =
    static(ResourceLoader.Classpath("public"), "js" to ContentType("text/javascript"))

private fun frontend(request: Request): Response {
    if (request.method != Method.GET) {
        return Response(Status.NOT_FOUND)
    }
    val asset = assets(request)
    return if (asset.status == Status.NOT_FOUND) assets(Request(Method.GET, "/index.html"))
    else asset
}

private fun shutdown(): Response {
    val worker =
        Thread({
            Thread.sleep(SHUTDOWN_DELAY_MS)
            shutdownAction()
        })
    worker.isDaemon = true
    worker.start()
    return respond(ShuttingDown.serializer(), ShuttingDown(true))
}
