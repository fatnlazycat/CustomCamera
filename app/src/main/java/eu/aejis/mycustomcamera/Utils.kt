package eu.aejis.mycustomcamera

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Camera
import android.net.Uri
import android.os.Environment
import java.io.File.separator
import android.os.Environment.DIRECTORY_PICTURES
import android.os.Environment.getExternalStoragePublicDirectory
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


    fun getCameraDisplayOrientation(activity: Activity,
                                    cameraId: Int): Int {
        val info = android.hardware.Camera.CameraInfo()
        android.hardware.Camera.getCameraInfo(cameraId, info)
        val displayRotation = activity.windowManager.defaultDisplay.rotation
        var defaultDisplayOrientation = 0
        when (displayRotation) {
            Surface.ROTATION_0 -> defaultDisplayOrientation = 0
            Surface.ROTATION_90 -> defaultDisplayOrientation = 90
            Surface.ROTATION_180 -> defaultDisplayOrientation = 180
            Surface.ROTATION_270 -> defaultDisplayOrientation = 270
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

    fun getOptimalCameraSize(sizes: List<Camera.Size>?, w: Int, h: Int): Camera.Size? {
        //Max image size from Bogdanovich
        val BIGGER_SIDE_OF_IMAGE_MAX_SIZE = 1280

        if (sizes == null) return null

        val widthToCheck = Math.min(w, BIGGER_SIDE_OF_IMAGE_MAX_SIZE)
        val heightToCheck = Math.min(h, BIGGER_SIDE_OF_IMAGE_MAX_SIZE)
        val optimalSize: Camera.Size? = sizes
                .filter { it.height <= heightToCheck && it.width <= widthToCheck }
                .minBy { Math.abs(it.height - heightToCheck) }

        return optimalSize
    }

    fun getAcceptableVideoSize(sizes: List<Camera.Size>?, w: Int, h: Int): Camera.Size? {
        val ACCEPTABLE_VIDEO_WIDTH = 640
        val ACCEPTABLE_VIDEO_HEIGHT = 480

        if (sizes == null) return null

        val acceptableSizes = sizes.filter {
            it.height <= h && it.width <= w &&
                    it.height >= ACCEPTABLE_VIDEO_HEIGHT && it.width >= ACCEPTABLE_VIDEO_WIDTH
        }

        val listToChooseFrom = if (acceptableSizes.none()) sizes else acceptableSizes
        val optimalSize = listToChooseFrom
                .sortedBy { Math.abs(it.width - ACCEPTABLE_VIDEO_WIDTH) }
                .minBy { Math.abs(it.height - ACCEPTABLE_VIDEO_HEIGHT) }


        return optimalSize
    }

}