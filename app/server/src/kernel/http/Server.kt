package dbest.kernel.http

import com.sun.net.httpserver.HttpServer
import dbest.features.sessions.Sessions
import dbest.features.sessions.closeAllSessions
import dbest.kernel.util.orDefault
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import org.http4k.core.HttpHandler
import org.http4k.server.HttpExchangeHandler

internal fun loopbackServer(handler: HttpHandler, port: Int): HttpServer {
    require(port in 0..65535, { "porta invalida" })
    val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0)
    server.createContext("/", HttpExchangeHandler(handler))
    server.executor = Executors.newFixedThreadPool(4)
    return server
}

fun main() {
    val port = orDefault(System.getenv("PORT")?.toIntOrNull(), 8000)
    val sessions = Sessions()
    Runtime.getRuntime().addShutdownHook(Thread({
        closeAllSessions(sessions)
        sessions.engine.close()
    }))

    val server = loopbackServer(router(sessions, newApiToken()), port)
    server.start()
    println("PORTA: http://${server.address.hostString}:${server.address.port}")
}
