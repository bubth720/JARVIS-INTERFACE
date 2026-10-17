package com.jarvis.interfaceapp.avatar

/** Media boundary retained for future MP4/2D/hybrid backends; no player or IPC. */
enum class AvatarMediaKind { MP4, ANIMATION_2D, HYBRID }

/** Host visibility, viewport size and explicit disposal; implemented by the local prototype. */
interface AvatarSession {
    fun setVisible(visible: Boolean)
    fun resize(widthPx: Int, heightPx: Int)
    fun release()
}
