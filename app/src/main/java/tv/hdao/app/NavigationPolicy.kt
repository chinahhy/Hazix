package tv.hdao.app

import java.net.URI
import java.util.Locale

/** Keeps top-level navigation inside the requested single-site app boundary. */
object NavigationPolicy {
    private const val PRIMARY_HOST = "hdao.tv"

    fun isAllowed(url: String): Boolean {
        if (url == "about:blank") return true
        if (url.startsWith("blob:https://$PRIMARY_HOST/")) return true

        return runCatching {
            val uri = URI(url)
            uri.scheme.equals("https", ignoreCase = true) && isAllowedHost(uri.host)
        }.getOrDefault(false)
    }

    fun isAllowedHost(host: String?): Boolean {
        val normalized = host?.lowercase(Locale.US)?.trimEnd('.') ?: return false
        return normalized == PRIMARY_HOST || normalized.endsWith(".$PRIMARY_HOST")
    }
}
