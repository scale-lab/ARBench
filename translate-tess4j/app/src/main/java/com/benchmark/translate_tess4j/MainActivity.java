package com.benchmark.translate_tess4j;/*
 * Copyright 2018 Google LLC. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.ar.core.Anchor;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.exceptions.NotYetAvailableException;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.rendering.ViewRenderable;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.TransformableNode;
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;
import com.googlecode.tesseract.android.TessBaseAPI;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final double MIN_OPENGL_VERSION = 3.0;

    private ArFragment arFragment;
    private ViewRenderable viewRenderable;

    private TessBaseAPI tessBaseAPI;
    private Translator englishSpanishTranslator;

    @Override
    @SuppressWarnings({"AndroidApiChecker", "FutureReturnValueIgnored"})
    // CompletableFuture requires api level 24
    // FutureReturnValueIgnored is not valid
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!checkIsSupportedDeviceOrFinish(this)) {
            return;
        }

        File f = new File(getFilesDir() + "/tesseract/tessdata");
        File trainedDataFile = new File(f.getPath() + "/" + File.separator + "eng.traineddata");
        if (!f.exists()) f.mkdirs();
        copyTrainedData();

        initializeTranslator();

        tessBaseAPI = new TessBaseAPI();
        tessBaseAPI.setDebug(true);

        final String path = getFilesDir() + "/tesseract";
        tessBaseAPI.init(path, "eng");
        tessBaseAPI.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO);

        setContentView(R.layout.activity_main);
        arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.ux_fragment);

        arFragment.setOnTapArPlaneListener(
                (HitResult hitResult, Plane plane, MotionEvent motionEvent) -> handleTap(hitResult));
    }

    private void initializeTranslator() {
        TranslatorOptions options = new TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.ENGLISH)
                .setTargetLanguage(TranslateLanguage.SPANISH)
                .build();
        englishSpanishTranslator = Translation.getClient(options);

        DownloadConditions conditions = new DownloadConditions.Builder()
                .requireWifi()
                .build();
        englishSpanishTranslator.downloadModelIfNeeded(conditions)
                .addOnSuccessListener(
                        (OnSuccessListener) o -> Log.d("SUCCESS", "Downloaded translation model"))
                .addOnFailureListener(
                        e -> Log.e("FAILURE", "Could not download translation model"));
    }

    private void copyTrainedData() {
        try {
            String filepath = getFilesDir() + "/tesseract/tessdata/eng.traineddata";
            AssetManager assetManager = getAssets();
            InputStream inStream = assetManager.open("tessdata/eng.traineddata");
            OutputStream outStream = new FileOutputStream(filepath);
            byte[] buffer = new byte[1024];
            int read;
            while ((read = inStream.read(buffer)) != -1) {
                outStream.write(buffer, 0, read);
            }
            outStream.flush();
            outStream.close();
            inStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void showFrameAlertDialog(ImageView view, String originalText, String translatedText) {
        new AlertDialog.Builder(MainActivity.this)
                .setTitle("Frame captured:")
                .setMessage("OCR Text: " + originalText + "\n\n" + "Translated text: " + translatedText)
                .setView(view)
                .setPositiveButton("Close", (DialogInterface dialog, int which) -> {
                })
                .show();
    }

    private Bitmap imageToBitmap(Image image) {
        byte[] bytes = NV21toJPEG(YUV420toNV21(image), image.getWidth(), image.getHeight(), 100);
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
    }

    private Bitmap rotateBitmap(Bitmap bitmap) {
        android.graphics.Matrix matrix = new android.graphics.Matrix();
        matrix.postRotate(90);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    private static byte[] NV21toJPEG(byte[] nv21, int width, int height, int quality) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        YuvImage yuv = new YuvImage(nv21, ImageFormat.NV21, width, height, null);
        yuv.compressToJpeg(new Rect(0, 0, width, height), quality, out);
        return out.toByteArray();
    }

    private static byte[] YUV420toNV21(Image image) {
        byte[] nv21;
        ByteBuffer yBuffer = image.getPlanes()[0].getBuffer();
        ByteBuffer uBuffer = image.getPlanes()[1].getBuffer();
        ByteBuffer vBuffer = image.getPlanes()[2].getBuffer();

        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();

        nv21 = new byte[ySize + uSize + vSize];

        yBuffer.get(nv21, 0, ySize);
        vBuffer.get(nv21, ySize, vSize);
        uBuffer.get(nv21, ySize + vSize, uSize);

        return nv21;
    }


    private void handleTap(HitResult hitResult) {
        Image image = null;
        try {
            image = arFragment.getArSceneView().getArFrame().acquireCameraImage();
        } catch (NotYetAvailableException e) {
            e.printStackTrace();
        }

        if (image == null) return;
        Bitmap bitmapImage = rotateBitmap(imageToBitmap(image));
        ImageView frameImage = new ImageView(this);
        frameImage.setImageBitmap(bitmapImage);
        tessBaseAPI.setImage(bitmapImage);

        final String ocrText = tessBaseAPI.getUTF8Text();

        englishSpanishTranslator.translate(ocrText)
                .addOnSuccessListener(
                        new OnSuccessListener() {
                            @Override
                            public void onSuccess(Object o) {
                                Log.i("TRANSLATED TEXT", o.toString());
//                                runOnUiThread(() -> showFrameAlertDialog(frameImage, ocrText, o.toString()));
                                addTextToScene(hitResult, o.toString());
                            }
                        })
                .addOnFailureListener(
                        new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                Log.i("FAILURE", "FAILED TO TRANSLATE TEXT");

                                runOnUiThread(() -> showFrameAlertDialog(frameImage, ocrText, "FAILED TO TRANSLATE TEXT"));
                            }
                        });

        image.close();
        tessBaseAPI.clear();
    }

    private void addTextToScene(HitResult hitResult, String translatedText) {
        ViewRenderable.builder()
                .setView(this, R.layout.ar_text)
                .build()
                .thenAccept(renderable -> {
                    TextView textView = (TextView) renderable.getView().findViewById(R.id.arText);
                    textView.setText(translatedText);

                    viewRenderable = renderable;

                    Anchor anchor = hitResult.createAnchor();
                    AnchorNode anchorNode = new AnchorNode(anchor);
                    anchorNode.setParent(arFragment.getArSceneView().getScene());

                    TransformableNode view = new TransformableNode(arFragment.getTransformationSystem());
                    view.setParent(anchorNode);
                    view.setRenderable(viewRenderable);
                    view.getRotationController();
                    view.getScaleController();
                    view.getTranslationController();
                    view.select();
                });
    }

    public static boolean checkIsSupportedDeviceOrFinish(final Activity activity) {
        if (Build.VERSION.SDK_INT < VERSION_CODES.N) {
            Log.e(TAG, "Sceneform requires Android N or later");
            Toast.makeText(activity, "Sceneform requires Android N or later", Toast.LENGTH_LONG).show();
            activity.finish();
            return false;
        }
        String openGlVersionString =
                ((ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE))
                        .getDeviceConfigurationInfo()
                        .getGlEsVersion();
        if (Double.parseDouble(openGlVersionString) < MIN_OPENGL_VERSION) {
            Log.e(TAG, "Sceneform requires OpenGL ES 3.0 later");
            Toast.makeText(activity, "Sceneform requires OpenGL ES 3.0 or later", Toast.LENGTH_LONG)
                    .show();
            activity.finish();
            return false;
        }
        return true;
    }
}
