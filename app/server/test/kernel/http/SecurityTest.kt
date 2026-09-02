package dbest.kernel.http

import dbest.features.sessions.Sessions
import dbest.kernel.json.parsedJson
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SecurityTest {

    @Test
    fun `mutating routes require the per-launch token`() {
        val app = router(Sessions(), "test-token")

        assertEquals(Status.UNAUTHORIZED, app(Request(Method.POST, "/sessions")).status)
        assertEquals(
            Status.OK,
            app(Request(Method.POST, "/sessions").header("X-DBest-Token", "test-token")).status,
        )
    }

    @Test
    fun `bootstrap serves the token without caching it`() {
        val response = router(Sessions(), "test-token")(Request(Method.GET, "/bootstrap"))

        assertEquals(Status.OK, response.status)
        assertEquals("no-store", response.header("Cache-Control"))
        assertEquals("test-token", parsedJson(response.bodyString()).jsonObject.getValue("token").jsonPrimitive.content)
    }

    @Test
    fun `network server is bound only to loopback`() {
        val server = loopbackServer({ org.http4k.core.Response(Status.OK) }, 0)
        try {
            server.start()
            assertTrue(server.address.address.isLoopbackAddress)
        } finally {
            server.stop(0)
        }
    }
}
