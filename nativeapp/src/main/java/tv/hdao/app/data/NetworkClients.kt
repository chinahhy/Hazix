package tv.hdao.app.data

import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress
import java.util.concurrent.TimeUnit

internal const val API_USER_AGENT = "Hazix-Android"
internal const val MEDIA_USER_AGENT = "Mozilla/5.0 (Linux; Android) Hazix-Android"
internal const val MEDIA_REFERER = "https://hdao.tv/"

object NetworkClients {
    private val bootstrapClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private fun encryptedDns(url: String, vararg addresses: String): DnsOverHttps =
        DnsOverHttps.Builder()
            .client(bootstrapClient)
            .url(url.toHttpUrl())
            .includeIPv6(false)
            .bootstrapDnsHosts(*addresses.map(InetAddress::getByName).toTypedArray())
            .build()

    private val aliDns = encryptedDns(
        "https://dns.alidns.com/dns-query",
        "223.5.5.5",
        "223.6.6.6",
    )

    private val cloudflareDns = encryptedDns(
        "https://cloudflare-dns.com/dns-query",
        "1.1.1.1",
        "1.0.0.1",
    )

    private val resilientDns: Dns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> =
            runCatching { aliDns.lookup(hostname) }
                .recoverCatching { cloudflareDns.lookup(hostname) }
                .getOrElse { Dns.SYSTEM.lookup(hostname) }
    }

    val httpClient: OkHttpClient = bootstrapClient.newBuilder()
        .dns(resilientDns)
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(18, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Same client but resolving through the system resolver instead of
     * DNS-over-HTTPS.
     *
     * On a network whose proxy only routes the fake IPs it hands out itself,
     * the address an encrypted resolver returns is the real one and connecting
     * to it can time out. This client is the second attempt for exactly that
     * case, and the encrypted path covers the opposite one.
     */
    val systemDnsClient: OkHttpClient = bootstrapClient.newBuilder()
        .dns(Dns.SYSTEM)
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(18, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()
}
