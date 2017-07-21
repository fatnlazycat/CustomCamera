package eu.aejis.mycustomcamera

import android.content.ContentValues.TAG
import android.content.Context
import android.content.Context.WINDOW_SERVICE
import android.hardware.Camera
import android.hardware.Camera.Size
import android.util.Log
import android.view.*
import java.io.IOException
import android.view.Surface.ROTATION_270
import android.view.Surface.ROTATION_180
import android.view.Surface.ROTATION_90
import android.view.Surface.ROTATION_0
import android.support.v4.view.ViewCompat.getRotation
import android.app.Activity




/** A basic Camera preview class  */
class CameraPreview(context: Context) : SurfaceView(context) {
    var mSupportedPreviewSizes: List<Camera.Size>? = null
    var bestFitPreviewSize: Camera.Size? = null
    private val mHolder: SurfaceHolder = holder

    fun init(c: SurfaceHolder.Callback, supportedPreviewSizes: List<Camera.Size>?) {
        // Install a SurfaceHolder.Callback so we get notified when the
        // underlying surface is created and destroyed.
        mHolder.addCallback(c)
        // deprecated setting, but required on Android versions prior to 3.0
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS)

        mSupportedPreviewSizes = supportedPreviewSizes
    }

    /*override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = View.resolveSize(suggestedMinimumWidth, widthMeasureSpec)
        val height = View.resolveSize(suggestedMinimumHeight, heightMeasureSpec)

        if (mSupportedPreviewSizes != null) {
            bestFitPreviewSize = Utils.getOptimalCameraSize(mSupportedPreviewSizes, width, height)
        }

        bestFitPreviewSize?.let { previewSize ->
            *//*val ratio: Float
            if (previewSize.height >= previewSize.width)
                ratio = previewSize.height.toFloat() / previewSize.width.toFloat()
            else
                ratio = previewSize.width.toFloat() / previewSize.height.toFloat()

            // One of these methods should be used, second method squishes preview slightly
            setMeasuredDimension(width, (width * ratio).toInt())
            //        setMeasuredDimension((int) (width * ratio), height);*//*
            setMeasuredDimension(previewSize.width, previewSize.height)
        }
    }*/
}