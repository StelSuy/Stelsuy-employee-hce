package com.stelsuy.employee.hce

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val tvCode = findViewById<TextView>(R.id.tvEmployeeCode)
        val btnCopy = findViewById<Button>(R.id.btnCopy)

        // ВАЖНО: метод называется getOrCreateEmployeeId()
        val code = Prefs.getOrCreateEmployeeId(this)
        tvCode.text = code

        btnCopy.setOnClickListener {
            copyToClipboard("Employee ID", code)
            Toast.makeText(this, "ID скопирован: $code", Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyToClipboard(label: String, text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
    }
}
