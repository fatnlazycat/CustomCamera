package eu.aejis.mycustomcamera

import android.content.Context
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
    private val TAG = "CustomCamera"

    companion object {
        const val JPG = ".jpg"
        const val PNG = ".png"
        const val MP4 = ".mp4"

        const val DEFAULT_CAMERA_ID = 0
    }


    var mCamera: Camera? = null
        set(value) {
            field = value
            //val parametersString = cameraParameters?.flatten()

            val focusMode   = cameraParameters?.focusMode
            val videoSize   = cameraParameters?.get(IntentExtras.VIDEO_SIZE)
            val pictureSize = cameraParameters?.pictureSize
            val previewSize = cameraParameters?.previewSize

            //Log.d(TAG, "parametersString=" + parametersString)
            value?.parameters   ?. let {
                cameraParameters = it
                //Log.d(TAG, "parametersString=" + it.flatten())
            }
            //parametersString    ?. let { cameraParameters?.unflatten(it) }



            cameraParameters?. let {params ->
                focusMode   ?. let {params.focusMode = it}
                videoSize   ?. let {params.set(IntentExtras.VIDEO_SIZE, it)}
                pictureSize ?. let {params.setPictureSize(it.width, it.height)}
                previewSize ?. let {params.setPreviewSize(it.width, it.height)}
                                    params.setRotation(mediaOrientation)
                ctx.mPreview?.cameraParameters = params
            }
        }

    private var cameraParameters: Camera.Parameters? = null
    private var mMediaRecorder: MediaRecorder? = null
    private var actionImage = true
    private var isRecording = false
    private var processingFile = false
    private var previewOrientation: Int = 0
    private val cameraOrientation: Int by lazy {Utils.getCameraOrientation(DEFAULT_CAMERA_ID)}
    private var mediaOrientation: Int = 0

    private val orientationListener: OrientationEventListener by lazy {
        Utils.makeOrientationListener(ctx.asActivity(), {
            orient ->
            val oldMediaOrientation = mediaOrientation
            mediaOrientation = (Math.round(Math.max(orient, 0) / 90.0).toInt() * 90 + cameraOrientation) % 360
            //Math.max - because orient can be -1
            // % 360 - to switch from 360 to 0

            if (mediaOrientation != oldMediaOrientation) {
                val viewsAngle = 90 * (1 - mediaOrientation / 90) //because the Activity orientation is portrait
                ctx.onRotation(viewsAngle)
            }
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
    private val sensor: Sensor by lazy {sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)}

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

        actionImage = Utils.booleanFromMediaStoreAction(action)
    }

    private fun initCamera() {
        Log.d(TAG, "initCamera before, camera=" + mCamera)
        if (mCamera == null) {
            mCamera = Utils.getCameraInstance()
        }

        //handle this exception in Activity to finish it
        if (mCamera == null) throw NullPointerException("camera is null, can't init!")

        Log.d(TAG, "initCamera after, camera=" + mCamera)
    }

    private fun deleteMediaFile() {
        lastCapturedFile?.delete()
    }

    private fun missionComplete() {
        Log.d(TAG, "missionComplete entered")
        lastCapturedFile?. let {
            if (it.exists() && it.length() > 0) {
                Log.d(TAG, "missionComplete updating UI")
                ctx.setUIStatus(false)
                ctx.showResult(it.toString())
            }
        }
    }

    private fun startVideoRecording() {
        isRecording = true
        // initialize video camera
        if (prepareVideoRecorder()) {
            // Camera is available and unlocked, MediaRecorder is prepared, now you can start recording
            mMediaRecorder?.start()
            /*record-during-incoming-call block
            ctx.mPreview?.visibility = View.GONE
            */
            orientationListener.disable()

            // inform the user that recording has started
            ctx.setUIStatus(true)
        } else {
            // prepare didn't work, release the camera
            releaseMediaRecorder()
            isRecording = false
            // inform user
            ctx.showError("Can't prepare media recorder!")
        }
    }

    private fun stopVideoRecording() {
        Log.d(TAG, "stopVideoRecording()")
        // stop recording and release camera
        if (isRecording && !processingFile) {
            processingFile = true
            try {
                mMediaRecorder?.stop() // stop the recording
            } catch (e: RuntimeException) {//thrown when stop immediately after start
                Log.d(TAG, "MediaRecorder.stop - no data", e)
            }

            releaseMediaRecorder() // release the MediaRecorder object

            orientationListener.enable()

            missionComplete()

            isRecording = false
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
                //ctx.setRecordButtonEnabled(true)
                if (parameters.supportedFocusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                    parameters.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE
                } else if (parameters.supportedFocusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                    useSensor = true
                }
                parameters.setRotation(mediaOrientation)
            }
        }
        Log.d(TAG, "initMode, mode=" + modeVideo)
        mCamera?.parameters = cameraParameters

        processingFile = false
    }

    fun getActionListener(): View.OnClickListener {
        return getActionListener(actionImage)
    }

    fun getActionListener(actionFlagImage: Boolean): View.OnClickListener {
        this.actionImage = actionFlagImage

        val imageListener = View.OnClickListener { v: View ->
            Log.d(TAG, "imageListener entered, processingFile=" + processingFile)
            if (processingFile) return@OnClickListener

            //ctx.setRecordButtonEnabled(false)
            processingFile = true

            cameraParameters?.setRotation(mediaOrientation)
            Log.d(TAG, "getActionListener, cameraParameters.pictureSize=${cameraParameters?.pictureSize?.height}x${cameraParameters?.pictureSize?.width}")
            mCamera?.parameters = cameraParameters
            Log.d(TAG, "getActionListener, mCamera.parameters.pictureSize=${mCamera?.parameters?.pictureSize?.height}x${mCamera?.parameters?.pictureSize?.width}")

            // get an image from the camera
            Log.d(TAG, "before takePicture")
            mCamera?.takePicture(null, null, pictureCallback)
        }

        val videoListener = View.OnClickListener {
            if (processingFile) return@OnClickListener
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
        Log.d(TAG, "pictureCallback entered")
        lastCapturedFile = getMediaFile(mediaPath, MediaStore.ACTION_IMAGE_CAPTURE)
        if (lastCapturedFile == null) {
            Log.d(TAG, "Error creating media file, check storage permissions")
            processingFile = false
            //ctx.setRecordButtonEnabled(true)
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

        missionComplete()
        Log.d(TAG, "pictureCallback exit")
    }

    private fun prepareVideoRecorder(): Boolean {
        val camera = mCamera

        //no need to check if it's null, moreover - we don't need the old MediaRecorder, if any, because it can be in wrong state
        /*if (mMediaRecorder == null)*/ mMediaRecorder = MediaRecorder()
        val mediaRecorder = mMediaRecorder

        if (camera == null || mediaRecorder == null) return false
        else {
            mediaRecorder.setOnInfoListener(this)
            mediaRecorder.setOnErrorListener(this)

            // Step 1: Unlock and set camera to MediaRecorder
            Log.d(TAG, "prepareVideoRecorder, camera=" + camera)
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
            lastCapturedFile = getMediaFile(mediaPath, MediaStore.ACTION_VIDEO_CAPTURE)
            //or
            // lastCapturedFile = Utils.getMediaFile(ctx, MediaStore.ACTION_VIDEO_CAPTURE)
            if (lastCapturedFile == null) {
                Log.d(TAG, "Error creating media file, check storage permissions")
                return false
            }

            mediaRecorder.setAudioChannels(1) //mono
            mediaRecorder.setVideoEncodingBitRate(2 * 1024 * 1024) //2Mbps
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

    private fun releaseMediaRecorder() {
        mMediaRecorder?.reset()   // clear recorder configuration
        mMediaRecorder?.release() // release the recorder object
        mMediaRecorder = null
        Log.d(TAG, "releaseVideoRecorder, camera=" + mCamera)
        mCamera?.lock()           // lock camera for later use
        mCamera?.startPreview()
    }

    fun releaseCamera() {
        mCamera?.release()        // release the camera for other applications
        mCamera = null
        //cameraParameters = null
    }

    fun releaseAll() {
        releaseMediaRecorder()
        releaseCamera()
    }


    override fun surfaceCreated(holder: SurfaceHolder) {
        Log.d(TAG, "surfaceCreated")

        // The Surface has been created, now tell the camera where to draw the preview.
        /*record-during-incoming-call block
        if (!isRecording)*/
         /*   try {
            mCamera?.let {camera ->
                camera.setPreviewDisplay(holder)
                camera.startPreview()
            }
        } catch (e: IOException) {
            Log.d(TAG, "Error setting camera preview: " + e.message)
        }*/
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Log.d(TAG, "surfaceDestroyed")
        // empty. Take care of releasing the Camera preview in your activity.
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        Log.d(TAG, "surfaceChanged")

        // If your preview can change or rotate, take care of those events here.
        // Make sure to stop the preview before resizing or reformatting it.

        if (holder.surface == null) {
            // preview surface does not exist
            return
        }

        /*record-during-incoming-call block
        if (!isRecording)*/
        mCamera?.let { camera ->
            // stop preview before making changes
            try {
                camera.stopPreview()

                //call this after the preview is stopped - otherwise crash on Nexus 7
                initMode(modeVideo = !actionImage)
            } catch (e: Exception) {
                // ignore: tried to stop a non-existent preview
            }

            // green mess in high quality video file without this
            // parameters.set( "cam_mode", 1 )

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

    private fun doAutoFocus() {
        autoFocusInProgress = true
        mCamera?.autoFocus({ _, _ ->
            autoFocusInProgress = false
        })
    }

    override fun onInfo(mr: MediaRecorder?, what: Int, extra: Int) {
        when (what) {
            MediaRecorder.MEDIA_RECORDER_INFO_UNKNOWN ->
                ctx.showError("mediaRecorder info unknown=" + extra)

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
        ctx.showError("mediaRecorder unknown error =" + extra)
        stopVideoRecording()
    }

}