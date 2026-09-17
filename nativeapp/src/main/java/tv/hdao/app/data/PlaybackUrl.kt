package tv.hdao.app.data

private const val PLAYBACK_PROXY = "https://stream.hdao.tv/api/proxy/m3u8?url="
private const val BASE64_URL_ALPHABET =
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

internal fun playbackUrl(originalUrl: String): String {
    if (originalUrl.startsWith(PLAYBACK_PROXY)) return originalUrl
    return PLAYBACK_PROXY + originalUrl.toByteArray(Charsets.UTF_8).base64UrlNoPadding()
}

private fun ByteArray.base64UrlNoPadding(): String {
    val output = StringBuilder((size * 4 + 2) / 3)
    var index = 0
    while (index + 2 < size) {
        val value = ((this[index].toInt() and 0xff) shl 16) or
            ((this[index + 1].toInt() and 0xff) shl 8) or
            (this[index + 2].toInt() and 0xff)
        output.append(BASE64_URL_ALPHABET[value ushr 18])
        output.append(BASE64_URL_ALPHABET[(value ushr 12) and 0x3f])
        output.append(BASE64_URL_ALPHABET[(value ushr 6) and 0x3f])
        output.append(BASE64_URL_ALPHABET[value and 0x3f])
        index += 3
    }
    when (size - index) {
        1 -> {
            val value = (this[index].toInt() and 0xff) shl 16
            output.append(BASE64_URL_ALPHABET[value ushr 18])
            output.append(BASE64_URL_ALPHABET[(value ushr 12) and 0x3f])
        }
        2 -> {
            val value = ((this[index].toInt() and 0xff) shl 16) or
                ((this[index + 1].toInt() and 0xff) shl 8)
            output.append(BASE64_URL_ALPHABET[value ushr 18])
            output.append(BASE64_URL_ALPHABET[(value ushr 12) and 0x3f])
            output.append(BASE64_URL_ALPHABET[(value ushr 6) and 0x3f])
        }
    }
    return output.toString()
}
