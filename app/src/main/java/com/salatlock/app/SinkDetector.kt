package com.salatlock.app

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.min

/**
 * Reconnaît un lavabo grâce à un petit réseau de neurones (MobileNet v2, ImageNet)
 * qui tourne entièrement sur le téléphone, sans internet.
 * ImageNet contient justement la classe « washbasin, handbasin, washbowl, lavabo ».
 */
class SinkDetector(ctx: Context) {
    private val interpreter: Interpreter
    private val labels: List<String>
    private val input = ByteBuffer.allocateDirect(SIZE * SIZE * 3).order(ByteOrder.nativeOrder())
    private val output = Array(1) { ByteArray(1001) }
    private val pixels = IntArray(SIZE * SIZE)
    private val sinkIdx: Int
    private val helperIdx: List<Int>
    private val outScale: Float
    private val outZero: Int
    private val probs = FloatArray(1001)

    class Result(val sinkScore: Float, val topLabel: String, val topScore: Float)

    init {
        val fd = ctx.assets.openFd("sink_model.tflite")
        val buf = FileInputStream(fd.fileDescriptor).channel
            .map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
        interpreter = Interpreter(buf, Interpreter.Options().setNumThreads(2))
        labels = ctx.assets.open("labels.txt").bufferedReader().readLines()
        sinkIdx = labels.indexOf("washbasin")
        // Objets qu'on voit souvent autour d'un lavabo : ils aident un peu.
        helperIdx = listOf("soap dispenser", "tub", "bathtub").map { labels.indexOf(it) }.filter { it >= 0 }
        val q = interpreter.getOutputTensor(0).quantizationParams()
        outScale = q.scale
        outZero = q.zeroPoint
    }

    fun classify(src: Bitmap): Result {
        val s = min(src.width, src.height)
        val crop = Bitmap.createBitmap(src, (src.width - s) / 2, (src.height - s) / 2, s, s)
        val img = Bitmap.createScaledBitmap(crop, SIZE, SIZE, true)
        img.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)
        input.rewind()
        for (px in pixels) {
            input.put((px shr 16 and 0xFF).toByte())
            input.put((px shr 8 and 0xFF).toByte())
            input.put((px and 0xFF).toByte())
        }
        interpreter.run(input, output)
        val out = output[0]
        // Ce modèle sort des « logits » quantifiés : on les convertit en probabilités (softmax).
        var maxL = Float.NEGATIVE_INFINITY
        for (i in out.indices) {
            val l = outScale * ((out[i].toInt() and 0xFF) - outZero)
            probs[i] = l
            if (l > maxL) maxL = l
        }
        var sum = 0f
        for (i in probs.indices) { probs[i] = kotlin.math.exp(probs[i] - maxL); sum += probs[i] }
        var best = 0
        for (i in probs.indices) { probs[i] /= sum; if (probs[i] > probs[best]) best = i }
        fun prob(i: Int) = probs[i]
        val score = (if (sinkIdx >= 0) prob(sinkIdx) else 0f) + 0.3f * helperIdx.sumOf { prob(it).toDouble() }.toFloat()
        return Result(score.coerceAtMost(1f), labels.getOrElse(best) { "?" }, prob(best))
    }

    fun close() = interpreter.close()

    companion object { const val SIZE = 224 }
}
