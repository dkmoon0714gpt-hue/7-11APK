package com.chaoshangka.manager.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream

/**
 * 卡片照片的本機儲存。照片一律放在 App 私有目錄 `filesDir/card_photos/`，
 * 不會上傳到任何地方；解除安裝 APP 時系統會一併刪除。
 */
class PhotoStore(private val context: Context) {

    private val root: File
        get() = File(context.filesDir, DIR_NAME).apply { if (!exists()) mkdirs() }

    private var counter = 0

    fun newPhotoFile(prefix: String = "card"): File {
        counter += 1
        return File(root, "${prefix}_${System.currentTimeMillis()}_$counter.jpg")
    }

    /** 相簿匯入：把使用者選取的照片複製到私有目錄。 */
    fun importFromUri(uri: Uri): String? {
        val target = newPhotoFile(prefix = "import")
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(target).use { output -> input.copyTo(output) }
            } ?: return null
            target.absolutePath
        } catch (e: Exception) {
            target.delete()
            null
        }
    }

    fun delete(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching { File(path).takeIf { it.exists() && it.absolutePath.startsWith(root.absolutePath) }?.delete() }
    }

    fun exists(path: String?): Boolean =
        !path.isNullOrBlank() && File(path).exists()

    fun loadBitmap(path: String?, maxSize: Int = 1600): Bitmap? {
        if (path.isNullOrBlank() || !exists(path)) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > maxSize || bounds.outHeight / sample > maxSize) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = BitmapFactory.decodeFile(path, options) ?: return null
        return applyExifRotation(path, decoded)
    }

    private fun applyExifRotation(path: String, bitmap: Bitmap): Bitmap {
        val exif = runCatching { ExifInterface(path) }.getOrNull() ?: return bitmap
        val rotation = when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
        if (rotation == 0) return bitmap
        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /** 供備份使用：回傳目前所有照片檔（含檔名）。 */
    fun allPhotoFiles(): List<File> = root.listFiles()?.toList().orEmpty()

    /** 還原備份時把解出的檔案路徑轉成私有目錄中的新檔案。 */
    fun restorePhoto(fileName: String, bytes: ByteArray): String {
        val target = File(root, fileName)
        FileOutputStream(target).use { it.write(bytes) }
        return target.absolutePath
    }

    companion object {
        const val DIR_NAME = "card_photos"
    }
}
