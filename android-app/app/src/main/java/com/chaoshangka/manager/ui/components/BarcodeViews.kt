package com.chaoshangka.manager.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.chaoshangka.manager.barcode.BarcodeRenderer
import com.chaoshangka.manager.domain.model.Barcode
import com.chaoshangka.manager.ui.theme.BarcodeBlack
import com.chaoshangka.manager.ui.theme.BarcodeWhite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private sealed interface BarcodeImageState {
    data class Rendered(val bitmap: Bitmap) : BarcodeImageState

    data class OriginalPhoto(val bitmap: Bitmap) : BarcodeImageState

    data object None : BarcodeImageState
}

/**
 * 條碼展示。
 *
 *  - 能以本機函式庫可靠重建時，用 ZXing 重新繪製相同內容的條碼。
 *  - 無法可靠重建時，改顯示原始卡片照片，**不會**畫出錯誤條碼。
 *  - 一維條碼保留留白與比例；二維碼維持正方形比例。
 */
@Composable
fun BarcodeView(
    barcode: Barcode,
    photoPath: String?,
    modifier: Modifier = Modifier,
    maxWidthDp: Int = 360,
) {
    val density = LocalDensity.current
    val widthPx = with(density) { maxWidthDp.dp.roundToPx() }

    val state by produceState<BarcodeImageState>(
        initialValue = BarcodeImageState.None,
        barcode.rawValue,
        barcode.barcodeFormat,
        widthPx,
        photoPath,
    ) {
        value = BarcodeImageState.None
        val rendered = withContext(Dispatchers.Default) {
            BarcodeRenderer.render(barcode.rawValue, barcode.barcodeFormat, widthPx)
        }
        if (rendered != null) {
            value = BarcodeImageState.Rendered(rendered)
        } else {
            val photo = withContext(Dispatchers.IO) { decodePhoto(photoPath) }
            value = if (photo != null) BarcodeImageState.OriginalPhoto(photo) else BarcodeImageState.None
        }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val widthModifier = if (barcode.barcodeFormat.isTwoDimensional) {
            Modifier.width(260.dp)
        } else {
            Modifier.fillMaxWidth()
        }

        when (val current = state) {
            is BarcodeImageState.Rendered -> Image(
                bitmap = current.bitmap.asImageBitmap(),
                contentDescription = "${barcode.barcodeFormat.displayName} 條碼",
                contentScale = ContentScale.FillWidth,
                modifier = widthModifier
                    .background(BarcodeWhite)
                    .padding(8.dp),
            )

            is BarcodeImageState.OriginalPhoto -> Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    bitmap = current.bitmap.asImageBitmap(),
                    contentDescription = "原始卡片照片",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "無法可靠重新編碼，顯示原始卡片照片，請以原卡條碼為準。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            BarcodeImageState.None -> Text(
                text = "沒有可顯示的條碼影像，請重新拍攝卡片或手動補登條碼。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }

        if (barcode.rawValue.isNotBlank()) {
            Text(
                text = barcode.rawValue,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = BarcodeBlack,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .background(BarcodeWhite)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}

internal fun decodePhoto(path: String?, maxSize: Int = 2000): Bitmap? {
    if (path.isNullOrBlank()) return null
    return runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (bounds.outWidth / sample > maxSize || bounds.outHeight / sample > maxSize) {
            sample *= 2
        }
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()
}
