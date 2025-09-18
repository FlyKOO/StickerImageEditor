package com.example.stickerdemo

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.stickerdemo.R
import com.example.stickerdemo.ui.theme.StickerImageEditorTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.max
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            StickerImageEditorTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    StickerEditorScreen()
                }
            }
        }
    }
}

@Composable
private fun StickerEditorScreen() {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val baseBitmap = remember(context) {
        BitmapFactory.decodeResource(
            context.resources,
            R.drawable.sample_photo,
            BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
        ) ?: throw IllegalStateException("无法加载示例图片")
    }
    val baseImage = remember(baseBitmap) { baseBitmap.asImageBitmap() }
    val baseAspectRatio = remember(baseBitmap) {
        if (baseBitmap.height == 0) 1.6f else baseBitmap.width.toFloat() / baseBitmap.height.toFloat()
    }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val stickers = remember { mutableStateListOf<StickerState>() }
    var selectedStickerId by remember { mutableStateOf<Long?>(null) }
    var nextStickerId by remember { mutableStateOf(0L) }
    var isSaving by remember { mutableStateOf(false) }

    fun addSticker(text: String) {
        if (containerSize == IntSize.Zero) return
        val id = nextStickerId
        stickers.add(
            StickerState(
                id = id,
                text = text,
                center = Offset(
                    x = containerSize.width / 2f,
                    y = containerSize.height / 2f
                )
            )
        )
        nextStickerId += 1
        selectedStickerId = id
    }

    fun updateSticker(id: Long, transform: (StickerState) -> StickerState) {
        val index = stickers.indexOfFirst { it.id == id }
        if (index != -1) {
            val current = stickers[index]
            val updated = transform(current)
            if (updated != current) {
                stickers[index] = updated
            }
        }
    }

    LaunchedEffect(containerSize) {
        if (containerSize != IntSize.Zero && stickers.isEmpty()) {
            addSticker("可拖拽/缩放/旋转的贴纸")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ElevatedButton(
                onClick = { addSticker("可拖拽/缩放/旋转的贴纸") },
                enabled = containerSize != IntSize.Zero
            ) {
                Text(text = "添加文字贴纸")
            }
            ElevatedButton(
                onClick = { addSticker("新的贴纸 ${stickers.size + 1}") },
                enabled = containerSize != IntSize.Zero
            ) {
                Text(text = "添加新贴纸")
            }
            ElevatedButton(
                onClick = {
                    scope.launch {
                        if (containerSize == IntSize.Zero || stickers.isEmpty()) return@launch
                        isSaving = true
                        try {
                            val savedUri = withContext(Dispatchers.IO) {
                                saveEditedImage(
                                    context = context,
                                    baseBitmap = baseBitmap,
                                    stickers = stickers.map { it.copy() },
                                    containerSize = containerSize,
                                    density = density
                                )
                            }
                            Toast.makeText(
                                context,
                                "图片已保存：${savedUri.lastPathSegment ?: ""}",
                                Toast.LENGTH_SHORT
                            ).show()
                        } catch (error: Exception) {
                            Toast.makeText(
                                context,
                                "保存失败: ${error.localizedMessage ?: error.javaClass.simpleName}",
                                Toast.LENGTH_SHORT
                            ).show()
                        } finally {
                            isSaving = false
                        }
                    }
                },
                enabled = !isSaving && containerSize != IntSize.Zero && stickers.isNotEmpty()
            ) {
                Text(text = if (isSaving) "保存中..." else "保存图片")
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(baseAspectRatio)
                .onGloballyPositioned { containerSize = it.size },
            shape = RoundedCornerShape(16.dp)
        ) {
            Box(modifier = Modifier.background(Color(0xFF101010))) {
                Image(
                    bitmap = baseImage,
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize()
                )

                stickers.forEach { sticker ->
                    val isSelected = sticker.id == selectedStickerId
                    Sticker(
                        state = sticker,
                        isSelected = isSelected,
                        onSelected = { selectedStickerId = sticker.id },
                        onDrag = { localDelta ->
                            if (containerSize == IntSize.Zero) return@Sticker
                            updateSticker(sticker.id) { current ->
                                if (current.size == IntSize.Zero) current else {
                                    val deltaGlobal = localDelta.rotate(current.rotation) * current.scale
                                    val candidateCenter = current.center + deltaGlobal
                                    val clamped = clampCenterInsideContainer(
                                        candidate = candidateCenter,
                                        container = containerSize,
                                        stickerSize = current.size,
                                        scale = current.scale,
                                        rotation = current.rotation
                                    )
                                    current.copy(center = clamped)
                                }
                            }
                        },
                        onScaleHandleDrag = { localPointer ->
                            if (containerSize == IntSize.Zero) return@Sticker
                            updateSticker(sticker.id) { current ->
                                if (current.size == IntSize.Zero) current else {
                                    val pointerInContainer = localToContainer(
                                        local = localPointer,
                                        center = current.center,
                                        stickerSize = current.size,
                                        scale = current.scale,
                                        rotation = current.rotation
                                    )
                                    val baseVector = Offset(
                                        x = -current.size.width / 2f,
                                        y = current.size.height / 2f
                                    )
                                    val baseLength = baseVector.length()
                                    if (baseLength == 0f) {
                                        current
                                    } else {
                                        val desiredVector = pointerInContainer - current.center
                                        val tentativeScale = if (desiredVector == Offset.Zero) {
                                            current.scale
                                        } else {
                                            desiredVector.length() / baseLength
                                        }
                                        val targetScale = tentativeScale.coerceIn(MIN_SCALE, MAX_SCALE)
                                        val rotatedBase = baseVector.rotate(current.rotation)
                                        val desiredScaledVector = rotatedBase * targetScale
                                        val candidateCenter = pointerInContainer - desiredScaledVector
                                        val clampedCenter = clampCenterInsideContainer(
                                            candidate = candidateCenter,
                                            container = containerSize,
                                            stickerSize = current.size,
                                            scale = targetScale,
                                            rotation = current.rotation
                                        )
                                        current.copy(center = clampedCenter, scale = targetScale)
                                    }
                                }
                            }
                        },
                        onRotateHandleDrag = { localPointer ->
                            if (containerSize == IntSize.Zero) return@Sticker
                            updateSticker(sticker.id) { current ->
                                if (current.size == IntSize.Zero) current else {
                                    val pointerInContainer = localToContainer(
                                        local = localPointer,
                                        center = current.center,
                                        stickerSize = current.size,
                                        scale = current.scale,
                                        rotation = current.rotation
                                    )
                                    val pointerVector = pointerInContainer - current.center
                                    if (pointerVector == Offset.Zero) {
                                        current
                                    } else {
                                        val baseVector = Offset(
                                            x = current.size.width / 2f,
                                            y = current.size.height / 2f
                                        )
                                        val baseAngle = baseVector.angleDegrees()
                                        val pointerAngle = pointerVector.angleDegrees()
                                        val newRotation = normalizeAngle(pointerAngle - baseAngle)
                                        val clampedCenter = clampCenterInsideContainer(
                                            candidate = current.center,
                                            container = containerSize,
                                            stickerSize = current.size,
                                            scale = current.scale,
                                            rotation = newRotation
                                        )
                                        current.copy(rotation = newRotation, center = clampedCenter)
                                    }
                                }
                            }
                        },
                        onRemove = {
                            val index = stickers.indexOfFirst { it.id == sticker.id }
                            if (index != -1) {
                                stickers.removeAt(index)
                                if (selectedStickerId == sticker.id) {
                                    selectedStickerId = stickers.lastOrNull()?.id
                                }
                            }
                        },
                        onSizeChanged = { newSize ->
                            updateSticker(sticker.id) { current ->
                                if (current.size == newSize) {
                                    current
                                } else {
                                    val clampedCenter = if (containerSize == IntSize.Zero || newSize == IntSize.Zero) {
                                        current.center
                                    } else {
                                        clampCenterInsideContainer(
                                            candidate = current.center,
                                            container = containerSize,
                                            stickerSize = newSize,
                                            scale = current.scale,
                                            rotation = current.rotation
                                        )
                                    }
                                    current.copy(size = newSize, center = clampedCenter)
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

private suspend fun saveEditedImage(
    context: Context,
    baseBitmap: Bitmap,
    stickers: List<StickerState>,
    containerSize: IntSize,
    density: Density
): Uri {
    if (containerSize == IntSize.Zero) {
        throw IllegalStateException("容器尺寸不可用")
    }
    val mutableBitmap = baseBitmap.copy(Bitmap.Config.ARGB_8888, true)
        ?: throw IllegalStateException("无法创建原图副本")
    if (mutableBitmap.width == 0 || mutableBitmap.height == 0) {
        mutableBitmap.recycle()
        throw IllegalStateException("原图尺寸无效")
    }

    val canvas = Canvas(mutableBitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val scaleX = mutableBitmap.width / containerSize.width.toFloat()
    val scaleY = mutableBitmap.height / containerSize.height.toFloat()

    stickers.forEach { sticker ->
        if (sticker.size == IntSize.Zero) return@forEach
        val stickerBitmap = renderStickerBitmap(sticker, density)
        val matrix = Matrix().apply {
            postTranslate(-stickerBitmap.width / 2f, -stickerBitmap.height / 2f)
            postScale(sticker.scale * scaleX, sticker.scale * scaleY)
            postRotate(sticker.rotation)
            postTranslate(sticker.center.x * scaleX, sticker.center.y * scaleY)
        }
        canvas.drawBitmap(stickerBitmap, matrix, paint)
        stickerBitmap.recycle()
    }

    return saveBitmapToGallery(context, mutableBitmap)
}

private fun renderStickerBitmap(sticker: StickerState, density: Density): Bitmap {
    val width = max(sticker.size.width, 1)
    val height = max(sticker.size.height, 1)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val cornerRadius = with(density) { 12.dp.toPx() }
    val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color(0x88000000).toArgb()
    }
    canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), cornerRadius, cornerRadius, backgroundPaint)

    val horizontalPadding = with(density) { 16.dp.toPx() }
    val verticalPadding = with(density) { 12.dp.toPx() }
    val textWidth = max(1f, width - horizontalPadding * 2).roundToInt()
    val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.White.toArgb()
        textSize = with(density) { 18.sp.toPx() }
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    val layout = StaticLayout.Builder
        .obtain(sticker.text, 0, sticker.text.length, textPaint, textWidth)
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setIncludePad(false)
        .build()

    canvas.save()
    canvas.translate(horizontalPadding, verticalPadding)
    layout.draw(canvas)
    canvas.restore()

    return bitmap
}

private fun saveBitmapToGallery(context: Context, bitmap: Bitmap): Uri {
    val resolver = context.contentResolver
    val filename = "Sticker_${System.currentTimeMillis()}.png"
    val contentValues = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, filename)
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/StickerImageEditor")
        } else {
            @Suppress("DEPRECATION")
            val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val targetDir = File(picturesDir, "StickerImageEditor")
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }
            @Suppress("DEPRECATION")
            put(MediaStore.Images.Media.DATA, File(targetDir, filename).absolutePath)
        }
    }

    var uri: Uri? = null
    try {
        uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?: throw IllegalStateException("无法创建媒体库记录")
        resolver.openOutputStream(uri)?.use { output ->
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                throw IllegalStateException("图片压缩失败")
            }
        } ?: throw IllegalStateException("无法写入图片数据")
        return uri
    } catch (error: Exception) {
        if (uri != null) {
            resolver.delete(uri, null, null)
        }
        throw error
    } finally {
        if (!bitmap.isRecycled) {
            bitmap.recycle()
        }
    }
}

private const val MIN_SCALE = 0.4f
private const val MAX_SCALE = 4f

@Composable
private fun Sticker(
    state: StickerState,
    isSelected: Boolean,
    onSelected: () -> Unit,
    onDrag: (localDelta: Offset) -> Unit,
    onScaleHandleDrag: (localPointer: Offset) -> Unit,
    onRotateHandleDrag: (localPointer: Offset) -> Unit,
    onRemove: () -> Unit,
    onSizeChanged: (IntSize) -> Unit
) {
    val handleBackground = Color(0xFF2A2A2A)
    val baseModifier = Modifier
        .pointerInput(Unit) {
            detectTapGestures(onTap = { onSelected() })
        }
        .pointerInput(isSelected) {
            detectDragGestures(
                onDragStart = { onSelected() }
            ) { change, dragAmount ->
                change.consume()
                onDrag(dragAmount)
            }
        }

    Box(
        modifier = Modifier
            .onGloballyPositioned { coordinates ->
                onSizeChanged(coordinates.size)
            }
            .graphicsLayer {
                val stickerSize = state.size
                val width = stickerSize.width
                val height = stickerSize.height
                translationX = state.center.x - width / 2f
                translationY = state.center.y - height / 2f
                scaleX = state.scale
                scaleY = state.scale
                rotationZ = state.rotation
                transformOrigin = TransformOrigin(0.5f, 0.5f)
            }
    ) {
        Box(
            modifier = baseModifier
                .clip(RoundedCornerShape(12.dp))
                .background(if (isSelected) Color(0xCC000000) else Color(0x88000000))
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = state.text,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        if (isSelected) {
            IconButton(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = CONTROL_BUTTON_OFFSET, y = -CONTROL_BUTTON_OFFSET)
                    .size(CONTROL_BUTTON_SIZE),
                onClick = onRemove,
                colors = IconButtonDefaults.iconButtonColors(containerColor = handleBackground)
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "移除贴纸",
                    tint = Color.White
                )
            }

            ControlHandle(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = -CONTROL_BUTTON_OFFSET, y = CONTROL_BUTTON_OFFSET),
                icon = Icons.Filled.OpenInFull,
                contentDescription = "缩放贴纸",
                backgroundColor = handleBackground,
                onDrag = onScaleHandleDrag
            )

            ControlHandle(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = CONTROL_BUTTON_OFFSET, y = CONTROL_BUTTON_OFFSET),
                icon = Icons.Filled.RotateRight,
                contentDescription = "旋转贴纸",
                backgroundColor = handleBackground,
                onDrag = onRotateHandleDrag
            )
        }
    }
}
 
@Composable
private fun ControlHandle(
    modifier: Modifier,
    icon: ImageVector,
    contentDescription: String,
    backgroundColor: Color,
    onDrag: (localPointer: Offset) -> Unit
) {
    var originInParent by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier = modifier
            .size(CONTROL_BUTTON_SIZE)
            .onGloballyPositioned { coordinates ->
                originInParent = coordinates.positionInParent()
            }
            .clip(CircleShape)
            .background(backgroundColor)
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    onDrag(originInParent + change.position)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(18.dp)
        )
    }
}

private val CONTROL_BUTTON_SIZE = 32.dp
private val CONTROL_BUTTON_OFFSET = CONTROL_BUTTON_SIZE / 2

private data class StickerState(
    val id: Long,
    val text: String,
    val center: Offset,
    val scale: Float = 1f,
    val rotation: Float = 0f,
    val size: IntSize = IntSize.Zero
)

private fun clampCenterInsideContainer(
    candidate: Offset,
    container: IntSize,
    stickerSize: IntSize,
    scale: Float,
    rotation: Float
): Offset {
    if (container == IntSize.Zero || stickerSize == IntSize.Zero) return candidate

    val halfExtents = calculateAabbHalfExtents(stickerSize, scale, rotation)

    val minX = halfExtents.x
    val maxX = container.width - halfExtents.x
    val minY = halfExtents.y
    val maxY = container.height - halfExtents.y

    val clampedX = if (minX > maxX) container.width / 2f else candidate.x.coerceIn(minX, maxX)
    val clampedY = if (minY > maxY) container.height / 2f else candidate.y.coerceIn(minY, maxY)

    return Offset(clampedX, clampedY)
}

private fun calculateAabbHalfExtents(
    stickerSize: IntSize,
    scale: Float,
    rotation: Float
): Offset {
    val scaledWidth = stickerSize.width * scale
    val scaledHeight = stickerSize.height * scale

    val halfWidth = scaledWidth / 2f
    val halfHeight = scaledHeight / 2f

    val radians = rotation.toRadians()
    val cosTheta = abs(cos(radians))
    val sinTheta = abs(sin(radians))

    val halfAabbWidth = halfWidth * cosTheta + halfHeight * sinTheta
    val halfAabbHeight = halfWidth * sinTheta + halfHeight * cosTheta

    return Offset(halfAabbWidth, halfAabbHeight)
}

private fun normalizeAngle(angle: Float): Float {
    var normalized = angle % 360f
    if (normalized < 0f) normalized += 360f
    return normalized
}

private fun localToContainer(
    local: Offset,
    center: Offset,
    stickerSize: IntSize,
    scale: Float,
    rotation: Float
): Offset {
    val localCenter = Offset(
        x = stickerSize.width / 2f,
        y = stickerSize.height / 2f
    )
    val relative = local - localCenter
    val rotated = relative.rotate(rotation)
    val scaled = rotated * scale
    return center + scaled
}

private fun Offset.rotate(angle: Float): Offset {
    if (angle == 0f) return this
    val radians = angle.toRadians()
    val cosTheta = cos(radians)
    val sinTheta = sin(radians)
    return Offset(
        x = (x * cosTheta) - (y * sinTheta),
        y = (x * sinTheta) + (y * cosTheta)
    )
}

private operator fun Offset.times(value: Float): Offset = Offset(x * value, y * value)

private fun Float.toRadians(): Float = (this / 180f) * PI.toFloat()

private fun Offset.length(): Float = hypot(x, y)

private fun Offset.angleDegrees(): Float = Math.toDegrees(atan2(y.toDouble(), x.toDouble())).toFloat()
