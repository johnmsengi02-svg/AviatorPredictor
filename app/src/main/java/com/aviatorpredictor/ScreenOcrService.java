package com.aviatorpredictor;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.hardware.display.*;
import android.media.*;
import android.media.projection.*;
import android.os.*;
import android.util.DisplayMetrics;
import android.view.*;
import android.widget.*;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.*;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.nio.ByteBuffer;
import java.util.regex.*;

public class ScreenOcrService extends Service {
    private WindowManager wm;
    private LinearLayout overlay;
    private TextView txtSignal, txtStatus;
    private MediaProjection mp;
    private VirtualDisplay vd;
    private ImageReader ir;
    private TextRecognizer tr;
    private Handler handler;
    private Runnable runner;
    private int w, h, dpi;
    private boolean active = false;

    @Override
    public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel("ocr", "OCR", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
            startForeground(101, new Notification.Builder(this, "ocr").setContentTitle("Aviator OCR").setSmallIcon(android.R.drawable.ic_menu_view).build());
        } else {
            startForeground(101, new Notification.Builder(this).setContentTitle("Aviator OCR").setSmallIcon(android.R.drawable.ic_menu_view).build());
        }
        tr = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        handler = new Handler(Looper.getMainLooper());
        setupOverlay();
    }

    @Override
    public int onStartCommand(Intent i, int f, int id) {
        if (i != null && i.hasExtra("RESULT_CODE")) {
            MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            Intent data = i.getParcelableExtra("DATA");
            if (mpm != null && data != null) {
                mp = mpm.getMediaProjection(i.getIntExtra("RESULT_CODE", Activity.RESULT_CANCELED), data);
                startCapture();
            }
        }
        return START_NOT_STICKY;
    }

    private void setupOverlay() {
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        overlay = new LinearLayout(this);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setBackgroundColor(Color.argb(235, 18, 22, 35));
        overlay.setPadding(24, 18, 24, 18);

        TextView head = new TextView(this);
        head.setText("AVIATOR LIVE OCR");
        head.setTextColor(Color.rgb(0, 230, 150));
        head.setTextSize(13);
        head.setTypeface(null, Typeface.BOLD);
        overlay.addView(head);

        txtStatus = new TextView(this);
        txtStatus.setText("Status: Waiting...");
        txtStatus.setTextColor(Color.LTGRAY);
        txtStatus.setTextSize(12);
        overlay.addView(txtStatus);

        txtSignal = new TextView(this);
        txtSignal.setText(SignalEngine.getSignalAnalysis());
        txtSignal.setTextColor(Color.WHITE);
        txtSignal.setTextSize(12);
        txtSignal.setPadding(0, 6, 0, 8);
        overlay.addView(txtSignal);

        Button btn = new Button(this);
        btn.setText("STOP OCR");
        btn.setTextSize(11);
        btn.setOnClickListener(v -> stopSelf());
        overlay.addView(btn);

        int type = Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(-2, -2, type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.BOTTOM | Gravity.START;
        p.x = 30;
        p.y = 120;

        overlay.setOnTouchListener(new View.OnTouchListener() {
            int ix, iy; float tx, ty;
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_DOWN) { ix = p.x; iy = p.y; tx = e.getRawX(); ty = e.getRawY(); return true; }
                if (e.getAction() == MotionEvent.ACTION_MOVE) { p.x = ix + (int)(e.getRawX() - tx); p.y = iy - (int)(e.getRawY() - ty); wm.updateViewLayout(overlay, p); return true; }
                return false;
            }
        });
        wm.addView(overlay, p);
    }

    private void startCapture() {
        DisplayMetrics m = getResources().getDisplayMetrics();
        w = m.widthPixels; h = m.heightPixels; dpi = m.densityDpi;
        ir = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2);
        vd = mp.createVirtualDisplay("OcrCap", w, h, dpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, ir.getSurface(), null, null);
        active = true;
        runner = () -> { if (active) { scan(); handler.postDelayed(runner, 1200); } };
        handler.post(runner);
    }

    private void scan() {
        Image img = null;
        try {
            img = ir.acquireLatestImage();
            if (img == null) return;
            Image.Plane[] pl = img.getPlanes();
            ByteBuffer buf = pl[0].getBuffer();
            int ps = pl[0].getPixelStride(), rs = pl[0].getRowStride();
            Bitmap bmp = Bitmap.createBitmap(w + (rs - ps * w) / ps, h, Bitmap.Config.ARGB_8888);
            bmp.copyPixelsFromBuffer(buf);
            img.close(); img = null;

            int ch = (int)(h * 0.75);
            Bitmap crop = Bitmap.createBitmap(bmp, 0, 0, w, ch);
            bmp.recycle();
            Bitmap scaled = Bitmap.createScaledBitmap(crop, (int)(w * 1.5), (int)(ch * 1.5), true);
            crop.recycle();

            tr.process(InputImage.fromBitmap(scaled, 0)).addOnSuccessListener(vt -> {
                parse(vt.getText());
                scaled.recycle();
            }).addOnFailureListener(e -> scaled.recycle());
        } catch (Exception e) { if (img != null) img.close(); }
    }

    private void parse(String raw) {
        if (raw == null || raw.trim().isEmpty()) { txtStatus.setText("Screen: Blank"); return; }
        Matcher m = Pattern.compile("(\\d+\\.\\d{1,2})\\s*[xX]").matcher(raw);
        while (m.find()) {
            try {
                double val = Double.parseDouble(m.group(1));
                if (val >= 1.0 && val <= 5000.0) {
                    SignalEngine.addResult(val);
                    txtStatus.setText("Last Read: " + String.format("%.2fx", val));
                    txtSignal.setText(SignalEngine.getSignalAnalysis());
                    return;
                }
            } catch (Exception ignored) {}
        }
        txtStatus.setText("Scanning... (No 'x')");
    }

    @Override
    public void onDestroy() {
        active = false;
        if (handler != null && runner != null) handler.removeCallbacks(runner);
        if (vd != null) vd.release();
        if (ir != null) ir.close();
        if (mp != null) mp.stop();
        if (overlay != null && wm != null) wm.removeView(overlay);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent i) { return null; }
}
