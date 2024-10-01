package webview.again.facialattendance

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.opencv.core.Mat
import org.opencv.core.MatOfRect
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.CascadeClassifier
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.File
import java.io.FileOutputStream
import java.io.ObjectInputStream
import kotlin.math.sqrt

class FaceDetector(private val context: Context) {

    private lateinit var faceCascade: CascadeClassifier
    private lateinit var interpreter: Interpreter

    init {
        loadFaceCascade()
        interpreter = ModelLoader(context).loadModelFile("facenet.tflite")
    }

    private fun loadFaceCascade() {
        val cascadeFile = File(context.filesDir, "haarcascade_frontalface_default.xml")
        if (!cascadeFile.exists()) {
            val inputStream = context.resources.openRawResource(R.raw.haarcascade_frontalface_default)
            val outputStream = FileOutputStream(cascadeFile)
            val buffer = ByteArray(4096)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
            }
            inputStream.close()
            outputStream.close()
        }
        faceCascade = CascadeClassifier(cascadeFile.absolutePath)
    }

    fun detectFaces(frame: Mat) {
        val grayFrame = Mat()
        Imgproc.cvtColor(frame, grayFrame, Imgproc.COLOR_BGR2GRAY)
        val faces = MatOfRect()
        faceCascade.detectMultiScale(grayFrame, faces)

        for (rect in faces.toArray()) {
            val faceBitmap = Bitmap.createBitmap(frame.cols(), frame.rows(), Bitmap.Config.ARGB_8888)
            org.opencv.android.Utils.matToBitmap(frame, faceBitmap)

            val faceEmbedding = getFaceEmbedding(faceBitmap)
            if (isFaceRecognized(faceEmbedding, loadKnownFaceEmbeddings())) {
                Log.d("Face Recognition", "Face recognized")
                AttendanceManager(context).saveEmployeeData("EmployeeName")
            } else {
                Log.d("Face Recognition", "Face not recognized")
            }
        }
    }

    private fun getFaceEmbedding(faceBitmap: Bitmap): FloatArray {
        val imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(112, 112, ResizeOp.ResizeMethod.BILINEAR))
            .build()

        val tensorImage = TensorImage.fromBitmap(faceBitmap)
        val processedImage = imageProcessor.process(tensorImage)

        val output = TensorBuffer.createFixedSize(intArrayOf(1, 128), org.tensorflow.lite.DataType.FLOAT32)
        interpreter.run(processedImage.buffer, output.buffer.rewind())

        return output.floatArray
    }

    private fun loadKnownFaceEmbeddings(): Map<String, FloatArray> {
        val file = File(context.filesDir, "face_embeddings.ser")
        val embeddingsMap = mutableMapOf<String, FloatArray>()

        if (file.exists()) {
            ObjectInputStream(file.inputStream()).use { ois ->
                val existingMap = ois.readObject() as? Map<String, FloatArray>
                if (existingMap != null) {
                    embeddingsMap.putAll(existingMap)
                }
            }
        }

        return embeddingsMap
    }

    private fun isFaceRecognized(faceEmbedding: FloatArray, knownEmbeddings: Map<String, FloatArray>): Boolean {
        val threshold = 0.6f
        for ((_, knownEmbedding) in knownEmbeddings) {
            val distance = calculateEuclideanDistance(faceEmbedding, knownEmbedding)
            if (distance < threshold) return true
        }
        return false
    }

    private fun calculateEuclideanDistance(embedding1: FloatArray, embedding2: FloatArray): Float {
        var sum = 0.0f
        for (i in embedding1.indices) {
            val diff = embedding1[i] - embedding2[i]
            sum += diff * diff
        }
        return sqrt(sum)
    }
}
