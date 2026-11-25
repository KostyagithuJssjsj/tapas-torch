package com.tapas.torch;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public class TorchTileService extends TileService {
    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTile();
    }

    @Override
    public void onClick() {
        super.onClick();
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        if (Build.VERSION.SDK_INT >= 34) {
            PendingIntent pi = PendingIntent.getActivity(
                this, 0, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            startActivityAndCollapse(pi);
        } else {
            startActivityAndCollapse(i);
        }
    }

    private void updateTile() {
        Tile t = getQsTile();
        if (t == null) return;
        int hw = TorchController.readHw();
        if (hw > 0) {
            int pct = Math.max(1, Math.min(100, Math.round(TorchController.hwToFrac(hw) * 100f)));
            t.setState(Tile.STATE_ACTIVE);
            if (Build.VERSION.SDK_INT >= 29) t.setSubtitle(pct + "%");
        } else {
            t.setState(Tile.STATE_INACTIVE);
            if (Build.VERSION.SDK_INT >= 29) t.setSubtitle("Выкл");
        }
        t.updateTile();
    }
}
