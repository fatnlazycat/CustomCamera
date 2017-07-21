package eu.aejis.mycustomcamera

import android.app.Activity
import java.io.File

/**
 * Created by Dima on 08.07.2017.
 */
interface CameraHost {
    var mPreview: CameraPreview?

    fun setRecordButtonStatus(status: Boolean)

    fun showResult(mediaFileName: String)

    fun asActivity(): Activity {
        if (this is Activity) return this
        else throw Exception("CameraHost should be a subclass of Activity!")
    }

    fun showError(message: String)
}