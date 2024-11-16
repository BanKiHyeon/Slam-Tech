package com.tonecolor.slamandroid.filament.test

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.core.content.ContextCompat

import android.Manifest
import android.graphics.ImageFormat
import android.hardware.HardwareBuffer
import android.media.ImageReader
import android.opengl.Matrix
import android.os.Build
import android.os.Looper
import androidx.annotation.RequiresApi

import com.google.android.filament.*

import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit


class CameraHelper(val activity: Activity, private val filamentEngine: Engine, private val filamentMaterial: MaterialInstance) {

    companion object {
        private const val tag = "CameraHelper"
        private const val kRequestCameraPermission = 1
        private const val kImageReaderMaxImages = 7
    }

    private lateinit var cameraId : String
    private var resolution = Size(640, 480)

    private val cameraOpenCloseLock = Semaphore(1)

    private var cameraDevice: CameraDevice? = null

    private var backgroundHandler: Handler? = null
    private var backgroundThread: HandlerThread? = null

    private var filamentTexture: Texture? = null
    private var filamentStream: Stream? = null

    private var captureSession: CameraCaptureSession? = null
    private lateinit var captureRequest: CaptureRequest

    @RequiresApi(Build.VERSION_CODES.Q)
    private val imageReader = ImageReader.newInstance(
        resolution.width,
        resolution.height,
        ImageFormat.PRIVATE,
        kImageReaderMaxImages,
        HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE)

    @Suppress("deprecation")
    private val display = if (Build.VERSION.SDK_INT >= 30) {
        Api30Impl.getDisplay(activity)
    } else {
        activity.windowManager.defaultDisplay!!
    }

    @RequiresApi(30)
    class Api30Impl {
        companion object {
            fun getDisplay(context: Context) = context.display!!
        }
    }

    private val cameraCallback = object : CameraDevice.StateCallback() {
        @RequiresApi(Build.VERSION_CODES.Q)
        override fun onOpened(cameraDevice: CameraDevice) {
            cameraOpenCloseLock.release()
            this@CameraHelper.cameraDevice = cameraDevice
            createCaptureSession()
        }
        override fun onDisconnected(cameraDevice: CameraDevice) {
            cameraOpenCloseLock.release()
            cameraDevice.close()
            this@CameraHelper.cameraDevice = null
        }
        override fun onError(cameraDevice: CameraDevice, error: Int) {
            onDisconnected(cameraDevice)
            this@CameraHelper.activity.finish()
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun createCaptureSession() {
        filamentStream?.apply { filamentEngine.destroyStream(this) }
        filamentStream = Stream.Builder().build(filamentEngine)

        if (filamentTexture == null) {
            filamentTexture = Texture.Builder()
                .sampler(Texture.Sampler.SAMPLER_EXTERNAL)
                .format(Texture.InternalFormat.RGB8)
                .build(filamentEngine)
        }

        val aspectRatio = resolution.width.toFloat() / resolution.height.toFloat()
        val textureTransform = FloatArray(16)
        Matrix.setIdentityM(textureTransform, 0)

        when (display.rotation) {
            Surface.ROTATION_0 -> {
                Matrix.translateM(textureTransform, 0, 1.0f, 0.0f, 0.0f)
                Matrix.rotateM(textureTransform, 0, 90.0f, 0.0f, 0.0f, 1.0f)
                Matrix.translateM(textureTransform, 0, 1.0f, 0.0f, 0.0f)
                Matrix.scaleM(textureTransform, 0, -1.0f, 1.0f / aspectRatio, 1.0f)
            }
            Surface.ROTATION_90 -> {
                Matrix.translateM(textureTransform, 0, 1.0f, 1.0f, 0.0f)
                Matrix.rotateM(textureTransform, 0, 180.0f, 0.0f, 0.0f, 1.0f)
                Matrix.translateM(textureTransform, 0, 1.0f, 0.0f, 0.0f)
                Matrix.scaleM(textureTransform, 0, -1.0f / aspectRatio, 1.0f, 1.0f)
            }
            Surface.ROTATION_270 -> {
                Matrix.translateM(textureTransform, 0, 1.0f, 0.0f, 0.0f)
                Matrix.scaleM(textureTransform, 0, -1.0f / aspectRatio, 1.0f, 1.0f)
            }
        }


        val sampler = TextureSampler(TextureSampler.MinFilter.LINEAR, TextureSampler.MagFilter.LINEAR, TextureSampler.WrapMode.CLAMP_TO_EDGE)
        filamentTexture!!.setExternalStream(filamentEngine, filamentStream!!)
        filamentMaterial.setParameter("videoTexture", filamentTexture!!, sampler)
        filamentMaterial.setParameter("textureTransform", MaterialInstance.FloatElement.MAT4, textureTransform, 0, 1)

        val captureRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
        captureRequestBuilder.addTarget(imageReader.surface)

        cameraDevice?.createCaptureSession(listOf(imageReader.surface),
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
                    if (cameraDevice == null) return
                    captureSession = cameraCaptureSession
                    captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                    captureRequest = captureRequestBuilder.build()
                    captureSession!!.setRepeatingRequest(captureRequest, null, backgroundHandler)
                    Log.i(tag, "Created CaptureRequest.")
                }
                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.e(tag, "onConfigureFailed")
                }
            }, null)
    }

    fun openCamera(cameraId : String = "0") {
        val manager = activity.getSystemService(Context.CAMERA_SERVICE) as CameraManager

        //TODO You must implement logic to check whether the camera available on the device is a valid camera.
        /*for (id in manager.cameraIdList) {
            val characteristics = manager.getCameraCharacteristics(cameraId)
            val cameraDirection = characteristics.get(CameraCharacteristics.LENS_FACING)
            if (cameraDirection != null && cameraDirection == CameraCharacteristics.LENS_FACING_FRONT) {
                continue
            }

            this.cameraId = id
            Log.i(tag, "Selected camera $cameraId.")

            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: continue
            resolution = map.getOutputSizes(SurfaceTexture::class.java)[0]
            Log.i(tag, "Highest resolution is $resolution.")
        }*/

        this.cameraId = cameraId
        val characteristics = manager.getCameraCharacteristics(cameraId)
        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        resolution = map!!.getOutputSizes(SurfaceTexture::class.java)[0]

        val permission = ContextCompat.checkSelfPermission(this.activity, Manifest.permission.CAMERA)
        if (permission != PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(arrayOf(Manifest.permission.CAMERA), kRequestCameraPermission)
            return
        }
        if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
            throw RuntimeException("Time out waiting to lock camera opening.")
        }

        manager.openCamera(cameraId, cameraCallback, backgroundHandler)
    }

    fun onResume() {
        backgroundThread = HandlerThread("CameraBackground").also { it.start() }
        backgroundHandler = Handler(backgroundThread?.looper!!)
    }

    fun onPause() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            Log.e(tag, e.toString())
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    fun pushExternalImageToFilament() {
        val stream = filamentStream
        if (stream != null) {
            imageReader.acquireLatestImage()?.also {
                stream.setAcquiredImage(it.hardwareBuffer, Handler(Looper.getMainLooper())) {
                    it.close()
                }
            }
        }
    }

    fun onRequestPermissionsResult(requestCode: Int, grantResults: IntArray): Boolean {
        if (requestCode == kRequestCameraPermission) {
            if (grantResults.size != 1 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Log.e(tag, "Unable to obtain camera position.")
            }
            return true
        }
        return false
    }
}