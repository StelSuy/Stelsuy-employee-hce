package com.stelsuy.employee.hce

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.nfc.NfcAdapter
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private var nfcAdapter: NfcAdapter? = null

    private lateinit var tvEmployeeCode: TextView
    private lateinit var btnCopy: Button

    private lateinit var nfcDisabledBlock: LinearLayout
    private lateinit var btnOpenNfcSettings: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Views
        tvEmployeeCode = findViewById(R.id.tvEmployeeCode)
        btnCopy = findViewById(R.id.btnCopy)
        nfcDisabledBlock = findViewById(R.id.nfcDisabledBlock)
        btnOpenNfcSettings = findViewById(R.id.btnOpenNfcSettings)

        // NFC adapter (може бути null на пристроях без NFC)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        // ✅ 1) Генеруємо/дістаємо код ОДРАЗУ і показуємо на екрані
        val empId = Prefs.getOrCreateEmployeeId(this)
        tvEmployeeCode.text = empId

        // ✅ 2) Гарантуємо, що ключ існує (для PUB/SIGN в HCE)
        try { Crypto.ensureKey() } catch (_: Exception) {}

        // Кнопка: відкрити NFC налаштування
        btnOpenNfcSettings.setOnClickListener { openNfcSettings() }

        // Кнопка: копіювати код
        btnCopy.setOnClickListener {
            val code = tvEmployeeCode.text?.toString()?.trim().orEmpty()
            if (code.isBlank() || code == "----") {
                Toast.makeText(this, "Код ще не згенеровано", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            copyToClipboard(code)
            Toast.makeText(this, "Скопійовано ✅", Toast.LENGTH_SHORT).show()
        }

        updateNfcUi()
    }

    override fun onResume() {
        super.onResume()
        updateNfcUi()
    }

    private fun updateNfcUi() {
        val adapter = nfcAdapter

        // Якщо NFC немає на пристрої — не показуємо блок, але і не ламаємось
        if (adapter == null) {
            nfcDisabledBlock.visibility = View.GONE
            btnCopy.isEnabled = true
            btnCopy.alpha = 1f
            return
        }

        val enabled = try { adapter.isEnabled } catch (_: Exception) { true }

        if (enabled) {
            nfcDisabledBlock.visibility = View.GONE
            btnCopy.isEnabled = true
            btnCopy.alpha = 1f
        } else {
            nfcDisabledBlock.visibility = View.VISIBLE
            // копіювання коду дозволяємо навіть якщо NFC вимкнено
            btnCopy.isEnabled = true
            btnCopy.alpha = 1f
        }
    }

    private fun openNfcSettings() {
        try {
            startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("employee_code", text))
    }
}