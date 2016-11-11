package com.sourab.videorecorder.util;

import android.content.Context;

public class CustomUtil {

  public static int rotateVideo(Context paramContext, String currentVideo, String mOutput,
      boolean frontCamera, boolean isRotateVideo) {
    return (new Processor(getEncodingLibraryPath(paramContext), paramContext)).newCommand()
        .addInputPath(currentVideo)
        .setRotateFilter(frontCamera, isRotateVideo)
        .setAudioCopy()
        .setThread()
        .setPreset()
        .setStrict()
        .enableOverwrite()
        .processToOutput(mOutput);
  }

  public static int transponseVideo(Context paramContext, String currentVideo, String mOutput,
      boolean frontCamera, boolean isRotateVideo) {
    return (new Processor(getEncodingLibraryPath(paramContext), paramContext)).newCommand()
        .addInputPath(currentVideo)
        .setTransposeFilter(frontCamera, isRotateVideo)
        .setAudioCopy()
        .setThread()
        .setPreset()
        .setStrict()
        .enableOverwrite()
        .processToOutput(mOutput);
  }

  public static String getEncodingLibraryPath(Context paramContext) {
    return paramContext.getApplicationInfo().nativeLibraryDir + "/libencoding.so";
  }

  public static void addBitmapOverlayOnVideo(Context context, String videoPath, String bitmapPath,
      String outputPath) {
    (new Processor(getEncodingLibraryPath(context), context)).newCommand()
        .enableOverwrite()
        .addInputPath(videoPath)
        .setWaterMark(bitmapPath)
        .setThread()
        .setPreset()
        .setStrict()
        .processToOutput(outputPath);
  }
}


