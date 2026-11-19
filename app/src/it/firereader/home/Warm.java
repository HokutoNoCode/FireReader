package it.firereader.home;

import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.IBinder;
import android.view.*;

public class Warm extends Service {
    View v;
    @Override public IBinder onBind(Intent i) { return null; }
    @Override public int onStartCommand(Intent i, int f, int id) {
        int level = i != null ? i.getIntExtra("level", 40) : 40;
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (v == null) {
            v = new View(this);
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(-1, -1,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
            try { wm.addView(v, lp); } catch (Exception e) { stopSelf(); return START_NOT_STICKY; }
        }
        int a = Math.max(0, Math.min(200, level * 2));
        v.setBackgroundColor((a << 24) | 0x00FF8A1E);
        return START_STICKY;
    }
    @Override public void onDestroy() {
        if (v != null) try { ((WindowManager) getSystemService(WINDOW_SERVICE)).removeView(v); } catch (Exception e) {}
        super.onDestroy();
    }
}
