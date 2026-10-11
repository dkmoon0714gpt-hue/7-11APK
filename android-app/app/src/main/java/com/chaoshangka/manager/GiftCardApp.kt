package com.chaoshangka.manager

import android.app.Application
import com.chaoshangka.manager.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class GiftCardApp : Application() {

    lateinit var container: AppContainer
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // 第一次啟動寫入預設分類；已有資料不會被覆蓋。
        applicationScope.launch {
            runCatching { container.seedIfNeeded() }
        }
    }
}
