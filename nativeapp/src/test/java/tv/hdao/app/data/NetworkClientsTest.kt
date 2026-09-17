package tv.hdao.app.data

import okhttp3.Request
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkClientsTest {
    @Test
    fun encryptedDnsBypassesFakeIpAndLoadsCatalog() {
        val addresses = NetworkClients.httpClient.dns.lookup("hdao.tv")
            .mapNotNull { it.hostAddress }

        assertTrue("加密 DNS 没有返回地址", addresses.isNotEmpty())
        assertFalse(
            "仍然拿到了 Fake-IP：$addresses",
            addresses.any { it.startsWith("28.") || it.startsWith("198.18.") || it.startsWith("198.19.") },
        )

        val request = Request.Builder()
            .url("https://hdao.tv/api/vods/featured")
            .build()
        NetworkClients.httpClient.newCall(request).execute().use { response ->
            assertTrue("接口返回 ${response.code}", response.isSuccessful)
            assertTrue(response.body?.string().orEmpty().contains("\"hero\""))
        }
    }
}
