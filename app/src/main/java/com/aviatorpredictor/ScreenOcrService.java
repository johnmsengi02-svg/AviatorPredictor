package com.aviatorpredictor;

import android.app.*;
import android.content.Context;
import android.content.Intent;
import android.graphics.*;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.*;
import android.util.DisplayMetrics;
import android.view.*;
import android.widget.*;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.nio.ByteBuffer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ScreenOcrService extends Service {

    private WindowManager windowManager;
    private LinearLayout overlayView;
    private TextView overlaySignalText, overlayLastReadText;

    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private TextRecognizer textRecognizer;

    private Handler handler;
    private Runnable scanRunnable;
    private int screenWidth, screenHeight, screenDensity;
    private boolean isScanning = false;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(101, buildNotification());

        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        handler = new Handler(Looper.getMainLooper());

        setupFloatingOverlay();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.hasExtra("RESULT_CODE")) {
            int resultCode = intent.getIntExtra("RESULT_CODE", Activity.RESULT_CANCELED);
            Intent data = intent.getParcelableExtra("DATA");

            MediaProjectionManager projectionManager =
                    (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);

            if (projectionManager != null && data != null) {
                mediaProjection = projectionManager.getMediaProjection(resultCode, data);
                startScreenCapture();
            }
        }
        return START_NOT_STICKY;
    }

    private void setupFloatingOverlay() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        overlayView = new LinearLayout(this);
        overlayView.setOrientation(LinearLayout.VERTICAL);
        overlayView.setBackgroundColor(Color.argb(230, 18, 22, 35));
        overlayView.setPadding(24, 18, 24, 18);

        TextView header = new TextView(this);
        header.setText("AVIATOR LIVE OCR");
        header.setTextColor(Color.rgb(0, 230, 150));
        header.setTextSize(13);
        header.setTypeface(null, Typeface.BOLD);
        overlayView.addView(header);

        overlayLastReadText = new TextView(this);
        overlayLastReadText.setText("Last Read: Scanning...");
        overlayLastReadText.setTextColor(Color.LTGRAY);
        overlayLastReadText.setTextSize(12);
        overlayView.addView(overlayLastReadText);

        overlaySignalText = new TextView(this);
        overlaySignalText.setText(SignalEngine.getSignalAnalysis());
        overlaySignalText.setTextColor(Color.WHITE);
        overlaySignalText.setTextSize(12);
        overlaySignalText.setPadding(0, 6, 0, 8);
        overlayView.addView(overlaySignalText);

        Button closeBtn = new Button(this);
        closeBtn.setText("STOP OCR");
        closeBtn.setTextSize(11);
        closeBtn.setOnClickListener(v -> stopSelf());
        overlayView.addView(closeBtn);

        int layoutFlag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 30;
        params.y = 150;

        // Wezesha kuburuza (drag) kidirisha popote kwenye skrini
        overlayView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(overlayView, params);
                        return true;
                }
                return false;
            }
        });

        windowManager.addView(overlayView, params);
    }

    private void startScreenCapture() {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        screenWidth = metrics.widthPixels;
        screenHeight = metrics.heightPixels;
        screenDensity = metrics.densityDpi;

        imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2);

        virtualDisplay = mediaProjection.createVirtualDisplay(
                "AviatorScreenCapture",
                screenWidth,
                screenHeight,
                screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(),
                null,
                null
        );

        isScanning = true;
        scanRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isScanning) return;
                captureAndProcessFrame();
                handler.postDelayed(this, 1500); // Soma skrini kila sekunde 1.5
            }
        };
        handler.post(scanRunnable);
    }

    private void captureAndProcessFrame() {
        Image image = null;
        try {
            image = imageReader.acquireLatestImage();
            if (image == null) return;

            Image.Plane[] planes = image.getPlanes();
            ByteBuffer buffer = planes[0].getBuffer();
            int pixelStride = planes[0].getPixelStride();
            int rowStride = planes[0].getRowStride();
            int rowPadding = rowStride - pixelStride * screenWidth;

            Bitmap bitmap = Bitmap.createBitmap(
                    screenWidth + rowPadding / pixelStride,
                    screenHeight,
                    Bitmap.Config.ARGB_8888
            );
            bitmap.copyPixelsFromBuffer(buffer);
            image.close();
            image = null;

            // Kata sehemu ya juu ya skrini (ambapo historia ya multipliers hukaa) ili OCR iwe haraka
            Bitmap croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight / 2);
            bitmap.recycle();

            InputImage inputImage = InputImage.fromBitmap(croppedBitmap, 0);
            textRecognizer.process(inputImage)
                    .addOnSuccessListener(visionText -> {
                        extractMultiplier(visionText.getText());
                        croppedBitmap.recycle();
                    })
                    .addOnFailureListener(e -> croppedBitmap.recycle());

        } catch (Exception e) {
            if (image != null) image.close();
        }
    }

    private void extractMultiplier(String rawText) {
        // Tafuta muundo wa namba zenye 'x' kama 1.45x, 12.30x, 2.00x
        Pattern pattern = Pattern.compile("\\b(\\d+\\.\\d{1,2})[xX]\\b");
        Matcher matcher = pattern.matcher(rawText);

        if (matcher.find()) {
            try {
                double detectedValue = Double.parseDouble(matcher.group(1));
                SignalEngine.addResult(detectedValue);

                overlayLastReadText.setText("Last Read: " + String.format("%.2fx", detectedValue));
                overlaySignalText.setText(SignalEngine.getSignalAnalysis());
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "ocr_channel",
                    "Aviator Live OCR Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, "ocr_channel")
                : new Notification.Builder(this);

        return builder.setContentTitle("Aviator Predictor OCR Active")
                .setContentText("Reading multipliers from screen...")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .build();
    }

    @Override
    public void onDestroy() {
        isScanning = false;
        if (handler != null && scanRunnable != null) handler.removeCallbacks(scanRunnable);
