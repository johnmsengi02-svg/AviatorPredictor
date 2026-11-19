package com.aviatorpredictor;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.*;

public class MainActivity extends Activity {

    private static final int REQUEST_OVERLAY_PERMISSION = 1001;
    private static final int REQUEST_SCREEN_CAPTURE = 1002;

    TextView signalBox, stats, historyView;
    EditText input;
    MediaProjectionManager projectionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(Color.rgb(15, 18, 28));

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(36, 90, 36, 40);

        TextView title = new TextView(this);
        title.setText("AVIATOR PREDICTOR PRO");
        title.setTextSize(24);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER);
        layout.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Live OCR Screen Reader & Signal Engine");
        subtitle.setTextColor(Color.LTGRAY);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, 0, 0, 24);
        layout.addView(subtitle);

        Button liveScanBtn = new Button(this);
        liveScanBtn.setText("START LIVE SCREEN OCR");
        liveScanBtn.setBackgroundColor(Color.rgb(0, 180, 110));
        liveScanBtn.setTextColor(Color.WHITE);
        liveScanBtn.setTypeface(null, Typeface.BOLD);
        layout.addView(liveScanBtn);

        signalBox = new TextView(this);
        signalBox.setTextColor(Color.rgb(255, 215, 0));
        signalBox.setBackgroundColor(Color.rgb(26, 32, 48));
        signalBox.setTextSize(15);
        signalBox.setTypeface(null, Typeface.BOLD);
        signalBox.setPadding(24, 24, 24, 24);
        LinearLayout.LayoutParams signalParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        signalParams.setMargins(0, 24, 0, 24);
        signalBox.setLayoutParams(signalParams);
        layout.addView(signalBox);

        input = new EditText(this);
        input.setHint("Manual input (e.g. 2.45)");
        input.setSingleLine(true);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.GRAY);
        layout.addView(input);

        Button addButton = new Button(this);
        addButton.setText("ADD MANUAL RESULT");
        layout.addView(addButton);

        stats = new TextView(this);
        stats.setTextColor(Color.WHITE);
        stats.setTextSize(16);
        stats.setPadding(0, 24, 0, 16);
        layout.addView(stats);

        Button clearButton = new Button(this);
        clearButton.setText("CLEAR HISTORY");
        layout.addView(clearButton);

        historyView = new TextView(this);
        historyView.setTextColor(Color.rgb(100, 220, 180));
        historyView.setTextSize(15);
        historyView.setPadding(0, 24, 0, 20);
        layout.addView(historyView);

        liveScanBtn.setOnClickListener(v -> initiateLiveOcr());

        addButton.setOnClickListener(v -> {
            try {
                double value = Double.parseDouble(input.getText().toString().trim());
                if (value >= 1.0) {
                    SignalEngine.addResult(value);
                    input.setText("");
                    updateDisplay();
                } else {
                    input.setError("Multiplier must be 1.00 or higher");
                }
            } catch (Exception e) {
                input.setError("Enter a valid number");
            }
        });

        clearButton.setOnClickListener(v -> {
            SignalEngine.clear();
            updateDisplay();
        });

        scrollView.addView(layout);
        setContentView(scrollView);
        updateDisplay();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateDisplay();
    }

    private void initiateLiveOcr() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Enable 'Display over other apps' first", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION);
            return;
        }

        if (projectionManager != null) {
            startActivityForResult(projectionManager.createScreenCaptureIntent(), REQUEST_SCREEN_CAPTURE);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_OVERLAY_PERMISSION) {
            if (Settings.canDrawOverlays(this)) {
                initiateLiveOcr();
            } else {
                Toast.makeText(this, "Overlay permission is required!", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQUEST_SCREEN_CAPTURE) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                Intent serviceIntent = new Intent(this, ScreenOcrService.class);
                serviceIntent.putExtra("RESULT_CODE", resultCode);
                serviceIntent.putExtra("DATA", data);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent);
                } else {
                    startService(serviceIntent);
                }
                Toast.makeText(this, "Live OCR & Floating Overlay Started!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Screen capture permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    void updateDisplay() {
        signalBox.setText(SignalEngine.getSignalAnalysis());
        stats.setText(SignalEngine.getStatsSummary());

        StringBuilder text = new StringBuilder("ROUND HISTORY (LIVE + MANUAL)\n\n");
        for (int i = 0; i < SignalEngine.history.size(); i++) {
            text.append(i + 1)
                .append(".  ")
                .append(String.format("%.2fx", SignalEngine.history.get(i)))
                .append("\n");
        }
        historyView.setText(text.toString());
    }
}
