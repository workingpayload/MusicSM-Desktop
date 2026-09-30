package com.example.musicsmd.support

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.musicsmd.share.copyTextToClipboard
import com.example.musicsmd.share.qrImageBitmap
import com.example.musicsmd.ui.theme.Coral
import com.example.musicsmd.ui.theme.GlassFillStrong
import com.example.musicsmd.ui.theme.GlassStroke
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Where a Diet Coke goes: the same UPI details as the "Buy me a Diet Coke" button on the website. */
object DietCoke {
    const val UPI_ID = "rs91963@pingpay"
    const val PAYEE = "Raj"

    /** A UPI payment request any UPI app understands, with the amount left to the payer. */
    const val UPI_URI = "upi://pay?pa=$UPI_ID&pn=$PAYEE&cu=INR"

    /** The website's cup-with-a-straw icon (a 24 × 24 stroke drawing), so every MusicSM shows the same one. */
    val icon: ImageVector by lazy {
        ImageVector.Builder(
            name = "DietCoke",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(
            pathData = addPathNodes("M6 7h12l-1.2 13.2a1 1 0 0 1-1 .8H8.2a1 1 0 0 1-1-.8L6 7z M4.5 7h15 M14 7l2-4"),
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ).build()
    }
}

// Once per launch, not every time the dock is composed again.
private var fizzedThisLaunch = false

/**
 * The "Diet Coke" entry at the foot of the dock. A moment after the app opens it gives one little
 * shake while bubbles fizz out of the straw, so it gets noticed once and then stays quiet.
 */
@Composable
fun DietCokeDockButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shake = remember { Animatable(0f) }
    val pop = remember { Animatable(1f) }
    val fizz = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (fizzedThisLaunch) return@LaunchedEffect
        fizzedThisLaunch = true
        delay(FIZZ_DELAY_MS)
        launch { fizz.animateTo(1f, tween(durationMillis = 1_500, easing = LinearEasing)) }
        launch {
            pop.animateTo(1.22f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
            pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
        }
        shake.animateTo(
            targetValue = 0f,
            animationSpec = keyframes {
                durationMillis = 1_100
                1f at 110
                -1f at 260
                0.75f at 410
                -0.5f at 560
                0.3f at 710
                -0.12f at 860
            },
        )
    }

    val color = Coral
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp)
            .clip(RoundedCornerShape(22.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .drawWithContent {
                    drawContent()
                    // Three bubbles rising out of the straw's tip, one after another.
                    val progress = fizz.value
                    if (progress <= 0f || progress >= 1f) return@drawWithContent
                    val unit = size.width / 24f
                    repeat(3) { i ->
                        val t = ((progress - i * 0.18f) / 0.64f).coerceIn(0f, 1f)
                        if (t <= 0f || t >= 1f) return@repeat
                        val x = (16f + i * 1.5f + sin(t * PI.toFloat() * 2f + i) * 1.4f) * unit
                        val y = (3f - t * 9f) * unit
                        drawCircle(
                            color = color.copy(alpha = sin(t * PI.toFloat()) * 0.9f),
                            radius = (1.1f + i * 0.35f) * unit,
                            center = Offset(x, y),
                        )
                    }
                },
        ) {
            Icon(
                imageVector = DietCoke.icon,
                contentDescription = "Buy me a Diet Coke",
                tint = color,
                modifier = Modifier.size(24.dp).graphicsLayer {
                    rotationZ = shake.value * 16f
                    scaleX = pop.value
                    scaleY = pop.value
                },
            )
        }
        Text(
            text = "Diet Coke",
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

/**
 * "Buy me a Diet Coke": the UPI details, as on the website. A computer has no UPI app, so the
 * payment request is a QR code to scan with a phone, with the UPI ID to copy as well.
 */
@Composable
fun DietCokeDialog(onDismiss: () -> Unit) {
    val qr = remember { qrImageBitmap(DietCoke.UPI_URI, size = 480) }
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1_800)
            copied = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(DietCoke.icon, contentDescription = null, tint = Coral, modifier = Modifier.size(32.dp)) },
        title = { Text("Buy me a Diet Coke 🥤") },
        text = {
            Column(
                modifier = Modifier.widthIn(max = 400.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "If you like MusicSM, buy me a Diet Coke! It keeps me going and keeps the app free for everyone.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                if (qr != null) {
                    Spacer(Modifier.height(18.dp))
                    Box(
                        modifier = Modifier.background(Color.White, RoundedCornerShape(18.dp)).padding(10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(bitmap = qr, contentDescription = "UPI QR code", modifier = Modifier.size(200.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Scan with any UPI app: Google Pay, PhonePe, Paytm…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(18.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(GlassFillStrong)
                        .border(1.dp, GlassStroke, RoundedCornerShape(50))
                        .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "UPI ID",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(DietCoke.UPI_ID, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = {
                        copyTextToClipboard(DietCoke.UPI_ID)
                        copied = true
                    }) { Text(if (copied) "Copied" else "Copy") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

private const val FIZZ_DELAY_MS = 1_200L
