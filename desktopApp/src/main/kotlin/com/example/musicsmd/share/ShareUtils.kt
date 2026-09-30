package com.example.musicsmd.share

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.MultiFormatWriter
import com.google.zxing.client.j2se.BufferedImageLuminanceSource
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import org.jetbrains.skia.Image
import java.awt.Color
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO

fun copyTextToClipboard(text: String) {
    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
}

fun qrImageBitmap(text: String, size: Int = 512): ImageBitmap? = runCatching {
    bufferedQr(text, size).toImageBitmap()
}.getOrNull()

fun saveQrPng(text: String, file: File, size: Int = 1024) {
    val target = if (file.extension.equals("png", ignoreCase = true)) file else File(file.parentFile, file.name + ".png")
    ImageIO.write(bufferedQr(text, size), "png", target)
}

fun decodeQrImage(file: File): String? = runCatching {
    val image = ImageIO.read(file) ?: return null
    MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(BufferedImageLuminanceSource(image)))).text
}.getOrNull()

private fun bufferedQr(text: String, size: Int): BufferedImage {
    val hints = mapOf(
        EncodeHintType.MARGIN to 1,
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
    )
    val matrix = MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
    return matrix.toBufferedImage()
}

private fun BitMatrix.toBufferedImage(): BufferedImage {
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    val black = Color.BLACK.rgb
    val white = Color.WHITE.rgb
    for (x in 0 until width) {
        for (y in 0 until height) {
            image.setRGB(x, y, if (get(x, y)) black else white)
        }
    }
    return image
}

private fun BufferedImage.toImageBitmap(): ImageBitmap {
    val bytes = ByteArrayOutputStream().use { out ->
        ImageIO.write(this, "png", out)
        out.toByteArray()
    }
    return Image.makeFromEncoded(bytes).toComposeImageBitmap()
}
