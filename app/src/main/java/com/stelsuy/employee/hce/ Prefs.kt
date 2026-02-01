package com.stelsuy.employee.hce

import android.content.Context
import java.security.SecureRandom

object Prefs {
    private const val PREFS = "employee_prefs"

    // Вкл/выкл эмуляцию карты (если нужно выключать)
    private const val KEY_ENABLED = "enabled"

    // Твой "простой ID", который вводит админ
    private const val KEY_EMPLOYEE_ID = "employee_id"

    // Без I, O, L, 0, 1 — чтобы не путались при вводе
    private const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
    private val rnd = SecureRandom()

    fun isEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_ENABLED, true) // по умолчанию включено
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    /**
     * Возвращает сохранённый employee_id или создаёт новый при первом запуске.
     * Формат: XXXX-XXXX (пример: K7M3-P9Q2)
     */
    fun getOrCreateEmployeeId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_EMPLOYEE_ID, null)
        if (!existing.isNullOrBlank()) return existing

        val id = generateSimpleId()
        prefs.edit().putString(KEY_EMPLOYEE_ID, id).apply()
        return id
    }

    /**
     * Если вдруг надо пересоздать ID (опционально)
     */
    fun regenerateEmployeeId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val id = generateSimpleId()
        prefs.edit().putString(KEY_EMPLOYEE_ID, id).apply()
        return id
    }

    private fun generateSimpleId(): String {
        return "${randomChars(4)}-${randomChars(4)}"
    }

    private fun randomChars(n: Int): String {
        val sb = StringBuilder(n)
        repeat(n) {
            val idx = rnd.nextInt(ALPHABET.length)
            sb.append(ALPHABET[idx])
        }
        return sb.toString()
    }
}
