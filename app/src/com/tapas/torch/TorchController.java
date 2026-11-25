package com.tapas.torch;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public final class TorchController {
    public static final String LED_PATH = "/sys/class/leds/led:torch/brightness";
    private static final String PREFS = "tapas_torch_prefs";
    private static final String KEY_LAST_FRAC = "last_frac";

    private static Process suProc;
    private static OutputStream suOut;
    private static int lastWrittenHw = -1;

    public static int fracToHw(float frac) {
        if (frac <= 0.005f) return 0;
        if (frac >= 0.995f) return 255;
        // Perceptual gamma curve so 1..255 feels completely linear to human eyes like iOS
        double curved = Math.pow(frac, 2.05);
        int hw = (int) Math.round(2.0 + curved * 253.0);
        return Math.max(2, Math.min(255, hw));
    }

    public static float hwToFrac(int hw) {
        if (hw <= 0) return 0f;
        if (hw >= 255) return 1f;
        double norm = Math.max(0.0, (hw - 2.0) / 253.0);
        return (float) Math.pow(norm, 1.0 / 2.05);
    }

    public static int readHw() {
        try (BufferedReader br = new BufferedReader(new FileReader(LED_PATH))) {
            String line = br.readLine();
            if (line != null) return Integer.parseInt(line.trim());
        } catch (Exception ignored) {}
        return Math.max(0, lastWrittenHw);
    }

    public static synchronized void writeHw(int hw) {
        hw = Math.max(0, Math.min(255, hw));
        if (hw == lastWrittenHw) return;
        lastWrittenHw = hw;
        byte[] data = (hw + "\n").getBytes(StandardCharsets.UTF_8);
        try (FileOutputStream fos = new FileOutputStream(LED_PATH)) {
            fos.write(data);
            fos.flush();
            return;
        } catch (Exception ignored) {}
        // Fallback persistent su shell if direct sysfs write is blocked
        try {
            if (suProc == null || !suProc.isAlive()) {
                suProc = Runtime.getRuntime().exec("su");
                suOut = suProc.getOutputStream();
            }
            suOut.write(("echo " + hw + " > " + LED_PATH + "\n").getBytes(StandardCharsets.UTF_8));
            suOut.flush();
        } catch (Exception ignored) {}
    }

    public static void saveLastFrac(Context ctx, float frac) {
        if (frac > 0.02f) {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putFloat(KEY_LAST_FRAC, frac).apply();
        }
    }

    public static float getLastFrac(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getFloat(KEY_LAST_FRAC, 0.55f);
    }
}
