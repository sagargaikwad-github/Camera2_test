package com.eits.camera2_test;

import static android.app.PendingIntent.getActivity;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import android.content.RestrictionEntry;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    ImageView backBTN,flashBTN;
    ImageView mRecordImageButton;
    //ImageButton mStillImageButton;
    Boolean mIsRecording=false;
    File mVideoFolder;
    String mVideoFileName;
    ArrayList<String>commandListStartRecord=new ArrayList<>();
    ArrayList<String>commandListStopRecord=new ArrayList<>();
    Switch switchMic;
    public boolean isListening=false;
    CountDownTimer countDownTimer;
    File mImageFolder;
    String mImageFileName;
    SpeechRecognizer mSpeechRecognizer;


    SharedPreferences sharedPreferences;


    private static final int REQUEST_CAMERA_PERMISSION_RESULT=100;
    private static final int REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION_RESULT=200;
    private static final int REQUEST_MIC_PERMISSION_RESULT=300;

    private static final int STATE_PREVIEW=0;
    private static final int STATE_WAIT_LOCK=1;
    private int mCaptureState=STATE_PREVIEW;


    TextView Text;
    TextureView mTextureView;
    TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surfaceTexture, int i, int i1) {
            setUpCamera(i, i1);
            connectCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surfaceTexture, int i, int i1) {
            configureTransform(i,i1);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surfaceTexture) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surfaceTexture) {

        }
    };

    CameraDevice mCameraDevice;
    CameraDevice.StateCallback mCameraDeviceStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            mCameraDevice = camera;

            if (mIsRecording)
            {
                try {
                    createVideoFileName();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                // Toast.makeText(MainActivity.this, String.valueOf(flashStatus), Toast.LENGTH_SHORT).show();
                startRecord();
                mMediaRecorder.start();

            }
            startPreview();

        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            Toast.makeText(MainActivity.this, "Disconnected", Toast.LENGTH_SHORT).show();
            camera.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            camera.close();
            mCameraDevice = null;
        }
    };

    private HandlerThread mBackgroundHandlerThread;
    private Handler mBackgroundHandler;

    String mCameraId;
    Size mPreviewSize;
    Size mVideoSize;

    Size mImageSize;
    private ImageReader mImageReader;
    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener= new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader imageReader) {
            mBackgroundHandler.post(new ImageSaver(imageReader.acquireLatestImage()));
        }
    };

    private class ImageSaver implements Runnable{
        private final Image mImage;
        public ImageSaver(Image image)
        {
            mImage=image;
        }
        @Override
        public void run() {
            ByteBuffer byteBuffer=mImage.getPlanes()[0].getBuffer();
            byte[] bytes=new byte[byteBuffer.remaining()];
            byteBuffer.get(bytes);

            FileOutputStream fileOutputStream=null;
            try {
                fileOutputStream=new FileOutputStream(mImageFileName);
                fileOutputStream.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            }finally {
                mImage.close();
                if(fileOutputStream!=null)
                {
                    try {
                        fileOutputStream.close();
                    }catch (Exception e)
                    {
                        e.printStackTrace();
                    }
                }
            }

        }
    }

    MediaRecorder mMediaRecorder;
    int mTotalRotation;

    CameraCaptureSession mPreviewCaptureSession;
    CameraCaptureSession.CaptureCallback mPreviewCaptureCallback=new CameraCaptureSession.CaptureCallback() {
        private void process(CaptureResult captureResult)
        {
            switch (mCaptureState)
            {
                case STATE_PREVIEW:
                    break;
                case STATE_WAIT_LOCK:
                    mCaptureState=STATE_PREVIEW;
                    Integer afState=captureResult.get(CaptureResult.CONTROL_AF_STATE);
                    if(afState==CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED ||
                            afState==CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED)
                    {

                        startStillCaptureRequest();
                    }

                    break;
            }
        }
        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);

            process(result);
        }
    };

    CameraCaptureSession mRecordCaptureSession;
    CameraCaptureSession.CaptureCallback mRecordCaptureCallback=new CameraCaptureSession.CaptureCallback() {
        private void process(CaptureResult captureResult)
        {
            switch (mCaptureState)
            {
                case STATE_PREVIEW:
                    break;
                case STATE_WAIT_LOCK:
                    mCaptureState=STATE_PREVIEW;
                    Integer afState=captureResult.get(CaptureResult.CONTROL_AF_STATE);
                    if(afState==CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED ||
                            afState==CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED)
                    {
                        Toast.makeText(MainActivity.this, "AF Locked", Toast.LENGTH_SHORT).show();
                        startStillCaptureRequest();
                    }

                    break;
            }
        }
        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);

            process(result);
        }
    };

    private CaptureRequest.Builder mCaptureRequestBuilder;
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
//        ORIENTATIONS.append(Surface.ROTATION_0, 0);
//        ORIENTATIONS.append(Surface.ROTATION_90, 90);
//        ORIENTATIONS.append(Surface.ROTATION_180, 180);
//        ORIENTATIONS.append(Surface.ROTATION_270, 270);

        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    private static class CompareSizeByArea implements Comparator<Size> {
        @Override
        public int compare(Size size, Size t1) {
            return Long.signum((long) size.getWidth() * size.getHeight() /
                    (long) t1.getWidth() * t1.getHeight());
        }
    }

    boolean flashStatus=false;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);


        mTextureView = findViewById(R.id.textureView);
        switchMic=findViewById(R.id.switch1);
        Text=findViewById(R.id.Text);

        mRecordImageButton=findViewById(R.id.recordBTN);
        //mStillImageButton=findViewById(R.id.cameraButton);
        backBTN=findViewById(R.id.backBTN);
        flashBTN=findViewById(R.id.flashBTN);

        commandListStartRecord.add("start recording");
        commandListStartRecord.add("start");

        commandListStopRecord.add("stop");
        commandListStopRecord.add("stop recording");

        createVideoFolder();
        createImageFolder();

        mMediaRecorder=new MediaRecorder();

        //createSpeechRecognizer();


        mRecordImageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(mIsRecording)
                {
                    mIsRecording=false;
                    mRecordImageButton.setImageResource(R.drawable.record_paused);


                    //toggleFlashModeRecord(flashStatus);

                    mMediaRecorder.stop();
                    mMediaRecorder.reset();
                    startPreview();
                }else
                {
                    checkWriteStoragePermission();
                }
            }
        });

//        mStillImageButton.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                lockfocus();
//            }
//        });

        backBTN.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onBackPressed();
            }
        });


        flashBTN.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view)
            {
                if(flashStatus)
                {
                    flashStatus=false;
                    flashBTN.setImageResource(R.drawable.ic_flash_off);
                }else
                {
                    flashStatus=true;
                    flashBTN.setImageResource(R.drawable.ic_flash_on);
                }

                if(mIsRecording)
                {
                    try {
                        toggleFlashModeRecord(flashStatus);
                    }catch (Exception e)
                    {
                        Toast.makeText(MainActivity.this, e.toString(), Toast.LENGTH_SHORT).show();
                    }
                }else
                {
                    toggleFlashMode(flashStatus);
                }

            }
        });
    }

//    private void SpeechRecognizationFunction() {
//        if(isListening)
//        {
//            handleSpeechEnd();
//        }
//        else {
//            handleSpeechBegin();
//        }
//    }

    public Intent createIntent()
    {
        Intent intent= new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"en-IN");
        return intent;


    }
    public void createSpeechRecognizer()
    {
        mSpeechRecognizer= SpeechRecognizer.createSpeechRecognizer(this);

        mSpeechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle bundle) {

            }

            @Override
            public void onBeginningOfSpeech() {
                isListening = true;
            }

            @Override
            public void onRmsChanged(float v) {

            }

            @Override
            public void onBufferReceived(byte[] bytes) {

            }

            @Override
            public void onEndOfSpeech() {
               isListening=false;
               mSpeechRecognizer.startListening(createIntent());
            }

            @Override
            public void onError(int i) {
                Log.e("TAG","onError");

                String message;
                switch (i) {
                    case SpeechRecognizer.ERROR_AUDIO:
                        message = "Audio recording error";
                        break;
                    case SpeechRecognizer.ERROR_CLIENT:
                        message = "Client side error";
                        isListening=false;
                        createSpeechRecognizer();
                        break;
                    case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                        message = "Insufficient permissions";
                        break;
                    case SpeechRecognizer.ERROR_NETWORK:
                        message = "Network error";
                        break;
                    case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                        message = "Network timeout";
                        break;
                    case SpeechRecognizer.ERROR_NO_MATCH:
                        message = "No match";
                        break;
                    case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                        message = "RecognitionService busy";
                        //isListening=false;
                        break;
                    case SpeechRecognizer.ERROR_SERVER:
                        message = "error from server";
                        break;
                    case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                        message = "No speech input";
                        break;
                    default:
                        message = "Didn't understand, please try again.";
                        break;
                }
                Log.e("ErrorPrint",message);

                if(!isListening)
                {
                    mSpeechRecognizer.startListening(createIntent());
                }
            }

            @Override
            public void onResults(Bundle bundle) {
                ArrayList<String> matches=bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if(matches!=null && matches.size()>0)
                {
                    String command= matches.get(0);
                    handleCommand(command);
                }
            }

            @Override
            public void onPartialResults(Bundle bundle) {
                ArrayList<String> matches=bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if(matches!=null && matches.size()>0)
                {
                    String partialResult = matches.get(0);
                    handleCommand(partialResult);
                }
            }

            @Override
            public void onEvent(int i, Bundle bundle) {

            }
        });
    }

//    public void handleSpeechBegin()
//    {
//        isListening=true;
//        mSpeechRecognizer.startListening(createIntent());
//        System.out.println("Mic on");
//    }
//    public void handleSpeechEnd()
//    {
//        isListening=false;
//        mSpeechRecognizer.cancel();
//        System.out.println("Mic off");
//        // handleSpeechBegin();
//        // mSpeechRecognizer.startListening(createIntent());
//    }

    public void handleCommand(String command)
    {
        //Toast.makeText(this, command, Toast.LENGTH_SHORT).show();

        if(commandListStartRecord.contains(command))
        {
            if(switchMic.isChecked())
            {
                if(mIsRecording==false)
                {
                    Toast.makeText(this, "Recording Started", Toast.LENGTH_SHORT).show();

                    mIsRecording=true;
                    mRecordImageButton.setImageResource(R.drawable.record_resumed);
                    try {
                        createVideoFileName();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    startRecord();
                    mMediaRecorder.start();
                }
            }

        }
        else if(commandListStopRecord.contains(command))
        {
            if(switchMic.isChecked())
            {
                if(mIsRecording==true)
                {
                    Toast.makeText(this, "Recording Stopped", Toast.LENGTH_SHORT).show();

                    mIsRecording=false;
                    mRecordImageButton.setImageResource(R.drawable.record_paused);

                    //toggleFlashModeRecord(flashStatus);

                    mMediaRecorder.stop();
                    mMediaRecorder.reset();
                    startPreview();

                }
            }

        }else
        {
           System.out.println(command);
        }



//        if(isListening==false)
//        handleSpeechBegin();


//        if(commandList.equals(command))
//        {
//            Toast.makeText(this, "11111111111111111111", Toast.LENGTH_SHORT).show();
//        }else
//        {
//            Toast.makeText(this, "222222222222222", Toast.LENGTH_SHORT).show();
//        }
    }


    private boolean checkVoicePermissions() {
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.M)
        {
            if(ContextCompat.checkSelfPermission(this,Manifest.permission.RECORD_AUDIO)==
                    PackageManager.PERMISSION_GRANTED)
            {
                return true;
            }else
            {
                if(shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO))
                {
                    Toast.makeText(this, "Please grant Mic Permission", Toast.LENGTH_SHORT).show();
                }
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},REQUEST_MIC_PERMISSION_RESULT);
            }
        }else
        {
            Toast.makeText(this, "Permission Granted Already", Toast.LENGTH_SHORT).show();
            return true;
        }
        return false;
    }

    private void toggleFlashMode(boolean flashStatus) {
////        if(mRecordCaptureSession==null)
////        {
        try {
            if (flashStatus) {
                mCaptureRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
                //mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
            } else {
                mCaptureRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
                //mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
            }
            mPreviewCaptureSession.setRepeatingRequest(mCaptureRequestBuilder.build(), null, null);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    ////        }else
////        {
////            try {
////                if (flashStatus) {
////                    mCaptureRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
////                    mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
////                } else {
////                    mCaptureRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
////                    mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
////                }
////                mRecordCaptureSession.setRepeatingRequest(mCaptureRequestBuilder.build(), null, null);
////
////            } catch (CameraAccessException e) {
////                e.printStackTrace();
////            }
////        }
//
//    }
    private void toggleFlashModeRecord(boolean flashStatus) {
        try {
            if (flashStatus) {
                mCaptureRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
                //mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
            } else {
                mCaptureRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
                //mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
            }
            mRecordCaptureSession.setRepeatingRequest(mCaptureRequestBuilder.build(), null, null);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
//
//
    }

    @Override
    protected void onResume() {
        super.onResume();

        startBackgroundThread();

        createSpeechRecognizer();

        sharedPreferences=getSharedPreferences("SwitchBtnValue",MODE_PRIVATE);
        String btnCheck=sharedPreferences.getString("switch","");

        if(btnCheck.equals("true"))
        {
            switchMic.setChecked(true);
            mSpeechRecognizer.startListening(createIntent());
        }else
        {
            //handleSpeechEnd();
            switchMic.setChecked(false);
            mSpeechRecognizer.stopListening();
            mSpeechRecognizer.cancel();
        }

        switchMic.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                Toast.makeText(MainActivity.this, String.valueOf(b), Toast.LENGTH_SHORT).show();
                if(b)
                {
                    mSpeechRecognizer.startListening(createIntent());

                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putString("switch", "true");
                    editor.apply();
                }else
                {
//                  if(switchMic.isChecked()) {
//                        handleSpeechEnd();
                    mSpeechRecognizer.stopListening();
                    mSpeechRecognizer.cancel();

                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putString("switch", "false");
                    editor.apply();
//                    }
                }
            }
        });

        if (mTextureView.isAvailable()) {
            setUpCamera(mTextureView.getWidth(), mTextureView.getHeight());
            connectCamera();
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }

        countDown();
        countDownTimer.start();

    }

    private void countDown() {
        countDownTimer=new CountDownTimer(50000,1000) {
            @Override
            public void onTick(long l) {
                int seconds = (int) (l / 1000);
                Text.setText(String.valueOf(seconds));
            }
            @Override
            public void onFinish() {
                countDown();
                countDownTimer.start();
            }
        };
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode==REQUEST_CAMERA_PERMISSION_RESULT)
        {
            if (grantResults[0]!=PackageManager.PERMISSION_GRANTED)
            {
                Toast.makeText(this, "Grant Camera Permission", Toast.LENGTH_SHORT).show();
            }
            if (grantResults[1]!=PackageManager.PERMISSION_GRANTED)
            {
                //Toast.makeText(this, "Grant Audio Permission", Toast.LENGTH_SHORT).show();
            }
        }
        if(requestCode==REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION_RESULT)
        {
            if(grantResults[0]==PackageManager.PERMISSION_GRANTED)
            {
//                mIsRecording=true;
//                mRecordImageButton.setImageResource(R.drawable.record_resumed);
//                try {
//                    createVideoFileName();
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
                Toast.makeText(this, "Permission Granted", Toast.LENGTH_SHORT).show();
            }else
            {
                Toast.makeText(this, "App needs to Save Video Please Grant", Toast.LENGTH_SHORT).show();
            }
        }

        if(requestCode==REQUEST_MIC_PERMISSION_RESULT)
        {
            if(grantResults[0]==PackageManager.PERMISSION_GRANTED)
            {
                Toast.makeText(this, "Permission Granted", Toast.LENGTH_SHORT).show();
            }else
            {
                Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        countDownTimer.cancel();

        stopBackgroundThread();

        //flashStatus=false;
        //mIsRecording=false;

        mIsRecording=false;
        mRecordImageButton.setImageResource(R.drawable.record_paused);
        //toggleFlashModeRecord(flashStatus);

        if (mSpeechRecognizer != null) {
            mSpeechRecognizer.destroy();
        }

        try {
            mMediaRecorder.stop();
            mMediaRecorder.reset();
        }catch (Exception e)
        {

        }
//        Toast.makeText(this, mIsRecording.toString(), Toast.LENGTH_SHORT).show();

        flashStatus=false;
        if(flashStatus)
        {
            flashBTN.setImageResource(R.drawable.ic_flash_on);
        }else
        {
            flashBTN.setImageResource(R.drawable.ic_flash_off);
        }

        CloseCamera();
    }


    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

        View decorView = getWindow().getDecorView();
        if (hasFocus) {
            decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        }
    }
    static class CompareSizesByArea implements Comparator<Size> {
        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }

    }
    private void setUpCamera(int width, int height) {
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String cameraId : cameraManager.getCameraIdList()) {
                CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);
                if (cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }
                StreamConfigurationMap map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                int deviceOrientation = getWindowManager().getDefaultDisplay().getRotation();


                mTotalRotation = sensorToDeviceOrientation(cameraCharacteristics, deviceOrientation);

//                boolean swappedDimensions = false;
//                switch (deviceOrientation) {
//                    case Surface.ROTATION_90:
//                    case Surface.ROTATION_270:
//                            swappedDimensions = true;
//                        break;
//                }


//                int rotateWidth = width;
//                int rotateHeight = height;

                int rotateWidth = width;
                int rotateHeight = height;

//                if (swappedDimensions) {
//                     rotateWidth = height;
//                     rotateHeight = width;
//                }


//                boolean swappedDimensions = false;
//                switch (deviceOrientation) {
//                    case Surface.ROTATION_0:
//                    case Surface.ROTATION_180:
//                        if (mTotalRotation == 90 || mTotalRotation == 270) {
//                            swappedDimensions = true;
//                        }
//                        break;
//                    case Surface.ROTATION_90:
//                    case Surface.ROTATION_270:
//                        if (mTotalRotation == 0 || mTotalRotation == 180) {
//                            swappedDimensions = true;
//                        }
//                        break;
//                    default:
//                        Log.e(TAG, "Display rotation is invalid: " + mTotalRotation);
//                }



//                int rotateWidth = width;
//                int rotateHeight = height;
//
//                if (swappedDimensions) {
//                    rotateWidth = height;
//                    rotateHeight = width;
//                }

                boolean swapRotation = mTotalRotation ==  90 || mTotalRotation == 270;
                if (swapRotation) {
                    rotateWidth = height;
                    rotateHeight = width;
                }

                mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class), rotateWidth, rotateHeight);
                mVideoSize = chooseOptimalSize(map.getOutputSizes(MediaRecorder.class), rotateWidth, rotateHeight);
                mImageSize = chooseOptimalSize(map.getOutputSizes(ImageFormat.JPEG), rotateWidth, rotateHeight);
                mImageReader=ImageReader.newInstance(mImageSize.getWidth(),mImageSize.getHeight(),ImageFormat.JPEG,1);
                mImageReader.setOnImageAvailableListener(mOnImageAvailableListener,mBackgroundHandler);

//                Point displaySize = new Point();
//                MainActivity.this.getWindowManager().getDefaultDisplay().getSize(displaySize);
//                int rotatedPreviewWidth = width;
//                int rotatedPreviewHeight = height;
//
//                if (swappedDimensions) {
//                    rotatedPreviewWidth = height;
//                    rotatedPreviewHeight = width;
//
//                }

//                mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
//                        rotateWidth,rotateHeight);
//                mVideoSize = chooseOptimalSize(map.getOutputSizes(MediaRecorder.class), rotateWidth, rotateHeight);
//                mImageSize = chooseOptimalSize(map.getOutputSizes(ImageFormat.JPEG), rotateWidth, rotateHeight);
//                mImageReader=ImageReader.newInstance(mImageSize.getWidth(),mImageSize.getHeight(),ImageFormat.JPEG,1);
//                mImageReader.setOnImageAvailableListener(mOnImageAvailableListener,mBackgroundHandler);

                // We fit the aspect ratio of TextureView to the size of preview we picked.

                mCameraId = cameraId;
                return;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
        }
    }

    private void configureTransform(int viewWidth, int viewHeight) {
        Activity activity = MainActivity.this;
        if (null == mTextureView || null == mPreviewSize || null == activity) {
            return;
        }
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();

        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / mPreviewSize.getHeight(),
                    (float) viewWidth / mPreviewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
            // matrix.postRotate(0,centerX,centerY);

        }
        else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        }

        mTextureView.setTransform(matrix);

    }

//    @Override
//    public void onConfigurationChanged(@NonNull Configuration newConfig) {
//        super.onConfigurationChanged(newConfig);
//
//        boolean isLandscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE;
//
//        int degrees = 0;
//        switch (.getRotation()){
//            case Surface.ROTATION_0:
//                degrees = isLandscape? 0 : 90;
//                break;
//            case Surface.ROTATION_90:
//                degrees = isLandscape? 0 : 270;
//                break;
//            case Surface.ROTATION_180:
//                degrees = isLandscape? 180 : 270;
//                break;
//            case Surface.ROTATION_270:
//                degrees = isLandscape? 180 : 90;
//                break;
//        }
//        mCa.rotateDisplay(degrees, isLandscape);
//    }

    private void connectCamera() {
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.M)
            {
                if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)
                {
                    cameraManager.openCamera(mCameraId, mCameraDeviceStateCallback, mBackgroundHandler);
                }
                else
                {
                    if(shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                        Toast.makeText(this, "Camera Permission Required", Toast.LENGTH_SHORT).show();
                    }
                    requestPermissions(new String[]{Manifest.permission.CAMERA,
                            Manifest.permission.RECORD_AUDIO},REQUEST_CAMERA_PERMISSION_RESULT);
                }
            }
            else
            {
                cameraManager.openCamera(mCameraId, mCameraDeviceStateCallback, mBackgroundHandler);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }



    private void startRecord()
    {
        try {
            setUpMediaRecorder();

            SurfaceTexture surfaceTexture=mTextureView.getSurfaceTexture();
            surfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(),mPreviewSize.getHeight());
            Surface previewSurface=new Surface(surfaceTexture);

            Surface recordSurface=mMediaRecorder.getSurface();
            mCaptureRequestBuilder=mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            mCaptureRequestBuilder.addTarget(previewSurface);
            mCaptureRequestBuilder.addTarget(recordSurface);

            if(flashStatus)
            {
                mCaptureRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
            }else
            {
                mCaptureRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
            }


            mCameraDevice.createCaptureSession(Arrays.asList(previewSurface, recordSurface,mImageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                            mRecordCaptureSession=cameraCaptureSession;
                            try {
                                cameraCaptureSession.setRepeatingRequest(
                                        mCaptureRequestBuilder.build(),null,null);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }
                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {

                        }
                    },null);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private void startPreview()
    {

        SurfaceTexture surfaceTexture=mTextureView.getSurfaceTexture();
        surfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(),mPreviewSize.getHeight());
        Surface previewSurface=new Surface(surfaceTexture);

        try {
            mCaptureRequestBuilder=mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mCaptureRequestBuilder.addTarget(previewSurface);

            if(flashStatus)
            {
                mCaptureRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
            }else
            {
                mCaptureRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
            }

            mCameraDevice.createCaptureSession(Arrays.asList(previewSurface,mImageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                            mPreviewCaptureSession=cameraCaptureSession;
                            try {
                                mPreviewCaptureSession.setRepeatingRequest(mCaptureRequestBuilder.build(),
                                        null,mBackgroundHandler);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                            Toast.makeText(MainActivity.this, "Unable to Preview", Toast.LENGTH_SHORT).show();
                        }
                    },null);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }


    private void startStillCaptureRequest()
    {
        try {
            if(mIsRecording)
            {
                mCaptureRequestBuilder=mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_VIDEO_SNAPSHOT);
            }
            else {
                mCaptureRequestBuilder=mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            }

            mCaptureRequestBuilder.addTarget(mImageReader.getSurface());
            mCaptureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION,mTotalRotation);

            CameraCaptureSession.CaptureCallback stillCaptureCallback=new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
                    super.onCaptureStarted(session, request, timestamp, frameNumber);
                    try {
                        createImageFileName();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            };

            if(mIsRecording)
            {
                mRecordCaptureSession.capture(mCaptureRequestBuilder.build(),stillCaptureCallback,null);
            }else
            {
                mPreviewCaptureSession.capture(mCaptureRequestBuilder.build(),stillCaptureCallback,null);
            }


        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void CloseCamera() {
        if (mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
    }

    private void startBackgroundThread()
    {
        mBackgroundHandlerThread=new HandlerThread("Camera2API");
        mBackgroundHandlerThread.start();
        mBackgroundHandler=new Handler(mBackgroundHandlerThread.getLooper());
    }
    private void stopBackgroundThread()
    {
        mBackgroundHandlerThread.quitSafely();
        try {
            mBackgroundHandlerThread.join();
            mBackgroundHandlerThread=null;
            mBackgroundHandler=null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }


    }

    private static int sensorToDeviceOrientation(CameraCharacteristics cameraCharacteristics,int deviceOrientation)
    {
        int sensorOrientation=cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        deviceOrientation=ORIENTATIONS.get(deviceOrientation);
        return (sensorOrientation+deviceOrientation*1+360) % 360;

    }

    private static Size chooseOptimalSize(Size[] choices,int width,int height)
    {
        List<Size>bigEnough=new ArrayList<Size>();
        for (Size option:choices)
        {
            if (option.getHeight()==option.getWidth()*height/width &&
                    option.getWidth()>= width && option.getHeight() >= height)
            {
                bigEnough.add(option);
            }
        }
        if (bigEnough.size()>0)
        {
            return Collections.min(bigEnough,new CompareSizeByArea());
        }else {
            return choices[0];
        }
    }


    private void createVideoFolder()
    {
        File movieFile= Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
        mVideoFolder =new File(movieFile,"camera2VideoAPI");
        if(!mVideoFolder.exists())
        {
            mVideoFolder.mkdirs();
        }
    }

    private File createVideoFileName() throws IOException {
        String timestamp=new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String prepend="VIDEO_"+timestamp+"_";
        File videoFile=File.createTempFile(prepend,".mp4",mVideoFolder);
        mVideoFileName =videoFile.getAbsolutePath();
        return videoFile;
    }

    private void createImageFolder()
    {
        File imgFile= Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        mImageFolder =new File(imgFile,"camera2VideoAPI");
        if(!mImageFolder.exists())
        {
            mImageFolder.mkdirs();
        }
    }

    private File createImageFileName() throws IOException {
        String timestamp=new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String prepend="IMAGE_"+timestamp+"_";
        File imageFile=File.createTempFile(prepend,".jpg",mImageFolder);
        mImageFileName =imageFile.getAbsolutePath();
        return imageFile;
    }

    private void checkWriteStoragePermission()
    {
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.M)
        {
            if(ContextCompat.checkSelfPermission(this,Manifest.permission.WRITE_EXTERNAL_STORAGE)==
                    PackageManager.PERMISSION_GRANTED)
            {
                mIsRecording=true;
                mRecordImageButton.setImageResource(R.drawable.record_resumed);
                try {
                    createVideoFileName();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                startRecord();
                mMediaRecorder.start();
            }else
            {
                if(shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE))
                {
                    Toast.makeText(this, "Please grant Video Permission", Toast.LENGTH_SHORT).show();
                }
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION_RESULT);
            }
        }else
        {
            mIsRecording=true;
            mRecordImageButton.setImageResource(R.drawable.record_resumed);
            try {
                createVideoFileName();
                startRecord();
                mMediaRecorder.start();
            } catch (IOException e) {
                Toast.makeText(this, e.toString(), Toast.LENGTH_SHORT).show();
            }

        }
    }
    private void setUpMediaRecorder() throws IOException
    {
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        //mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);


        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mMediaRecorder.setOutputFile(mVideoFileName);
        mMediaRecorder.setVideoEncodingBitRate(1000000);
        //mMediaRecorder.setVideoFrameRate(30);
        mMediaRecorder.setVideoSize(mVideoSize.getWidth(),mVideoSize.getHeight());
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        //mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mMediaRecorder.prepare();




    }

    private void lockfocus()
    {
        mCaptureState=STATE_WAIT_LOCK;
        mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,CaptureRequest.CONTROL_AF_TRIGGER_START);
        try {
            if(mIsRecording)
            {
                mRecordCaptureSession.capture(mCaptureRequestBuilder.build(),mRecordCaptureCallback,mBackgroundHandler);
            }else
            {
                mPreviewCaptureSession.capture(mCaptureRequestBuilder.build(),mPreviewCaptureCallback,mBackgroundHandler);
            }

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();

    }
}