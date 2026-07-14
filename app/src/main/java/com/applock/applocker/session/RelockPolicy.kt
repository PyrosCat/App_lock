package com.applock.applocker.session

/**
 * When a protected app relocks after the user leaves it
 * (Requirements §2 Lock Engine — custom timeout settings).
 */
enum class RelockPolicy {
    /** Relock the moment the user leaves the app. */
    IMMEDIATE,

    /** 10-second buffer after leaving before relock. */
    GRACE_10S,

    /** Stay unlocked until the screen turns off. */
    SCREEN_OFF,
}
