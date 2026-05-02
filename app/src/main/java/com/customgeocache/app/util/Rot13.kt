package com.customgeocache.app.util

/** Geocaching hints jsou v ROT13. Tahle funkce je dekóduje (i šifruje, je samoinverzní). */
object Rot13 {
    fun decode(text: String): String = buildString(text.length) {
        for (c in text) {
            append(
                when (c) {
                    in 'A'..'Z' -> ('A' + ((c - 'A' + 13) % 26))
                    in 'a'..'z' -> ('a' + ((c - 'a' + 13) % 26))
                    else -> c
                }
            )
        }
    }
}
