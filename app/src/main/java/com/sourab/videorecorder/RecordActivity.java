package com.sourab.videorecorder;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Camera;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.OrientationEventListener;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.bytedeco.javacv.FrameRecorder;

public class RecordActivity extends Activity implements View.OnClickListener {

  private final static String CLASS_LABEL = "RecordActivity";
  private PowerManager.WakeLock mWakeLock;
  private int screenWidth = 480;

  private DeviceOrientationEventListener orientationListener;
  private int deviceOrientation = OrientationEventListener.ORIENTATION_UNKNOWN;
  private boolean isRotateVideo = false;

  private int defaultCameraId = -1;
  private int defaultScreenResolution = -1;
  private int cameraSelection = 0;
  private boolean initSuccess = false;

  private Camera cameraDevice;
  private RecordActivity.CameraView cameraView;
  private Camera.Parameters cameraParameters = null;
  private volatile FFmpegFrameRecorder videoRecorder;
  private RecorderThread recorderThread;

  private boolean recording = false;
  private boolean isPreviewOn = false;
  private boolean isFrontCam = false;

  private int frameRate = 30;
  private byte[] bufferByte;

  private int previewWidth = 480;
  private int previewHeight = 480;

  private long firstTime = 0;
  private SavedFrames lastSavedframe = new SavedFrames(null, 0L, false, false);

  View swapCamera;
  private String strVideoPath;
  private int currentResolution = CONSTANTS.RESOLUTION_MEDIUM_VALUE;
  private File fileVideoPath;
  private View pause;
  private View recordContainer;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.record);

    PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
    mWakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, CLASS_LABEL);
    mWakeLock.acquire();

    DisplayMetrics displaymetrics = new DisplayMetrics();
    getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);
    screenWidth = displaymetrics.widthPixels;

    orientationListener = new DeviceOrientationEventListener(RecordActivity.this);

    initLayout();
  }

  private void initLayout() {
    swapCamera = findViewById(R.id.swap_camera);
    recordContainer = findViewById(R.id.record_container);
    recordContainer.setOnClickListener(this);
    pause = findViewById(R.id.pause);
    pause.setOnClickListener(this);
    if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)) {
      swapCamera.setVisibility(View.VISIBLE);
    } else {
      swapCamera.setVisibility(View.GONE);
    }
    initCameraLayout();
  }

  private void initCameraLayout() {
    new AsyncTask<String, Integer, Boolean>() {

      @Override
      protected Boolean doInBackground(String... params) {
        boolean result = setCamera();
        if (!initSuccess) {
          initVideoRecorder();
          startRecording();
          initSuccess = true;
        }
        return result;
      }

      @Override
      protected void onPostExecute(Boolean result) {
        if (!result || cameraDevice == null) {
          finish();
          return;
        }

        RelativeLayout topLayout = (RelativeLayout) findViewById(R.id.camera_container);
        if (topLayout != null && topLayout.getChildCount() > 0) topLayout.removeAllViews();

        cameraView = new RecordActivity.CameraView(RecordActivity.this, cameraDevice);

        handleSurfaceChanged();
        if (recorderThread == null) {
          recorderThread = new RecorderThread(videoRecorder, previewWidth, previewHeight);
          recorderThread.start();
        }
        RelativeLayout.LayoutParams layoutParam1 = new RelativeLayout.LayoutParams(screenWidth,
            (int) (screenWidth * (previewWidth / (previewHeight * 1f))));
        layoutParam1.addRule(RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE);
        RelativeLayout.LayoutParams layoutParam2 =
            new RelativeLayout.LayoutParams(screenWidth, screenWidth);
        layoutParam2.topMargin = screenWidth;

        View view = new View(RecordActivity.this);
        view.setFocusable(false);
        view.setBackgroundColor(Color.BLACK);
        view.setFocusableInTouchMode(false);
        topLayout.addView(cameraView, layoutParam1);

        swapCamera.setOnClickListener(RecordActivity.this);
        if (cameraSelection == Camera.CameraInfo.CAMERA_FACING_FRONT) {
          //flashIcon.setVisibility(View.GONE);
          isFrontCam = true;
        } else {
          //flashIcon.setVisibility(View.VISIBLE);
          isFrontCam = false;
        }
      }
    }.execute("start");
  }

  private boolean setCamera() {
    try {
      if (Build.VERSION.SDK_INT > Build.VERSION_CODES.FROYO) {
        int numberOfCameras = Camera.getNumberOfCameras();
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        for (int i = 0; i < numberOfCameras; i++) {
          Camera.getCameraInfo(i, cameraInfo);
          if (cameraInfo.facing == cameraSelection) {
            defaultCameraId = i;
          }
        }
      }
      stopPreview();
      if (cameraDevice != null) cameraDevice.release();

      if (defaultCameraId >= 0) {
        cameraDevice = Camera.open(defaultCameraId);
      } else {
        cameraDevice = Camera.open();
      }
    } catch (Exception e) {
      return false;
    }
    return true;
  }

  private void initVideoRecorder() {
    strVideoPath = Util.createFinalPath(this);

    RecorderParameters recorderParameters = Util.getRecorderParameter(currentResolution);
    frameRate = recorderParameters.getVideoFrameRate();

    fileVideoPath = new File(strVideoPath);
    videoRecorder =
        new FFmpegFrameRecorder(strVideoPath, CONSTANTS.OUTPUT_WIDTH, CONSTANTS.OUTPUT_HEIGHT,
            recorderParameters.getAudioChannel());
    videoRecorder.setFormat(recorderParameters.getVideoOutputFormat());
    videoRecorder.setSampleRate(recorderParameters.getAudioSamplingRate());
    videoRecorder.setFrameRate(recorderParameters.getVideoFrameRate());
    videoRecorder.setVideoCodec(recorderParameters.getVideoCodec());
    videoRecorder.setVideoQuality(recorderParameters.getVideoQuality());
    videoRecorder.setAudioQuality(recorderParameters.getVideoQuality());
    videoRecorder.setAudioCodec(recorderParameters.getAudioCodec());
    videoRecorder.setVideoBitrate(recorderParameters.getVideoBitrate());
    videoRecorder.setAudioBitrate(recorderParameters.getAudioBitrate());
  }

  private void initiateRecording() {
    firstTime = System.currentTimeMillis();
    recording = true;
  }

  public void startRecording() {
    try {
      if (videoRecorder != null) {
        videoRecorder.start();
      } else {
        finish();
      }
    } catch (FFmpegFrameRecorder.Exception e) {
      e.printStackTrace();
    }
  }

  @Override
  protected void onResume() {
    super.onResume();

    if (orientationListener != null) orientationListener.enable();

    if (mWakeLock == null) {
      PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
      mWakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, CLASS_LABEL);
      mWakeLock.acquire();
    }
  }

  @Override
  protected void onPause() {
    super.onPause();
    if (orientationListener != null) orientationListener.disable();
    if (mWakeLock != null) {
      mWakeLock.release();
      mWakeLock = null;
    }
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    releaseResources();

    if (cameraView != null) {
      cameraView.stopPreview();
      if (cameraDevice != null) {
        cameraDevice.setPreviewCallback(null);
        cameraDevice.release();
      }
      cameraDevice = null;
    }
    cameraDevice = null;
    cameraView = null;
    if (mWakeLock != null) {
      mWakeLock.release();
      mWakeLock = null;
    }
  }

  @Override
  public void onClick(View view) {
    switch (view.getId()) {
      case R.id.record_container:
        if (deviceOrientation == 0) {
          isRotateVideo = true;
        } else {
          isRotateVideo = false;
        }

        view.setVisibility(View.GONE);
        pause.setVisibility(View.VISIBLE);
        initiateRecording();
        break;
      case R.id.swap_camera:
        cameraSelection = ((cameraSelection == Camera.CameraInfo.CAMERA_FACING_BACK)
            ? Camera.CameraInfo.CAMERA_FACING_FRONT : Camera.CameraInfo.CAMERA_FACING_BACK);
        isFrontCam = ((cameraSelection == Camera.CameraInfo.CAMERA_FACING_BACK) ? false : true);

        initCameraLayout();

        //if (cameraSelection == Camera.CameraInfo.CAMERA_FACING_FRONT) {
        //  flashIcon.setVisibility(View.GONE);
        //} else {
        //  flashIcon.setVisibility(View.VISIBLE);
        //  if (isFlashOn) {
        //    cameraParameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
        //    cameraDevice.setParameters(cameraParameters);
        //  }
        //}
        break;
      case R.id.pause:
        new AsyncStopRecording().execute();
        break;
    }
  }

  public class AsyncStopRecording extends AsyncTask<Void, Integer, Void> {

    private ProgressBar bar;
    private TextView progress;
    private Dialog creatingProgress;

    @Override
    protected void onPreExecute() {

      creatingProgress = new Dialog(RecordActivity.this, R.style.Dialog_loading_noDim);
      Window dialogWindow = creatingProgress.getWindow();
      WindowManager.LayoutParams lp = dialogWindow.getAttributes();
      lp.width = (int) (getResources().getDisplayMetrics().density * 240);
      lp.height = (int) (getResources().getDisplayMetrics().density * 80);
      lp.gravity = Gravity.CENTER;
      dialogWindow.setAttributes(lp);
      creatingProgress.setCanceledOnTouchOutside(false);
      creatingProgress.setContentView(R.layout.activity_recorder_progress);

      progress = (TextView) creatingProgress.findViewById(R.id.recorder_progress_progresstext);
      bar = (ProgressBar) creatingProgress.findViewById(R.id.recorder_progress_progressbar);
      creatingProgress.show();

      super.onPreExecute();
    }

    @Override
    protected void onProgressUpdate(Integer... values) {
      progress.setText("Processing...");
      bar.setProgress(values[0]);
    }

    @Override
    protected Void doInBackground(Void... params) {
      recording = false;
      recorderThread.stopRecord();
      releaseResources();
      publishProgress(100);
      return null;
    }

    @Override
    protected void onPostExecute(Void result) {
      if (!isFinishing()) {
        creatingProgress.dismiss();
      }

      initCameraLayout();
      recordContainer.setVisibility(View.VISIBLE);
      Toast.makeText(RecordActivity.this, "File saved to: " + strVideoPath, Toast.LENGTH_LONG)
          .show();
    }
  }

  private void releaseResources() {
    if (recorderThread != null) {
      recorderThread.finish();
    }
    try {
      if (videoRecorder != null) {
        videoRecorder.stop();
        videoRecorder.release();
      }
    } catch (FrameRecorder.Exception e) {
      e.printStackTrace();
    }
    videoRecorder = null;
    lastSavedframe = null;
  }

  public void stopPreview() {
    if (isPreviewOn && cameraDevice != null) {
      isPreviewOn = false;
      cameraDevice.stopPreview();
    }
  }

  class CameraView extends SurfaceView implements SurfaceHolder.Callback, Camera.PreviewCallback {

    private SurfaceHolder mHolder;

    public CameraView(Context context, Camera camera) {
      super(context);
      cameraDevice = camera;
      cameraParameters = cameraDevice.getParameters();
      mHolder = getHolder();
      mHolder.addCallback(RecordActivity.CameraView.this);
      mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
      cameraDevice.setPreviewCallbackWithBuffer(RecordActivity.CameraView.this);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
      try {
        stopPreview();
        cameraDevice.setPreviewDisplay(holder);
      } catch (IOException exception) {
        cameraDevice.release();
        cameraDevice = null;
      }
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
      if (isPreviewOn) cameraDevice.stopPreview();
      handleSurfaceChanged();
      startPreview();
      cameraDevice.autoFocus(null);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
      try {
        mHolder.addCallback(null);
        cameraDevice.setPreviewCallback(null);
      } catch (RuntimeException e) {
      }
    }

    public void startPreview() {
      if (!isPreviewOn && cameraDevice != null) {
        isPreviewOn = true;
        cameraDevice.startPreview();
      }
    }

    public void stopPreview() {
      if (isPreviewOn && cameraDevice != null) {
        isPreviewOn = false;
        cameraDevice.stopPreview();
      }
    }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
      long frameTimeStamp = 1000L * (System.currentTimeMillis() - firstTime);
      if (recording) {
        if (lastSavedframe != null && lastSavedframe.getFrameBytesData() != null) {
          recorderThread.putByteData(lastSavedframe);
        }
      }
      lastSavedframe = new SavedFrames(data, frameTimeStamp, isRotateVideo, isFrontCam);
      cameraDevice.addCallbackBuffer(bufferByte);
    }
  }

  private void handleSurfaceChanged() {
    if (cameraDevice == null) {
      finish();
      return;
    }
    List<Camera.Size> resolutionList = Util.getResolutionList(cameraDevice);
    if (resolutionList != null && resolutionList.size() > 0) {
      Collections.sort(resolutionList, new Util.ResolutionComparator());
      Camera.Size previewSize = null;
      if (defaultScreenResolution == -1) {
        boolean hasSize = false;
        for (int i = 0; i < resolutionList.size(); i++) {
          Camera.Size size = resolutionList.get(i);
          if (size != null && size.width == 640 && size.height == 480) {
            previewSize = size;
            hasSize = true;
            break;
          }
        }
        if (!hasSize) {
          int mediumResolution = resolutionList.size() / 2;
          if (mediumResolution >= resolutionList.size()) {
            mediumResolution = resolutionList.size() - 1;
          }
          previewSize = resolutionList.get(mediumResolution);
        }
      } else {
        if (defaultScreenResolution >= resolutionList.size()) {
          defaultScreenResolution = resolutionList.size() - 1;
        }
        previewSize = resolutionList.get(defaultScreenResolution);
      }
      if (previewSize != null) {
        previewWidth = previewSize.width;
        previewHeight = previewSize.height;
        cameraParameters.setPreviewSize(previewWidth, previewHeight);
        if (videoRecorder != null) {
          videoRecorder.setImageWidth(previewWidth);
          videoRecorder.setImageHeight(previewHeight);
        }
      }
    }

    bufferByte = new byte[previewWidth * previewHeight * 3 / 2];

    cameraDevice.addCallbackBuffer(bufferByte);

    cameraParameters.setPreviewFrameRate(frameRate);

    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.FROYO) {
      cameraDevice.setDisplayOrientation(
          Util.determineDisplayOrientation(RecordActivity.this, defaultCameraId));
      List<String> focusModes = cameraParameters.getSupportedFocusModes();
      if (focusModes != null) {
        Log.i("video", Build.MODEL);
        if (((Build.MODEL.startsWith("GT-I950"))
            || (Build.MODEL.endsWith("SCH-I959"))
            || (Build.MODEL.endsWith("MEIZU MX3"))) && focusModes.contains(
            Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {

          cameraParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
        } else if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
          cameraParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
        } else if (focusModes.contains(Camera.Parameters.FOCUS_MODE_FIXED)) {
          cameraParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_FIXED);
        }
      }
    } else {
      cameraDevice.setDisplayOrientation(90);
    }
    cameraDevice.setParameters(cameraParameters);
  }

  private class DeviceOrientationEventListener extends OrientationEventListener {
    public DeviceOrientationEventListener(Context context) {
      super(context);
    }

    @Override
    public void onOrientationChanged(int orientation) {
      if (orientation == ORIENTATION_UNKNOWN) return;
      deviceOrientation = Util.roundOrientation(orientation, deviceOrientation);
      if (deviceOrientation == 0) {
        isRotateVideo = true;
      } else {
        isRotateVideo = false;
      }
    }
  }
}
