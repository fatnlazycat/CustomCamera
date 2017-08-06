package eu.aejis.mycustomcamera

import android.content.Context
import android.hardware.Camera
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View


/** A basic Camera preview class  */
class CameraPreview(context: Context) : SurfaceView(context) {
    lateinit var cameraParameters: Camera.Parameters
    //var bestFitPreviewSize: Camera.Size? = null
    var cameraOrientation: Int = 0
    private val mHolder: SurfaceHolder = holder
    var videoMode = false

    fun init(c: SurfaceHolder.Callback, cameraParams: Camera.Parameters, orientation: Int) {
        // Install a SurfaceHolder.Callback so we get notified when the
        // underlying surface is created and destroyed.
        mHolder.addCallback(c)
        // deprecated setting, but required on Android versions prior to 3.0
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS)

        cameraParameters = cameraParams
        cameraOrientation = orientation
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = View.resolveSize(suggestedMinimumWidth, widthMeasureSpec)
        val height = View.resolveSize(suggestedMinimumHeight, heightMeasureSpec)
        var newWidth = width
		var newHeight = height

        val supportedPreviewSizes = cameraParameters.supportedPreviewSizes

        val (bestFitPreviewSize, bestFitPictureSize) = if (videoMode) {
            val supportedPictureSizes = getSupportedVideoSizes()
            Utils.getOptimalCameraSizesForVideo(supportedPreviewSizes, supportedPictureSizes, width, height)
        } else {
            val supportedPictureSizes = cameraParameters.supportedPictureSizes
            Utils.getOptimalCameraSizesForImage(supportedPreviewSizes, supportedPictureSizes, width, height)
        }

        bestFitPictureSize?.let {pictureSize ->
            if (videoMode) cameraParameters.set(IntentExtras.VIDEO_SIZE, Utils.cameraSizeToString(pictureSize))
            else cameraParameters.setPictureSize(pictureSize.width, pictureSize.height)
        }

        bestFitPreviewSize?.let { previewSize ->
            cameraParameters.setPreviewSize(previewSize.width, previewSize.height)

            val biggerPreviewSide = Math.max(previewSize.height, previewSize.width)
            val smallerPreviewSide = Math.min(previewSize.height, previewSize.width)

            val ratio: Float = biggerPreviewSide.toFloat() / smallerPreviewSide.toFloat()

            val biggerDimension = Math.max(width, height)
            val smallerDimension = Math.min(width, height)

            val biggerDimensionScaled = Math.min((smallerDimension * ratio).toInt(), biggerDimension)
            val smallerDimensionScaled = (biggerDimensionScaled / ratio).toInt()

            if (width == biggerDimension) {
                newWidth = biggerDimensionScaled
                newHeight = smallerDimensionScaled
            } else {
                newWidth = smallerDimensionScaled
                newHeight = biggerDimensionScaled
            }
        }
        setMeasuredDimension(newWidth, newHeight)
        //setMeasuredDimension(480, 640)

        /*when (cameraOrientation) {
            0, 180  -> setMeasuredDimension(newWidth, newHeight)
            90, 270 -> setMeasuredDimension(newHeight, newWidth)
        }*/
    }

    fun getSupportedVideoSizes(): List<Camera.Size> {
        val supportedSizes = cameraParameters.supportedVideoSizes
        // Video sizes may be null, which indicates that all the supported
        // preview sizes are supported for video recording.
        return supportedSizes ?: cameraParameters.supportedPreviewSizes
    }
}