package it.firereader.home;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.*;
import android.provider.Settings;
import android.text.*;
import android.util.TypedValue;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.*;

public class Home extends Activity {
    static final int INK = 0xFF1A1A1A, GREY = 0xFF6E6E6E, LINE = 0xFFDDDDDD, PAPER = 0xFFFAFAF7, CHIP = 0xFFEFEDE8;
    static final String[] EXT = {".pdf", ".epub", ".cbr", ".cbz", ".mobi", ".azw3", ".fb2", ".djvu", ".txt", ".doc", ".docx", ".rtf"};
    SharedPreferences prefs;
    LinearLayout root, content;
    TextView clock, battery;
    Handler ui = new Handler(Looper.getMainLooper());
    static final Map<String, Bitmap> covers = new HashMap<>();
    String tab = "home", query = "", sort = "recent";
    volatile ServerSocket server;

    int dp(float v) { return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()); }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("k", 0);
        sort = prefs.getString("sort", "recent");
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(PAPER);
        setContentView(root);
        if (prefs.getBoolean("warm", false)) startWarm(prefs.getInt("warmLevel", 40));
    }

    @Override protected void onResume() { super.onResume(); hideBars(); build(); }
    @Override public void onWindowFocusChanged(boolean f) { super.onWindowFocusChanged(f); if (f) hideBars(); }
    @Override public void onBackPressed() { if (!tab.equals("home") || query.length() > 0) { tab = "home"; query = ""; build(); } }

    void hideBars() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    TextView text(String s, float sp, int color, boolean serif, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(sp); t.setTextColor(color);
        t.setTypeface(serif ? Typeface.SERIF : Typeface.SANS_SERIF, bold ? Typeface.BOLD : Typeface.NORMAL);
        return t;
    }
    View rule() { View v = new View(this); v.setBackgroundColor(LINE); v.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(1))); return v; }
    GradientDrawable round(int color, float r) { GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(r)); return g; }

    // ---------------- layout ----------------
    void build() {
        root.removeAllViews();
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(20), dp(12), dp(20), dp(8));
        top.addView(text("FireReader", 15, GREY, false, false), new LinearLayout.LayoutParams(0, -2, 1));
        battery = text("", 14, GREY, false, false);
        clock = text("", 14, INK, false, true); clock.setPadding(dp(16), 0, 0, 0);
        top.addView(battery); top.addView(clock);
        root.addView(top);
        root.addView(searchBar());
        root.addView(rule());
        updateStatus();

        ScrollView sv = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(16), dp(20), dp(24));
        sv.addView(content);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(rule());
        root.addView(navBar());

        List<File> books = scan();
        if (query.length() > 0) buildSearch(books);
        else if (tab.equals("home")) buildHome(books);
        else if (tab.equals("lib")) buildLibrary(books);
        else buildComfort();
    }

    View searchBar() {
        FrameLayout wrap = new FrameLayout(this);
        wrap.setPadding(dp(20), dp(2), dp(20), dp(12));
        final EditText e = new EditText(this);
        e.setHint("Cerca titolo o autore");
        e.setHintTextColor(GREY); e.setTextColor(INK); e.setTextSize(16);
        e.setSingleLine(true);
        e.setBackground(round(CHIP, 22));
        e.setPadding(dp(18), dp(10), dp(18), dp(10));
        e.setText(query);
        e.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        e.setOnEditorActionListener((v, a, ev) -> { query = e.getText().toString().trim(); hideKb(e); build(); return true; });
        TW tw = new TW(); tw.h = this; e.addTextChangedListener(tw);
        wrap.addView(e, new FrameLayout.LayoutParams(-1, -2));
        return wrap;
    }
    void hideKb(View v) { ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(v.getWindowToken(), 0); }

    LinearLayout navBar() {
        LinearLayout nav = new LinearLayout(this);
        nav.setPadding(0, dp(4), 0, dp(8));
        nav.addView(navItem("Home", "home"), new LinearLayout.LayoutParams(0, -2, 1));
        nav.addView(navItem("I miei libri", "lib"), new LinearLayout.LayoutParams(0, -2, 1));
        nav.addView(navItem("Comfort", "comfort"), new LinearLayout.LayoutParams(0, -2, 1));
        return nav;
    }
    View navItem(String label, final String id) {
        boolean on = id.equals(tab) && query.isEmpty();
        TextView t = text(label, 16, on ? INK : GREY, false, on);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(12), 0, dp(12));
        t.setOnClickListener(v -> { tab = id; query = ""; build(); });
        return t;
    }

    // ---------------- pages ----------------
    void buildHome(List<File> books) {
        List<File> recent = new ArrayList<>();
        for (File f : books) if (prefs.getLong("t:" + f.getPath(), 0) > 0) recent.add(f);
        File hero = !recent.isEmpty() ? recent.get(0) : (books.isEmpty() ? null : books.get(0));
        boolean started = !recent.isEmpty();
        content.addView(sectionTitle(started ? "Continua a leggere" : "Inizia a leggere"));
        if (hero == null) {
            TextView e = text("Nessun libro ancora.\nVai su Comfort → Ricevi libri via Wi-Fi per aggiungerne dal PC o dal telefono.", 16, GREY, true, false);
            e.setPadding(0, dp(16), 0, dp(24)); content.addView(e); return;
        }
        content.addView(heroRow(hero, started));
        if (recent.size() > 1) {
            content.addView(rule());
            content.addView(header("Letti di recente", null));
            HorizontalScrollView hs = new HorizontalScrollView(this);
            hs.setHorizontalScrollBarEnabled(false);
            LinearLayout row = new LinearLayout(this);
            int w = dp(104), h = dp(152);
            for (int i = 1; i < Math.min(recent.size(), 10); i++) {
                LinearLayout cell = cell(recent.get(i), w, h, true);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, -2);
                lp.rightMargin = dp(14);
                row.addView(cell, lp);
            }
            hs.addView(row);
            hs.setPadding(0, dp(4), 0, dp(18));
            content.addView(hs);
        }
        content.addView(rule());
        content.addView(header("I miei libri", "Vedi tutti (" + books.size() + ")  ›"));
        List<File> shelf = new ArrayList<>();
        for (File f : sorted(books, "title")) if (!recent.contains(f)) shelf.add(f);
        if (shelf.isEmpty()) shelf = sorted(books, "title");
        content.addView(grid(shelf.subList(0, Math.min(6, shelf.size())), 3));
    }

    View heroRow(final File f, boolean started) {
        LinearLayout row = new LinearLayout(this);
        row.setPadding(0, dp(8), 0, dp(22));
        ImageView cover = coverView(f, dp(140), dp(205));
        cover.setOnClickListener(v -> open(f));
        row.addView(cover);
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(18), dp(2), 0, 0);
        String[] ta = titleAuthor(f);
        info.addView(text(ta[0], 22, INK, true, true));
        if (ta[1].length() > 0) { TextView a = text(ta[1], 16, GREY, true, false); a.setPadding(0, dp(6), 0, 0); info.addView(a); }
        String when = started ? "Aperto " + ago(prefs.getLong("t:" + f.getPath(), 0)) : kind(f);
        TextView k = text(when, 13, GREY, false, false); k.setPadding(0, dp(10), 0, dp(18)); info.addView(k);
        TextView btn = text(started ? "Continua" : "Leggi", 16, PAPER, false, true);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(dp(26), dp(12), dp(26), dp(12));
        btn.setBackground(round(INK, 24));
        btn.setOnClickListener(v -> open(f));
        info.addView(btn, new LinearLayout.LayoutParams(-2, -2));
        row.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
        return row;
    }

    void buildLibrary(List<File> books) {
        content.addView(sectionTitle("I miei libri"));
        TextView c = text(books.size() + " titoli", 14, GREY, false, false);
        c.setPadding(0, dp(2), 0, dp(10)); content.addView(c);
        LinearLayout chips = new LinearLayout(this);
        chips.setPadding(0, 0, 0, dp(8));
        chips.addView(chip("Recenti", "recent")); chips.addView(chip("Titolo", "title")); chips.addView(chip("Autore", "author"));
        content.addView(chips);
        content.addView(grid(sorted(books, sort), 3));
    }

    void buildSearch(List<File> books) {
        String q = query.toLowerCase(Locale.ITALY);
        List<File> res = new ArrayList<>();
        for (File f : books) { String[] ta = titleAuthor(f); if ((ta[0] + " " + ta[1] + " " + f.getName()).toLowerCase(Locale.ITALY).contains(q)) res.add(f); }
        content.addView(sectionTitle("Risultati per “" + query + "”"));
        TextView c = text(res.isEmpty() ? "Nessun libro trovato" : res.size() + " titoli", 14, GREY, false, false);
        c.setPadding(0, dp(2), 0, dp(10)); content.addView(c);
        content.addView(grid(res, 3));
    }

    View chip(String label, final String id) {
        boolean on = id.equals(sort);
        TextView t = text(label, 14, on ? PAPER : INK, false, on);
        t.setPadding(dp(16), dp(8), dp(16), dp(8));
        t.setBackground(round(on ? INK : CHIP, 18));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2); lp.rightMargin = dp(10); t.setLayoutParams(lp);
        t.setOnClickListener(v -> { sort = id; prefs.edit().putString("sort", id).apply(); build(); });
        return t;
    }

    // ---------------- comfort ----------------
    void buildComfort() {
        content.addView(sectionTitle("Comfort di lettura"));
        TextView sub = text("Regola lo schermo per leggere bene, di giorno e di sera.", 14, GREY, false, false);
        sub.setPadding(0, dp(4), 0, dp(14)); content.addView(sub);

        // brightness
        content.addView(label("Luminosità"));
        SeekBar br = new SeekBar(this);
        br.setMax(255);
        try { br.setProgress(Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS)); } catch (Exception e) {}
        SB sb1 = new SB(); sb1.h = this; sb1.mode = 0; br.setOnSeekBarChangeListener(sb1);
        content.addView(br, padded(-1, -2, 0, dp(6), 0, dp(16)));

        // warm light
        final boolean warmOn = prefs.getBoolean("warm", false);
        content.addView(toggleRow("Luce calda", "Toni caldi per leggere la sera senza affaticare gli occhi", warmOn, on -> {
            prefs.edit().putBoolean("warm", on).apply();
            if (on) startWarm(prefs.getInt("warmLevel", 40)); else stopService(new Intent(this, Warm.class));
            build();
        }));
        if (warmOn) {
            SeekBar wl = new SeekBar(this);
            wl.setMax(100); wl.setProgress(prefs.getInt("warmLevel", 40));
            SB sb2 = new SB(); sb2.h = this; sb2.mode = 1; wl.setOnSeekBarChangeListener(sb2);
            content.addView(wl, padded(-1, -2, 0, 0, 0, dp(10)));
        }

        // grayscale
        boolean gray = Settings.Secure.getInt(getContentResolver(), "accessibility_display_daltonizer_enabled", 0) == 1;
        content.addView(toggleRow("Bianco e nero", "Schermo in scala di grigi, effetto carta", gray, on -> {
            try {
                Settings.Secure.putInt(getContentResolver(), "accessibility_display_daltonizer", 0);
                Settings.Secure.putInt(getContentResolver(), "accessibility_display_daltonizer_enabled", on ? 1 : 0);
            } catch (Exception e) { toast("Permesso mancante"); }
            build();
        }));

        // screen timeout
        int to = Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT, 600000);
        content.addView(label("Spegnimento schermo"));
        LinearLayout tos = new LinearLayout(this); tos.setPadding(0, dp(6), 0, dp(18));
        int[] mins = {2, 5, 10, 30};
        for (final int m : mins) {
            boolean on = to == m * 60000;
            TextView t = text(m + " min", 14, on ? PAPER : INK, false, on);
            t.setPadding(dp(16), dp(8), dp(16), dp(8)); t.setBackground(round(on ? INK : CHIP, 18));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2); lp.rightMargin = dp(10);
            t.setOnClickListener(v -> { try { Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT, m * 60000); } catch (Exception e) {} build(); });
            tos.addView(t, lp);
        }
        content.addView(tos);

        content.addView(rule());
        content.addView(header("Aggiungi libri", null));
        content.addView(actionRow("Ricevi libri via Wi-Fi", "Dal PC o dal telefono, senza cavo", v -> wifiReceive()));
        content.addView(actionRow("Aggiorna libreria", "Rileggi le cartelle Books e Download", v -> { covers.clear(); toast("Libreria aggiornata"); tab = "lib"; build(); }));
        content.addView(rule());
        content.addView(header("Dispositivo", null));
        content.addView(actionRow("Wi-Fi", "Connetti o cambia rete", v -> go(new Intent(Settings.ACTION_WIFI_SETTINGS))));
        content.addView(actionRow("Impostazioni di lettura (ReadEra)", "Caratteri, margini, girare pagina con i tasti volume", v -> { Intent x = getPackageManager().getLaunchIntentForPackage("org.readera"); if (x != null) go(x); }));
        content.addView(actionRow("Tutte le impostazioni", "Impostazioni di sistema Android", v -> go(new Intent(Settings.ACTION_SETTINGS))));
    }

    interface Toggle { void set(boolean on); }
    View toggleRow(String title, String desc, boolean on, final Toggle t) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(8), 0, dp(12));
        LinearLayout tx = new LinearLayout(this); tx.setOrientation(LinearLayout.VERTICAL);
        tx.addView(text(title, 17, INK, false, true));
        tx.addView(text(desc, 13, GREY, false, false));
        r.addView(tx, new LinearLayout.LayoutParams(0, -2, 1));
        Switch s = new Switch(this); s.setChecked(on);
        s.setOnCheckedChangeListener((b, c) -> t.set(c));
        r.addView(s);
        return r;
    }
    View actionRow(String title, String desc, View.OnClickListener l) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setPadding(0, dp(12), 0, dp(12));
        r.addView(text(title + "  ›", 17, INK, false, true));
        r.addView(text(desc, 13, GREY, false, false));
        r.setOnClickListener(l);
        return r;
    }
    TextView label(String s) { return text(s, 17, INK, false, true); }
    LinearLayout.LayoutParams padded(int w, int h, int l, int t, int r, int b) { LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h); lp.setMargins(l, t, r, b); return lp; }
    void go(Intent i) { try { startActivity(i); } catch (Exception e) { toast("Non disponibile"); } }

    void startWarm(int level) {
        try { Intent i = new Intent(this, Warm.class); i.putExtra("level", level); startService(i); } catch (Exception e) {}
    }

    // ---------------- wifi upload ----------------
    void wifiReceive() {
        int ip = 0;
        try { ip = ((WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE)).getConnectionInfo().getIpAddress(); } catch (Exception e) {}
        if (ip == 0) { toast("Collega prima il tablet al Wi-Fi"); go(new Intent(Settings.ACTION_WIFI_SETTINGS)); return; }
        final String addr = String.format(Locale.ROOT, "http://%d.%d.%d.%d:8080", ip & 255, (ip >> 8) & 255, (ip >> 16) & 255, (ip >> 24) & 255);
        startServer();
        final TextView status = text("In attesa di libri…", 15, GREY, false, false);
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(24), dp(20), dp(24), dp(8));
        box.addView(text("Sul PC o sul telefono, connesso alla stessa rete Wi-Fi, apri nel browser:", 15, INK, false, false));
        TextView a = text(addr, 22, INK, false, true); a.setPadding(0, dp(14), 0, dp(14)); box.addView(a);
        box.addView(text("Poi trascina i file (EPUB, PDF, MOBI, CBZ…). Finiscono nella tua libreria.", 14, GREY, false, false));
        status.setPadding(0, dp(14), 0, 0); box.addView(status);
        uploadStatus = status;
        new AlertDialog.Builder(this).setView(box).setCancelable(false)
            .setPositiveButton("Fatto", (d, w) -> { stopServer(); uploadStatus = null; covers.clear(); build(); }).show();
    }
    volatile TextView uploadStatus; volatile int received = 0;

    void startServer() {
        if (server != null) return;
        received = 0;
        new Thread(() -> {
            try {
                ServerSocket ss = new ServerSocket(8080); server = ss;
                while (!ss.isClosed()) {
                    final Socket s = ss.accept();
                    new Thread(() -> handle(s)).start();
                }
            } catch (Exception e) { server = null; }
        }).start();
    }
    void stopServer() { try { if (server != null) server.close(); } catch (Exception e) {} server = null; }

    void handle(Socket s) {
        try {
            s.setSoTimeout(60000);
            InputStream in = new BufferedInputStream(s.getInputStream());
            String reqLine = readLine(in); if (reqLine == null) { s.close(); return; }
            long len = 0; String line;
            while ((line = readLine(in)) != null && line.length() > 0) {
                int c = line.indexOf(':');
                if (c > 0 && line.substring(0, c).trim().equalsIgnoreCase("content-length")) len = Long.parseLong(line.substring(c + 1).trim());
            }
            String[] parts = reqLine.split(" ");
            OutputStream out = s.getOutputStream();
            if (parts.length > 1 && parts[0].equals("PUT") && parts[1].startsWith("/upload?name=")) {
                String name = URLDecoder.decode(parts[1].substring(13), "UTF-8").replaceAll("[\\\\/:*?\"<>|]", "_");
                File dir = new File(Environment.getExternalStorageDirectory(), "Books"); dir.mkdirs();
                File f = new File(dir, name);
                FileOutputStream fo = new FileOutputStream(f);
                byte[] buf = new byte[65536]; long left = len;
                while (left > 0) { int n = in.read(buf, 0, (int) Math.min(buf.length, left)); if (n < 0) break; fo.write(buf, 0, n); left -= n; }
                fo.close();
                received++;
                final String msg = "Ricevuti " + received + " libri · ultimo: " + name;
                ui.post(() -> { if (uploadStatus != null) { uploadStatus.setText(msg); uploadStatus.setTextColor(INK); } });
                byte[] ok = "OK".getBytes("UTF-8");
                out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: " + ok.length + "\r\nConnection: close\r\n\r\n").getBytes("UTF-8")); out.write(ok);
            } else {
                byte[] html = PAGE.getBytes("UTF-8");
                out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: " + html.length + "\r\nConnection: close\r\n\r\n").getBytes("UTF-8")); out.write(html);
            }
            out.flush(); s.close();
        } catch (Exception e) { try { s.close(); } catch (Exception x) {} }
    }
    static String readLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder(); int c;
        while ((c = in.read()) >= 0) { if (c == '\n') break; if (c != '\r') sb.append((char) c); }
        return (c < 0 && sb.length() == 0) ? null : sb.toString();
    }
    static final String PAGE = "<!doctype html><html lang=it><head><meta charset=utf-8><meta name=viewport content='width=device-width,initial-scale=1'><title>Invia libri</title>"
        + "<style>body{font-family:Georgia,serif;background:#FAFAF7;color:#1a1a1a;max-width:640px;margin:40px auto;padding:0 20px}h1{font-weight:700}"
        + "#d{border:2px dashed #999;border-radius:16px;padding:48px 20px;text-align:center;font-size:18px;cursor:pointer;background:#fff}#d.o{background:#efede8}"
        + "li{font-family:sans-serif;font-size:14px;margin:6px 0}.ok{color:#2b7a2b}.er{color:#b00}</style></head><body>"
        + "<h1>Invia libri al tablet</h1><p>Trascina qui i tuoi ebook (EPUB, PDF, MOBI, CBZ, CBR…) oppure tocca per sceglierli.</p>"
        + "<div id=d>Trascina i file qui<br><small>o tocca per sceglierli</small></div><input id=f type=file multiple hidden><ul id=l></ul>"
        + "<script>const d=document.getElementById('d'),f=document.getElementById('f'),l=document.getElementById('l');"
        + "d.onclick=()=>f.click();f.onchange=()=>send(f.files);d.ondragover=e=>{e.preventDefault();d.classList.add('o')};d.ondragleave=()=>d.classList.remove('o');"
        + "d.ondrop=e=>{e.preventDefault();d.classList.remove('o');send(e.dataTransfer.files)};"
        + "async function send(fs){for(const x of fs){const li=document.createElement('li');li.textContent=x.name+' … invio';l.appendChild(li);"
        + "try{const r=await fetch('/upload?name='+encodeURIComponent(x.name),{method:'PUT',body:x});li.textContent=x.name+(r.ok?' ✓ inviato':' ✗ errore');li.className=r.ok?'ok':'er'}"
        + "catch(e){li.textContent=x.name+' ✗ errore';li.className='er'}}}</script></body></html>";

    // ---------------- books ----------------
    TextView sectionTitle(String s) { return text(s, 20, INK, true, true); }
    View header(String title, String more) {
        LinearLayout h = new LinearLayout(this);
        h.setGravity(Gravity.CENTER_VERTICAL);
        h.setPadding(0, dp(18), 0, dp(6));
        h.addView(sectionTitle(title), new LinearLayout.LayoutParams(0, -2, 1));
        if (more != null) { TextView m = text(more, 15, GREY, false, false); m.setOnClickListener(v -> { tab = "lib"; build(); }); h.addView(m); }
        return h;
    }

    View grid(List<File> list, int cols) {
        LinearLayout g = new LinearLayout(this);
        g.setOrientation(LinearLayout.VERTICAL);
        int w = (getResources().getDisplayMetrics().widthPixels - dp(40) - dp(16) * (cols - 1)) / cols;
        int h = (int) (w * 1.45f);
        LinearLayout row = null;
        for (int i = 0; i < list.size(); i++) {
            if (i % cols == 0) { row = new LinearLayout(this); row.setPadding(0, dp(10), 0, dp(10)); g.addView(row); }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, -2);
            if (i % cols != 0) lp.leftMargin = dp(16);
            row.addView(cell(list.get(i), w, h, false), lp);
        }
        return g;
    }

    LinearLayout cell(final File f, int w, int h, boolean showWhen) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.addView(coverView(f, w, h));
        String[] ta = titleAuthor(f);
        TextView t = text(ta[0], 13, INK, true, true); t.setMaxLines(2); t.setEllipsize(TextUtils.TruncateAt.END); t.setPadding(0, dp(6), 0, 0);
        cell.addView(t);
        if (ta[1].length() > 0) { TextView a = text(ta[1], 12, GREY, true, false); a.setMaxLines(1); a.setEllipsize(TextUtils.TruncateAt.END); cell.addView(a); }
        if (showWhen) cell.addView(text(ago(prefs.getLong("t:" + f.getPath(), 0)), 11, GREY, false, false));
        cell.setOnClickListener(v -> open(f));
        cell.setOnLongClickListener(v -> { details(f); return true; });
        return cell;
    }

    void details(final File f) {
        String[] ta = titleAuthor(f);
        new AlertDialog.Builder(this).setTitle(ta[0])
            .setMessage((ta[1].isEmpty() ? "" : ta[1] + "\n") + kind(f) + "\n" + f.getParent())
            .setPositiveButton("Leggi", (d, w) -> open(f))
            .setNeutralButton("Segna come non letto", (d, w) -> { prefs.edit().remove("t:" + f.getPath()).apply(); build(); })
            .setNegativeButton("Chiudi", null).show();
    }

    ImageView coverView(final File f, int w, int h) {
        final ImageView iv = new ImageView(this);
        iv.setLayoutParams(new LinearLayout.LayoutParams(w, h));
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setElevation(dp(3));
        iv.setBackgroundColor(0xFFFFFFFF);
        Bitmap cached = covers.get(f.getPath());
        if (cached != null) { iv.setImageBitmap(cached); return iv; }
        iv.setImageBitmap(placeholder(f, w, h));
        final int tw = w, th = h;
        new Thread(() -> {
            final Bitmap b = loadCover(f, tw, th);
            if (b != null) { covers.put(f.getPath(), b); ui.post(() -> iv.setImageBitmap(b)); }
        }).start();
        return iv;
    }

    Bitmap placeholder(File f, int w, int h) {
        Bitmap b = Bitmap.createBitmap(Math.max(w, 1), Math.max(h, 1), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.drawColor(CHIP);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(INK); p.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD)); p.setTextSize(w / 9f);
        String t = titleAuthor(f)[0];
        float y = h / 3f; int start = 0;
        while (start < t.length() && y < h - w / 9f) {
            int n = p.breakText(t, start, t.length(), true, w * 0.8f, null);
            if (n <= 0) break;
            c.drawText(t, start, start + n, w * 0.1f, y, p);
            start += n; y += w / 7f;
        }
        return b;
    }

    synchronized Bitmap loadCover(File f, int w, int h) {
        File cache = new File(getCacheDir(), Integer.toHexString(f.getPath().hashCode()) + "_" + f.length() + ".png");
        try {
            if (cache.exists()) return BitmapFactory.decodeFile(cache.getPath());
            Bitmap out = null;
            String e = ext(f);
            if (e.equals(".pdf")) {
                ParcelFileDescriptor pfd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
                PdfRenderer r = new PdfRenderer(pfd);
                PdfRenderer.Page pg = r.openPage(0);
                int bw = Math.min(600, w * 2), bh = (int) (bw * (float) pg.getHeight() / pg.getWidth());
                out = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
                out.eraseColor(Color.WHITE);
                pg.render(out, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                pg.close(); r.close(); pfd.close();
            } else if (e.equals(".epub") || e.equals(".cbz")) {
                ZipFile z = new ZipFile(f);
                ZipEntry best = null;
                List<ZipEntry> imgs = new ArrayList<>();
                for (Enumeration<? extends ZipEntry> en = z.entries(); en.hasMoreElements();) {
                    ZipEntry ze = en.nextElement(); String n = ze.getName().toLowerCase(Locale.ROOT);
                    if (n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png")) { imgs.add(ze); if (n.contains("cover")) best = ze; }
                }
                if (best == null && !imgs.isEmpty()) {
                    Collections.sort(imgs, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
                    best = imgs.get(0);
                }
                if (best != null) { BitmapFactory.Options o = new BitmapFactory.Options(); o.inSampleSize = 2; out = BitmapFactory.decodeStream(z.getInputStream(best), null, o); }
                z.close();
            }
            if (out != null) { FileOutputStream fo = new FileOutputStream(cache); out.compress(Bitmap.CompressFormat.PNG, 90, fo); fo.close(); }
            return out;
        } catch (Throwable t) { return null; }
    }

    String ext(File f) { String n = f.getName().toLowerCase(Locale.ROOT); int i = n.lastIndexOf('.'); return i >= 0 ? n.substring(i) : ""; }
    String kind(File f) { return ext(f).toUpperCase(Locale.ITALY).replace(".", "") + " · " + Math.max(1, f.length() / 1048576) + " MB"; }

    String[] titleAuthor(File f) {
        String n = f.getName(); int i = n.lastIndexOf('.'); if (i > 0) n = n.substring(0, i);
        n = n.replace('_', ' ').trim();
        int d = n.indexOf(" - ");
        if (d > 0) return new String[]{n.substring(d + 3).trim(), n.substring(0, d).trim()};
        return new String[]{n, ""};
    }

    String ago(long t) {
        if (t <= 0) return "";
        long m = (System.currentTimeMillis() - t) / 60000;
        if (m < 2) return "adesso";
        if (m < 60) return m + " min fa";
        if (m < 24 * 60) return (m / 60) + " ore fa";
        if (m < 48 * 60) return "ieri";
        return (m / 1440) + " giorni fa";
    }

    List<File> scan() {
        List<File> out = new ArrayList<>();
        File sd = Environment.getExternalStorageDirectory();
        walk(new File(sd, "Books"), out, 0); walk(new File(sd, "Download"), out, 0);
        return sorted(out, "recent");
    }

    List<File> sorted(List<File> in, final String how) {
        List<File> out = new ArrayList<>(in);
        Collections.sort(out, (a, b) -> {
            if (how.equals("author")) { int c = titleAuthor(a)[1].compareToIgnoreCase(titleAuthor(b)[1]); if (c != 0) return c; }
            if (how.equals("recent")) { int c = Long.compare(prefs.getLong("t:" + b.getPath(), 0), prefs.getLong("t:" + a.getPath(), 0)); if (c != 0) return c; }
            return titleAuthor(a)[0].compareToIgnoreCase(titleAuthor(b)[0]);
        });
        return out;
    }

    void walk(File d, List<File> out, int depth) {
        File[] fs = d.listFiles(); if (fs == null || depth > 4) return;
        for (File f : fs) {
            if (f.isDirectory()) { if (!f.getName().startsWith(".")) walk(f, out, depth + 1); continue; }
            String e = ext(f);
            for (String x : EXT) if (x.equals(e)) { out.add(f); break; }
        }
    }

    void open(File f) {
        prefs.edit().putLong("t:" + f.getPath(), System.currentTimeMillis()).apply();
        String e = ext(f);
        String mime = e.equals(".pdf") ? "application/pdf" : e.equals(".epub") ? "application/epub+zip"
            : e.equals(".cbr") ? "application/x-cbr" : e.equals(".cbz") ? "application/x-cbz"
            : e.equals(".mobi") ? "application/x-mobipocket-ebook" : e.equals(".djvu") ? "image/vnd.djvu"
            : e.equals(".txt") ? "text/plain" : e.equals(".fb2") ? "application/x-fictionbook+xml"
            : e.equals(".docx") ? "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            : e.equals(".doc") ? "application/msword" : e.equals(".rtf") ? "application/rtf" : "application/octet-stream";
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(Uri.fromFile(f), mime);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        i.setPackage("org.readera");
        try { startActivity(i); }
        catch (Exception ex) { i.setPackage(null); try { startActivity(i); } catch (Exception ex2) { toast("Nessuna app per aprire questo file"); } }
    }

    void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    void updateStatus() {
        if (clock == null) return;
        clock.setText(new SimpleDateFormat("HH:mm", Locale.ITALY).format(new Date()));
        Intent bi = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (bi != null) {
            int lvl = bi.getIntExtra(BatteryManager.EXTRA_LEVEL, -1), sc = bi.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            boolean ch = bi.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
            battery.setText((ch ? "In carica · " : "") + (lvl * 100 / sc) + "%");
        }
        ui.removeCallbacksAndMessages("tick");
        ui.postAtTime(this::updateStatus, "tick", SystemClock.uptimeMillis() + 30000);
    }

    static class TW implements TextWatcher {
        Home h;
        public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
        public void onTextChanged(CharSequence s, int a, int b, int c) {}
        public void afterTextChanged(Editable s) { if (s.toString().trim().isEmpty() && h.query.length() > 0) { h.query = ""; h.build(); } }
    }
    static class SB implements SeekBar.OnSeekBarChangeListener {
        Home h; int mode;
        public void onProgressChanged(SeekBar s, int p, boolean u) {
            if (!u) return;
            if (mode == 1) { h.prefs.edit().putInt("warmLevel", p).apply(); h.startWarm(p); return; }
            try {
                Settings.System.putInt(h.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, 0);
                Settings.System.putInt(h.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, Math.max(1, p));
            } catch (Exception e) {}
            WindowManager.LayoutParams lp = h.getWindow().getAttributes(); lp.screenBrightness = Math.max(1, p) / 255f; h.getWindow().setAttributes(lp);
        }
        public void onStartTrackingTouch(SeekBar s) {}
        public void onStopTrackingTouch(SeekBar s) {}
    }
}
