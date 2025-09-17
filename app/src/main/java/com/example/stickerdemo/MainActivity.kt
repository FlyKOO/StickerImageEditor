package com.example.stickerdemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.stickerdemo.ui.theme.StickerImageEditorTheme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
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
                    onTransform = { pan, zoomChange, rotationChange, centroid ->
                        if (stickerSize == IntSize.Zero || containerSize == IntSize.Zero) return@Sticker

                        val targetScale = (scale * zoomChange).coerceIn(MIN_SCALE, MAX_SCALE)
                        val scaleFactor = if (scale == 0f) 1f else targetScale / scale
                        val targetRotation = normalizeAngle(rotation + rotationChange)

                        val centroidBefore = localToContainer(
                            local = centroid,
                            center = stickerCenter,
                            stickerSize = stickerSize,
                            scale = scale,
                            rotation = rotation
                        )

                        val centroidAfterPanOnly = localToContainer(
                            local = centroid + pan,
                            center = stickerCenter,
                            stickerSize = stickerSize,
                            scale = scale,
                            rotation = rotation
                        )

                        val panGlobal = centroidAfterPanOnly - centroidBefore
                        val centerVector = stickerCenter - centroidBefore
                        val rotatedScaledVector = centerVector.rotate(rotationChange) * scaleFactor
                        val newCenter = centroidBefore + panGlobal + rotatedScaledVector

                        stickerCenter = clampCenterInsideContainer(
                            candidate = newCenter,
                            container = containerSize,
                            stickerSize = stickerSize,
                            scale = targetScale,
                            rotation = targetRotation
                        )
                        scale = targetScale
                        rotation = targetRotation
                    }
                )
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
    onTransform: (pan: Offset, zoom: Float, rotation: Float, centroid: Offset) -> Unit
) {
    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, rotation ->
                    onTransform(pan, zoom, rotation, centroid)
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
}

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
