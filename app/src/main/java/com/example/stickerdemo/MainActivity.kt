package com.example.stickerdemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.compose.ui.geometry.Offset
import kotlin.math.max
import kotlin.math.min
import com.example.stickerdemo.ui.theme.StickerImageEditorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            StickerImageEditorTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    StickerOnImageDemo()
                }
            }
        }
    }
}

/**
 * 背景图上放一个可拖拽/缩放/旋转的贴纸
 */
@Composable
fun StickerOnImageDemo() {
    // 容器（即图片显示区域）的像素尺寸
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    // 贴纸原始（未缩放前）像素尺寸
    var stickerSizePx by remember { mutableStateOf(IntSize.Zero) }

    // 贴纸状态：位移（相对容器左上角，单位像素）、缩放、旋转
    var offset by remember { mutableStateOf(Offset.Zero) }
    var scale by remember { mutableStateOf(1f) }
    var rotation by remember { mutableStateOf(0f) }

    // 用一张示例图做背景（你可以换成自己的）
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        // 背景图外面再套个 Card，让边界清晰
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.6f) // 随便定个比例，实际可根据图片宽高比
                .onGloballyPositioned { containerSize = it.size },
            shape = RoundedCornerShape(16.dp)
        ) {
            Box {
                Image(
                    painter = painterResource(id = android.R.drawable.ic_menu_gallery),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // —— 贴纸 —— //
                Sticker(
                    text = "可拖拽/缩放/旋转的贴纸",
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        // 我们用 graphicsLayer 的 translationX/Y 放到自定义 offset 位置
                        .graphicsLayer {
                            translationX = offset.x
                            translationY = offset.y
                            scaleX = scale
                            scaleY = scale
                            rotationZ = rotation
                        }
                        // 测量贴纸“原始尺寸”，用于边界约束
                        .onGloballyPositioned { stickerSizePx = it.size },
                    onGesture = { pan, zoom, rotationChange ->
                        // 1) 先处理选择（如果你有选择态的话，可在此标记）
                        // 2) 再应用手势到状态（像素坐标系）
                        val newScale = (scale * zoom).coerceIn(0.2f, 5f)
                        val newRotation = (rotation + rotationChange)

                        // 计算新的位移（像素）
                        val newOffset = offset + pan

                        // 约束位移：保持贴纸“可视矩形”在容器内
                        val clampedOffset = clampOffsetInsideContainer(
                            candidate = newOffset,
                            container = containerSize,
                            stickerOriginal = stickerSizePx,
                            scale = newScale
                        )

                        offset = clampedOffset
                        scale = newScale
                        rotation = newRotation
                    }
                )
            }
        }
    }
}

/**
 * 一个简单的“文本贴纸”示例；你也可以换成图片贴纸或自绘内容。
 */
@Composable
private fun Sticker(
    text: String,
    modifier: Modifier = Modifier,
    onGesture: (pan: Offset, zoom: Float, rotationChange: Float) -> Unit
) {
    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectTransformGestures(
                    onGesture = { _, pan, zoom, rotationChange ->
                        // `pan` 即像素偏移；这里不需要任何比例换算
                        onGesture(pan, zoom, rotationChange)
                    }
                )
            }
            .clip(RoundedCornerShape(12.dp))
            .padding(4.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp),
            style = TextStyle(
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        )
        // 简单铺一个半透明底，让贴纸更明显
        Box(
            Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(12.dp))
                .graphicsLayer { alpha = 0.25f }
                .background(Color.Black)
        )
    }
}

/**
 * 把贴纸的“可视矩形”限制在容器内部：
 *  - 这里以贴纸的未旋转外接矩形做近似（旋转时可能会略出界，通常足够实用）。
 *  - 如需精确旋转后的 AABB，可在此根据 rotation 计算旋转矩形的包围盒后再约束。
 */
private fun clampOffsetInsideContainer(
    candidate: Offset,
    container: IntSize,
    stickerOriginal: IntSize,
    scale: Float
): Offset {
    if (container == IntSize.Zero || stickerOriginal == IntSize.Zero) return candidate

    val stickerW = stickerOriginal.width * scale
    val stickerH = stickerOriginal.height * scale

    // 允许的范围：贴纸左上角 ∈ [0 .. container - sticker]
    val minX = 0f
    val minY = 0f
    val maxX = container.width - stickerW
    val maxY = container.height - stickerH

    // 若贴纸比容器还大，则让其可在范围内自由拖动（上面的 max 可能为负）
    val clampedX = clamp(candidate.x, minX.coerceAtMost(maxX), maxX.coerceAtLeast(minX))
    val clampedY = clamp(candidate.y, minY.coerceAtMost(maxY), maxY.coerceAtLeast(minY))

    return Offset(clampedX, clampedY)
}

private fun clamp(v: Float, minV: Float, maxV: Float): Float =
    max(minV, min(v, maxV))

// 给 Box.background 用
private fun Modifier.background(color: Color) = this.then(
    androidx.compose.ui.draw.drawBehind { drawRect(color) }
)
