package com.chaoshangka.manager.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chaoshangka.manager.di.AppContainer
import com.chaoshangka.manager.ui.components.BarcodeView
import com.chaoshangka.manager.ui.theme.BarcodeBlack
import com.chaoshangka.manager.ui.theme.BarcodeWhite
import com.chaoshangka.manager.ui.viewmodel.AppViewModelFactory
import com.chaoshangka.manager.ui.viewmodel.CardDetailViewModel

/**
 * 全螢幕條碼：超商櫃檯直接出示用。
 *
 *  - 白底黑條碼，維持原始比例、不裁切。
 *  - 進入畫面會把螢幕亮度調到最高，離開畫面（onDispose）會還原原本設定。
 *  - 不誇大：實際可否被掃描仍須以現場設備為準。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullscreenBarcodeScreen(
    container: AppContainer,
    cardId: Long,
    onBack: () -> Unit,
) {
    val viewModel: CardDetailViewModel = viewModel(factory = AppViewModelFactory(container))
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(cardId) { viewModel.setCardId(cardId) }

    val context = LocalContext.current
    DisposableEffect(Unit) {
        val window = context.findActivity()?.window
        val previous = window?.attributes?.screenBrightness
        window?.let {
            val attributes = it.attributes
            attributes.screenBrightness = 1f
            it.attributes = attributes
        }
        onDispose {
            window?.let {
                val attributes = it.attributes
                attributes.screenBrightness = previous ?: -1f
                it.attributes = attributes
            }
        }
    }

    Scaffold(
        containerColor = BarcodeWhite,
        contentColor = BarcodeBlack,
        topBar = {
            TopAppBar(
                title = { Text(text = "條碼", color = BarcodeBlack) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "關閉", tint = BarcodeBlack)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BarcodeWhite),
            )
        },
    ) { padding ->
        val card = state.card
        if (card == null) {
            Column(modifier = Modifier.padding(padding).padding(16.dp)) {
                Text("載入中…", color = BarcodeBlack)
            }
            return@Scaffold
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = card.productName.ifBlank { "（未命名商品）" },
                        color = BarcodeBlack,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = card.storeType.displayName + if (card.denomination != null) "・${card.denomination} 元" else "",
                        color = BarcodeBlack,
                    )
                }
            }
            items(card.barcodes.sortedByDescending { it.isPrimary }, key = { it.id }) { barcode ->
                Surface(color = BarcodeWhite) {
                    BarcodeView(barcode = barcode, photoPath = card.originalImagePath, maxWidthDp = 420)
                }
            }
            item {
                Text(
                    text = "請將螢幕對準櫃檯掃描設備。實際可掃描性依現場設備與條碼狀況而定。",
                    color = BarcodeBlack,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
