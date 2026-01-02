package com.happy.jesshome.ui.ir

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.happy.jesshome.R
import com.happy.jesshome.util.GreeAcFrames
import com.happy.jesshome.util.GreeIrCandidates
import com.happy.jesshome.util.Hex
import com.happy.jesshome.util.IrRawParser
import com.happy.jesshome.util.IrSender

class IrActivity : AppCompatActivity(R.layout.activity_ir) {

    private companion object {
        private const val PREFS_NAME: String = "ir_codes"
        private const val KEY_FREQ: String = "freq"
        private const val KEY_ON_RAW: String = "gree_on_raw"
        private const val KEY_OFF_RAW: String = "gree_off_raw"
        private const val KEY_CANDIDATE_INDEX: String = "gree_candidate_index"
        private const val DEFAULT_FREQ_HZ: Int = 38_000
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 这里需要处理系统状态栏的安全区，否则内容可能会顶到状态栏下面。
        applySystemBarInsets()

        val tvFrame = findViewById<TextView>(R.id.tv_frame)
        val btnOn = findViewById<Button>(R.id.btn_gree_on)
        val btnOff = findViewById<Button>(R.id.btn_gree_off)
        val btnSave = findViewById<Button>(R.id.btn_save_ir)
        val tvCandidate = findViewById<TextView>(R.id.tv_candidate)
        val btnPrevCandidate = findViewById<Button>(R.id.btn_prev_candidate)
        val btnNextCandidate = findViewById<Button>(R.id.btn_next_candidate)
        val etRawOn = findViewById<EditText>(R.id.et_ir_raw_on)
        val etRawOff = findViewById<EditText>(R.id.et_ir_raw_off)
        val etFreq = findViewById<EditText>(R.id.et_ir_freq)

        val sp = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var candidateIndex = sp.getInt(KEY_CANDIDATE_INDEX, 0)

        loadPrefsInto(sp, etFreq, etRawOn, etRawOff)
        applyCandidateToViews(candidateIndex, tvCandidate, etFreq, etRawOn, etRawOff, overwriteRaw = false)

        fun showAndCopy(bytes: ByteArray, label: String) {
            val hex = Hex.toHex(bytes)
            tvFrame.text = hex
            copyToClipboard(label, hex)
            Log.i("IrActivity", "Gree frame($label)=$hex")
            Toast.makeText(this, "已生成并复制：$label", Toast.LENGTH_SHORT).show()
        }

        btnOn.setOnClickListener {
            val ok = tryTransmitIr(tvFrame, etFreq, etRawOn, "格力空调-开机")
            if (!ok) showAndCopy(GreeAcFrames.powerOn(), "格力空调-开机")
        }

        btnOff.setOnClickListener {
            val ok = tryTransmitIr(tvFrame, etFreq, etRawOff, "格力空调-关机")
            if (!ok) showAndCopy(GreeAcFrames.powerOff(), "格力空调-关机")
        }

        btnPrevCandidate.setOnClickListener {
            if (GreeIrCandidates.list.isEmpty()) return@setOnClickListener
            candidateIndex = ((candidateIndex - 1) + GreeIrCandidates.list.size) % GreeIrCandidates.list.size
            sp.edit().putInt(KEY_CANDIDATE_INDEX, candidateIndex).apply()
            applyCandidateToViews(candidateIndex, tvCandidate, etFreq, etRawOn, etRawOff, overwriteRaw = true)
            Toast.makeText(this, "已切换：${GreeIrCandidates.list[candidateIndex].name}", Toast.LENGTH_SHORT).show()
        }

        btnNextCandidate.setOnClickListener {
            if (GreeIrCandidates.list.isEmpty()) return@setOnClickListener
            candidateIndex = (candidateIndex + 1) % GreeIrCandidates.list.size
            sp.edit().putInt(KEY_CANDIDATE_INDEX, candidateIndex).apply()
            applyCandidateToViews(candidateIndex, tvCandidate, etFreq, etRawOn, etRawOff, overwriteRaw = true)
            Toast.makeText(this, "已切换：${GreeIrCandidates.list[candidateIndex].name}", Toast.LENGTH_SHORT).show()
        }

        btnSave.setOnClickListener {
            savePrefsFrom(sp, etFreq, etRawOn, etRawOff)
            tvFrame.text = "已保存红外码：开机=${etRawOn.text?.length ?: 0}字，关机=${etRawOff.text?.length ?: 0}字。"
            Toast.makeText(this, "已保存到本机。", Toast.LENGTH_SHORT).show()
        }
    }

    private fun applySystemBarInsets() {
        val scroll = findViewById<android.view.View>(R.id.scroll_container)
        val density = resources.displayMetrics.density
        val designPaddingPx = (20f * density).toInt()

        ViewCompat.setOnApplyWindowInsetsListener(scroll) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(
                left = designPaddingPx,
                top = sys.top + designPaddingPx,
                right = designPaddingPx,
                bottom = sys.bottom + designPaddingPx
            )
            insets
        }
        ViewCompat.requestApplyInsets(scroll)
    }

    private fun copyToClipboard(label: String, text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    private fun tryTransmitIr(
        tv: TextView,
        etFreq: EditText,
        etRaw: EditText,
        label: String
    ): Boolean {
        if (!IrSender.hasEmitter(this)) {
            tv.text = "你的手机当前环境不支持红外发射。"
            Toast.makeText(this, "未检测到红外发射器。", Toast.LENGTH_SHORT).show()
            return false
        }

        val rawText = etRaw.text?.toString().orEmpty().trim()
        if (rawText.isEmpty()) {
            tv.text = "请先粘贴该空调可用的红外 raw 波形。"
            Toast.makeText(this, "请先粘贴 raw 波形。", Toast.LENGTH_SHORT).show()
            return false
        }

        val freq = etFreq.text?.toString()?.toIntOrNull() ?: DEFAULT_FREQ_HZ
        val parsed = IrRawParser.parsePatternUs(rawText)
        val pattern = normalizePattern(parsed)
        if (pattern.size < 6) {
            tv.text = "raw 波形格式不正确：数量太少，至少需要若干对高低电平时长。"
            Toast.makeText(this, "raw 波形数量太少。", Toast.LENGTH_SHORT).show()
            return false
        }

        return try {
            IrSender.transmit(this, freq, pattern)
            tv.text = "已发射红外：$label\nfreq=$freq\nlen=${pattern.size}"
            Toast.makeText(this, "已发射：$label", Toast.LENGTH_SHORT).show()
            true
        } catch (t: Throwable) {
            tv.text = "红外发射失败：${t.message ?: t.javaClass.simpleName}"
            Toast.makeText(this, "红外发射失败。", Toast.LENGTH_SHORT).show()
            false
        }
    }

    private fun normalizePattern(input: IntArray): IntArray {
        if (input.isEmpty()) return input

        // 红外 raw 波形必须是亮/灭成对；如果是奇数长度，自动丢弃最后一个，避免直接判失败。
        val trimmed = if (input.size % 2 == 0) input else input.copyOf(input.size - 1)

        // 去掉前后的异常 0 值，避免部分来源包含 0 造成发射异常。
        var start = 0
        var end = trimmed.size
        while (start < end && trimmed[start] <= 0) start++
        while (end > start && trimmed[end - 1] <= 0) end--
        return if (start == 0 && end == trimmed.size) trimmed else trimmed.copyOfRange(start, end)
    }

    private fun loadPrefsInto(sp: android.content.SharedPreferences, etFreq: EditText, etOn: EditText, etOff: EditText) {
        val freq = sp.getInt(KEY_FREQ, DEFAULT_FREQ_HZ)
        val on = sp.getString(KEY_ON_RAW, "").orEmpty()
        val off = sp.getString(KEY_OFF_RAW, "").orEmpty()

        if (etFreq.text.isNullOrBlank()) etFreq.setText(freq.toString())
        if (etOn.text.isNullOrBlank()) etOn.setText(on)
        if (etOff.text.isNullOrBlank()) etOff.setText(off)
    }

    private fun savePrefsFrom(sp: android.content.SharedPreferences, etFreq: EditText, etOn: EditText, etOff: EditText) {
        val freq = etFreq.text?.toString()?.toIntOrNull() ?: DEFAULT_FREQ_HZ
        sp.edit()
            .putInt(KEY_FREQ, freq)
            .putString(KEY_ON_RAW, etOn.text?.toString().orEmpty())
            .putString(KEY_OFF_RAW, etOff.text?.toString().orEmpty())
            .apply()
    }

    private fun applyCandidateToViews(
        index: Int,
        tvCandidate: TextView,
        etFreq: EditText,
        etOn: EditText,
        etOff: EditText,
        overwriteRaw: Boolean,
    ) {
        if (GreeIrCandidates.list.isEmpty()) {
            tvCandidate.text = "当前候选码：无"
            return
        }
        val safeIndex = index.coerceIn(0, GreeIrCandidates.list.size - 1)
        val c = GreeIrCandidates.list[safeIndex]
        tvCandidate.text = "当前候选码：${c.name}（${safeIndex + 1}/${GreeIrCandidates.list.size}）"

        if (overwriteRaw) {
            etFreq.setText(c.frequencyHz.toString())
            etOn.setText(c.powerOnRaw)
            etOff.setText(c.powerOffRaw ?: c.powerOnRaw)
        } else {
            if (etFreq.text.isNullOrBlank()) etFreq.setText(c.frequencyHz.toString())
            if (etOn.text.isNullOrBlank()) etOn.setText(c.powerOnRaw)
            if (etOff.text.isNullOrBlank()) etOff.setText(c.powerOffRaw ?: c.powerOnRaw)
        }
    }
}


