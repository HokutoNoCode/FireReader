package it.firereader.home;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.*;
import android.graphics.pdf.PdfRenderer;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.File;
import java.util.*;

/**
 * Built-in PDF reader with automatic margin cropping.
 * Print PDFs have wide white margins: we find where the text really is
 * (separately for odd and even pages, since books mirror their margins)
 * and scale that area to fill the screen width. The file is never modified.
 */
public class Reader extends Activity {
    static final int PAPER = 0xFFFFFFFF;
    SharedPreferences prefs;
    String path;
    ParcelFileDescriptor pfd;
    PdfRenderer pdf;
    final Object lock = new Object();
    int count, page;
    boolean autoCrop = true;
    RectF[] parityBox = new RectF[2];          // normalized 0..1 content boxes
    final Map<Integer, Bitmap> cache = new HashMap<>();
    ImageView view;
    LinearLayout overlay;
    TextView pageInfo, cropBtn;
    SeekBar seek;
    TextView footer;
    Handler ui = new Handler(Looper.getMainLooper());
    int sw, sh;
    volatile int renderGen = 0;
    GestureDetector gestures;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("k", 0);
        path = getIntent().getStringExtra("path");
        if (path == null && getIntent().getData() != null) path = getIntent().getData().getPath();
        if (path == null) { finish(); return; }
        autoCrop = prefs.getBoolean("crop:" + path, true);
        try {
            pfd = ParcelFileDescriptor.open(new File(path), ParcelFileDescriptor.MODE_READ_ONLY);
            pdf = new PdfRenderer(pfd);
            count = pdf.getPageCount();
        } catch (Exception e) { Toast.makeText(this, "Impossibile aprire il PDF", Toast.LENGTH_LONG).show(); finish(); return; }
        page = Math.max(0, Math.min(count - 1, prefs.getInt("page:" + path, 0)));
        prefs.edit().putInt("pages:" + path, count).putLong("t:" + path, System.currentTimeMillis()).apply();

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(PAPER);
        view = new ImageView(this);
        view.setScaleType(ImageView.ScaleType.FIT_START);
        root.addView(view, new FrameLayout.LayoutParams(-1, -1));

        footer = new TextView(this);
        footer.setTextSize(11); footer.setTextColor(0xFF8A8A8A);
        footer.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams fl = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        fl.bottomMargin = dp(4);
        root.addView(footer, fl);

        overlay = buildOverlay();
        overlay.setVisibility(View.GONE);
        root.addView(overlay, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        setContentView(root);

        Tap tap = new Tap(); tap.r = this;
        gestures = new GestureDetector(this, tap);
        view.setOnTouchListener((v, ev) -> gestures.onTouchEvent(ev));

        loadBoxes();
    }

    int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    @Override public void onWindowFocusChanged(boolean f) {
        super.onWindowFocusChanged(f);
        if (f) getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        renderGen++;
        synchronized (lock) { try { if (pdf != null) pdf.close(); if (pfd != null) pfd.close(); } catch (Exception e) {} pdf = null; }
    }

    @Override public boolean onKeyDown(int k, KeyEvent e) {
        if (k == KeyEvent.KEYCODE_VOLUME_DOWN || k == KeyEvent.KEYCODE_PAGE_DOWN || k == KeyEvent.KEYCODE_DPAD_RIGHT) { go(page + 1); return true; }
        if (k == KeyEvent.KEYCODE_VOLUME_UP || k == KeyEvent.KEYCODE_PAGE_UP || k == KeyEvent.KEYCODE_DPAD_LEFT) { go(page - 1); return true; }
        return super.onKeyDown(k, e);
    }
    @Override public boolean onKeyUp(int k, KeyEvent e) {
        if (k == KeyEvent.KEYCODE_VOLUME_DOWN || k == KeyEvent.KEYCODE_VOLUME_UP) return true;
        return super.onKeyUp(k, e);
    }

    static class Tap extends GestureDetector.SimpleOnGestureListener {
        Reader r;
        @Override public boolean onDown(MotionEvent e) { return true; }
        @Override public boolean onSingleTapUp(MotionEvent e) {
            float x = e.getX() / r.view.getWidth();
            if (r.overlay.getVisibility() == View.VISIBLE) { r.overlay.setVisibility(View.GONE); return true; }
            if (x > 0.66f) r.go(r.page + 1);
            else if (x < 0.33f) r.go(r.page - 1);
            else r.showOverlay();
            return true;
        }
        @Override public boolean onFling(MotionEvent a, MotionEvent b, float vx, float vy) {
            if (a == null || b == null) return false;
            float dx = b.getX() - a.getX();
            if (Math.abs(dx) > r.dp(60) && Math.abs(dx) > Math.abs(b.getY() - a.getY())) { r.go(dx < 0 ? r.page + 1 : r.page - 1); return true; }
            return false;
        }
    }

    // ---------- overlay ----------
    LinearLayout buildOverlay() {
        LinearLayout o = new LinearLayout(this);
        o.setOrientation(LinearLayout.VERTICAL);
        o.setBackgroundColor(0xF2FAFAF7);
        o.setPadding(dp(20), dp(14), dp(20), dp(18));
        o.setElevation(dp(8));
        TextView title = new TextView(this);
        title.setText(titleOf(path)); title.setTextSize(17); title.setTextColor(0xFF1A1A1A);
        title.setTypeface(Typeface.SERIF, Typeface.BOLD); title.setMaxLines(1);
        o.addView(title);
        pageInfo = new TextView(this); pageInfo.setTextSize(14); pageInfo.setTextColor(0xFF6E6E6E);
        pageInfo.setPadding(0, dp(4), 0, dp(8));
        o.addView(pageInfo);
        seek = new SeekBar(this);
        seek.setMax(Math.max(0, count - 1));
        Seek sk = new Seek(); sk.r = this; seek.setOnSeekBarChangeListener(sk);
        o.addView(seek);
        LinearLayout row = new LinearLayout(this);
        row.setPadding(0, dp(12), 0, 0);
        TextView back = pill("‹ Libreria"); back.setOnClickListener(v -> finish());
        cropBtn = pill(""); cropBtn.setOnClickListener(v -> {
            autoCrop = !autoCrop; prefs.edit().putBoolean("crop:" + path, autoCrop).apply();
            clearCache(); render(); updateOverlay(); });
        TextView first = pill("Inizio"); first.setOnClickListener(v -> go(0));
        row.addView(back); row.addView(cropBtn); row.addView(first);
        o.addView(row);
        TextView hint = new TextView(this);
        hint.setText("Tocca a destra o premi Volume giù per andare avanti, a sinistra o Volume su per tornare indietro.");
        hint.setTextSize(12); hint.setTextColor(0xFF8A8A8A); hint.setPadding(0, dp(12), 0, 0);
        o.addView(hint);
        return o;
    }
    TextView pill(String s) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(14); t.setTextColor(0xFF1A1A1A);
        t.setPadding(dp(14), dp(8), dp(14), dp(8));
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setColor(0xFFEFEDE8); g.setCornerRadius(dp(18)); t.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2); lp.rightMargin = dp(10); t.setLayoutParams(lp);
        return t;
    }
    static class Seek implements SeekBar.OnSeekBarChangeListener {
        Reader r;
        public void onProgressChanged(SeekBar s, int p, boolean u) { if (u) r.pageInfo.setText("Pagina " + (p + 1) + " di " + r.count); }
        public void onStartTrackingTouch(SeekBar s) {}
        public void onStopTrackingTouch(SeekBar s) { r.go(s.getProgress()); }
    }
    void showOverlay() { updateOverlay(); overlay.setVisibility(View.VISIBLE); }
    void updateOverlay() {
        pageInfo.setText("Pagina " + (page + 1) + " di " + count + " · " + percent() + "%");
        seek.setProgress(page);
        cropBtn.setText(autoCrop ? "Margini: ritagliati" : "Margini: originali");
    }
    int percent() { return count <= 1 ? 100 : Math.round(100f * page / (count - 1)); }

    // ---------- navigation ----------
    void go(int p) {
        if (p < 0 || p >= count) return;
        page = p;
        prefs.edit().putInt("page:" + path, page).putLong("t:" + path, System.currentTimeMillis()).apply();
        render();
        if (overlay.getVisibility() == View.VISIBLE) updateOverlay();
    }

    void clearCache() { synchronized (cache) { cache.clear(); } }

    void render() {
        sw = view.getWidth(); sh = view.getHeight();
        if (sw == 0) { view.post(this::render); return; }
        footer.setText((page + 1) + " / " + count + "   ·   " + percent() + "%");
        Bitmap cached; synchronized (cache) { cached = cache.get(page); }
        if (cached != null) { view.setImageBitmap(cached); prefetch(); return; }
        final int gen = ++renderGen, p = page;
        new Thread(() -> {
            Bitmap bm = draw(p);
            if (bm == null || gen != renderGen) return;
            synchronized (cache) { cache.put(p, bm); }
            ui.post(() -> { if (page == p) view.setImageBitmap(bm); prefetch(); });
        }).start();
    }

    void prefetch() {
        final int[] want = {page + 1, page - 1};
        new Thread(() -> {
            for (int p : want) {
                if (p < 0 || p >= count) continue;
                synchronized (cache) { if (cache.containsKey(p)) continue; }
                Bitmap bm = draw(p);
                if (bm != null) synchronized (cache) {
                    cache.put(p, bm);
                    Iterator<Integer> it = cache.keySet().iterator();
                    while (it.hasNext()) { int k = it.next(); if (Math.abs(k - page) > 2) it.remove(); }
                }
            }
        }).start();
    }

    /** Render page p cropped (if enabled) and scaled to fill the screen width. */
    Bitmap draw(int p) {
        synchronized (lock) {
            if (pdf == null) return null;
            try {
                PdfRenderer.Page pg = pdf.openPage(p);
                float pw = pg.getWidth(), ph = pg.getHeight();
                RectF box = new RectF(0, 0, 1, 1);
                if (autoCrop && p > 0) {
                    RectF pb = parityBox[p % 2];
                    RectF own = scan(pg, 160);
                    if (pb != null) { box = new RectF(pb); if (own != null) box.union(own); }
                    else if (own != null) box = own;
                    float padX = 0.025f, padY = 0.02f;
                    box.set(Math.max(0, box.left - padX), Math.max(0, box.top - padY), Math.min(1, box.right + padX), Math.min(1, box.bottom + padY));
                }
                float cw = box.width() * pw, ch = box.height() * ph;
                float scale = Math.min(sw / cw, sh / ch);
                // prefer filling the width: allow vertical overflow up to 4% before falling back to fit-page
                if (sw / cw * ch <= sh * 1.04f) scale = sw / cw;
                int bw = sw, bh = Math.min(sh, Math.round(ch * scale));
                Bitmap bm = Bitmap.createBitmap(bw, Math.max(1, bh), Bitmap.Config.ARGB_8888);
                bm.eraseColor(Color.WHITE);
                Matrix m = new Matrix();
                float ox = (bw - cw * scale) / 2f;
                m.postTranslate(-box.left * pw, -box.top * ph);
                m.postScale(scale, scale);
                m.postTranslate(ox, 0);
                pg.render(bm, null, m, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                pg.close();
                return bm;
            } catch (Throwable t) { return null; }
        }
    }

    /** Find the non-white area of a page on a small render; returns normalized rect or null if blank. */
    static RectF scan(PdfRenderer.Page pg, int w) {
        int h = Math.max(1, Math.round(w * (float) pg.getHeight() / pg.getWidth()));
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        b.eraseColor(Color.WHITE);
        Matrix m = new Matrix(); m.setScale(w / (float) pg.getWidth(), h / (float) pg.getHeight());
        pg.render(b, null, m, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
        int[] px = new int[w * h]; b.getPixels(px, 0, w, 0, 0, w, h); b.recycle();
        int[] col = new int[w], row = new int[h];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int c = px[y * w + x];
            int lum = (((c >> 16) & 255) * 3 + ((c >> 8) & 255) * 6 + (c & 255)) / 10;
            if (lum < 215) { col[x]++; row[y]++; }
        }
        int x0 = -1, x1 = -1, y0 = -1, y1 = -1;
        for (int x = 0; x < w; x++) if (col[x] >= 1) { if (x0 < 0) x0 = x; x1 = x; }
        for (int y = 0; y < h; y++) if (row[y] >= 1) { if (y0 < 0) y0 = y; y1 = y; }
        if (x0 < 0 || y0 < 0) return null;
        return new RectF(x0 / (float) w, y0 / (float) h, (x1 + 1) / (float) w, (y1 + 1) / (float) h);
    }

    /** Typical text area for odd and even pages, from a sample of pages (cached per file). */
    void loadBoxes() {
        final String key = "box:" + path + ":" + new File(path).length();
        String saved = prefs.getString(key, null);
        if (saved != null) {
            String[] v = saved.split(",");
            if (v.length == 8) {
                for (int i = 0; i < 2; i++) parityBox[i] = new RectF(Float.parseFloat(v[i * 4]), Float.parseFloat(v[i * 4 + 1]), Float.parseFloat(v[i * 4 + 2]), Float.parseFloat(v[i * 4 + 3]));
                render(); return;
            }
        }
        render();   // show something right away
        new Thread(() -> {
            List<List<RectF>> found = new ArrayList<>(); found.add(new ArrayList<>()); found.add(new ArrayList<>());
            int first = Math.min(count - 1, 4), samples = 24;
            for (int i = 0; i < samples && count > 1; i++) {
                int p = first + (int) ((long) (count - 1 - first) * i / Math.max(1, samples - 1));
                if (p <= 0 || p >= count) continue;
                synchronized (lock) {
                    if (pdf == null) return;
                    try { PdfRenderer.Page pg = pdf.openPage(p); RectF r = scan(pg, 160); pg.close();
                          if (r != null && r.width() > 0.2f && r.height() > 0.2f) found.get(p % 2).add(r); } catch (Throwable t) {}
                }
            }
            StringBuilder sb = new StringBuilder();
            for (int par = 0; par < 2; par++) {
                List<RectF> l = found.get(par).isEmpty() ? found.get(1 - par) : found.get(par);
                if (l.isEmpty()) return;
                parityBox[par] = new RectF(q(l, 0, 0.2f), q(l, 1, 0.2f), q(l, 2, 0.8f), q(l, 3, 0.8f));
                RectF r = parityBox[par];
                sb.append(r.left).append(',').append(r.top).append(',').append(r.right).append(',').append(r.bottom).append(par == 0 ? "," : "");
            }
            prefs.edit().putString(key, sb.toString()).apply();
            ui.post(() -> { clearCache(); render(); });
        }).start();
    }
    static float q(List<RectF> l, int side, float frac) {
        float[] a = new float[l.size()];
        for (int i = 0; i < a.length; i++) { RectF r = l.get(i); a[i] = side == 0 ? r.left : side == 1 ? r.top : side == 2 ? r.right : r.bottom; }
        Arrays.sort(a);
        return a[Math.min(a.length - 1, Math.round(frac * (a.length - 1)))];
    }

    static String titleOf(String p) {
        String n = new File(p).getName(); int i = n.lastIndexOf('.'); if (i > 0) n = n.substring(0, i);
        int d = n.indexOf(" - "); return d > 0 ? n.substring(d + 3) : n;
    }
}
