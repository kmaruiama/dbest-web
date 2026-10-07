package dbest

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status

class ServerTest {

    @Test
    fun `writes need the per-launch token, reads do not`() {
        Harness()
            .use({ harness ->
                assertEquals(
                    Status.UNAUTHORIZED,
                    harness.app(Request(Method.POST, "/sessions").body("{\"name\":\"x\"}")).status)
                assertEquals(Status.OK, harness.app(Request(Method.GET, "/sessions")).status)
                assertEquals(
                    Status.OK, harness.send(Method.POST, "/sessions", "{\"name\":\"x\"}").status)
            })
    }

    @Test
    fun `bootstrap serves the token without letting it be cached`() {
        Harness()
            .use({ harness ->
                val response = harness.app(Request(Method.GET, "/bootstrap"))
                assertEquals("no-store", response.header("Cache-Control"))
                assertEquals("{\"token\":\"$TOKEN\"}", response.bodyString())
            })
    }

    @Test
    fun `every failure is a JSON error with its status`() {
        Harness()
            .use({ harness ->
                val malformed = harness.send(Method.POST, "/sessions", "{not json")
                assertEquals(Status.BAD_REQUEST, malformed.status)
                assertTrue(malformed.bodyString().startsWith("{\"error\":"))
                assertEquals(
                    Status.NOT_FOUND, harness.send(Method.GET, "/sessions/nope/session").status)
                val sid = harness.create()
                val unknownTag =
                    harness.send(
                        Method.POST,
                        "/sessions/$sid/commands",
                        "{\"@type\":\"explode-everything\"}")
                assertEquals(Status.BAD_REQUEST, unknownTag.status)
                assertEquals(
                    Status.NOT_FOUND,
                    harness
                        .send(Method.POST, "/sessions/open", "{\"path\":\"/no/such/file.dbest\"}")
                        .status)
            })
    }

    @Test
    fun `responses carry the security headers`() {
        Harness()
            .use({ harness ->
                val response = harness.app(Request(Method.GET, "/sessions"))
                assertEquals("nosniff", response.header("X-Content-Type-Options"))
                assertEquals("no-referrer", response.header("Referrer-Policy"))
                assertTrue(
                    response
                        .header("Content-Security-Policy")
                        .orEmpty()
                        .contains("default-src 'self'"))
            })
    }

    @Test
    fun `the server binds only to loopback`() {
        val server = loopbackServer({ Response(Status.OK) }, 0)
        try {
            server.start()
            assertTrue(server.address.address.isLoopbackAddress)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `shutdown answers before it exits`() {
        Harness()
            .use({ harness ->
                val triggered = CountDownLatch(1)
                shutdownAction = { triggered.countDown() }
                try {
                    assertEquals(Status.OK, harness.send(Method.POST, "/shutdown").status)
                    assertTrue(triggered.await(2, TimeUnit.SECONDS))
                } finally {
                    shutdownAction = { exitProcess(0) }
                }
            })
    }
}
