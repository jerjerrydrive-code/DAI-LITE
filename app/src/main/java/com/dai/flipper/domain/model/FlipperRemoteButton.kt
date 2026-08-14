package com.dai.flipper.domain.model

/**
 * Physical navigation buttons exposed by Flipper's GUI input RPC.
 */
enum class FlipperRemoteButton(
    val label: String
) {
    UP("Up"),
    DOWN("Down"),
    LEFT("Left"),
    RIGHT("Right"),
    OK("OK"),
    BACK("Back")
}
