package dbest.kernel.http

import dbest.kernel.json.json
import dbest.kernel.json.obj
import dbest.kernel.util.existsInCollection
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import org.http4k.core.Filter
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status

private const val API_TOKEN_HEADER = "X-DBest-Token"
private val unsafeMethods = setOf(Method.POST, Method.PUT, Method.PATCH, Method.DELETE)

internal fun newApiToken(): String {
    val bytes = ByteArray(32)
    SecureRandom().nextBytes(bytes)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}

internal fun bootstrapRoute(token: String): (Request) -> Response = { request ->
    if (request.method == Method.GET && request.uri.path == "/bootstrap") {
        jsonResponse(Status.OK, obj("token" to json(token))).header("Cache-Control", "no-store")
    } else {
        Response(Status.NOT_FOUND)
    }
}

internal fun apiTokenFilter(token: String): Filter = Filter({ next ->
    { request ->
        if (existsInCollection(request.method, unsafeMethods) && !hasToken(request, token)) {
            errorResponse(Status.UNAUTHORIZED, "autorizacao da aplicacao ausente ou invalida")
        } else {
            next(request)
        }
    }
})

val securityHeadersFilter: Filter = Filter({ next ->
    { request ->
        next(request)
            .header("Content-Security-Policy", "default-src 'self'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'; object-src 'none'; style-src 'self' 'unsafe-inline'")
            .header("Referrer-Policy", "no-referrer")
            .header("X-Content-Type-Options", "nosniff")
    }
})

private fun hasToken(request: Request, token: String): Boolean {
    val provided = request.header(API_TOKEN_HEADER) ?: return false
    return MessageDigest.isEqual(provided.toByteArray(Charsets.UTF_8), token.toByteArray(Charsets.UTF_8))
}
