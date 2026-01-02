package com.happy.jesshome.util

object Hex {

    fun toHex(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        val sb = StringBuilder(bytes.size * 3)
        for (i in bytes.indices) {
            val b = bytes[i].toInt() and 0xFF
            sb.append(b.toString(16).padStart(2, '0').uppercase())
            if (i != bytes.lastIndex) sb.append(' ')
        }
        return sb.toString()
    }
}


