package eu.aejis.mycustomcamera

import android.animation.Animator
import android.app.Activity
import android.content.Intent
import android.graphics.PorterDuff
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
//import com.github.glomadrian.materialanimatedswitch.MaterialAnimatedSwitch
import com.polyak.iconswitch.IconSwitch
import com.splunk.mint.Mint
import com.squareup.picasso.MemoryPolicy
import com.squareup.picasso.Picasso
import org.jetbrains.anko.longToast
import org.jetbrains.anko.toast

open class CameraActivity : AppCompatActivity(), CameraHost {
    val TAG = "CameraActivity"

    private val picasso: Picasso by lazy {
        val builder = Picasso.Builder(this)
        builder.listener(object : Picasso.Listener {
            override fun onImageLoadFailed(picasso: Picasso, uri: Uri, exception: Exception) {
                Log.d(TAG, exception.message)
                Mint.leaveBreadcrumb(exception.toString())

                val tvError = findViewById<TextView>(R.id.tvError)
                tvError.visibility = View.VISIBLE
                tvError.text = exception.toString()
            }
        })
        builder.build()
    }

    override var mPreview: CameraPreview? = null

    private var customCamera: CustomCamera? = null

    private var chronometer: Chronometer? = null
    private var btnCapture: ImageButton? = null
    //var switchPhotoVideo: MaterialAnimatedSwitch? = null
    private var switchPhotoVideo: IconSwitch? = null
    private var btnOK: Button? = null
    private var btnCancel: Button? = null
    private var ivResult: ImageView? = null
    private var vvResult: VideoView? = null

    private var showingFile: String? = null
    private var isPaused = true
    private val noSwitch by lazy { intent.hasExtra(IntentExtras.NO_SWITCH) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        Mint.leaveBreadcrumb("CameraActivity onCreate")

        // Create an instance of Camera
        if (!Utils.checkCamera(this)) {
            longToast(R.string.cameraError)
            finish()
        } else {
            // Create our Preview view and set it as the content of our activity.
            mPreview = CameraPreview(this)

            val layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            layoutParams.gravity = Gravity.CENTER
            mPreview?.layoutParams = layoutParams

            val preview = findViewById<FrameLayout>(R.id.camera_preview)
            preview.addView(mPreview)

            chronometer         = findViewById(R.id.chronometer)
            btnCapture          = findViewById(R.id.button_capture)
            //switchPhotoVideo    = findViewById(R.id.switchPhotoVideo) as MaterialAnimatedSwitch
            switchPhotoVideo    = findViewById(R.id.switchPhotoVideo)
            btnOK               = findViewById(R.id.btnOK)
            btnCancel           = findViewById(R.id.btnCancel)
            ivResult            = findViewById(R.id.ivResult)
            vvResult            = findViewById(R.id.vvResult)


            val action: String? = if (savedInstanceState != null && savedInstanceState.containsKey(IntentExtras.SWITCH_STATE))
                Utils.mediaStoreActionFromBoolean(savedInstanceState.getBoolean(IntentExtras.SWITCH_STATE))
            else intent?.action

            val mediaPath: String? = intent?.data?.path

            try {
                customCamera = CustomCamera(this, action, mediaPath)
            } catch (e: Exception) {
                closeAfterException(e)
            }

            if (noSwitch) {
                switchPhotoVideo?.visibility = View.GONE
            } else {
                switchPhotoVideo?.checked = if (Utils.booleanFromMediaStoreAction(action)) IconSwitch.Checked.LEFT
                else IconSwitch.Checked.RIGHT
            }

            btnCapture?.setOnClickListener(customCamera?.getActionListener())

            btnOK?.setOnClickListener { _: View? ->
                Mint.leaveBreadcrumb("btnOk clicked")
                btnCancel?.isEnabled = false
                btnOK?.isEnabled = false
                val resultIntent = Intent()
                //val mediaFileName = customCamera?.lastCapturedFile?.toString()
                resultIntent.putExtra(IntentExtras.MEDIA_FILE, showingFile)
                setResult(Activity.RESULT_OK, resultIntent)
                finish()
            }

            fun resetUI() {
                Log.d(TAG, "reset UI")
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
                Mint.leaveBreadcrumb("btnCancel clicked")
                btnCancel?.isEnabled = false
                btnOK?.isEnabled = false
                resetUI()
                onResumeActions()
                showingFile = null
                btnCancel?.text = getString(R.string.No)
            }

            /*//MaterialAnimatedSwitch implementation
            if (!noSwitch) switchPhotoVideo?.setOnCheckedChangeListener({
                flagVideo *//*true == pressed == video*//* ->
                customCamera?.initMode(modeVideo = flagVideo)
                btnCapture?.setOnClickListener(
                        customCamera?.getActionListener(actionFlagImage = !flagVideo)
                )
            })*/

            if (!noSwitch) switchPhotoVideo?.setCheckedChangeListener({
                side ->
                Mint.leaveBreadcrumb("switch clicked to $side")
                val flagVideo = (side == IconSwitch.Checked.RIGHT)
                setCustomCameraAndActionListener(flagVideo)
            })

            if (intent.hasExtra(IntentExtras.START_IMMEDIATELY) &&
                    !(savedInstanceState != null &&
                        savedInstanceState.containsKey(IntentExtras.START_IMMEDIATELY)))
                btnCapture?.postDelayed({
                    try { btnCapture?.performClick() } //IllegalStateException in MediaRecorder.start
                    catch (e: Exception) {closeAfterException(e)}
                }, 600)

            showingFile = savedInstanceState?.getString(IntentExtras.SHOWING_FILE)
        }
    }

    private fun setCustomCameraAndActionListener(flagVideo: Boolean) {
        customCamera?.initMode(modeVideo = flagVideo)
        btnCapture?.setOnClickListener(
                customCamera?.getActionListener(actionFlagImage = !flagVideo)
        )
    }

    override fun onResume() {
        super.onResume()

        Mint.leaveBreadcrumb("CameraActivity onResume")

        isPaused = false
        val valShowingFile = showingFile
        if (valShowingFile == null) onResumeActions()
        else {
            setUIStatus(false)
            showResult(valShowingFile)
        }
    }
    private fun onResumeActions() {
        try {
            //this can throw NPE
            customCamera?.onResumeActions()

            mPreview?. let { v ->
                if (v.visibility == View.GONE) v.visibility = View.VISIBLE
            }
        } catch (e: Exception) {
            closeAfterException(e)
        }
    }

    override fun onPause() {
        Mint.leaveBreadcrumb("CameraActivity onPause")
        Log.d(TAG, "onPause")
        isPaused = true
        /*record-during-incoming-call block -> comment out onPauseActions()*/
        onPauseActions()
        super.onPause()
    }
    private fun onPauseActions() {
        Mint.leaveBreadcrumb("CameraActivity onPauseActions")
        customCamera?.onPauseActions()
        mPreview?.visibility = View.GONE
    }

    override fun onDestroy() {
        Mint.leaveBreadcrumb("CameraActivity onDestroy")

        Log.d(TAG, "onDestroy")
        customCamera = null
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        Mint.leaveBreadcrumb("CameraActivity onSaveInstanceState")

        showingFile ?. let {outState?.putString(IntentExtras.SHOWING_FILE, showingFile)}
        switchPhotoVideo ?. let {
            if (!noSwitch)
                outState?.putBoolean(IntentExtras.SWITCH_STATE, it.checked == IconSwitch.Checked.LEFT)
        }
        if (intent.hasExtra(IntentExtras.START_IMMEDIATELY)) outState?.putBoolean(IntentExtras.START_IMMEDIATELY, false)
        super.onSaveInstanceState(outState)
    }

    override fun setUIStatus(status: Boolean) {
        switchPhotoVideo?.   visibility = View.GONE
        Log.d(TAG, "set btnCapture, status=$status")
        if (status) { //recording - show "Stop"
            btnCapture?. let { with(it) {
                animate().rotationBy(360F)
                setColorFilter(
                        ContextCompat.getColor(this@CameraActivity, android.R.color.holo_red_light),
                        PorterDuff.Mode.MULTIPLY
                )
            }}
            chronometer?. let { with(it) {
                visibility = View.VISIBLE
                base = SystemClock.elapsedRealtime()
                start()
            }}
        } else { //stopped - show "Start"
            btnCapture?. let { with(it) {
                animate().rotationBy(-360F).setListener(object : Animator.AnimatorListener {
                    fun setInvisible() {
                        //can't be GONE since it's used to place btnCancel & btnOk in RelativeLayout
                        if (mPreview?.visibility == View.GONE) visibility = View.INVISIBLE
                        animate().setListener(null)
                        Log.d(TAG, "btnCapture animation ended, setInvisible")
                    }
                    override fun onAnimationEnd     (animation: Animator?) { setInvisible() }
                    override fun onAnimationCancel  (animation: Animator?) { setInvisible() }
                    override fun onAnimationStart   (animation: Animator?) {                }
                    override fun onAnimationRepeat  (animation: Animator?) {                }
                })
            }}
            btnOK?.         visibility = View.VISIBLE
            btnOK?.         isEnabled = true
            btnCancel?.     visibility = View.VISIBLE
            btnCancel?.     isEnabled = true
            chronometer?.   let { with(it) {
                visibility = View.GONE
                stop()
            }}
        }
    }

    override fun onRotation(newRotation: Int) {
        btnCapture?.rotation = newRotation.toFloat()
    }

    override fun setRecordButtonEnabled(status: Boolean) {
        btnCapture?.isEnabled = status
    }

    override fun showResult(mediaFileName: String) {
        showingFile = mediaFileName

        if (isPaused) return //don't need any UI actions then

        onPauseActions()

        val FILE_PREFIX = "file://"
        val fileNameWithPrefix = if (mediaFileName.startsWith(FILE_PREFIX)) mediaFileName
            else FILE_PREFIX + mediaFileName

        if (mediaFileName.endsWith(CustomCamera.JPG)) {
            ivResult ?. let { imageView ->
                imageView.visibility = View.VISIBLE
                val screenSize = Utils.getScreenSizes(this)

                picasso.load(fileNameWithPrefix)
                        /*.placeholder(R.mipmap.ic_launcher)
                        .error(R.mipmap.ic_launcher_round)*/
                        .memoryPolicy(MemoryPolicy.NO_CACHE)
                        .resize(screenSize.x, screenSize.y)
                        .centerInside()
                        .into(ivResult)
            }

        } else if (mediaFileName.endsWith(CustomCamera.MP4)) {
            val mc = MediaController(this)

            vvResult?. let { vView ->
                vView.setMediaController(mc)
                vView.setVideoPath(fileNameWithPrefix)
                vView.visibility = View.VISIBLE
                vView.setOnPreparedListener { mc.show() }
                vView.setOnErrorListener ({mp: MediaPlayer, what: Int, extra: Int ->
                    Log.d(TAG, "videoView onErrorListener entered")
                    btnOK?.visibility = View.GONE
                    btnCancel?.text = getString(R.string.OK)
                    false
                })
            }
        } else {
            toast(R.string.media_format_not_supported)
            finish()
        }
    }

    override fun showError(message: String) {
        toast(message)
    }

    private fun closeAfterException(e: Exception) {
        Mint.logException("CameraParameters", customCamera?.cameraParameters.toString(), e)
        customCamera?.releaseCamera()
        toast(R.string.cameraError)
        finish()
    }
}
