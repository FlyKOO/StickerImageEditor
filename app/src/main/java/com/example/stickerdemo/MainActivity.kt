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

// 主界面 Activity，负责承载 Compose 内容
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 使用 Compose 设置界面内容
        setContent {
            // 应用自定义主题
            StickerImageEditorTheme {
                // 使用 Surface 作为界面容器
                Surface(color = MaterialTheme.colorScheme.background) {
                    // 展示贴纸编辑页面
                    StickerEditorScreen()
                }
            }
        }
    }
}

// 贴纸编辑界面主体，负责展示图片与交互逻辑
@Composable
private fun StickerEditorScreen() {
    // 当前上下文对象
    val context = LocalContext.current
    // 当前屏幕密度，用于像素与 dp/sp 转换
    val density = LocalDensity.current
    // 协程作用域，用于执行异步保存操作
    val scope = rememberCoroutineScope()
    // 读取示例图片，作为底图
    val baseBitmap = remember(context) {
        BitmapFactory.decodeResource(
            context.resources,
            R.drawable.sample_photo,
            BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
        ) ?: throw IllegalStateException("无法加载示例图片")
    }
    // 将 Bitmap 转换为 Compose 可用的 ImageBitmap
    val baseImage = remember(baseBitmap) { baseBitmap.asImageBitmap() }
    // 计算底图宽高比，确保显示比例正确
    val baseAspectRatio = remember(baseBitmap) {
        if (baseBitmap.height == 0) 1.6f else baseBitmap.width.toFloat() / baseBitmap.height.toFloat()
    }
    // 贴纸容器的实际尺寸
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    // 保存当前所有贴纸状态
    val stickers = remember { mutableStateListOf<StickerState>() }
    // 当前选中的贴纸 ID
    var selectedStickerId by remember { mutableStateOf<Long?>(null) }
    // 生成新贴纸的自增 ID
    var nextStickerId by remember { mutableStateOf(0L) }
    // 标记是否正在保存图片
    var isSaving by remember { mutableStateOf(false) }

    // 添加新贴纸到列表中
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

    // 根据 ID 更新贴纸数据
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

    // 容器尺寸准备好后自动添加默认贴纸
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
        // 顶部功能按钮区域
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 添加默认文案的贴纸
            ElevatedButton(
                onClick = { addSticker("可拖拽/缩放/旋转的贴纸") },
                enabled = containerSize != IntSize.Zero
            ) {
                Text(text = "添加文字贴纸")
            }
            // 添加带序号的新贴纸
            ElevatedButton(
                onClick = { addSticker("新的贴纸 ${stickers.size + 1}") },
                enabled = containerSize != IntSize.Zero
            ) {
                Text(text = "添加新贴纸")
            }
            // 保存合成后图片
            ElevatedButton(
                onClick = {
                    scope.launch {
                        if (containerSize == IntSize.Zero || stickers.isEmpty()) return@launch
                        isSaving = true
                        try {
                            // 在 IO 线程执行图片保存
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
                // 显示底图
                Image(
                    bitmap = baseImage,
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize()
                )

                // 遍历绘制所有贴纸
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
                                    // 将本地点增量转换为旋转后的全局坐标增量
                                    val deltaGlobal = localDelta.rotate(current.rotation) * current.scale
                                    val candidateCenter = current.center + deltaGlobal
                                    // 贴纸移动后需要限制在容器边界内
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
                                    // 将控制手柄坐标转换为容器坐标系
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
                                                // 根据指针距离计算目标缩放值
                                                desiredVector.length() / baseLength
                                            }
                                            val targetScale = tentativeScale.coerceIn(MIN_SCALE, MAX_SCALE)
                                            val rotatedBase = baseVector.rotate(current.rotation)
                                            val desiredScaledVector = rotatedBase * targetScale
                                            val candidateCenter = pointerInContainer - desiredScaledVector
                                            // 缩放后重新校正中心位置
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
                                    // 计算控制手柄在容器中的位置
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
                                        // 角度差转换为新的贴纸旋转角度
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
                                    // 如果删除的是当前选中项，则选中剩余的最后一项
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
                                        // 尺寸变化后需要重新计算中心点
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

// 将贴纸合成到底图后保存到媒体库
private suspend fun saveEditedImage(
    context: Context,
    baseBitmap: Bitmap,
    stickers: List<StickerState>,
    containerSize: IntSize,
    density: Density
): Uri {
    // 容器尺寸不可用时无法继续
    if (containerSize == IntSize.Zero) {
        throw IllegalStateException("容器尺寸不可用")
    }
    // 创建底图的可编辑副本
    val mutableBitmap = baseBitmap.copy(Bitmap.Config.ARGB_8888, true)
        ?: throw IllegalStateException("无法创建原图副本")
    if (mutableBitmap.width == 0 || mutableBitmap.height == 0) {
        mutableBitmap.recycle()
        throw IllegalStateException("原图尺寸无效")
    }

    // 在副本上进行绘制
    val canvas = Canvas(mutableBitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val scaleX = mutableBitmap.width / containerSize.width.toFloat()
    val scaleY = mutableBitmap.height / containerSize.height.toFloat()

    stickers.forEach { sticker ->
        if (sticker.size == IntSize.Zero) return@forEach
        // 将贴纸渲染成位图
        val stickerBitmap = renderStickerBitmap(sticker, density)
        val matrix = Matrix().apply {
            // 构建贴纸变换矩阵
            postTranslate(-stickerBitmap.width / 2f, -stickerBitmap.height / 2f)
            postScale(sticker.scale * scaleX, sticker.scale * scaleY)
            postRotate(sticker.rotation)
            postTranslate(sticker.center.x * scaleX, sticker.center.y * scaleY)
        }
        // 将贴纸绘制到底图上
        canvas.drawBitmap(stickerBitmap, matrix, paint)
        stickerBitmap.recycle()
    }

    // 保存图片到系统媒体库
    return saveBitmapToGallery(context, mutableBitmap)
}

// 生成单个贴纸的位图，用于保存到图片中
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

// 将位图写入系统相册
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
        // 插入媒体库记录并写入图片数据
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
            // 如果发生异常则删除已插入的记录
            resolver.delete(uri, null, null)
        }
        throw error
    } finally {
        if (!bitmap.isRecycled) {
            bitmap.recycle()
        }
    }
}

// 贴纸允许的最小缩放比例
private const val MIN_SCALE = 0.4f
// 贴纸允许的最大缩放比例
private const val MAX_SCALE = 4f

// 单个贴纸组件，负责展示文字与控制手柄
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
    // 控制手柄的底色
    val handleBackground = Color(0xFF2A2A2A)
    // 基础手势逻辑：点击选中、拖动移动
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
                // 记录组件真实尺寸供后续计算
                onSizeChanged(coordinates.size)
            }
            .graphicsLayer {
                val stickerSize = state.size
                val width = stickerSize.width
                val height = stickerSize.height
                // 根据状态配置平移、缩放与旋转
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
            // 显示贴纸文本内容
            Text(
                text = state.text,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        if (isSelected) {
            // 删除按钮位于右上角
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

            // 缩放手柄位于左下角
            ControlHandle(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = -CONTROL_BUTTON_OFFSET, y = CONTROL_BUTTON_OFFSET),
                icon = Icons.Filled.OpenInFull,
                contentDescription = "缩放贴纸",
                backgroundColor = handleBackground,
                onDrag = onScaleHandleDrag
            )

            // 旋转手柄位于右下角
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
 
// 贴纸控制手柄组件，处理缩放与旋转的拖动事件
@Composable
private fun ControlHandle(
    modifier: Modifier,
    icon: ImageVector,
    contentDescription: String,
    backgroundColor: Color,
    onDrag: (localPointer: Offset) -> Unit
) {
    // 记录手柄在父布局中的原点位置
    var originInParent by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier = modifier
            .size(CONTROL_BUTTON_SIZE)
            .onGloballyPositioned { coordinates ->
                // 获取手柄相对于父布局的坐标
                originInParent = coordinates.positionInParent()
            }
            .clip(CircleShape)
            .background(backgroundColor)
            .pointerInput(Unit) {
                // 将拖动位置转换为局部坐标回传
                detectDragGestures { change, _ ->
                    change.consume()
                    onDrag(originInParent + change.position)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // 绘制手柄图标
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(18.dp)
        )
    }
}

// 控制手柄的尺寸
private val CONTROL_BUTTON_SIZE = 32.dp
// 控制手柄偏移量，用于将图标移动到角落
private val CONTROL_BUTTON_OFFSET = CONTROL_BUTTON_SIZE / 2

// 贴纸的状态数据，包含位置、缩放、旋转等信息
private data class StickerState(
    val id: Long,
    val text: String,
    val center: Offset,
    val scale: Float = 1f,
    val rotation: Float = 0f,
    val size: IntSize = IntSize.Zero
)

// 将贴纸中心点限制在容器范围内
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

// 计算贴纸旋转后的轴对齐包围盒半径
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

// 将角度归一化到 0~360 范围
private fun normalizeAngle(angle: Float): Float {
    var normalized = angle % 360f
    if (normalized < 0f) normalized += 360f
    return normalized
}

// 将贴纸局部坐标转换为容器坐标
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

// 对偏移量执行二维旋转
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

// 偏移量与标量相乘得到新的偏移量
private operator fun Offset.times(value: Float): Offset = Offset(x * value, y * value)

// 将角度转换为弧度
private fun Float.toRadians(): Float = (this / 180f) * PI.toFloat()

// 计算偏移量向量的长度
private fun Offset.length(): Float = hypot(x, y)

// 计算偏移量与 X 轴的夹角（角度制）
private fun Offset.angleDegrees(): Float = Math.toDegrees(atan2(y.toDouble(), x.toDouble())).toFloat()
