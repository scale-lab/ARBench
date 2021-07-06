package com.benchmark.translate_tess4j;

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
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.ar.core.Anchor;
import com.google.ar.core.CameraConfig;
import com.google.ar.core.CameraConfigFilter;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.RecordingConfig;
import com.google.ar.core.RecordingStatus;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.NotYetAvailableException;
import com.google.ar.core.exceptions.RecordingFailedException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.ViewRenderable;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.TransformableNode;
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;
import com.googlecode.leptonica.android.Pixa;
import com.googlecode.tesseract.android.TessBaseAPI;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final double MIN_OPENGL_VERSION = 3.0;

    private ArFragment arFragment;
    private ViewRenderable viewRenderable;

    private Session session;
    private boolean isRecording;

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
                (HitResult hitResult, Plane plane, MotionEvent motionEvent) -> handleTap());

    }

    public void onRecordingClick(View view) {
        if (isRecording) {
            stopRecording();
            isRecording = false;
        } else {
            startRecording();
            isRecording = true;
        }

        if (session.getRecordingStatus() == RecordingStatus.OK) {
            ((Button) view).setText("Stop recording");
            Log.v("Recording", "It is recording");
        }

        if (session.getRecordingStatus() == RecordingStatus.NONE) {
            ((Button) view).setText("Start Recording");
            Log.v("Recording", "It is not recording");
        }
    }

    private void startRecording() {
        try {
            session = new Session(getApplicationContext());
        } catch (UnavailableArcoreNotInstalledException | UnavailableDeviceNotCompatibleException | UnavailableApkTooOldException | UnavailableSdkTooOldException e) {
            e.printStackTrace();
        }
        String destination = new File(getApplicationContext().getFilesDir(), "recording.mp4").getAbsolutePath();
        RecordingConfig recordingConfig =
                new RecordingConfig(session)
                        .setMp4DatasetFilePath(destination)
                        .setAutoStopOnPause(true);
        try {
            session.startRecording(recordingConfig);
        } catch (RecordingFailedException e) {
            Log.e(TAG, "Failed to start recording", e);
        }

    }

    private void stopRecording() {
        try {
            session.stopRecording();
        } catch (RecordingFailedException e) {
            Log.e(TAG, "Failed to stop recording", e);
        }
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


    private void handleTap() {
        configureSession();
        Frame frame = null;
        Image image = null;
        try {
            frame = arFragment.getArSceneView().getArFrame();
            image = frame.acquireCameraImage();
        } catch (NotYetAvailableException e) {
            e.printStackTrace();
        }

        if (frame == null || image == null) return;
        Bitmap bitmapImage = rotateBitmap(imageToBitmap(image));
        ImageView frameImage = new ImageView(this);
        frameImage.setImageBitmap(bitmapImage);
        tessBaseAPI.setImage(bitmapImage);

        final String ocrText = tessBaseAPI.getUTF8Text();
        Pixa words = tessBaseAPI.getTextlines();
        List<HitResult> hitResultList = null;
//        float r1 = image.getWidth()/bitmapImage.getWidth();
//        float r2 = image.getHeight()/bitmapImage.getHeight();
        Vector3 position = null;

        if (words.size() > 0) {
            int x = words.getBoxRect(0).centerX();
            int y = words.getBoxRect(0).centerY();
            Log.i("TESSERACT", "TEXT POSITION: (" + x + ", " + y + ")");
            Config.InstantPlacementMode i = arFragment.getArSceneView().getSession().getConfig().getInstantPlacementMode();
            position = new Vector3((float)x/image.getWidth(),(float)y/image.getHeight(),0);

            hitResultList = frame.hitTestInstantPlacement(x, y, 0.1f);
        }

        final HitResult textHit;
        if (hitResultList == null || hitResultList.size() == 0) {
            return;
        }
        textHit = hitResultList.get(0);


        Vector3 finalPosition = position;
        englishSpanishTranslator.translate(ocrText)
                .addOnSuccessListener(
                        new OnSuccessListener() {
                            @Override
                            public void onSuccess(Object o) {
                                Log.i("TRANSLATED TEXT", o.toString());
//                                runOnUiThread(() -> showFrameAlertDialog(frameImage, ocrText, o.toString()));
                                addTextToScene(textHit, o.toString(), finalPosition);
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

    private void addTextToScene(HitResult hitResult, String translatedText, Vector3 position) {
        TextView textView = findViewById(R.id.translated_text);
        textView.setTranslationX(position.x*textView.getWidth());
        textView.setTranslationY(position.y*textView.getHeight());
        textView.setText(translatedText);
        textView.requestLayout();
//        ViewRenderable.builder()
//                .setView(this, R.layout.ar_text)
//                .build()
//                .thenAccept(renderable -> {
//                    TextView textView = (TextView) renderable.getView().findViewById(R.id.arText);
//                    textView.setText(translatedText);
//
//                    viewRenderable = renderable;
//
//                    Anchor anchor = hitResult.createAnchor();
//                    AnchorNode anchorNode = new AnchorNode(anchor);
//                    anchorNode.setParent(arFragment.getArSceneView().getScene());
//                    Log.i("ARCORE", "TEXT POSITION: (" + anchorNode.getLocalPosition().x +
//                            ", " + anchorNode.getLocalPosition().y + ", " + anchorNode.getLocalPosition().z + ")");
//
//                    TransformableNode view = new TransformableNode(arFragment.getTransformationSystem());
//                    //if (position != null) view.setWorldPosition(position);
//                    view.setParent(anchorNode);
//                    view.setRenderable(viewRenderable);
//                    view.getRotationController();
//                    view.getScaleController();
//                    view.getTranslationController();
//                    view.select();
//                });
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

    private void configureSession() {
        Session session = arFragment.getArSceneView().getSession();
        Config config = session.getConfig();
        config.setLightEstimationMode(Config.LightEstimationMode.ENVIRONMENTAL_HDR);
        if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
            config.setDepthMode(Config.DepthMode.AUTOMATIC);
        } else {
            config.setDepthMode(Config.DepthMode.DISABLED);
        }
        config.setInstantPlacementMode(Config.InstantPlacementMode.LOCAL_Y_UP);
        // don't detect planes
//    config.setPlaneFindingMode(Config.PlaneFindingMode.DISABLED);
        // don't match framerate to camera
//        config.setUpdateMode(Config.UpdateMode.LATEST_CAMERA_IMAGE);
        // use stereo camera
        CameraConfigFilter cameraConfigFilter = new CameraConfigFilter(session);
        cameraConfigFilter.setStereoCameraUsage(java.util.EnumSet.of(CameraConfig.StereoCameraUsage.REQUIRE_AND_USE));
        List<CameraConfig> cameraConfigs = session.getSupportedCameraConfigs(cameraConfigFilter);
        if (!cameraConfigs.isEmpty()) {
            session.setCameraConfig(cameraConfigs.get(0));
        } else {
//      new AlertDialog.Builder(this).setMessage("no stereo").show();
        }
        session.configure(config);
    }
}
