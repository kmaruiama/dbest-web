package dbest.kernel.http

import dbest.features.canvas.canvasRoutes
import dbest.features.canvas.nodeRoutes
import dbest.features.catalog.catalogRoutes
import dbest.features.config.configRoutes
import dbest.features.export.exportRoutes
import dbest.features.ingest.ingestRoutes
import dbest.features.sessions.Sessions
import dbest.features.sessions.sessionRoutes
import org.http4k.core.HttpHandler
import org.http4k.core.Status
import org.http4k.core.then
import org.http4k.routing.routes

fun router(sessions: Sessions, apiToken: String? = null): HttpHandler {
    val api = routes(
        catalogRoutes(),
        configRoutes(),
        sessionRoutes(sessions),
        canvasRoutes(sessions),
        nodeRoutes(sessions),
        exportRoutes(sessions),
        ingestRoutes(),
        shutdownRoute,
    )
    val frontend = frontendRoutes()
    val routes = { request: org.http4k.core.Request ->
        val handled = if (apiToken != null) {
            val bootstrap = bootstrapRoute(apiToken)(request)
            if (bootstrap.status != Status.NOT_FOUND) bootstrap
            else apiTokenFilter(apiToken).then(api)(request)
        } else {
            api(request)
        }
        if (handled.status == Status.NOT_FOUND) frontend(request) else handled
    }
    return accessLogFilter.then(errorFilter).then(securityHeadersFilter).then(routes)
}
