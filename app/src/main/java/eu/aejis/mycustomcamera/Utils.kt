package eu.aejis.mycustomcamera

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Point
import android.hardware.Camera
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.OrientationEventListener
import android.view.Surface
import java.io.File
import java.text.SimpleDateFormat
import java.util.*


/**
 * Created by Dima on 29.06.2017.
 */
object Utils {
    val TAG = "CameraUtils"
    val MEDIA_TYPE_IMAGE = 1
    val MEDIA_TYPE_VIDEO = 2

    private val FILE_NAME_PREFIX = "punisher_"
    const val JPG = ".jpg"
    val PNG = ".png"
    val MP4 = ".mp4"

    fun getMediaFileUri(context: Context, mediaType: String): Uri? {
        val file = getMediaFile(context, mediaType)
        val result = if (file != null) Uri.fromFile(file) else null
        return result
    }

    fun getMediaFile(context: Context, mediaType: String): File? {
        var mediaFile: File? = null
        var fileName = "yet empty"
        if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
            try {
                val appPrivateDir = context.getExternalFilesDir(null)
                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
                val extension = if (mediaType == MediaStore.ACTION_IMAGE_CAPTURE) JPG else MP4
                fileName = appPrivateDir.toString() + File.separator + FILE_NAME_PREFIX + timeStamp + extension
                mediaFile = File(fileName)
            } catch (e: Exception) {
                return null
            }

        }
        return mediaFile
    }

    /** Check if this device has a camera  */
    fun checkCameraHardware(context: Context): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA)
    }

    /** A safe way to get an instance of the Camera object.  */
    fun getCameraInstance(): Camera? {
        var c: Camera? = null
        try {
            c = Camera.open(CustomCamera.DEFAULT_CAMERA_ID) // attempt to get a Camera instance
        } catch (e: Exception) {
            // Camera is not available (in use or does not exist)
        }

        return c // returns null if camera is unavailable
    }


    fun getCameraDisplayOrientation(activity: Activity, cameraId: Int): Int {
        val info = getCameraInfo(cameraId)
        val displayRotation = activity.windowManager.defaultDisplay.rotation
        var defaultDisplayOrientation = 0
        when (displayRotation) {
            Surface.ROTATION_0      -> defaultDisplayOrientation = 0
            Surface.ROTATION_90     -> defaultDisplayOrientation = 90
            Surface.ROTATION_180    -> defaultDisplayOrientation = 180
            Surface.ROTATION_270    -> defaultDisplayOrientation = 270
        }

        var result: Int
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + defaultDisplayOrientation) % 360
            result = (360 - result) % 360  // compensate the mirror
        } else {  // back-facing
            result = (info.orientation - defaultDisplayOrientation + 360) % 360
        }
        return result
    }

    fun getCameraInfo(cameraId: Int): android.hardware.Camera.CameraInfo {
        val info = android.hardware.Camera.CameraInfo()
        android.hardware.Camera.getCameraInfo(cameraId, info)
        return info
    }

    fun getCameraOrientation(cameraId: Int): Int {
        val info = android.hardware.Camera.CameraInfo()
        android.hardware.Camera.getCameraInfo(cameraId, info)
        return info.orientation
    }

    fun makeOrientationListener(ctx: Context, callback: (Int) -> Unit): OrientationEventListener {
        val orientationListener = object : OrientationEventListener(ctx) {
            override fun onOrientationChanged(orientation: Int) {
                callback(orientation)
            }
        }
        return orientationListener
    }

    fun resumeOrintationListening(listener: OrientationEventListener) {
        with(listener) {
            if (canDetectOrientation()) enable()
        }
    }

    fun pauseOrientationListening(listener: OrientationEventListener?) {
        listener?.disable()
    }

    fun getRatioDifference(arg1: Camera.Size?, arg2: Camera.Size?): Float {
        if (arg1 == null || arg2 == null) return 1.0F

        val ratio1 = arg1.height.toFloat() / arg1.width.toFloat()
        val ratio2 = arg2.height.toFloat() / arg2.width.toFloat()
        return Math.abs(ratio1 - ratio2)
    }

    fun getBigSideDifference(arg1: Camera.Size?, arg2: Point?): Int {
        if (arg1 == null || arg2 == null) return Int.MAX_VALUE

        val biggerSide1 = Math.max(arg1.height, arg1.width)
        val biggerSide2 = Math.max(arg2.y, arg2.x)
        return Math.abs(biggerSide1 - biggerSide2)
    }

    fun getOptimalCameraSizesForImage(previewSizes: List<Camera.Size>, pictureSizes: List<Camera.Size>, w: Int, h: Int):
            Pair<Camera.Size?, Camera.Size?> {

        //Max image size from Bogdanovich
        val BIGGER_SIDE_OF_IMAGE_MAX_SIZE = 1280

        //height compared to witdh & vice versa intentionally (different formats of returns)
        val excludeBigPreviewSizes = previewSizes.filter { it.height <= w && it.width <= h }

        val excludeBigPictureSizes = pictureSizes.filter { Math.max(it.height, it.width) <= BIGGER_SIDE_OF_IMAGE_MAX_SIZE }

        //data class CameraSizeInfo(val previewSize: Camera.Size, val pictureSize: Camera.Size, val ratioDifference: Float )


        val bestR = excludeBigPreviewSizes.map { previewSize ->
            getRatioDifference(previewSize, excludeBigPictureSizes.minBy { getRatioDifference(previewSize, it) })
        }.min()

        Log.d(TAG, "bestR=" + bestR)

        val cameraSizeInfoList = excludeBigPreviewSizes.map { previewSize ->
            val bestPicSize = excludeBigPictureSizes.sortedBy { picSize ->
                Math.abs(previewSize.height - picSize.height)
            }. minBy {
               getRatioDifference(it, previewSize)
            }
            Pair(previewSize, bestPicSize)
        }.filter { getRatioDifference(it.first, it.second) == bestR }

        var aspectTolerance = 0.0

        //width / height intentionally - see below
        val targetRatio = w.toFloat() / h.toFloat()
        Log.d(TAG, "targetRatio=" + targetRatio)

        fun getSizesWithTargetRatio(): List<Camera.Size> {

            //height / width intentionally - see higher (different formats of returns)
            var result = cameraSizeInfoList
                    .map {it.first}
                    .filter {
                        Math.abs((it.height.toFloat() / it.width.toFloat()) - targetRatio) <= aspectTolerance
                    }
            if (result.isEmpty()) {
                aspectTolerance += 0.05
                result = getSizesWithTargetRatio()
            }
            return result.sortedBy {
                Math.abs((it.height.toFloat() / it.width.toFloat()) - targetRatio)
            }
        }

        val optimalPreviewSize: Camera.Size? = getSizesWithTargetRatio().minBy {
            getBigSideDifference(it, Point(w, h))
        }
        Log.d(TAG, "optimalPreviewSize=" + optimalPreviewSize?.height + "x" + optimalPreviewSize?.width)
        val optimalPictureSize = cameraSizeInfoList.find { it.first == optimalPreviewSize }?.second ?: optimalPreviewSize
        Log.d(TAG, "optimalPictureSize=" + optimalPictureSize?.height + "x" + optimalPictureSize?.width)
        return Pair(optimalPreviewSize, optimalPictureSize)
    }

    fun getOptimalCameraSizesForVideo(previewSizes: List<Camera.Size>, pictureSizes: List<Camera.Size>, w: Int, h: Int):
            Pair<Camera.Size?, Camera.Size?> {

        val ACCEPTABLE_VIDEO_WIDTH = 640
        val ACCEPTABLE_VIDEO_HEIGHT = 480

        val excludeBigPictureSizes = pictureSizes.filter {
            it.height <= h && it.width <= w &&
                    it.height >= ACCEPTABLE_VIDEO_HEIGHT && it.width >= ACCEPTABLE_VIDEO_WIDTH
        }

        val listToChooseFrom = if (excludeBigPictureSizes.none()) pictureSizes else excludeBigPictureSizes
        val optimalPictureSize = listToChooseFrom
                .sortedBy { Math.abs(it.width - ACCEPTABLE_VIDEO_WIDTH) }
                .minBy { Math.abs(it.height - ACCEPTABLE_VIDEO_HEIGHT) }

        //height compared to witdh & vice versa intentionally (different formats of returns)
        val excludeBigPreviewSizes = previewSizes.filter { it.height <= w && it.width <= h }
        val optimalPreviewSize = excludeBigPreviewSizes
                .sortedBy { getBigSideDifference(it, Point(w, h)) }
                .minBy { getRatioDifference(it, optimalPictureSize) }

        return Pair(optimalPreviewSize, optimalPictureSize)
    }

    // returns screen sizes in pixels
    fun getScreenSizes(ctx: Activity): Point {
        val display = ctx.windowManager.defaultDisplay
        val size    = Point()
        display.getSize(size)
        return size
    }

    fun cameraSizeToString(arg: Camera.Size): String {
        return "" + arg.width + "x" + arg.height
    }
    fun stringToPoint(arg: String?): Point? {
        val r = Regex("\\d+x\\d+")
        if (arg != null && arg.matches(r)) {
            val list = arg.split("x")
            return Point(list.component1().toInt(), list.component2().toInt())
        } else return null /*throw IllegalArgumentException("arg of stringToPoint(arg: String) should be in format '\\d+x\\d+'")*/
    }

    fun booleanFromMediaStoreAction(mediaStoreAction: String?): Boolean = when (mediaStoreAction) {
        MediaStore.ACTION_IMAGE_CAPTURE -> true
        MediaStore.ACTION_VIDEO_CAPTURE -> false
        else                            -> true
    }

    fun mediaStoreActionFromBoolean(actionImage: Boolean) = when (actionImage) {
        true    -> MediaStore.ACTION_IMAGE_CAPTURE
        false   -> MediaStore.ACTION_VIDEO_CAPTURE
    }
}