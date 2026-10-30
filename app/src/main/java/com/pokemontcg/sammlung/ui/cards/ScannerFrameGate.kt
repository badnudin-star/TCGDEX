package com.pokemontcg.sammlung.ui.cards

import java.util.concurrent.atomic.AtomicBoolean

/** A non-blocking single-flight gate for asynchronous camera-frame processing. */
internal class ScannerFrameGate {
    private val inFlight = AtomicBoolean(false)

    /** Returns true to the one caller allowed to start processing a frame. */
    fun tryAcquire(): Boolean = inFlight.compareAndSet(false, true)

    /** Releases the gate after processing finishes or setup fails. */
    fun release() {
        inFlight.set(false)
    }
}
