package com.droidbubble.app.util

object UpstreamEndpoints {
    const val RELEASES_API = "https://api.github.com/repos/Velocity081/droidtools/releases"
    const val RELEASES_ATOM = "https://github.com/Velocity081/droidtools/releases.atom"

    fun downloadUrl(tag: String): String {
        return "https://github.com/Velocity081/droidtools/releases/download/$tag/Droidtools.apk"
    }
}
