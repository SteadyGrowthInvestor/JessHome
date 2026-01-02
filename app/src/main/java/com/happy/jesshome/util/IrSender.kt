package com.happy.jesshome.util

import android.content.Context
import android.hardware.ConsumerIrManager

object IrSender {

    fun hasEmitter(context: Context): Boolean {
        val ir = context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
        return ir?.hasIrEmitter() == true
    }

    /**
     * 发送红外 raw 波形。
     *
     * @param frequencyHz 红外载波频率，通常为 38000Hz。
     * @param patternUs 交替的“亮/灭”持续时间数组，单位为微秒。
     */
    fun transmit(context: Context, frequencyHz: Int, patternUs: IntArray) {
        val ir = context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
            ?: error("ConsumerIrManager 不可用。")
        ir.transmit(frequencyHz, patternUs)
    }
}


