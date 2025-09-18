package com.example.stickerdemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.stickerdemo.ui.theme.StickerImageEditorTheme
import kotlin.math.PI
import kotlin.math.atan2
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
    var stickerSize by remember { mutableStateOf(IntSize.Zero) }

    var stickerCenter by remember { mutableStateOf(Offset.Zero) }
    var scale by remember { mutableStateOf(1f) }
    var rotation by remember { mutableStateOf(0f) }

    var hasInitialPlacement by remember { mutableStateOf(false) }
    var isStickerVisible by remember { mutableStateOf(true) }

    LaunchedEffect(containerSize) {
        if (!hasInitialPlacement && containerSize != IntSize.Zero) {
            stickerCenter = Offset(
                x = containerSize.width / 2f,
                y = containerSize.height / 2f
            )
            hasInitialPlacement = true
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

                if (isStickerVisible) {
                    Sticker(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .onGloballyPositioned { stickerSize = it.size }
                            .graphicsLayer {
                                translationX = stickerCenter.x - stickerSize.width / 2f
                                translationY = stickerCenter.y - stickerSize.height / 2f
                                scaleX = scale
                                scaleY = scale
                                rotationZ = rotation
                                transformOrigin = TransformOrigin(0.5f, 0.5f)
                            },
                        text = "可拖拽/缩放/旋转的贴纸",
                        onDrag = { localDelta ->
                            if (stickerSize == IntSize.Zero || containerSize == IntSize.Zero) return@Sticker
                            val deltaGlobal = localDelta.rotate(rotation) * scale
                            val candidateCenter = stickerCenter + deltaGlobal
                            stickerCenter = clampCenterInsideContainer(
                                candidate = candidateCenter,
                                container = containerSize,
                                stickerSize = stickerSize,
                                scale = scale,
                                rotation = rotation
                            )
                        },
                        onScaleHandleDrag = { localPointer ->
                            if (stickerSize == IntSize.Zero || containerSize == IntSize.Zero) return@Sticker
                            val pointerInContainer = localToContainer(
                                local = localPointer,
                                center = stickerCenter,
                                stickerSize = stickerSize,
                                scale = scale,
                                rotation = rotation
                            )
                            val baseVector = Offset(
                                x = -stickerSize.width / 2f,
                                y = stickerSize.height / 2f
                            )
                            val baseLength = baseVector.length()
                            if (baseLength == 0f) return@Sticker
                            val desiredVector = pointerInContainer - stickerCenter
                            val tentativeScale = if (desiredVector == Offset.Zero) scale else desiredVector.length() / baseLength
                            val targetScale = tentativeScale.coerceIn(MIN_SCALE, MAX_SCALE)
                            val rotatedBase = baseVector.rotate(rotation)
                            val desiredScaledVector = rotatedBase * targetScale
                            val candidateCenter = pointerInContainer - desiredScaledVector
                            val clampedCenter = clampCenterInsideContainer(
                                candidate = candidateCenter,
                                container = containerSize,
                                stickerSize = stickerSize,
                                scale = targetScale,
                                rotation = rotation
                            )
                            stickerCenter = clampedCenter
                            scale = targetScale
                        },
                        onRotateHandleDrag = { localPointer ->
                            if (stickerSize == IntSize.Zero || containerSize == IntSize.Zero) return@Sticker
                            val pointerInContainer = localToContainer(
                                local = localPointer,
                                center = stickerCenter,
                                stickerSize = stickerSize,
                                scale = scale,
                                rotation = rotation
                            )
                            val pointerVector = pointerInContainer - stickerCenter
                            if (pointerVector == Offset.Zero) return@Sticker
                            val baseVector = Offset(
                                x = stickerSize.width / 2f,
                                y = stickerSize.height / 2f
                            )
                            val baseAngle = baseVector.angleDegrees()
                            val pointerAngle = pointerVector.angleDegrees()
                            val newRotation = normalizeAngle(pointerAngle - baseAngle)
                            rotation = newRotation
                            stickerCenter = clampCenterInsideContainer(
                                candidate = stickerCenter,
                                container = containerSize,
                                stickerSize = stickerSize,
                                scale = scale,
                                rotation = newRotation
                            )
                        },
                        onRemove = { isStickerVisible = false }
                    )
                }
            }
        }
    }
}

private const val MIN_SCALE = 0.4f
private const val MAX_SCALE = 4f

@Composable
private fun Sticker(
    modifier: Modifier,
    text: String,
    onDrag: (localDelta: Offset) -> Unit,
    onScaleHandleDrag: (localPointer: Offset) -> Unit,
    onRotateHandleDrag: (localPointer: Offset) -> Unit,
    onRemove: () -> Unit
) {
    val handleBackground = Color(0xFF2A2A2A)
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount)
                    }
                }
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xAA000000))
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = text,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

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
