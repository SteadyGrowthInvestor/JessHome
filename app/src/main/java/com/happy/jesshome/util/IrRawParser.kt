package com.happy.jesshome.util

/**
 * 解析用户粘贴的红外 raw 波形文本。
 *
 * 支持格式：
 * - "9000,4500,560,560,..."（逗号分隔）
 * - "9000 4500 560 560 ..."（空格分隔）
 * - 混合分隔也可以。
 */
object IrRawParser {

    fun parsePatternUs(input: String): IntArray {
        val tokens = input
            .replace("\n", " ")
            .replace("\r", " ")
            .replace(",", " ")
            .trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }

        val list = ArrayList<Int>(tokens.size)
        for (t in tokens) {
            val v = t.toIntOrNull() ?: continue
            if (v > 0) list.add(v)
        }
        return list.toIntArray()
    }
}


