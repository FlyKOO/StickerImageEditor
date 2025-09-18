package com.example.stickerdemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.consume
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.stickerdemo.ui.theme.StickerImageEditorTheme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

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
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val stickers = remember { mutableStateListOf<StickerState>() }
    var selectedStickerId by remember { mutableStateOf<Long?>(null) }
    var nextStickerIndex by remember { mutableStateOf(1) }

    LaunchedEffect(containerSize) {
        if (containerSize == IntSize.Zero) return@LaunchedEffect
        stickers.forEachIndexed { index, sticker ->
            if (sticker.size == IntSize.Zero) return@forEachIndexed
            val clamped = clampCenterInsideContainer(
                candidate = sticker.center,
                container = containerSize,
                stickerSize = sticker.size,
                scale = sticker.scale,
                rotation = sticker.rotation
            )
            if (clamped != sticker.center) {
                stickers[index] = sticker.copy(center = clamped)
            }
        }
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

    fun setStickerCenter(id: Long, newCenter: Offset) {
        updateSticker(id) { it.copy(center = newCenter) }
    }

    fun setStickerScale(id: Long, newScale: Float) {
        updateSticker(id) { sticker ->
            if (sticker.size == IntSize.Zero || containerSize == IntSize.Zero) {
                sticker.copy(scale = newScale)
            } else {
                val clampedCenter = clampCenterInsideContainer(
                    candidate = sticker.center,
                    container = containerSize,
                    stickerSize = sticker.size,
                    scale = newScale,
                    rotation = sticker.rotation
                )
                sticker.copy(center = clampedCenter, scale = newScale)
            }
        }
    }

    fun setStickerRotation(id: Long, newRotation: Float) {
        updateSticker(id) { sticker ->
            if (sticker.size == IntSize.Zero || containerSize == IntSize.Zero) {
                sticker.copy(rotation = newRotation)
            } else {
                val clampedCenter = clampCenterInsideContainer(
                    candidate = sticker.center,
                    container = containerSize,
                    stickerSize = sticker.size,
                    scale = sticker.scale,
                    rotation = newRotation
                )
                sticker.copy(center = clampedCenter, rotation = newRotation)
            }
        }
    }

    fun setStickerSize(id: Long, newSize: IntSize) {
        updateSticker(id) { sticker ->
            if (sticker.size == newSize) {
                sticker
            } else {
                val updated = sticker.copy(size = newSize)
                if (containerSize == IntSize.Zero || newSize == IntSize.Zero) {
                    updated
                } else {
                    val clampedCenter = clampCenterInsideContainer(
                        candidate = updated.center,
                        container = containerSize,
                        stickerSize = newSize,
                        scale = updated.scale,
                        rotation = updated.rotation
                    )
                    updated.copy(center = clampedCenter)
                }
            }
        }
    }

    fun removeSticker(id: Long) {
        val index = stickers.indexOfFirst { it.id == id }
        if (index != -1) {
            stickers.removeAt(index)
            if (selectedStickerId == id) {
                selectedStickerId = stickers.lastOrNull()?.id
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.6f)
                .onGloballyPositioned { containerSize = it.size },
            shape = RoundedCornerShape(16.dp)
        ) {
            Box(modifier = Modifier.background(Color(0xFF101010))) {
                Image(
                    painter = painterResource(id = android.R.drawable.ic_menu_gallery),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                stickers.forEach { sticker ->
                    StickerLayer(
                        sticker = sticker,
                        containerSize = containerSize,
                        isSelected = sticker.id == selectedStickerId,
                        onSelect = { selectedStickerId = sticker.id },
                        onCenterChange = { center -> setStickerCenter(sticker.id, center) },
                        onScaleChange = { scale -> setStickerScale(sticker.id, scale) },
                        onRotationChange = { rotation -> setStickerRotation(sticker.id, rotation) },
                        onSizeChange = { size -> setStickerSize(sticker.id, size) },
                        onRemove = { removeSticker(sticker.id) }
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = {
                val newId = System.currentTimeMillis()
                val initialCenter = if (containerSize == IntSize.Zero) {
                    Offset.Zero
                } else {
                    Offset(containerSize.width / 2f, containerSize.height / 2f)
                }
                stickers.add(
                    StickerState(
                        id = newId,
                        text = "贴纸 ${nextStickerIndex}",
                        center = initialCenter
                    )
                )
                nextStickerIndex += 1
                selectedStickerId = newId
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        ) {
            Icon(imageVector = Icons.Filled.Add, contentDescription = "添加贴纸")
        }
    }
}

@Composable
private fun StickerLayer(
    sticker: StickerState,
    containerSize: IntSize,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onCenterChange: (Offset) -> Unit,
    onScaleChange: (Float) -> Unit,
    onRotationChange: (Float) -> Unit,
    onSizeChange: (IntSize) -> Unit,
    onRemove: () -> Unit
) {
    val currentSticker by rememberUpdatedState(sticker)
    val currentContainer by rememberUpdatedState(containerSize)

    val translationXValue = sticker.center.x - sticker.size.width / 2f
    val translationYValue = sticker.center.y - sticker.size.height / 2f

    Box(
        modifier = Modifier
            .zIndex(if (isSelected) 1f else 0f)
            .graphicsLayer {
                translationX = translationXValue
                translationY = translationYValue
                scaleX = sticker.scale
                scaleY = sticker.scale
                rotationZ = sticker.rotation
                transformOrigin = TransformOrigin(0.5f, 0.5f)
            }
            .pointerInput(sticker.id) {
                detectDragGestures(
                    onDragStart = {
                        onSelect()
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val state = currentSticker
                        val container = currentContainer
                        if (state.size == IntSize.Zero || container == IntSize.Zero) return@detectDragGestures
                        val newCenter = clampCenterInsideContainer(
                            candidate = state.center + dragAmount,
                            container = container,
                            stickerSize = state.size,
                            scale = state.scale,
                            rotation = state.rotation
                        )
                        onCenterChange(newCenter)
                    }
                )
            }
            .pointerInput(sticker.id) {
                detectTapGestures {
                    onSelect()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .onGloballyPositioned { coordinates ->
                    onSizeChange(coordinates.size)
                }
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xAA000000))
                .then(
                    if (isSelected) {
                        Modifier.border(2.dp, Color.White, RoundedCornerShape(12.dp))
                    } else {
                        Modifier
                    }
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = sticker.text,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        if (isSelected) {
            StickerControlButton(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(HANDLE_OFFSET, -HANDLE_OFFSET),
                onClick = onRemove,
                icon = Icons.Filled.Close,
                contentDescription = "移除贴纸"
            )

            ScaleHandle(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(-HANDLE_OFFSET, HANDLE_OFFSET),
                stickerProvider = { currentSticker },
                onSelect = onSelect,
                onScaleChange = onScaleChange
            )

            RotateHandle(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(HANDLE_OFFSET, HANDLE_OFFSET),
                stickerProvider = { currentSticker },
                onSelect = onSelect,
                onRotationChange = onRotationChange
            )
        }
    }
}

@Composable
private fun StickerControlButton(
    modifier: Modifier,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String
) {
    Box(
        modifier = modifier
            .size(HANDLE_SIZE)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.9f))
            .pointerInput(Unit) {
                detectTapGestures {
                    onClick()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.Black,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun ScaleHandle(
    modifier: Modifier,
    stickerProvider: () -> StickerState,
    onSelect: () -> Unit,
    onScaleChange: (Float) -> Unit
) {
    var accumulatedProjection by remember { mutableStateOf(0f) }
    var initialScale by remember { mutableStateOf(1f) }

    Box(
        modifier = modifier
            .size(HANDLE_SIZE)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.9f))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        onSelect()
                        accumulatedProjection = 0f
                        initialScale = stickerProvider().scale
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val sticker = stickerProvider()
                        if (sticker.size == IntSize.Zero) return@detectDragGestures
                        val baseRadius = baseRadius(sticker.size)
                        if (baseRadius == 0f) return@detectDragGestures
                        val direction = handleDirectionBottomStart(sticker.size, sticker.rotation).normalize()
                        if (direction == Offset.Zero) return@detectDragGestures
                        val projection = dragAmount.x * direction.x + dragAmount.y * direction.y
                        accumulatedProjection += projection
                        val currentRadius = baseRadius * initialScale
                        val minRadius = baseRadius * MIN_SCALE
                        val maxRadius = baseRadius * MAX_SCALE
                        val newRadius = (currentRadius + accumulatedProjection).coerceIn(minRadius, maxRadius)
                        val newScale = newRadius / baseRadius
                        onScaleChange(newScale)
                    },
                    onDragEnd = {
                        accumulatedProjection = 0f
                    },
                    onDragCancel = {
                        accumulatedProjection = 0f
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.OpenWith,
            contentDescription = "缩放贴纸",
            tint = Color.Black,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun RotateHandle(
    modifier: Modifier,
    stickerProvider: () -> StickerState,
    onSelect: () -> Unit,
    onRotationChange: (Float) -> Unit
) {
    var accumulatedTangent by remember { mutableStateOf(0f) }
    var initialRotation by remember { mutableStateOf(0f) }
    var initialScale by remember { mutableStateOf(1f) }

    Box(
        modifier = modifier
            .size(HANDLE_SIZE)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.9f))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        onSelect()
                        accumulatedTangent = 0f
                        val sticker = stickerProvider()
                        initialRotation = sticker.rotation
                        initialScale = sticker.scale
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val sticker = stickerProvider()
                        if (sticker.size == IntSize.Zero) return@detectDragGestures
                        val baseRadius = baseRadius(sticker.size)
                        if (baseRadius == 0f) return@detectDragGestures
                        val direction = handleDirectionBottomEnd(sticker.size, sticker.rotation).normalize()
                        if (direction == Offset.Zero) return@detectDragGestures
                        val tangent = direction.perpendicular()
                        val radius = baseRadius * initialScale
                        if (radius == 0f) return@detectDragGestures
                        val projection = dragAmount.x * tangent.x + dragAmount.y * tangent.y
                        accumulatedTangent += projection
                        val deltaRadians = accumulatedTangent / radius
                        val deltaDegrees = deltaRadians * 180f / PI.toFloat()
                        val newRotation = normalizeAngle(initialRotation + deltaDegrees)
                        onRotationChange(newRotation)
                    },
                    onDragEnd = {
                        accumulatedTangent = 0f
                    },
                    onDragCancel = {
                        accumulatedTangent = 0f
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.RotateRight,
            contentDescription = "旋转贴纸",
            tint = Color.Black,
            modifier = Modifier.size(18.dp)
        )
    }
}

private data class StickerState(
    val id: Long,
    val text: String,
    val center: Offset = Offset.Zero,
    val scale: Float = 1f,
    val rotation: Float = 0f,
    val size: IntSize = IntSize.Zero
)

private const val MIN_SCALE = 0.4f
private const val MAX_SCALE = 4f
private val HANDLE_OFFSET = 24.dp
private val HANDLE_SIZE = 36.dp

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

private fun Float.toRadians(): Float = (this / 180f) * PI.toFloat()

private fun baseRadius(stickerSize: IntSize): Float {
    if (stickerSize == IntSize.Zero) return 0f
    return hypot(stickerSize.width / 2f, stickerSize.height / 2f)
}

private fun handleDirectionBottomStart(stickerSize: IntSize, rotation: Float): Offset {
    val local = Offset(-stickerSize.width / 2f, stickerSize.height / 2f)
    return local.rotate(rotation)
}

private fun handleDirectionBottomEnd(stickerSize: IntSize, rotation: Float): Offset {
    val local = Offset(stickerSize.width / 2f, stickerSize.height / 2f)
    return local.rotate(rotation)
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

private fun Offset.length(): Float = hypot(x, y)

private fun Offset.normalize(): Offset {
    val length = length()
    if (length == 0f) return Offset.Zero
    return Offset(x / length, y / length)
}

private fun Offset.perpendicular(): Offset = Offset(-y, x)
