package com.example.motionart

import com.example.motionart.internal.providerRequest
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProviderRequestTest {
    @Test(expected = CancellationException::class)
    fun `cancelled provider requests stop instead of trying other catalogs`() = runBlocking {
        providerRequest<Unit>("test") { throw CancellationException("Cancelled") }
        Unit
    }

    @Test
    fun `transport failures are logged misses while successful results are preserved`() = runBlocking {
        assertNull(providerRequest<Unit>("test") { throw IOException("Offline") })
        assertEquals("result", providerRequest("test") { "result" })
    }
}
