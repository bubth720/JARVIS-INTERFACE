package com.jarvis.interfaceapp.avatar

/** Design boundary only; no renderer, player, service or asset is loaded. */
enum class AvatarMediaKind { MP4, ANIMATION_2D, HYBRID }

/** A future host must pause hidden/background media and release rendering resources. */
interface AvatarSession {
    fun setVisible(visible: Boolean)
    fun resize(widthPx: Int, heightPx: Int)
    fun release()
}
