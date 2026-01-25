package com.barcodescanner.app.data.api

import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthHeaderInterceptorTest {
    @Test
    fun intercept_addsAuthorizationHeader_whenTokenProvided() {
        val interceptor = AuthHeaderInterceptor { "token-123" }
        val chain = FakeChain(Request.Builder().url("https://example.com").build())

        interceptor.intercept(chain)

        assertEquals("Bearer token-123", chain.proceededRequest.header("Authorization"))
    }

    @Test
    fun intercept_leavesRequestUntouched_whenTokenMissing() {
        val interceptor = AuthHeaderInterceptor { null }
        val request = Request.Builder()
            .url("https://example.com")
            .addHeader("X-Test", "value")
            .build()
        val chain = FakeChain(request)

        interceptor.intercept(chain)

        assertNull(chain.proceededRequest.header("Authorization"))
        assertEquals("value", chain.proceededRequest.header("X-Test"))
    }

    private class FakeChain(
        private val original: Request
    ) : okhttp3.Interceptor.Chain {
        lateinit var proceededRequest: Request

        override fun request(): Request = original

        override fun proceed(request: Request): Response {
            proceededRequest = request
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .build()
        }

        override fun call(): okhttp3.Call {
            throw UnsupportedOperationException("not used")
        }

        override fun connectTimeoutMillis(): Int = 0

        override fun withConnectTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit): okhttp3.Interceptor.Chain {
            return this
        }

        override fun readTimeoutMillis(): Int = 0

        override fun withReadTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit): okhttp3.Interceptor.Chain {
            return this
        }

        override fun writeTimeoutMillis(): Int = 0

        override fun withWriteTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit): okhttp3.Interceptor.Chain {
            return this
        }

        override fun connection(): okhttp3.Connection? = null
    }
}
