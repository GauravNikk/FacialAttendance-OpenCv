package webview.again.facialattendance

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.Camera
import android.os.Bundle
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.opencv.android.OpenCVLoader
import org.opencv.core.Mat
import org.opencv.android.Utils
import org.opencv.imgproc.Imgproc

class MainActivity : AppCompatActivity(), SurfaceHolder.Callback, Camera.PreviewCallback {

    private lateinit var surfaceView: SurfaceView
    private lateinit var surfaceHolder: SurfaceHolder
    private var camera: Camera? = null
    private lateinit var faceDetector: FaceDetector

    init {
        if (!OpenCVLoader.initDebug()) {
            Log.e("OpenCV", "OpenCV initialization failed")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        surfaceView = findViewById(R.id.surface_view)
        surfaceHolder = surfaceView.holder
        surfaceHolder.addCallback(this)

        faceDetector = FaceDetector(this)

        // Request camera permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 1)
        }
    }

//    override fun surfaceCreated(holder: SurfaceHolder) {
//        camera = Camera.open()
//        camera?.setPreviewDisplay(holder)
//        camera?.setPreviewCallback(this)
//        camera?.startPreview()
//    }
//
//    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
//        // Check if the camera is already running
//        if (camera != null) {
//            // Stop the preview before making changes
//            camera?.stopPreview()
//
//            // Set the camera parameters
//            val params = camera?.parameters
//            params?.setPreviewSize(width, height)
//            camera?.parameters = params
//
//            // Start the camera preview again
//            try {
//                camera?.setPreviewDisplay(holder)
//                camera?.startPreview()
//            } catch (e: Exception) {
//                e.printStackTrace()
//            }
//        }
//    }
//
override fun surfaceCreated(holder: SurfaceHolder) {
    try {
        camera = Camera.open()
        camera?.setPreviewDisplay(holder)

        // Configure camera parameters
        val params = camera?.parameters

        // Get supported preview sizes and choose an optimal one
        val supportedSizes = params?.supportedPreviewSizes
        val optimalSize = getOptimalPreviewSize(supportedSizes, surfaceView.width, surfaceView.height)
        params?.setPreviewSize(optimalSize?.width ?: 640, optimalSize?.height ?: 480)

        camera?.parameters = params

        camera?.setPreviewCallback(this)
        camera?.startPreview()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        if (camera != null) {
            try {
                camera?.stopPreview()

                // Adjust camera parameters here, like preview size
                val params = camera?.parameters
                val supportedSizes = params?.supportedPreviewSizes
                val optimalSize = getOptimalPreviewSize(supportedSizes, width, height)
                params?.setPreviewSize(optimalSize?.width ?: 640, optimalSize?.height ?: 480)

                camera?.parameters = params

                camera?.setPreviewDisplay(holder)
                camera?.startPreview()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Helper function to choose the best preview size based on the surface size
    private fun getOptimalPreviewSize(sizes: List<Camera.Size>?, w: Int, h: Int): Camera.Size? {
        val aspectRatio = w.toDouble() / h
        var optimalSize: Camera.Size? = null
        var minDiff = Double.MAX_VALUE

        sizes?.forEach { size ->
            val ratio = size.width.toDouble() / size.height
            if (Math.abs(ratio - aspectRatio) < minDiff) {
                optimalSize = size
                minDiff = Math.abs(ratio - aspectRatio)
            }
        }
        return optimalSize
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        camera?.stopPreview()
        camera?.release()
        camera = null
    }



    override fun onPreviewFrame(data: ByteArray?, camera: Camera?) {
        val size = camera?.parameters?.previewSize
        if (data != null && size != null) {
            // Create a Mat to hold the YUV image
            val yuvMat = Mat(size.height + size.height / 2, size.width, org.opencv.core.CvType.CV_8UC1)

            // Put the byte array (YUV) into the Mat
            yuvMat.put(0, 0, data)

            // Convert YUV to RGB
            val rgbMat = Mat(size.height, size.width, org.opencv.core.CvType.CV_8UC3)
            Imgproc.cvtColor(yuvMat, rgbMat, Imgproc.COLOR_YUV2RGB_NV21)

            // Pass the RGB Mat to the face detector
            faceDetector.detectFaces(rgbMat)
        }
    }

}
