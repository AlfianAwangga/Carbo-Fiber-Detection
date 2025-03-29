package com.example.carbofiber

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class ObjectDetector(context: Context, modelFileName: String) {

    private var interpreter: Interpreter? = null
    private val model: MappedByteBuffer = loadModelFile(context, modelFileName)
    private val labels = loadLabels(context)

    init {
        interpreter = Interpreter(model)
    }

    private fun loadModelFile(context: Context, modelFileName: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelFileName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    private fun loadLabels(context: Context): List<String> {
        val labels = mutableListOf<String>()
        try {
            val reader = BufferedReader(InputStreamReader(context.assets.open("labels.txt")))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                labels.add(line!!)
            }
            reader.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return labels
    }

    private fun preprocessImage(bitmap: Bitmap): ByteBuffer {
        val inputImage = Bitmap.createScaledBitmap(bitmap, 224, 224, true) // Sesuaikan ukuran input
        val inputBuffer = ByteBuffer.allocateDirect(224 * 224 * 3 * 4).order(ByteOrder.nativeOrder())
        val pixels = IntArray(224 * 224)
        inputImage.getPixels(pixels, 0, 224, 0, 0, 224, 224)
        for (pixelValue in pixels) {
            val r = (pixelValue shr 16) and 0xFF
            val g = (pixelValue shr 8) and 0xFF
            val b = pixelValue and 0xFF
            inputBuffer.putFloat((r - 127.5f) / 127.5f)
            inputBuffer.putFloat((g - 127.5f) / 127.5f)
            inputBuffer.putFloat((b - 127.5f) / 127.5f)
        }
        return inputBuffer
    }

    fun recognizeImage(bitmap: Bitmap): Pair<String, Float> {
        val inputImage = preprocessImage(bitmap)
        val outputBuffer = ByteBuffer.allocateDirect(1000 * 4).order(ByteOrder.nativeOrder()) // Sesuaikan ukuran output
        interpreter?.run(inputImage, outputBuffer)
        return postprocessOutput(outputBuffer)
    }

    private fun postprocessOutput(outputBuffer: ByteBuffer): Pair<String, Float> {
        val probabilities = FloatArray(labels.size)
        outputBuffer.rewind()
        for (i in probabilities.indices) {
            probabilities[i] = outputBuffer.float
        }

        var maxProbability = 0f
        var maxIndex = -1
        for (i in probabilities.indices) {
            if (probabilities[i] > maxProbability) {
                maxProbability = probabilities[i]
                maxIndex = i
            }
            Log.d("ObjectDetector", "${labels[i]}: ${String.format("%.4f", probabilities[i])}")
        }

        return if (maxIndex != -1) {
            Pair(labels[maxIndex], maxProbability)
        } else {
            Pair("Tidak Dapat Mengenali Objek", 0f)
        }
    }
}