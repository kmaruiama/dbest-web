package dbest.kernel.http

import dbest.features.sessions.EngineBusyException
import dbest.kernel.adapter.EngineException
import dbest.kernel.util.orDefault
import kotlinx.serialization.SerializationException
import org.http4k.core.Filter
import org.http4k.core.Response
import org.http4k.core.Status

val errorFilter = Filter({ next ->
    { request ->
        try {
            next(request)
        } catch (e: NotFoundException) {
            fail(Status.NOT_FOUND, e, "nao encontrado", trace = false)
        } catch (e: EngineBusyException) {
            fail(Status.CONFLICT, e, "engine ocupada", trace = false)
        } catch (e: IllegalArgumentException) {
            fail(Status.BAD_REQUEST, e, "requisicao invalida", trace = false)
        } catch (e: SerializationException) {
            fail(Status.BAD_REQUEST, e, "corpo malformado", trace = false)
        } catch (e: EngineException.PlanError) {
            fail(Status.UNPROCESSABLE_ENTITY, e, "plano invalido", trace = false)
        } catch (e: EngineException.StorageError) {
            fail(Status.BAD_GATEWAY, e, "erro de storage", trace = true, exposeMessage = false)
        } catch (e: EngineException) {
            fail(Status.INTERNAL_SERVER_ERROR, e, "falha na engine", trace = true, exposeMessage = false)
        }
    }
})

private fun fail(status: Status, e: Throwable, fallback: String, trace: Boolean, exposeMessage: Boolean = true): Response {
    val detail = orDefault(e.message, fallback)
    logError("${status.code} ${e.javaClass.simpleName}: $detail", if (trace) e else null)
    return errorResponse(status, if (exposeMessage) detail else fallback)
}
