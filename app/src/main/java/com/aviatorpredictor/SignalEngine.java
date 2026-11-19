package com.aviatorpredictor;

import java.util.ArrayList;
import java.util.List;

public class SignalEngine {

    public static final List<Double> history = new ArrayList<>();

    public static void addResult(double value) {
        if (value >= 1.0 && value <= 10000.0) {
            // Epuka kuingiza namba ile ile mara mbili mfululizo ndani ya sekunde chache
            if (!history.isEmpty() && Math.abs(history.get(0) - value) < 0.001) {
                return;
            }
            history.add(0, value);
            if (history.size() > 100) {
                history.remove(history.size() - 1);
            }
        }
    }

    public static void clear() {
        history.clear();
    }

    public static String getSignalAnalysis() {
        int total = history.size();
        if (total < 3) {
            return "STATUS: COLLECTING DATA (" + total + "/3)\n" +
                   "SIGNAL: WAIT FOR MORE ROUNDS";
        }

        // Angalia mizunguko 5 ya mwisho
        int sampleSize = Math.min(total, 5);
        int lowStreak = 0;
        double recentSum = 0;

        for (int i = 0; i < sampleSize; i++) {
            double val = history.get(i);
            recentSum += val;
            if (val < 1.50) {
                lowStreak++;
            }
        }

        double recentAvg = recentSum / sampleSize;

        // Uchambuzi wa mwenendo (Trend Logic)
        if (lowStreak >= 4) {
            return "SIGNAL: STRONG ENTRY (Target: 2.00x)\n" +
                   "CONFIDENCE: HIGH (4+ Low Rounds Detected)";
        } else if (lowStreak == 3 || recentAvg < 1.65) {
            return "SIGNAL: MODERATE ENTRY (Target: 1.50x)\n" +
                   "CONFIDENCE: MEDIUM (Recovery Zone)";
        } else if (history.get(0) >= 10.0) {
            return "SIGNAL: WAIT / COOL DOWN\n" +
                   "REASON: High Spike (" + String.format("%.2fx", history.get(0)) + ") Just Hit";
        } else {
            return "SIGNAL: SAFE SCALP (Target: 1.30x - 1.50x)\n" +
                   "CONFIDENCE: NORMAL (Recent Avg: " + String.format("%.2fx", recentAvg) + ")";
        }
    }

    public static String getStatsSummary() {
        int total = history.size();
        int above2 = 0;
        double sum = 0;

        for (double value : history) {
            sum += value;
            if (value >= 2.0) above2++;
        }

        double average = total == 0 ? 0 : sum / total;

        return "Rounds recorded: " + total +
               "\nResults >= 2x: " + above2 +
               "\nAverage multiplier: " + String.format("%.2fx", average);
    }
}
