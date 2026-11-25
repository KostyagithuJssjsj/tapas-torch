package com.tapas.torch;

import android.app.Activity;
import android.content.ComponentName;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.service.quicksettings.TileService;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private TextView pctLabel;
    private LinearLayout card;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window w = getWindow();
        w.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        if (Build.VERSION.SDK_INT >= 31) {
            w.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.setBlurBehindRadius(95);
            w.setAttributes(lp);
        }

        float d = getResources().getDisplayMetrics().density;

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xA8000000);
        root.setOnClickListener(v -> finishSmooth());

        card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setClickable(true);

        TextView title = new TextView(this);
        title.setText("Фонарик");
        title.setTextColor(Color.WHITE);
        title.setTextSize(20f);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        card.addView(title);

        pctLabel = new TextView(this);
        pctLabel.setTextColor(0xB3FFFFFF);
        pctLabel.setTextSize(15f);
        pctLabel.setPadding(0, (int)(4 * d), 0, (int)(26 * d));
        card.addView(pctLabel);

        IosTorchSliderView slider = new IosTorchSliderView(this);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams((int)(126 * d), (int)(345 * d));
        slider.setLayoutParams(slp);

        int currentHw = TorchController.readHw();
        float startFrac = TorchController.hwToFrac(currentHw);
        // If opened from tile while OFF, turn ON at last saved brightness (just like iPhone!)
        if (currentHw == 0) {
            startFrac = TorchController.getLastFrac(this);
            currentHw = TorchController.fracToHw(startFrac);
            TorchController.writeHw(currentHw);
        }
        slider.setFraction(startFrac);
        updateLabel(startFrac, currentHw);

        slider.setListener((f, hw) -> {
            updateLabel(f, hw);
            TileService.requestListeningState(this, new ComponentName(this, TorchTileService.class));
        });
        card.addView(slider);

        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        );
        root.addView(card, clp);
        setContentView(root);

        // Crisp iOS spring pop-in animation
        card.setScaleX(0.84f);
        card.setScaleY(0.84f);
        card.setAlpha(0f);
        card.animate().scaleX(1f).scaleY(1f).alpha(1f)
            .setDuration(240)
            .setInterpolator(new OvershootInterpolator(0.75f))
            .start();

        TileService.requestListeningState(this, new ComponentName(this, TorchTileService.class));
    }

    private void updateLabel(float frac, int hw) {
        if (hw <= 0) {
            pctLabel.setText("Выкл");
        } else {
            int pct = Math.max(1, Math.min(100, Math.round(frac * 100f)));
            pctLabel.setText(pct + "%");
        }
    }

    private void finishSmooth() {
        card.animate().scaleX(0.90f).scaleY(0.90f).alpha(0f).setDuration(130)
            .withEndAction(() -> {
                finish();
                overridePendingTransition(0, 0);
            }).start();
    }
}
