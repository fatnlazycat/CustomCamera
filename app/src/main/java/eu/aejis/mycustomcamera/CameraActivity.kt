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
import com.squareup.picasso.MemoryPolicy
import com.squareup.picasso.Picasso

open class CameraActivity : AppCompatActivity(), CameraHost {
    val TAG = "CameraActivity"

    private val picasso: Picasso by lazy {
        val builder = Picasso.Builder(this)
        builder.listener(object : Picasso.Listener {
            override fun onImageLoadFailed(picasso: Picasso, uri: Uri, exception: Exception) {
                Log.d(TAG, exception.message)

                val tvError = findViewById(R.id.tvError) as TextView
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

        // Create an instance of Camera
        if (!Utils.checkCameraHardware(this)) {
            Toast.makeText(this, "Camera problem!", Toast.LENGTH_LONG).show()
            finish()
        } else {
            // Create our Preview view and set it as the content of our activity.
            mPreview = CameraPreview(this)

            val layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            layoutParams.gravity = Gravity.CENTER
            mPreview?.layoutParams = layoutParams

            val preview = findViewById(R.id.camera_preview) as FrameLayout
            preview.addView(mPreview)

            chronometer         = findViewById(R.id.chronometer)      as Chronometer
            btnCapture          = findViewById(R.id.button_capture)   as ImageButton
            //switchPhotoVideo    = findViewById(R.id.switchPhotoVideo) as MaterialAnimatedSwitch
            switchPhotoVideo    = findViewById(R.id.switchPhotoVideo) as IconSwitch
            btnOK               = findViewById(R.id.btnOK)            as Button
            btnCancel           = findViewById(R.id.btnCancel)        as Button
            ivResult            = findViewById(R.id.ivResult)         as ImageView
            vvResult            = findViewById(R.id.vvResult)         as VideoView


            val action: String? = if (savedInstanceState != null && savedInstanceState.containsKey(IntentExtras.SWITCH_STATE))
                Utils.mediaStoreActionFromBoolean(savedInstanceState.getBoolean(IntentExtras.SWITCH_STATE))
            else intent?.action

            val mediaPath: String? = intent?.data?.path
            customCamera = CustomCamera(this, action, mediaPath)

            if (noSwitch) {
                switchPhotoVideo?.visibility = View.GONE
            } else {
                switchPhotoVideo?.checked = if (Utils.booleanFromMediaStoreAction(action)) IconSwitch.Checked.LEFT
                else IconSwitch.Checked.RIGHT
            }

            btnCapture?.setOnClickListener(customCamera?.getActionListener())

            btnOK?.setOnClickListener { _: View? ->
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
                val flagVideo = (side == IconSwitch.Checked.RIGHT)
                setCustomCameraAndActionListener(flagVideo)
            })

            if (intent.hasExtra(IntentExtras.START_IMMEDIATELY) &&
                    !(savedInstanceState != null &&
                        savedInstanceState.containsKey(IntentExtras.START_IMMEDIATELY)))
                btnCapture?.postDelayed({ btnCapture?.performClick() }, 600)

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
        isPaused = false
        val valShowingFile = showingFile
        if (valShowingFile == null) onResumeActions()
        else {
            setUIStatus(false)
            showResult(valShowingFile)
        }
    }
    private fun onResumeActions() {
        customCamera?.onResumeActions()
        mPreview?. let { v ->
            if (v.visibility == View.GONE) v.visibility = View.VISIBLE
        }
    }

    override fun onPause() {
        isPaused = true
        /*record-during-incoming-call block -> comment out onPauseActions()*/
        onPauseActions()
        super.onPause()
    }
    private fun onPauseActions() {
        Log.d(TAG, "onPause")
        customCamera?.onPauseActions()
        mPreview?.visibility = View.GONE
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        customCamera = null
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle?) {
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
        Log.d(TAG, "set btnCapture, status=" + status)
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
            Toast.makeText(this, R.string.media_format_not_supported, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
