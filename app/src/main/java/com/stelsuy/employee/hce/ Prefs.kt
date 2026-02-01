package com.stelsuy.employee.hce

import android.content.Context
import java.util.UUID

object Prefs {
    private const val SP = "employee_hce_prefs"
    private const val KEY_EMP_ID = "employee_id"
    private const val KEY_ENABLED = "enabled"

    fun isEnabled(ctx: Context): Boolean {
        val sp = ctx.getSharedPreferences(SP, Context.MODE_PRIVATE)
        return sp.getBoolean(KEY_ENABLED, true)
    }

    fun setEnabled(ctx: Context, enabled: Boolean) {
        val sp = ctx.getSharedPreferences(SP, Context.MODE_PRIVATE)
        sp.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    /** Генерує і зберігає стабільний ID при першому запуску */
    fun getOrCreateEmployeeId(ctx: Context): String {
        val sp = ctx.getSharedPreferences(SP, Context.MODE_PRIVATE)

        val existing = sp.getString(KEY_EMP_ID, null)
        if (!existing.isNullOrBlank()) return existing

        // Короткий читабельний код: EMP-XXXXXXXX (8 символів)
        val id = "EMP-" + UUID.randomUUID()
            .toString()
            .replace("-", "")
            .uppercase()
            .substring(0, 8)

        sp.edit().putString(KEY_EMP_ID, id).apply()
        return id
    }

    fun peekEmployeeId(ctx: Context): String? {
        val sp = ctx.getSharedPreferences(SP, Context.MODE_PRIVATE)
        val v = sp.getString(KEY_EMP_ID, null)
        return v?.takeIf { it.isNotBlank() }
    }
}