package eu.aejis.mycustomcamera

import android.app.Activity
import android.content.Intent
import android.graphics.PorterDuff
import android.net.Uri
import android.os.Bundle
import android.os.PersistableBundle
import android.os.SystemClock
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.widget.*
import com.github.glomadrian.materialanimatedswitch.MaterialAnimatedSwitch
import com.squareup.picasso.Picasso

open class CameraActivity : AppCompatActivity(), CameraHost {
    val TAG = "CameraActivity"

    override var mPreview: CameraPreview? = null

    private var customCamera: CustomCamera? = null

    var chronometer: Chronometer? = null
    var btnCapture: ImageButton? = null
    var switchPhotoVideo: MaterialAnimatedSwitch? = null
    var btnOK: Button? = null
    var btnCancel: Button? = null
    var ivResult: ImageView? = null
    var vvResult: VideoView? = null

    var showingFile: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        // Create an instance of Camera
        if (!Utils.checkCameraHardware(this)) {
            Toast.makeText(this, "Camera problem!", Toast.LENGTH_LONG).show()
            finish()
        } else {
            // Create our Preview view and set it as the content of our activity.
            mPreview = CameraPreview(this)
            val preview = findViewById(R.id.camera_preview) as FrameLayout
            preview.addView(mPreview)

            chronometer         = findViewById(R.id.chronometer)      as Chronometer
            btnCapture          = findViewById(R.id.button_capture)   as ImageButton
            switchPhotoVideo    = findViewById(R.id.switchPhotoVideo) as MaterialAnimatedSwitch
            btnOK               = findViewById(R.id.btnOK)            as Button
            btnCancel           = findViewById(R.id.btnCancel)        as Button
            ivResult            = findViewById(R.id.ivResult)         as ImageView
            vvResult            = findViewById(R.id.vvResult)         as VideoView

            val action: String? = intent?.action
            val mediaPath: String? = intent?.data?.path
            customCamera = CustomCamera(this, action, mediaPath)


            val noSwitch = intent.hasExtra(IntentExtras.NO_SWITCH)
            if (noSwitch) switchPhotoVideo?.visibility = View.GONE

            btnCapture?.setOnClickListener(customCamera?.getActionListener())

            btnOK?.setOnClickListener { _: View? ->
                val resultIntent = Intent()
                //val mediaFileName = customCamera?.lastCapturedFile?.toString()
                resultIntent.putExtra(IntentExtras.MEDIA_FILE, showingFile)
                setResult(Activity.RESULT_OK, resultIntent)
                finish()
            }

            fun resetUI() {
                ivResult?.setImageDrawable(null)
                ivResult?.visibility = View.GONE
                vvResult?.visibility = View.GONE

                btnCapture?.         visibility = View.VISIBLE
                btnOK?.              visibility = View.GONE
                btnCancel?.          visibility = View.GONE
                if (!noSwitch) switchPhotoVideo?.visibility = View.VISIBLE

                btnCapture?.clearColorFilter()
            }

            btnCancel?.setOnClickListener { _: View? ->
                resetUI()
                onResumeActions()
                showingFile = null
            }

            if (!noSwitch) switchPhotoVideo?.setOnCheckedChangeListener({
                flagVideo /*true == pressed == video*/ ->
                customCamera?.initMode(modeVideo = flagVideo)
                btnCapture?.setOnClickListener(
                        customCamera?.getActionListener(actionFlagImage = !flagVideo)
                )
            })

            if (intent.hasExtra(IntentExtras.START_IMMEDIATELY)) btnCapture?.post({
                intent.removeExtra(IntentExtras.START_IMMEDIATELY)
                btnCapture?.performClick()
            })

            showingFile = savedInstanceState?.getString(IntentExtras.SHOWING_FILE)
        }
    }

    override fun onResume() {
        super.onResume()
        val valShowingFile = showingFile
        if (valShowingFile == null) onResumeActions()
        else {
            setRecordButtonStatus(false)
            showResult(valShowingFile)
        }
    }
    fun onResumeActions() {
        customCamera?.onResumeActions()
        mPreview?. let { v ->
            if (v.visibility == View.GONE) v.visibility = View.VISIBLE
        }
    }

    override fun onPause() {
        onPauseActions()
        super.onPause()
    }
    fun onPauseActions() {
        customCamera?.onPauseActions()
        mPreview?.visibility = View.GONE
    }

    override fun onDestroy() {
        customCamera = null
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        showingFile ?. let {outState?.putString(IntentExtras.SHOWING_FILE, showingFile)}
        super.onSaveInstanceState(outState)
    }

    override fun setRecordButtonStatus(status: Boolean) {
        if (status) { //recording - show "Stop"
            btnCapture?.setColorFilter(
                    ContextCompat.getColor(this, android.R.color.holo_red_light),
                    PorterDuff.Mode.MULTIPLY
            )
            switchPhotoVideo?.   visibility = View.GONE

            chronometer?. let { with(it) {
                visibility = View.VISIBLE
                base = SystemClock.elapsedRealtime()
                start()
            }}
        } else { //stopped - show "Start"
            btnCapture?.         visibility = View.INVISIBLE
            btnOK?.              visibility = View.VISIBLE
            btnCancel?.          visibility = View.VISIBLE
            chronometer?. let { with(it) {
                visibility = View.GONE
                stop()
            }}
        }
    }

    override fun showResult(mediaFileName: String) {
        onPauseActions()

        showingFile = mediaFileName

        val FILE_PREFIX = "file://"
        val fileNameWithPrefix = if (mediaFileName.startsWith(FILE_PREFIX)) mediaFileName
            else FILE_PREFIX + mediaFileName

        if (mediaFileName.endsWith(CustomCamera.JPG)) {
            ivResult?.visibility = View.VISIBLE

             val builder = Picasso.Builder(this)
            builder.listener(object : Picasso.Listener
            {
                override fun onImageLoadFailed(picasso: Picasso, uri: Uri, exception: Exception)
                {
                    Log.d(TAG, exception.message)
                }
            })

            builder.build().

            /*Picasso.with(this).*/
                    load(fileNameWithPrefix).
                    placeholder(R.mipmap.ic_launcher).
                    error(R.mipmap.ic_launcher_round).
                    into(ivResult)

        } else if (mediaFileName.endsWith(CustomCamera.MP4)) {
            val mc = MediaController(this)

            vvResult?. let { vView ->
                vView.setMediaController(mc)
                vView.setVideoPath(fileNameWithPrefix)
                vView.visibility = View.VISIBLE
                vView.setOnPreparedListener { mc.show() }
            }
        } else {
            Toast.makeText(this, R.string.media_format_not_supported, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
