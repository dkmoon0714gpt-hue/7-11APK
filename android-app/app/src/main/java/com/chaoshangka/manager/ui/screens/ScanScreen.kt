package com.chaoshangka.manager.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.chaoshangka.manager.di.AppContainer
import com.chaoshangka.manager.scanner.LiveBarcodeAnalyzer
import com.chaoshangka.manager.ui.components.AppTopBar
import com.chaoshangka.manager.ui.theme.InfoContainer
import com.chaoshangka.manager.ui.theme.InfoBlue

/**
 * 拍照掃描。
 *
 *  - 取景框 + 自動對焦 + 閃光燈切換 + 重新拍攝 + 前後鏡頭切換。
 *  - 相機權限被拒絕時提供「從相簿選取」與「手動新增」兩條替代路徑。
 *  - 即時顯示鏡頭內是否已偵測到條碼（僅提示，實際內容以拍下的照片辨識為準）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onPhotoReady: (String) -> Unit,
    onManualEntry: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var flashEnabled by remember { mutableStateOf(false) }
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var liveBarcodeCount by remember { mutableIntStateOf(0) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasPermission = granted }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            val path = container.photoStore.importFromUri(uri)
            if (path != null) onPhotoReady(path) else errorMessage = "無法讀取選取的照片"
        }
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            errorMessage = null
        }
    }

    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    if (hasPermission) {
        DisposableEffect(lensFacing, lifecycleOwner) {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            val executor = ContextCompat.getMainExecutor(context)
            var analyzer: LiveBarcodeAnalyzer? = null

            cameraProviderFuture.addListener({
                runCatching {
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val capture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .setFlashMode(if (flashEnabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_AUTO)
                        .build()
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    analyzer = LiveBarcodeAnalyzer { detected ->
                        liveBarcodeCount = detected.count { it.hasValue }
                    }
                    analysis.setAnalyzer(executor, analyzer!!)

                    val selector = CameraSelector.Builder()
                        .requireLensFacing(lensFacing)
                        .build()

                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, capture, analysis)
                    imageCapture = capture
                }.onFailure { errorMessage = "無法啟動相機：${it.message}" }
            }, executor)

            onDispose {
                runCatching { cameraProviderFuture.get().unbindAll() }
                analyzer?.close()
            }
        }
    }

    Scaffold(
        topBar = { AppTopBar(title = "掃描卡片", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!hasPermission) {
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("沒有相機權限", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "你可以在系統設定中開啟相機權限，或改用下列方式：從相簿選取已經拍好的卡片照片，或完全手動輸入卡片資料。",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                                Text("再次要求權限")
                            }
                            OutlinedButton(onClick = {
                                galleryLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                )
                            }) { Text("從相簿選取") }
                        }
                        OutlinedButton(onClick = onManualEntry, modifier = Modifier.fillMaxWidth()) {
                            Text("手動新增卡片")
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(3f / 4f),
                ) {
                    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
                    // 卡片取景框（僅為視覺提示，不是用來固定座標擷取）
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth(0.86f)
                            .aspectRatio(1.6f)
                            .border(2.dp, Color.White, RoundedCornerShape(12.dp)),
                    )
                }

                if (liveBarcodeCount > 0) {
                    Surface(color = InfoContainer, shape = MaterialTheme.shapes.small) {
                        Text(
                            text = "鏡頭內偵測到 $liveBarcodeCount 組條碼，可以按快門拍下卡片",
                            color = InfoBlue,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { flashEnabled = !flashEnabled }) {
                        Icon(
                            imageVector = if (flashEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            contentDescription = "閃光燈",
                        )
                    }
                    Button(
                        onClick = {
                            val capture = imageCapture
                            if (capture == null) {
                                errorMessage = "相機尚未就緒"
                                return@Button
                            }
                            val file = container.photoStore.newPhotoFile()
                            val options = ImageCapture.OutputFileOptions.Builder(file).build()
                            capture.takePicture(
                                options,
                                ContextCompat.getMainExecutor(context),
                                object : ImageCapture.OnImageSavedCallback {
                                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                        onPhotoReady(file.absolutePath)
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        errorMessage = "拍照失敗：${exception.message}"
                                    }
                                },
                            )
                        },
                        modifier = Modifier.size(84.dp),
                    ) {
                        Icon(imageVector = Icons.Default.CameraAlt, contentDescription = "拍照")
                    }
                    IconButton(onClick = {
                        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                            CameraSelector.LENS_FACING_FRONT
                        } else {
                            CameraSelector.LENS_FACING_BACK
                        }
                    }) {
                        Icon(imageVector = Icons.Default.FlipCameraAndroid, contentDescription = "切換鏡頭")
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            galleryLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(imageVector = Icons.Default.PhotoLibrary, contentDescription = null)
                        Text(" 從相簿選取")
                    }
                    OutlinedButton(onClick = onManualEntry, modifier = Modifier.weight(1f)) {
                        Text("手動新增")
                    }
                }

                Text(
                    text = "請讓整張卡片進入畫面，並保持光線充足；多組條碼會全部辨識，再由你選擇主要條碼。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
