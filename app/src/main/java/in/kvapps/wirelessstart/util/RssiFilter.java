package in.kvapps.wirelessstart.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import in.kvapps.wirelessstart.enums.SignalState;

public class RssiFilter {
    private static final int MAX_HISTORY_SIZE = 7;
    private final List<Integer> rssiHistory = new ArrayList<>();
    private float smoothedValue = -100f;
    private int lastWidth = -1;

    public void reset() {
        rssiHistory.clear();
        smoothedValue = -100f;
        lastWidth = -1;
    }

    public int filterAndSmooth(int rawRssi, int parentWidth) {
        // Filter out invalid/empty RSSI packets
        if (rawRssi >= 0 || rawRssi == -999) {
            return lastWidth != -1 ? lastWidth : 0;
        }

        // 1. Sliding window history (Median Filter for outlier rejection)
        rssiHistory.add(rawRssi);
        if (rssiHistory.size() > MAX_HISTORY_SIZE) {
            rssiHistory.remove(0);
        }

        List<Integer> sortedList = new ArrayList<>(rssiHistory);
        Collections.sort(sortedList);

        int medianRssi;
        int size = sortedList.size();
        if (size % 2 == 0) {
            medianRssi = (sortedList.get((size / 2) - 1) + sortedList.get(size / 2)) / 2;
        } else {
            medianRssi = sortedList.get(size / 2);
        }

        // 2. Clamping bounds
        int minRssi = -100;
        int maxRssi = SignalState.SUPER_STRONG.getRssiLimit(); // Or your SignalState.SUPER_STRONG.getRssiLimit()

        if (maxRssi <= minRssi) return 0;

        int clampedRssi = Math.max(minRssi, Math.min(medianRssi, maxRssi));
        float normalizedPercent = (float) (clampedRssi - minRssi) / (maxRssi - minRssi);

        // 3. Perceptual curve scaling
        normalizedPercent = (float) Math.pow(normalizedPercent, 1.5);
        int targetWidth = (int) (parentWidth * normalizedPercent);

        // 4. Exponential Moving Average (EMA) smoothing
        if (smoothedValue == -100f) {
            smoothedValue = targetWidth;
        } else {
            float smoothingFactor = 0.15f;
            smoothedValue = smoothedValue + smoothingFactor * (targetWidth - smoothedValue);
        }

        // Deadband check: Ignore tiny width variations (< 4 pixels) to avoid layout jiggling
        int finalWidth = (int) smoothedValue;
        if (lastWidth != -1 && Math.abs(finalWidth - lastWidth) < 4) {
            return lastWidth;
        }

        lastWidth = finalWidth;
        return finalWidth;
    }
}