package com.uyttyu7532.photolapse.ui.camera

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Bitmap.createScaledBitmap
import android.graphics.ImageFormat
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.*
import android.util.DisplayMetrics
import android.util.Log
import android.util.SparseIntArray
import android.view.*
import android.view.View.INVISIBLE
import android.widget.FrameLayout
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.databinding.DataBindingUtil
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.ViewModelProvider
import coil.load
import com.uyttyu7532.photolapse.R
import com.uyttyu7532.photolapse.databinding.ActivityCameraBinding
import com.uyttyu7532.photolapse.model.Photo
import com.uyttyu7532.photolapse.utils.MyApplication
import com.jaygoo.widget.OnRangeChangedListener
import com.jaygoo.widget.RangeSeekBar
import splitties.toast.toast
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*


// Camera2 Document: https://developer.android.com/reference/android/hardware/camera2/package-summary
// Reference: https://webnautes.tistory.com/822
class CameraActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCameraBinding // Build->Rebuild Project 할때 생성된다고 함!(어쩐지)
    private lateinit var viewModel: CameraBackGroundViewModel
    private lateinit var folderName: String

    private lateinit var mContext: Context

    private lateinit var mSurfaceViewHolder: SurfaceHolder
    private lateinit var mImageReader: ImageReader
    private lateinit var mCameraDevice: CameraDevice
    private lateinit var mPreviewBuilder: CaptureRequest.Builder
    private lateinit var mSession: CameraCaptureSession

    private var mHandler: Handler? = null

    private lateinit var mAccelerometer: Sensor
    private lateinit var mMagnetometer: Sensor
    private lateinit var mSensorManager: SensorManager

    private val deviceOrientation: DeviceOrientation by lazy { DeviceOrientation() }
    private var mHeight: Int = 0
    private var mWidth: Int = 0

    private var realHeight: Int = 0
    private var realWidth: Int = 0

    private var capturedBitmap: Bitmap? = null
    private var cachePath: String = ""

    var mCameraId = CAMERA_BACK


    companion object {
        private const val TAG = "CameraActivity"

        const val CAMERA_BACK = "0"
        const val CAMERA_FRONT = "1"

        private val ORIENTATIONS = SparseIntArray()

        init {
            ORIENTATIONS.append(ExifInterface.ORIENTATION_NORMAL, 0)
            ORIENTATIONS.append(ExifInterface.ORIENTATION_ROTATE_90, 90)
            ORIENTATIONS.append(ExifInterface.ORIENTATION_ROTATE_180, 180)
            ORIENTATIONS.append(ExifInterface.ORIENTATION_ROTATE_270, 270)
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mContext = this

        val intent = intent
        folderName = intent.getStringExtra("folderName")
        var backgroundPhoto: Photo? = intent.getParcelableExtra("backgroundPhoto")


        // 상태바 숨기기
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        // 화면 켜짐 유지
        window.setFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )


        binding = DataBindingUtil.setContentView(this, R.layout.activity_camera)

        // 뷰모델 인스턴스를 가져온다.
        viewModel = ViewModelProvider(this).get(CameraBackGroundViewModel::class.java)

        // 원래 this로 액티비티를 연결했지만 뷰모델을 여기서 연결한다!
        binding.viewModel = viewModel
        binding.lifecycleOwner = this

        initSensor()
        initView()

        if (backgroundPhoto == null) {
            binding.alphaBackgroundImageSlider.visibility = INVISIBLE
        } else {
            binding.alphaBackgroundImage.load(File(backgroundPhoto?.absolute_file_path))
        }

//        binding.alphaBackgroundImageSlider.position =
//            MyApplication.prefs.getFloat("backGroundAlpha", 0.5f)

        binding.alphaBackgroundImageSlider.setIndicatorTextDecimalFormat("0")
        binding.alphaBackgroundImageSlider.setProgress(
            MyApplication.prefsAlpha.getFloat("backGroundAlpha", 0.5f) * 100
        )


        // 의문: binding.alphaBackgroundImageSlider vs alphaBackgroundImageSlider 무슨 차이가 있는거지?
        // 해결: findViewById로 별도의 선언 없이 사용 가능하다.
        // 뷰 바인딩의 장점 - 널 안정성 && 타입 안정성

        // 새로운 의문: 코틀린에서는 원래 안해도 됐잖아?
        // 해결: 맞다. 근데 코틀린 안드로이드 익스텐션이 2021년에 종료된다고 한다.
//        binding.alphaBackgroundImageSlider.positionListener = {
////            Log.d(TAG, "onCreate: $it")
//            viewModel.updateValue(it)
//        }

        binding.alphaBackgroundImageSlider.setOnRangeChangedListener(object :
            OnRangeChangedListener {
            override fun onRangeChanged(
                rangeSeekBar: RangeSeekBar,
                leftValue: Float,
                rightValue: Float,
                isFromUser: Boolean
            ) {
                viewModel.updateValue(leftValue / 100)
            }

            override fun onStartTrackingTouch(view: RangeSeekBar?, isLeft: Boolean) {

            }

            override fun onStopTrackingTouch(view: RangeSeekBar?, isLeft: Boolean) {

            }

        })

        Log.d(TAG, "onCreate: ${folderName}")
        Log.d(TAG, "onCreate: ${backgroundPhoto?.absolute_file_path}")
//        Glide.with(this).load(backgroundPhoto?.absolute_file_path)
//            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
//            .dontAnimate().into(
//                binding.alphaBackgroundImage
//            )


        binding.btnHome.setOnClickListener {
            this.onBackPressed()
        }

        binding.cameraBtn.setOnClickListener {
            // 사진 확인 화면 -> 해당 폴더에 저장하기

            getBitMapFromSurfaceView(binding.surfaceView) { bitmap ->

                capturedBitmap = bitmap

                var h = binding.surfaceView.height
                var w = binding.surfaceView.width

                Log.d(TAG, "onCreate: 세로$h 가로$w")

                var thumbnail = createScaledBitmap(bitmap!!, w / 10, h / 10, true)

                cachePath = saveCacheImage(bitmap)

                Log.d(TAG, "onCreate: cachePath $cachePath")

                val intent = Intent(this, CameraResultActivity::class.java)
//                intent.putExtra("folderName", folderName)
                intent.putExtra(
                    "thumbnailBitmap",
                    thumbnail
                )
                intent.putExtra("cachePath", cachePath)
                startActivityForResult(intent, 100)

//                persistImage(
//                    bitmap!!,
//                    folderName!!
//                )


            }
        }
    }

    private fun saveCacheImage(bitmap: Bitmap): String {
        var cachePath = ""
        try {
            //내부저장소 캐시 경로를 받아옵니다.
            var storage: File = cacheDir

            val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss")
            var time = dateFormat.format(Date())

            //저장할 파일 이름
            var fileName = "$time.jpg"

            //storage 에 파일 인스턴스를 생성합니다.
            var tempFile = File(storage, fileName)


            // 자동으로 빈 파일을 생성합니다.
            tempFile.createNewFile()

            // 파일을 쓸 수 있는 스트림을 준비합니다.
            var out = FileOutputStream(tempFile)

            // compress 함수를 사용해 스트림에 비트맵을 저장합니다.
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)

            // 스트림 사용후 닫아줍니다.
            out.close()
            cachePath = tempFile.absolutePath
            Log.d(TAG, "saveCacheImage: tempFilePath $cachePath")
        } catch (e: java.lang.Exception) {
            Log.d(TAG, "saveCacheImage: $e")
        }
        return cachePath
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 100) {
            when (resultCode) {
                RESULT_OK -> {

                    persistImage(
                        capturedBitmap!!,
                        folderName!!
                    )

                    val cacheFile = File(cachePath)

                    if (cacheFile.exists()) {
                        cacheFile.deleteOnExit()
                    }

                    finish()
                }
                RESULT_CANCELED -> {

                    val cacheFile = File(cachePath)

                    if (cacheFile.exists()) {
                        cacheFile.deleteOnExit()
                    }
                }
            }

        }
    }

    /**
     * Pixel copy to copy SurfaceView/VideoView into BitMap
     * Work with Surface View, Video View
     * Won't work on Normal View
     */
    private fun getBitMapFromSurfaceView(videoView: SurfaceView, callback: (Bitmap?) -> Unit) {
        val bitmap: Bitmap = Bitmap.createBitmap(
            videoView.width,
            videoView.height,
            Bitmap.Config.ARGB_8888
        );
        try {
            // Create a handler thread to offload the processing of the image.
            val handlerThread = HandlerThread("PixelCopier");
            handlerThread.start();
            PixelCopy.request(
                videoView, bitmap,
                PixelCopy.OnPixelCopyFinishedListener { copyResult ->
                    Log.d(TAG, "getBitMapFromSurfaceView: $copyResult")
                    if (copyResult == PixelCopy.SUCCESS) {
                        callback(bitmap)
                    }
                    handlerThread.quitSafely();
                },
                Handler(handlerThread.looper)
            )
        } catch (e: IllegalArgumentException) {
            callback(null)
            // PixelCopy may throw IllegalArgumentException, make sure to handle it
            e.printStackTrace()
        }
    }


    private fun persistImage(bitmap: Bitmap, folderName: String) {
        try {
//            val dir = File(getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString()+"/CertificateMarker")
//            val dir = File(
//                Environment.getExternalStoragePublicDirectory(
//                    Environment.DIRECTORY_DCIM
//                ).toString() + "/PhotoChangeRecord"
//            )

            val dir = File(
                getExternalFilesDir(
                    Environment.DIRECTORY_DCIM
                ).toString() + "/$folderName"
            )

            if (!dir!!.exists()) {
                dir.mkdirs()
            }
            val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss")
            var time = dateFormat.format(Date())
            val newFile = File(dir, "${time}.jpg")

//            val out = openFileOutput(newFile.toString(), MODE_APPEND)
            val out = FileOutputStream(newFile)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
            out.flush()
            out.close()

            Log.d(TAG, "persistImage: Success")
            Log.d(TAG, "persistImage: ${newFile.path}")

        } catch (e: Exception) {
            Log.d(TAG, "persistImage: Fail")
            Log.d(TAG, "persistImage: $e")
        }
    }

    private fun initSensor() {
        mSensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        mMagnetometer = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    }

    private fun initView() {
        with(DisplayMetrics()) {
            windowManager.defaultDisplay.getMetrics(this)
            mHeight = heightPixels
            mWidth = widthPixels
        }

        mSurfaceViewHolder = binding.surfaceView.holder
        mSurfaceViewHolder.addCallback(object : SurfaceHolder.Callback {

            override fun surfaceCreated(holder: SurfaceHolder) {
                initCameraAndPreview()

//                alpha_camera_image.invalidate()
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                mCameraDevice.close()
            }

            override fun surfaceChanged(
                holder: SurfaceHolder, format: Int,
                width: Int, height: Int
            ) {

            }
        })

        binding.btnConvert.setOnClickListener { switchCamera() }
    }

    private fun switchCamera() {
        when (mCameraId) {
            CAMERA_BACK -> {
                mCameraId = CAMERA_FRONT
                mCameraDevice.close()
                openCamera()
            }
            else -> {
                mCameraId = CAMERA_BACK
                mCameraDevice.close()
                openCamera()
            }
        }
    }


    fun initCameraAndPreview() {
        val handlerThread = HandlerThread("CAMERA2")
        handlerThread.start()
        mHandler = Handler(handlerThread.looper)

        openCamera()
    }

    private fun openCamera() {
        try {
            val mCameraManager = this.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val characteristics = mCameraManager.getCameraCharacteristics(mCameraId)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)

            val largestPreviewSize = map!!.getOutputSizes(ImageFormat.JPEG)[0]
            setAspectRatioTextureView(largestPreviewSize.height, largestPreviewSize.width)

            mImageReader = ImageReader.newInstance(
                largestPreviewSize.width,
                largestPreviewSize.height,
                ImageFormat.JPEG,
                7
            )
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED
            ) return

            mCameraManager.openCamera(mCameraId, deviceStateCallback, mHandler)
        } catch (e: CameraAccessException) {
            toast("카메라를 열지 못했습니다.")
        }
    }

    private val deviceStateCallback = object : CameraDevice.StateCallback() {
        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        override fun onOpened(camera: CameraDevice) {
            mCameraDevice = camera
            try {
                takePreview()
            } catch (e: CameraAccessException) {
                e.printStackTrace()
            }
        }

        override fun onDisconnected(camera: CameraDevice) {
            mCameraDevice.close()
        }

        override fun onError(camera: CameraDevice, error: Int) {
            toast("카메라를 열지 못했습니다.")
        }
    }

    @Throws(CameraAccessException::class)
    fun takePreview() {
        mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        mPreviewBuilder.addTarget(mSurfaceViewHolder.surface)
        mCameraDevice.createCaptureSession(
            listOf(mSurfaceViewHolder.surface, mImageReader.surface),
            mSessionPreviewStateCallback,
            mHandler
        )
    }

    private val mSessionPreviewStateCallback = object : CameraCaptureSession.StateCallback() {
        override fun onConfigured(session: CameraCaptureSession) {
            mSession = session
            try {
                // Key-Value 구조로 설정
                // 오토포커싱이 계속 동작
                mPreviewBuilder.set(
                    CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                )
                //필요할 경우 플래시가 자동으로 켜짐
                mPreviewBuilder.set(
                    CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH
                )
                mSession.setRepeatingRequest(mPreviewBuilder.build(), null, mHandler)
            } catch (e: CameraAccessException) {
                e.printStackTrace()
            }

        }

        override fun onConfigureFailed(session: CameraCaptureSession) {
            toast("카메라 구성 실패")
        }
    }

    override fun onResume() {
        super.onResume()

        mSensorManager.registerListener(
            deviceOrientation.eventListener, mAccelerometer, SensorManager.SENSOR_DELAY_UI
        )
        mSensorManager.registerListener(
            deviceOrientation.eventListener, mMagnetometer, SensorManager.SENSOR_DELAY_UI
        )
    }

    override fun onPause() {
        super.onPause()
        mSensorManager.unregisterListener(deviceOrientation.eventListener)
    }

    private fun setAspectRatioTextureView(ResolutionWidth: Int, ResolutionHeight: Int) {
        if (ResolutionWidth > ResolutionHeight) {
            val newWidth = mWidth
            val newHeight = mWidth * ResolutionWidth / ResolutionHeight

            realWidth = newWidth
            realHeight = newHeight

            updateTextureViewSize(newWidth, newHeight)

        } else {
            val newWidth = mWidth
            val newHeight = mWidth * ResolutionHeight / ResolutionWidth

            realWidth = newWidth
            realHeight = newHeight

            updateTextureViewSize(newWidth, newHeight)
        }

        val layoutParams: ViewGroup.LayoutParams = binding.alphaBackgroundImage.layoutParams
        layoutParams.width = realWidth
        layoutParams.height = realHeight
        Log.d(TAG, "setAspectRatioTextureView: $realHeight $realWidth\"")
        binding.alphaBackgroundImage.layoutParams = layoutParams

    }

    private fun updateTextureViewSize(viewWidth: Int, viewHeight: Int) {
        Log.d("ViewSize", "TextureView Width : $viewWidth TextureView Height : $viewHeight")
        binding.surfaceView.layoutParams = FrameLayout.LayoutParams(viewWidth, viewHeight)
    }
}