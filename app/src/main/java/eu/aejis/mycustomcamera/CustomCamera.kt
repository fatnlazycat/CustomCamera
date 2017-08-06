package eu.aejis.mycustomcamera

import android.content.Context
import android.graphics.Point
import android.hardware.*
import android.hardware.Camera.PictureCallback
import android.media.CamcorderProfile
import android.media.MediaRecorder
import android.provider.MediaStore
import android.util.Log
import android.view.OrientationEventListener
import android.view.SurfaceHolder
import android.view.View
import android.widget.Toast
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException


/**
 * Created by Dima on 27.06.2017.
 */
class CustomCamera (
        val ctx: CameraHost,
        action: String?,
        val mediaPath: String?) : SurfaceHolder.Callback, SensorEventListener,
                                  MediaRecorder.OnInfoListener, MediaRecorder.OnErrorListener {
    val TAG = "CustomCamera"

    companion object {
        const val JPG = ".jpg"
        const val PNG = ".png"
        const val MP4 = ".mp4"

        const val DEFAULT_CAMERA_ID = 0
    }


    var mCamera: Camera? = null

    val cameraParameters: Camera.Parameters? by lazy {
        mCamera?.parameters
    }

    var mMediaRecorder: MediaRecorder? = null
    private var actionImage = true
    var isRecording = false
    var processingFile = false
    var previewOrientation: Int = 0
    val cameraOrientation: Int by lazy {Utils.getCameraOrientation(DEFAULT_CAMERA_ID)}
    var mediaOrientation: Int = 0

    val orientationListener: OrientationEventListener by lazy {
        Utils.makeOrientationListener(ctx.asActivity(), {
            orient ->
            mediaOrientation = (Math.round(Math.max(orient, 0) / 90.0).toInt() * 90 + cameraOrientation) % 360
            //Math.max - because orient can be -1
            // % 360 - to switch from 360 to 0
        })
    }

    var autoFocusInProgress = false

    var useSensor = false
    var sensorInitialized = false
    var sensorLastX: Float = 0.0F
    var sensorLastY: Float = 0.0F
    var sensorLastZ: Float = 0.0F
    val sensorManager by lazy {
        ctx.asActivity().getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }
    val sensor: Sensor by lazy {sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)}

    var lastCapturedFile: File? = null
        set(value) {
            deleteMediaFile()
            field = value
        }

    init {
        initCamera()

        cameraParameters ?. let {cameraParameters ->
            ctx.mPreview?.init(this,
                    cameraParameters,
                    Utils.getCameraInfo(DEFAULT_CAMERA_ID).orientation)
        }

        actionImage = when (action) {
            MediaStore.ACTION_IMAGE_CAPTURE -> true
            MediaStore.ACTION_VIDEO_CAPTURE -> false
            else                            -> true
        }
    }

    fun initCamera() {
        if (mCamera == null) mCamera = Utils.getCameraInstance()
    }

    fun deleteMediaFile() {
        lastCapturedFile?.delete()
    }

    fun missionComplete() {
        lastCapturedFile?. let {
            if (it.exists() && it.length() > 0) {
                ctx.setRecordButtonStatus(false)
                ctx.showResult(it.toString())
            }
        }
    }

    fun startVideoRecording() {
        // initialize video camera
        if (prepareVideoRecorder()) {
            // Camera is available and unlocked, MediaRecorder is prepared,
            // now you can start recording
            mMediaRecorder?.start()

            orientationListener.disable()

            // inform the user that recording has started
            ctx.setRecordButtonStatus(true)
            isRecording = true
        } else {
            // prepare didn't work, release the camera
            releaseMediaRecorder()
            // inform user
            ctx.showError("Can't prepare media recorder!")
        }
    }

    fun stopVideoRecording() {
        // stop recording and release camera
        if (isRecording) {
            try {
                mMediaRecorder?.stop() // stop the recording
            } catch (e: RuntimeException) {//thrown when stop immediately after start
                Log.d(TAG, "MediaRecorder.stop - no data", e)
            }

            releaseMediaRecorder() // release the MediaRecorder object

            orientationListener.enable()

            isRecording = false

            missionComplete()
        }
    }

    fun initMode(modeVideo: Boolean) {
        ctx.setPreviewMode(modeVideo)
        cameraParameters?.let {parameters ->
            if (modeVideo) { //video
                if (parameters.supportedFocusModes != null
                        && parameters.supportedFocusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
                    parameters.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO
                }
            } else { //image
                //don't need this block because pictureSize is being set in CameraPreview.onMeasure()
                /*val previewSize = ctx.mPreview?.bestFitPreviewSize
                val supportedPictureSizes = parameters.supportedPictureSizes
                previewSize ?. let {
                    val bestPictureSize = Utils.getOptimalPictureSize(supportedPictureSizes, previewSize.width, previewSize.height)
                    bestPictureSize?.let {
                        parameters.setPictureSize(bestPictureSize.width, bestPictureSize.height)
                        //parameters.setPictureSize(640, 480)
                    }
                }*/

                if (parameters.supportedFocusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                    parameters.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE
                } else if (parameters.supportedFocusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                    useSensor = true
                }
                parameters.setRotation(mediaOrientation)
            }
        }
        mCamera?.parameters = cameraParameters
    }

    fun getActionListener(): View.OnClickListener {
        return getActionListener(actionImage)
    }

    fun getActionListener(actionFlagImage: Boolean): View.OnClickListener {
        this.actionImage = actionFlagImage

        val imageListener = View.OnClickListener {
            if (processingFile) return@OnClickListener

            //this block is duplicated in initMode()
/*            val displaySize = Point()
            ctx.asActivity().windowManager.defaultDisplay.getSize(displaySize)

            val parameters = mCamera?.parameters
            parameters ?. let {
                val supportedPictureSizes = parameters.supportedPictureSizes
                val bestPictureSize = Utils.getOptimalPictureSize(supportedPictureSizes, displaySize.x, displaySize.y)
                bestPictureSize?. let {
                    parameters.setPictureSize(bestPictureSize.width, bestPictureSize.height)
                }

                if (parameters.supportedFocusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                    parameters.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE
                } else if (parameters.supportedFocusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                    useSensor = true
                }
                parameters.setRotation(mediaOrientation)
                mCamera?.parameters = parameters
            }*/

            cameraParameters?.setRotation(mediaOrientation)
            mCamera?.parameters = cameraParameters

            // get an image from the camera
            mCamera?.takePicture(null, null, pictureCallback)
            processingFile = true
        }
        val videoListener = View.OnClickListener {
            if (isRecording) {
                stopVideoRecording()
            } else {
                startVideoRecording()
            }
        }
        return if (actionImage) imageListener else videoListener
    }

    fun getMediaFile(path: String?, mediaType: String): File? {
        if (path == null) return Utils.getMediaFile(ctx.asActivity(), mediaType)
        else {
            val fileExtension = when (mediaType) {
                MediaStore.ACTION_IMAGE_CAPTURE -> JPG
                MediaStore.ACTION_VIDEO_CAPTURE -> MP4
                else -> throw IllegalArgumentException(
                        "getMediaFile(mediaType: String) - mediaType should be one of " +
                                "MediaStore.ACTION_IMAGE_CAPTURE or MediaStore.ACTION_VIDEO_CAPTURE")
            }
            return File(mediaPath + fileExtension)
        }
    }

    private val pictureCallback = PictureCallback { data, camera ->
            lastCapturedFile = getMediaFile(mediaPath, MediaStore.ACTION_IMAGE_CAPTURE)
        if (lastCapturedFile == null) {
            Log.d(TAG, "Error creating media file, check storage permissions")
            processingFile = false
            return@PictureCallback
        }

        try {
            val fos = FileOutputStream(lastCapturedFile)
            fos.write(data)
            fos.close()
        } catch (e: FileNotFoundException) {
            Log.d(TAG, "File not found: " + e.message)
        } catch (e: IOException) {
            Log.d(TAG, "Error accessing file: " + e.message)
        }

        processingFile = false
        missionComplete()
    }

    private fun prepareVideoRecorder(): Boolean {
        val camera = mCamera
        if (mMediaRecorder == null) mMediaRecorder = MediaRecorder()
        val mediaRecorder = mMediaRecorder

        if (camera == null || mediaRecorder == null) return false
        else {
            mediaRecorder.setOnInfoListener(this)
            mediaRecorder.setOnErrorListener(this)

            //call this before unlocking the camera
            /*fun getBestVideoSize(): Camera.Size? {
                val displaySize = Point()
                ctx.asActivity().windowManager.defaultDisplay.getSize(displaySize)
                val result = Utils.getOptimalCameraSizesForVideo(getSupportedVideoSizes(camera), displaySize.x, displaySize.y)
                return result
            }
            val videoSize = getBestVideoSize()*/

            //this block is duplicated in initMode()
            /*val parameters = camera.parameters
            if (parameters.supportedFocusModes != null
                    && parameters.supportedFocusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
                parameters.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO
            }
            camera.parameters = parameters*/

            // Step 1: Unlock and set camera to MediaRecorder
            camera.stopPreview()
            camera.unlock()
            mediaRecorder.setCamera(camera)

            // Step 2: Set sources
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER)
            //mediaRecorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT)
            mediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA)
            //mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.DEFAULT)
            //mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT)
            //mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.DEFAULT)

            // Step 3: Set a CamcorderProfile (requires API Level 8 or higher) + override video size
            mediaRecorder.setProfile(CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH))

            val videoSize = Utils.stringToPoint(cameraParameters?.get(IntentExtras.VIDEO_SIZE))
            videoSize?. let {mediaRecorder.setVideoSize(videoSize.x, videoSize.y)}

            // Step 4: Set output file
            //lastCapturedFile = Utils.getMediaFile(ctx, MediaStore.ACTION_VIDEO_CAPTURE)
            lastCapturedFile = getMediaFile(mediaPath, MediaStore.ACTION_VIDEO_CAPTURE)
            if (lastCapturedFile == null) {
                Log.d(TAG, "Error creating media file, check storage permissions")
                return false
            }

            mediaRecorder.setOutputFile(lastCapturedFile.toString())
            mediaRecorder.setMaxFileSize(99 * 1024 * 1024) //99Mb
            //mediaRecorder.setMaxDuration(2000)

            // Step 5: Set the preview output
            mediaRecorder.setPreviewDisplay(ctx.mPreview?.holder?.surface)
            mediaRecorder.setOrientationHint(mediaOrientation)

            // Step 6: Prepare configured MediaRecorder
            try {
                mediaRecorder.prepare()
            } catch (e: IllegalStateException) {
                Log.d(TAG, "IllegalStateException preparing MediaRecorder: " + e.message)
                releaseMediaRecorder()
                return false
            } catch (e: IOException) {
                Log.d(TAG, "IOException preparing MediaRecorder: " + e.message)
                releaseMediaRecorder()
                return false
            }

            return true
        }
    }

    fun getSupportedVideoSizes(camera: Camera): List<Camera.Size> {
        val supportedSizes = camera.parameters.supportedVideoSizes
        // Video sizes may be null, which indicates that all the supported
        // preview sizes are supported for video recording.
        return supportedSizes ?: camera.parameters.supportedPreviewSizes
    }

    private fun releaseMediaRecorder() {
        mMediaRecorder?.reset()   // clear recorder configuration
        mMediaRecorder?.release() // release the recorder object
        mMediaRecorder = null

        mCamera?.lock()           // lock camera for later use
        mCamera?.startPreview()
    }

    fun releaseAll() {
        releaseMediaRecorder()

        mCamera?.release()        // release the camera for other applications
        mCamera = null
    }


    override fun surfaceCreated(holder: SurfaceHolder) {
        //we need it here so that the layout is complete
        initMode(modeVideo = !actionImage)

        // The Surface has been created, now tell the camera where to draw the preview.
        try {
            mCamera?.let {camera ->
                camera.setPreviewDisplay(holder)
                camera.startPreview()
            }
        } catch (e: IOException) {
            Log.d(TAG, "Error setting camera preview: " + e.message)
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        // empty. Take care of releasing the Camera preview in your activity.
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        // If your preview can change or rotate, take care of those events here.
        // Make sure to stop the preview before resizing or reformatting it.

        if (holder.surface == null) {
            // preview surface does not exist
            return
        }

        mCamera?.let { camera ->
            // stop preview before making changes
            try {
                camera.stopPreview()
            } catch (e: Exception) {
                // ignore: tried to stop a non-existent preview
            }
            // set preview size and make any resize, rotate or
            // reformatting changes here
            //val display = (context.getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay
            /*val previewSize = ctx.mPreview?.bestFitPreviewSize
            previewSize ?. let {
                cameraParameters?.setPreviewSize(it.width, it.height)
                //parameters.setPreviewSize(640, 480)
                Log.d(TAG, "preview size= ${it.width}x${it.height}")
            }*/


/*tag1            val h = ctx.mPreview?.bestFitPreviewSize?.height  ?: height
            val w = ctx.mPreview?.bestFitPreviewSize?.width   ?: width
            parameters.setPreviewSize(w, h)
            // green mess in video file without this
            parameters.set( "cam_mode", 1 )
            //parameters.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO
            //parameters.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE*/
            previewOrientation = Utils.getCameraDisplayOrientation(ctx.asActivity(), DEFAULT_CAMERA_ID)

            /*if (display.rotation == Surface.ROTATION_0) {
                parameters.setPreviewSize(h, w)
                camera.setDisplayOrientation(90)
            }

            if (display.rotation == Surface.ROTATION_90) {
                parameters.setPreviewSize(w, h)
            }

            if (display.rotation == Surface.ROTATION_180) {
                parameters.setPreviewSize(h, w)
            }

            if (display.rotation == Surface.ROTATION_270) {
                parameters.setPreviewSize(w, h)
                camera.setDisplayOrientation(180)
            }*/

            //requestLayout()

            // start preview with new settings
            try {
                camera.setDisplayOrientation(previewOrientation)
                camera.parameters = cameraParameters

                /**/
                /*camera.setDisplayOrientation(90)
                val parameters = camera.parameters
                parameters.setPreviewSize(320, 240)
                parameters.setPictureSize(1280, 960)
                camera.parameters = parameters*/
                /**/
                camera.setPreviewDisplay(holder)
                camera.startPreview()

            } catch (e: Exception) {
                Log.d(TAG, "Error starting camera preview: " + e.message)
            }
        }
    }

    fun onResumeActions() {
        initCamera()
        Utils.resumeOrintationListening(orientationListener)
        if (useSensor) {
            useSensor = sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun onPauseActions() {
        stopVideoRecording()
        releaseAll()
        Utils.pauseOrientationListening(orientationListener)
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val SENSOR_THRESHOLD = .6

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        if (!sensorInitialized) {
            sensorLastX = x
            sensorLastY = y
            sensorLastZ = z
            sensorInitialized = true
        }
        val deltaX = Math.abs(sensorLastX - x)
        val deltaY = Math.abs(sensorLastY - y)
        val deltaZ = Math.abs(sensorLastZ - z)

        if (!autoFocusInProgress
                && (deltaX > SENSOR_THRESHOLD || deltaY > SENSOR_THRESHOLD || deltaZ > SENSOR_THRESHOLD)) {
            doAutoFocus()
        }

        sensorLastX = x
        sensorLastY = y
        sensorLastZ = z
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { }

    fun doAutoFocus() {
        autoFocusInProgress = true
        mCamera?.autoFocus({ _, _ ->
            autoFocusInProgress = false
        })
    }

    override fun onInfo(mr: MediaRecorder?, what: Int, extra: Int) {
        when (what) {
            MediaRecorder.MEDIA_RECORDER_INFO_UNKNOWN ->
                Toast.makeText(
                        ctx.asActivity().applicationContext, "mediaRecorder info unknown=" + extra, Toast.LENGTH_LONG
                ).show()
            MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED,
            MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED -> {
                Toast.makeText(
                        ctx.asActivity().applicationContext, R.string.fileLimitReached, Toast.LENGTH_LONG
                ).show()
                stopVideoRecording()
            }
        }
    }

    override fun onError(mr: MediaRecorder?, what: Int, extra: Int) {
        Toast.makeText(
                ctx.asActivity().applicationContext, "mediaRecorder unknown error =" + extra, Toast.LENGTH_LONG
        ).show()
    }

}