package com.happy.jesshome.util

/**
 * 这是一个“临时协议示例”的格力空调开/关机指令帧构造器。
 *
 * 说明：
 * - 由于你的工程里目前还没有明确的设备通信协议（UDP/MQTT/串口等）与格力具体协议定义。
 * - 我先按常见“自定义 MCU 帧”写一个可用的占位格式，便于你在设备页直接生成并复制 hex 帧。
 *
 * 帧格式（示例）：
 * - Header: 0xA5 0x5A
 * - Len: payload 长度（1 字节）
 * - Cmd: 0x10（空调电源控制）
 * - Payload: 0x01 开机 / 0x00 关机
 * - Checksum: 从 Header 到 Payload 的逐字节求和，取低 8 位。
 */
object GreeAcFrames {

    private const val H1: Int = 0xA5
    private const val H2: Int = 0x5A
    private const val CMD_POWER: Int = 0x10

    fun powerOn(): ByteArray {
        return buildFrame(cmd = CMD_POWER, payload = byteArrayOf(0x01))
    }

    fun powerOff(): ByteArray {
        return buildFrame(cmd = CMD_POWER, payload = byteArrayOf(0x00))
    }

    private fun buildFrame(cmd: Int, payload: ByteArray): ByteArray {
        val len = payload.size and 0xFF

        val body = ByteArray(2 + 1 + 1 + payload.size + 1)
        var i = 0
        body[i++] = H1.toByte()
        body[i++] = H2.toByte()
        body[i++] = len.toByte()
        body[i++] = (cmd and 0xFF).toByte()
        payload.copyInto(body, destinationOffset = i)
        i += payload.size
        body[i] = checksum(body, endExclusive = i)
        return body
    }

    private fun checksum(bytes: ByteArray, endExclusive: Int): Byte {
        var sum = 0
        for (i in 0 until endExclusive) {
            sum = (sum + (bytes[i].toInt() and 0xFF)) and 0xFF
        }
        return sum.toByte()
    }
}


