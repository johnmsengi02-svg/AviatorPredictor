
package com.aviatorpredictor;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.*;
import java.util.ArrayList;

public class MainActivity extends Activity {

    ArrayList<Double> history = new ArrayList<>();
    TextView stats, historyView;
    EditText input;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 40, 32, 32);
        layout.setBackgroundColor(Color.rgb(15, 18, 28));

        TextView title = new TextView(this);
        title.setText("AVIATOR ANALYZER");
        title.setTextSize(26);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER);
        layout.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Round History & Statistics");
        subtitle.setTextColor(Color.LTGRAY);
        subtitle.setGravity(Gravity.CENTER);
        layout.addView(subtitle);

        input = new EditText(this);
        input.setHint("Enter multiplier (e.g. 2.45)");
        input.setSingleLine(true);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.GRAY);
        layout.addView(input);

        Button addButton = new Button(this);
        addButton.setText("ADD RESULT");
        layout.addView(addButton);

        stats = new TextView(this);
        stats.setTextColor(Color.WHITE);
        stats.setTextSize(17);
        stats.setPadding(0, 30, 0, 20);
        layout.addView(stats);

        historyView = new TextView(this);
        historyView.setTextColor(Color.rgb(100, 220, 180));
        historyView.setTextSize(16);
        layout.addView(historyView);

        Button clearButton = new Button(this);
        clearButton.setText("CLEAR HISTORY");
        layout.addView(clearButton);

        addButton.setOnClickListener(v -> {
            try {
                double value = Double.parseDouble(
                    input.getText().toString().trim()
                );

                if (value >= 1.0) {
                    history.add(0, value);
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
            history.clear();
            updateDisplay();
        });

        setContentView(layout);
        updateDisplay();
    }

    void updateDisplay() {
        int total = history.size();
        int above2 = 0;
        double sum = 0;

        for (double value : history) {
            sum += value;
            if (value >= 2.0) above2++;
        }

        double average = total == 0 ? 0 : sum / total;

        stats.setText(
            "Rounds recorded: " + total +
            "\nResults >= 2x: " + above2 +
            "\nAverage multiplier: " +
            String.format("%.2fx", average)
        );

        StringBuilder text = new StringBuilder("ROUND HISTORY\n\n");

        for (int i = 0; i < history.size(); i++) {
            text.append(i + 1)
                .append(".  ")
                .append(String.format("%.2fx", history.get(i)))
                .append("\n");
        }

        historyView.setText(text.toString());
    }
}
