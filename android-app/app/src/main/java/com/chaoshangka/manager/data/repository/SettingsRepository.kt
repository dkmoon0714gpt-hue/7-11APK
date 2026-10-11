package com.chaoshangka.manager.data.repository

import android.content.Context
import com.chaoshangka.manager.domain.model.GroupSortOrder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 本機偏好設定（提醒門檻與預設排序），儲存於 SharedPreferences，不涉及網路。
 * 7 天與 30 天為預設提醒區間，可由使用者調整。
 */
class SettingsRepository(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _warningDays = MutableStateFlow(prefs.getInt(KEY_WARNING_DAYS, DEFAULT_WARNING_DAYS))
    val warningDays: StateFlow<Int> = _warningDays.asStateFlow()

    private val _farWarningDays = MutableStateFlow(prefs.getInt(KEY_FAR_WARNING_DAYS, DEFAULT_FAR_WARNING_DAYS))
    val farWarningDays: StateFlow<Int> = _farWarningDays.asStateFlow()

    private val _groupSortOrder = MutableStateFlow(
        GroupSortOrder.entries.firstOrNull { it.name == prefs.getString(KEY_SORT, null) }
            ?: GroupSortOrder.EXPIRY,
    )
    val groupSortOrder: StateFlow<GroupSortOrder> = _groupSortOrder.asStateFlow()

    fun setWarningDays(days: Int) {
        val value = days.coerceIn(1, 90)
        prefs.edit().putInt(KEY_WARNING_DAYS, value).apply()
        _warningDays.value = value
    }

    fun setFarWarningDays(days: Int) {
        val value = days.coerceIn(1, 180)
        prefs.edit().putInt(KEY_FAR_WARNING_DAYS, value).apply()
        _farWarningDays.value = value
    }

    fun setGroupSortOrder(order: GroupSortOrder) {
        prefs.edit().putString(KEY_SORT, order.name).apply()
        _groupSortOrder.value = order
    }

    companion object {
        private const val PREFS_NAME = "chaoshangka_settings"
        private const val KEY_WARNING_DAYS = "warning_days"
        private const val KEY_FAR_WARNING_DAYS = "far_warning_days"
        private const val KEY_SORT = "group_sort_order"
        const val DEFAULT_WARNING_DAYS = 7
        const val DEFAULT_FAR_WARNING_DAYS = 30
    }
}
