package ma.chouitv.installer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.Base64;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static ma.chouitv.installer.Strings.t;

/**
 * ChouiTV Installer: six screens (language, prepare, connect, install, personalise, done) in ChouiTV's own look: dark green, gold,
 * glass cards, Thmanyah, and a Moroccan touch (zellige lattice, horseshoe arch, eight-point stars). Arabic, French or English.
 * It talks to the TV over classic ADB (Network debugging, port 5555).
 */
public class MainActivity extends Activity {
    private static final int GOLD = 0xFFD6AF6B, SAND = 0xFFF6EFE0, MUTED = 0xFFC6BBA4, DARK = 0xFF040E09;
    private static final int PICK_SETTINGS = 7, AUTH_SECONDS = 120;

    private final ExecutorService bg = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private Typeface display, medium, regular, script, ruqaa;
    private FrameLayout root;
    private int step;
    private Adb tv;
    private Flow flow;
    private String model = "";
    private volatile boolean busy;
    private String chosen = "";
    private Runnable ticker;

    // personalisation
    private String city = "الدار البيضاء", tz = "Africa/Casablanca", method = "21";
    private boolean adhanOn = true;
    private List<String[]> cityList = new ArrayList<>();
    private String importedB64;
    private TextView cityV, tzV, methodV, adhanV, importV;

    private static final String[] ZONES = {"Africa/Casablanca", "city", "Africa/Algiers", "Africa/Tunis", "Africa/Cairo", "Asia/Riyadh", "Asia/Dubai", "Europe/Paris"};
    private static final String[] METHODS = {"21", "auto", "19", "18", "5", "4", "3"};

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        display = Typeface.createFromAsset(getAssets(), "fonts/ThmanyahDisplay-Bold.otf");
        medium = Typeface.createFromAsset(getAssets(), "fonts/Thmanyah-Medium.otf");
        regular = Typeface.createFromAsset(getAssets(), "fonts/Thmanyah-Regular.otf");
        script = Typeface.createFromAsset(getAssets(), "fonts/GreatVibes-Regular.ttf");
        ruqaa = Typeface.createFromAsset(getAssets(), "fonts/ArefRuqaa-Bold.ttf");
        SharedPreferences sp = getSharedPreferences("installer", MODE_PRIVATE);
        String saved = sp.getString("lang", null);
        if (saved == null) {
            String l = Locale.getDefault().getLanguage();
            saved = l.equals("ar") || l.equals("fr") ? l : "en";
        }
        Strings.lang = saved;
        root = new FrameLayout(this);
        root.setBackground(background());
        setContentView(root);
        show(0);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopTicker();
        if (tv != null) tv.close();
        bg.shutdownNow();
    }

    @Override
    public void onBackPressed() {
        if (busy) return;
        if (step > 0 && step < 3) show(step - 1); else super.onBackPressed();
    }

    // ---------- the six screens ----------

    private void show(int n) {
        step = n;
        stopTicker();
        root.setLayoutDirection(Strings.rtl() ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        root.removeAllViews();
        root.addView(new Orn.Lattice(this), new FrameLayout.LayoutParams(-1, -1));
        root.addView(new Orn.Strip(this), new FrameLayout.LayoutParams(-1, dp(24), Gravity.TOP));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(22), dp(48), dp(22), dp(18));
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));
        if (n >= 1 && n <= 4) {
            col.addView(dots(n));
            String[][] k = {{"prep_title", "prep_sub"}, {"conn_title", "conn_sub"}, {"install_title", "install_sub"}, {"custom_title", "custom_sub"}};
            col.addView(text(t(k[n - 1][0]), display, 30, SAND), lp(0, 22, 0, 0));
            col.addView(text(t(k[n - 1][1]), regular, 15, MUTED), lp(0, 4, 0, 6));
            col.addView(new Orn.Rule(this), new LinearLayout.LayoutParams(-1, dp(22)));
            col.addView(new View(this), new LinearLayout.LayoutParams(-1, dp(8)));
        }
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setFillViewport(true);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        sv.addView(body, new ViewGroup.LayoutParams(-1, -2));
        col.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        TextView button = button("");
        col.addView(button, lp(0, 12, 0, 0));
        switch (n) {
            case 0: language(body, button); break;
            case 1: prepare(body, button); break;
            case 2: connect(body, button); break;
            case 3: install(body, button); break;
            case 4: personalise(body, button); break;
            default: done(body, button); break;
        }
    }

    private void language(final LinearLayout body, TextView button) {
        Orn.Arch arch = new Orn.Arch(this);
        LinearLayout in = new LinearLayout(this);
        in.setOrientation(LinearLayout.VERTICAL);
        in.setGravity(Gravity.CENTER);
        in.setPadding(dp(18), dp(40), dp(18), dp(20));
        TextView logo = text("ChouiTV", script, 54, SAND);
        logo.setGravity(Gravity.CENTER);
        gold(logo);
        in.addView(logo, new LinearLayout.LayoutParams(-1, -2));
        TextView tag = text(t("tagline"), Strings.rtl() ? ruqaa : medium, Strings.rtl() ? 22 : 16, SAND);
        tag.setGravity(Gravity.CENTER);
        in.addView(tag, lp(0, 12, 0, 0));
        arch.addView(in, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(dp(300), dp(250));
        ap.gravity = Gravity.CENTER_HORIZONTAL;
        ap.topMargin = dp(8);
        body.addView(arch, ap);
        TextView title = text(t("lang_title"), medium, 20, GOLD);
        title.setGravity(Gravity.CENTER);
        body.addView(title, lp(0, 22, 0, 0));
        TextView hint = text("Choisissez votre langue  ·  Choose your language", regular, 12, MUTED);
        hint.setGravity(Gravity.CENTER);
        hint.setTextDirection(View.TEXT_DIRECTION_LTR);
        body.addView(hint, lp(0, 2, 0, 12));
        String[][] langs = {{"ar", "AR", "العربية"}, {"fr", "FR", "Français"}, {"en", "EN", "English"}};
        for (final String[] l : langs) {
            LinearLayout c = card(Strings.lang.equals(l[0]));
            c.setOrientation(LinearLayout.HORIZONTAL);
            c.setGravity(Gravity.CENTER_VERTICAL);
            TextView code = text(l[1], medium, 14, Strings.lang.equals(l[0]) ? DARK : SAND);
            code.setGravity(Gravity.CENTER);
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(dp(8));
            g.setColor(Strings.lang.equals(l[0]) ? GOLD : 0xFF3C4840);
            code.setBackground(g);
            c.addView(code, new LinearLayout.LayoutParams(dp(40), dp(32)));
            TextView name = text(l[2], medium, 20, SAND);
            name.setTextDirection(l[0].equals("ar") ? View.TEXT_DIRECTION_RTL : View.TEXT_DIRECTION_LTR);
            name.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
            LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(0, -2, 1f);
            np.setMarginStart(dp(14));
            c.addView(name, np);
            c.setOnClickListener(v -> {
                Strings.lang = l[0];
                getSharedPreferences("installer", MODE_PRIVATE).edit().putString("lang", l[0]).apply();
                show(0);
            });
            body.addView(c, lp(0, 0, 0, 10));
        }
        button.setText(t("continue"));
        button.setOnClickListener(v -> show(1));
    }

    private void prepare(LinearLayout body, TextView button) {
        String[][] s = {{"prep1a", "prep1b"}, {"prep2a", "prep2b"}, {"prep3a", "prep3b"}};
        for (int i = 0; i < 3; i++) {
            LinearLayout c = card(false);
            c.setOrientation(LinearLayout.HORIZONTAL);
            c.setGravity(Gravity.CENTER_VERTICAL);
            c.addView(new Orn.StarNum(this, String.valueOf(i + 1), medium), new LinearLayout.LayoutParams(dp(40), dp(40)));
            LinearLayout tt = new LinearLayout(this);
            tt.setOrientation(LinearLayout.VERTICAL);
            tt.addView(text(t(s[i][0]), medium, 17, SAND));
            tt.addView(text(t(s[i][1]), regular, 14, MUTED));
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, -2, 1f);
            tp.setMarginStart(dp(14));
            c.addView(tt, tp);
            body.addView(c, lp(0, 0, 0, 12));
        }
        button.setText(t("next"));
        button.setOnClickListener(v -> show(2));
    }

    private void connect(final LinearLayout body, final TextView button) {
        body.addView(text(t("found"), medium, 15, GOLD), lp(0, 0, 0, 8));
        final LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        body.addView(list);
        list.addView(text(t("searching"), regular, 15, MUTED));
        body.addView(text(t("or_type"), medium, 15, GOLD), lp(0, 16, 0, 8));
        final EditText input = new EditText(this);
        input.setHint("192.168.1.24");
        input.setHintTextColor(0x66C6BBA4);
        input.setTextColor(SAND);
        input.setTypeface(regular);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        input.setInputType(InputType.TYPE_CLASS_PHONE);
        input.setTextDirection(View.TEXT_DIRECTION_LTR);
        input.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        input.setBackground(cardBg(false));
        input.setPadding(dp(16), dp(12), dp(16), dp(12));
        body.addView(input, new LinearLayout.LayoutParams(-1, dp(52)));
        LinearLayout tip = card(false);
        tip.setBackground(cardBg(false, 0x333F7A5C, 0xFF3F7A5C));
        tip.addView(text(t("on_tv"), medium, 15, GOLD));
        tip.addView(text(t("allow1"), regular, 14, SAND));
        tip.addView(text(t("allow2"), regular, 13, MUTED));
        body.addView(tip, lp(0, 16, 0, 0));
        final TextView status = text("", regular, 14, GOLD);
        body.addView(status, lp(0, 12, 0, 12));
        button.setText(t("connect"));
        button.setOnClickListener(v -> {
            if (busy) return;
            String ip = input.getText().toString().trim();
            if (ip.isEmpty()) ip = chosen;
            if (ip.isEmpty()) { Toast.makeText(this, t("pick_tv"), Toast.LENGTH_SHORT).show(); return; }
            connectTo(ip, button, status);
        });
        chosen = "";
        bg.execute(() -> {
            final List<String> tvs = Net.findTvs(5555);
            runOnUiThread(() -> {
                if (step != 2) return;
                list.removeAllViews();
                if (tvs.isEmpty()) list.addView(text(t("none_found"), regular, 14, MUTED));
                for (final String ip : tvs) {
                    final LinearLayout c = card(false);
                    c.addView(text(t("tv"), medium, 18, SAND));
                    TextView a = text(ip, regular, 14, MUTED);
                    a.setTextDirection(View.TEXT_DIRECTION_LTR);
                    c.addView(a);
                    c.setOnClickListener(v -> {
                        chosen = ip;
                        for (int i = 0; i < list.getChildCount(); i++) list.getChildAt(i).setBackground(cardBg(false));
                        c.setBackground(cardBg(true));
                        input.setText("");
                    });
                    list.addView(c, lp(0, 0, 0, 10));
                }
            });
        });
    }

    private void connectTo(final String ip, final TextView button, final TextView status) {
        busy = true;
        button.setAlpha(0.6f);
        status.setTextColor(GOLD);
        final int[] left = {AUTH_SECONDS};
        stopTicker();
        ticker = new Runnable() {
            @Override public void run() {
                if (!busy) return;
                status.setText(t("connecting", Math.max(left[0], 0)));
                left[0]--;
                ui.postDelayed(this, 1000);
            }
        };
        ui.post(ticker);
        bg.execute(() -> {
            try {
                Adb a = Adb.connect(ip, 5555, new File(getFilesDir(), "keys"));
                tv = a;
                flow = new Flow(a, getCacheDir());
                model = flow.model();
                runOnUiThread(() -> { busy = false; show(3); });
            } catch (final Exception e) {
                runOnUiThread(() -> {
                    busy = false;
                    stopTicker();
                    button.setAlpha(1f);
                    status.setTextColor(0xFFE8896B);
                    status.setText(friendly(e, ip, true));
                });
            }
        });
    }

    /** The weight of each of the eight steps in the global percentage (they add up to 100). */
    private static final int[] W = {3, 25, 15, 12, 15, 6, 12, 12};

    private void install(final LinearLayout body, final TextView button) {
        final int n = W.length;
        final TextView pctV = text("0%", display, 58, SAND);
        pctV.setGravity(Gravity.CENTER);
        pctV.setTextDirection(View.TEXT_DIRECTION_LTR);
        body.addView(pctV, lp(0, 0, 0, 4));
        final Orn.Bar bar = new Orn.Bar(this);
        body.addView(bar, new LinearLayout.LayoutParams(-1, dp(32)));
        LinearLayout meta = new LinearLayout(this);
        meta.setOrientation(LinearLayout.HORIZONTAL);
        final TextView stepV = text(t("step_of", 1, n), medium, 14, SAND);
        final TextView etaV = text("", regular, 14, GOLD);
        etaV.setGravity(Gravity.END);
        meta.addView(stepV, new LinearLayout.LayoutParams(0, -2, 1f));
        meta.addView(etaV, new LinearLayout.LayoutParams(0, -2, 1f));
        body.addView(meta, lp(6, 6, 6, 14));
        final String[] titles = {t("s_check"), t("s_download", ""), t("s_saver"), t("s_channels"), t("s_home"), t("s_perms"), t("s_defaults"), t("s_verify")};
        final String[] subs0 = {"", "", "", t("ch_sub"), "", t("sub_perms"), t("sub_defaults"), t("sub_verify")};
        final Orn.Node[] nodes = new Orn.Node[n];
        final TextView[] titleV = new TextView[n], subV = new TextView[n];
        for (int i = 0; i < n; i++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            nodes[i] = new Orn.Node(this, i == 0, i == n - 1);
            row.addView(nodes[i], new LinearLayout.LayoutParams(dp(30), -1));
            LinearLayout tt = new LinearLayout(this);
            tt.setOrientation(LinearLayout.VERTICAL);
            tt.setPadding(0, dp(8), 0, dp(10));
            titleV[i] = text(titles[i], medium, 16, SAND);
            subV[i] = text(subs0[i], regular, 13, MUTED);
            subV[i].setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
            tt.addView(titleV[i]);
            tt.addView(subV[i]);
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, -2, 1f);
            tp.setMarginStart(dp(10));
            row.addView(tt, tp);
            body.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
        final TextView err = text("", regular, 14, 0xFFE8896B);
        body.addView(err, lp(0, 10, 0, 0));
        button.setText(t("installing"));
        button.setAlpha(0.6f);
        busy = true;
        final long t0 = System.currentTimeMillis();
        bg.execute(() -> {
            int cur = 0;
            try {
                cur = 0;
                node(nodes, 0, Orn.Node.RUN);
                progress(0, 0f, bar, pctV, stepV, etaV, t0);
                String m = flow.model(), av = flow.androidVersion();
                sub(subV, 0, m + " · Android " + av);
                node(nodes, 0, Orn.Node.DONE);
                progress(0, 1f, bar, pctV, stepV, etaV, t0);

                cur = 1;
                node(nodes, 1, Orn.Node.RUN);
                final Net.Release rel = Net.latest();
                final String ver = rel.version;
                runOnUiThread(() -> titleV[1].setText(t("s_download", ver)));
                final String[] pk = {Flow.SAVER, Flow.CHANNELS, Flow.HOME};
                final java.io.File[] files = new java.io.File[3];
                for (int j = 0; j < 3; j++) {
                    String url = null, sha = "";
                    for (Net.Apk a : rel.apks) if (a.pkg.equals(pk[j])) { url = a.url; sha = a.sha; }
                    if (j == 1) { url = Net.CHANNELS; sha = ""; }
                    if (url == null) throw new IOException("no link for " + pk[j]);
                    final int jj = j;
                    files[j] = flow.download(pk[j], url, sha, (d, tt) -> {
                        progress(1, (jj + (tt > 0 ? (float) d / tt : 0f)) / 3f, bar, pctV, stepV, etaV, t0);
                        sub(subV, 1, (jj + 1) + "/3 · " + mb(d) + (tt > 0 ? " / " + mb(tt) : ""));
                    });
                }
                node(nodes, 1, Orn.Node.DONE);
                progress(1, 1f, bar, pctV, stepV, etaV, t0);

                for (int k = 0; k < 3; k++) {
                    cur = 2 + k;
                    final int s = cur;
                    node(nodes, s, Orn.Node.RUN);
                    flow.installFile(pk[k], files[k], (d, tt) -> {
                        float f = tt > 0 ? (float) d / tt : 0f;
                        progress(s, f * 0.85f, bar, pctV, stepV, etaV, t0);
                        sub(subV, s, f >= 1f ? t("sub_install") : t("sub_send", (int) (f * 100)));
                    });
                    if (s != 3) sub(subV, s, ver);
                    else sub(subV, s, t("ch_sub"));
                    node(nodes, s, Orn.Node.DONE);
                    progress(s, 1f, bar, pctV, stepV, etaV, t0);
                }

                cur = 5;
                node(nodes, 5, Orn.Node.RUN);
                flow.grantPermissions();
                node(nodes, 5, Orn.Node.DONE);
                progress(5, 1f, bar, pctV, stepV, etaV, t0);

                cur = 6;
                node(nodes, 6, Orn.Node.RUN);
                flow.setDefaults();
                node(nodes, 6, Orn.Node.DONE);
                progress(6, 1f, bar, pctV, stepV, etaV, t0);

                cur = 7;
                node(nodes, 7, Orn.Node.RUN);
                flow.verify();
                node(nodes, 7, Orn.Node.DONE);
                progress(7, 1f, bar, pctV, stepV, etaV, t0);

                try { cityList = flow.cities(); } catch (Exception ignored) { }
                Thread.sleep(700);
                runOnUiThread(() -> { busy = false; show(4); });
            } catch (final Exception e) {
                final int failed = cur;
                runOnUiThread(() -> {
                    busy = false;
                    nodes[failed].set(Orn.Node.FAIL);
                    err.setText(t("err_install", friendly(e, "", false)));
                    button.setText(t("retry"));
                    button.setAlpha(1f);
                    button.setOnClickListener(v -> show(3));
                });
            }
        });
    }

    private void node(final Orn.Node[] nodes, final int i, final int state) { runOnUiThread(() -> nodes[i].set(state)); }

    private void sub(final TextView[] subs, final int i, final String text) { runOnUiThread(() -> subs[i].setText(text)); }

    private String mb(long bytes) { return String.format(Locale.ENGLISH, "%.1f %s", bytes / 1048576.0, t("mb")); }

    /** The global percentage: the weights of the finished steps plus the share of the running one. */
    private void progress(final int step, float frac, final Orn.Bar bar, final TextView pctV, final TextView stepV, final TextView etaV, final long t0) {
        float done = 0;
        for (int i = 0; i < step; i++) done += W[i];
        final float pct = Math.min(100f, done + W[step] * Math.max(0f, Math.min(1f, frac)));
        final long el = (System.currentTimeMillis() - t0) / 1000;
        final String eta = pct < 6f || pct >= 100f ? "" : eta(Math.round(el * (100 - pct) / pct));
        runOnUiThread(() -> {
            pctV.setText(Math.round(pct) + "%");
            bar.set(pct / 100f);
            stepV.setText(t("step_of", step + 1, W.length));
            etaV.setText(eta);
        });
    }

    private String eta(long sec) { return sec < 45 ? t("eta_lt1") : t("eta_min", Math.max(1, Math.round(sec / 60f))); }

    private void personalise(LinearLayout body, final TextView button) {
        cityV = addChoice(body, t("city"), city, v -> pickCity());
        tzV = addChoice(body, t("tz"), label("z_", tz), v -> pick(t("tz"), ZONES, "z_", x -> { tz = x; tzV.setText(label("z_", tz)); }));
        methodV = addChoice(body, t("method"), label("m_", method), v -> pick(t("method"), METHODS, "m_", x -> { method = x; methodV.setText(label("m_", method)); }));
        adhanV = addChoice(body, t("adhan"), adhanOn ? t("on") : t("off"), v -> { adhanOn = !adhanOn; adhanV.setText(adhanOn ? t("on") : t("off")); });
        LinearLayout c = card(false);
        c.setBackground(cardBg(false, 0x1FD6AF6B, 0xCCD6AF6B));
        c.addView(text(t("import"), medium, 17, GOLD));
        importV = text(importedB64 != null ? t("import_ready") : t("import_sub"), regular, 13, MUTED);
        c.addView(importV);
        c.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            startActivityForResult(i, PICK_SETTINGS);
        });
        body.addView(c, lp(0, 6, 0, 0));
        button.setText(t("apply"));
        button.setOnClickListener(v -> {
            if (busy) return;
            busy = true;
            button.setAlpha(0.6f);
            button.setText(t("applying"));
            bg.execute(() -> {
                try {
                    if (importedB64 != null) {
                        flow.importSettings(importedB64, true);
                    } else {
                        flow.setCity(city);
                        flow.setTz(tz);
                        flow.setMethod(method);
                        flow.adhan(adhanOn);
                    }
                    flow.rescheduleAdhan();
                    runOnUiThread(() -> { busy = false; show(5); });
                } catch (final Exception e) {
                    runOnUiThread(() -> {
                        busy = false;
                        button.setAlpha(1f);
                        button.setText(t("apply"));
                        Toast.makeText(this, t("err_apply", friendly(e, "", false)), Toast.LENGTH_LONG).show();
                    });
                }
            });
        });
    }

    private void done(LinearLayout body, TextView button) {
        body.setGravity(Gravity.CENTER_HORIZONTAL);
        Orn.Arch arch = new Orn.Arch(this);
        LinearLayout in = new LinearLayout(this);
        in.setOrientation(LinearLayout.VERTICAL);
        in.setGravity(Gravity.CENTER);
        in.setPadding(dp(18), dp(36), dp(18), dp(16));
        TextView check = text("✓", medium, 40, GOLD);
        check.setGravity(Gravity.CENTER);
        GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        ring.setColor(0xFF142C1F);
        ring.setStroke(dp(3), GOLD);
        check.setBackground(ring);
        in.addView(check, new LinearLayout.LayoutParams(dp(84), dp(84)));
        TextView logo = text("ChouiTV", script, 38, SAND);
        logo.setGravity(Gravity.CENTER);
        gold(logo);
        in.addView(logo, lp(0, 10, 0, 0));
        arch.addView(in, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(dp(230), dp(230));
        ap.gravity = Gravity.CENTER_HORIZONTAL;
        ap.topMargin = dp(10);
        body.addView(arch, ap);
        TextView title = text(t("thanks_title"), display, 24, SAND);
        title.setGravity(Gravity.CENTER);
        body.addView(title, lp(0, 16, 0, 4));
        TextView s = text(t("thanks_sub"), regular, 15, MUTED);
        s.setGravity(Gravity.CENTER);
        body.addView(s);
        // the ask: a star on GitHub
        LinearLayout ask = card(false);
        ask.setBackground(cardBg(false, 0x1FD6AF6B, 0xCCD6AF6B));
        ask.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView st = text("★", medium, 26, GOLD);
        st.setGravity(Gravity.CENTER);
        ask.addView(st, new LinearLayout.LayoutParams(-1, -2));
        TextView q = text(t("star_title"), medium, 18, GOLD);
        q.setGravity(Gravity.CENTER);
        ask.addView(q);
        TextView qt = text(t("star_text"), regular, 14, SAND);
        qt.setGravity(Gravity.CENTER);
        ask.addView(qt, lp(0, 4, 0, 0));
        body.addView(ask, lp(0, 18, 0, 10));
        TextView star = text("★  " + t("star_btn"), medium, 17, GOLD);
        star.setGravity(Gravity.CENTER);
        GradientDrawable sb = new GradientDrawable();
        sb.setCornerRadius(dp(28));
        sb.setColor(0xFF142C1F);
        sb.setStroke(dp(2), GOLD);
        star.setBackground(sb);
        star.setOnClickListener(v -> {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/EMCHOUIT/ChouiTV"))); } catch (Exception ignored) { }
        });
        body.addView(star, new LinearLayout.LayoutParams(-1, dp(52)));
        LinearLayout tip = card(false);
        tip.setBackground(cardBg(false, 0x333F7A5C, 0xFF3F7A5C));
        tip.addView(text(t("tip"), medium, 15, GOLD));
        tip.addView(text(t("tip_text"), regular, 14, SAND));
        body.addView(tip, lp(0, 14, 0, 0));
        button.setText(t("open_tv"));
        button.setOnClickListener(v -> bg.execute(() -> { try { flow.openOnTv(); } catch (Exception ignored) { } }));
    }

    // ---------- choosers ----------

    private void pickCity() {
        if (cityList.isEmpty()) { Toast.makeText(this, t("cities_unavail"), Toast.LENGTH_SHORT).show(); return; }
        final String[] regions = new String[cityList.size()];
        for (int i = 0; i < regions.length; i++) regions[i] = cityList.get(i)[0];
        new AlertDialog.Builder(this).setTitle(t("region")).setItems(regions, (d, which) -> {
            final String[] cities = cityList.get(which)[1].split("،\\s*");
            new AlertDialog.Builder(this).setTitle(regions[which]).setItems(cities, (d2, w2) -> { city = cities[w2].trim(); cityV.setText(city); }).show();
        }).show();
    }

    private interface Pick { void got(String v); }

    private void pick(String title, final String[] keys, final String prefix, final Pick done) {
        String[] names = new String[keys.length];
        for (int i = 0; i < keys.length; i++) names[i] = label(prefix, keys[i]);
        new AlertDialog.Builder(this).setTitle(title).setItems(names, (d, w) -> done.got(keys[w])).show();
    }

    private static String label(String prefix, String key) { return t(prefix + key); }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != PICK_SETTINGS || res != RESULT_OK || data == null || data.getData() == null) return;
        final Uri uri = data.getData();
        bg.execute(() -> {
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                ByteArrayOutputStream o = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
                if (o.size() > 200000) throw new java.io.IOException(t("file_big"));
                final String b64 = Base64.encodeToString(o.toByteArray(), Base64.NO_WRAP);
                final String preview = flow.importSettings(b64, false);   // nothing is applied yet: what would change
                runOnUiThread(() -> new AlertDialog.Builder(this).setTitle(t("will_change"))
                    .setMessage(preview.isEmpty() ? t("no_change") : preview)
                    .setPositiveButton(t("use_file"), (d, w) -> { importedB64 = b64; if (importV != null) importV.setText(t("import_ready")); })
                    .setNegativeButton(t("cancel"), null).show());
            } catch (final Exception e) {
                runOnUiThread(() -> Toast.makeText(this, t("err_read", e.getMessage()), Toast.LENGTH_LONG).show());
            }
        });
    }

    // ---------- errors in plain words ----------

    private String friendly(Exception e, String ip, boolean connecting) {
        String m = e.getMessage() == null ? "" : e.getMessage();
        if (e instanceof UnknownHostException || m.startsWith("HTTP ") || m.contains("checksum") || m.contains("too small")) return t("err_net");
        if (e instanceof ConnectException || e instanceof NoRouteToHostException || m.contains("failed to connect") || m.contains("Network is unreachable")) {
            return connecting ? t("err_unreach", ip) : t("err_generic", m);
        }
        if (e instanceof SocketTimeoutException || m.contains("timed out") || m.contains("connection abort") || m.contains("closed the connection") || m.contains("did not accept")) {
            return connecting ? t("err_allow") : t("err_generic", m);
        }
        return t("err_generic", m.isEmpty() ? e.getClass().getSimpleName() : m);
    }

    private void stopTicker() {
        if (ticker != null) ui.removeCallbacks(ticker);
        ticker = null;
    }

    // ---------- views ----------

    private LinearLayout dots(int n) {
        LinearLayout d = new LinearLayout(this);
        d.setGravity(Gravity.START);
        for (int i = 0; i < 6; i++) {
            View v = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(dp(4));
            g.setColor(i <= n ? GOLD : 0xFF3C4840);
            v.setBackground(g);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(i == n ? 22 : 8), dp(5));
            p.setMarginEnd(dp(6));
            d.addView(v, p);
        }
        return d;
    }

    private TextView addChoice(LinearLayout body, String label, String value, View.OnClickListener click) {
        LinearLayout c = card(false);
        c.addView(text(label, regular, 13, MUTED));
        TextView v = text(value, medium, 19, SAND);
        v.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);   // an Arabic city name in a French page still starts on the reading side
        c.addView(v);
        c.setOnClickListener(click);
        body.addView(c, lp(0, 0, 0, 10));
        return v;
    }

    private void mark(TextView m, LinearLayout card, String state) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        switch (state) {
            case "done": g.setColor(0xFF3F7A5C); m.setText("✓"); m.setTextColor(SAND); card.setBackground(cardBg(false)); break;
            case "run": g.setColor(0); g.setStroke(dp(2), GOLD); m.setText("…"); m.setTextColor(GOLD); card.setBackground(cardBg(true)); break;
            case "fail": g.setColor(0xFF7A3F3F); m.setText("!"); m.setTextColor(SAND); break;
            default: g.setColor(0); g.setStroke(dp(2), 0xFF50645A); m.setText(""); break;
        }
        m.setBackground(g);
    }

    private void gold(final TextView tv) {
        tv.post(() -> {
            tv.getPaint().setShader(new LinearGradient(0, 0, 0, tv.getHeight(), 0xFFFFF0C4, 0xFFB27E30, Shader.TileMode.CLAMP));
            tv.invalidate();
        });
    }

    private TextView button(String label) {
        TextView b = text(label, medium, 19, DARK);
        b.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] {0xFFFFE296, 0xFFC48E3A});
        g.setCornerRadius(dp(30));
        b.setBackground(g);
        b.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(56)));
        return b;
    }

    private LinearLayout card(boolean selected) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(18), dp(14), dp(18), dp(14));
        c.setBackground(cardBg(selected));
        c.setClickable(true);
        return c;
    }

    private GradientDrawable cardBg(boolean selected) {
        return selected ? cardBg(true, 0x28D6AF6B, GOLD) : cardBg(false, 0x14FFFFFF, 0x2BFFFFFF);
    }

    private GradientDrawable cardBg(boolean selected, int fill, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(16));
        g.setColor(fill);
        g.setStroke(dp(selected ? 2 : 1), stroke);
        return g;
    }

    private GradientDrawable background() {
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] {0xFF0C1D12, DARK, 0xFF0A2016});
    }

    private TextView text(String s, Typeface f, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTypeface(f);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setGravity(Gravity.START);
        t.setTextDirection(Strings.rtl() ? View.TEXT_DIRECTION_RTL : View.TEXT_DIRECTION_FIRST_STRONG);
        t.setLineSpacing(dp(3), 1f);
        return t;
    }

    private LinearLayout.LayoutParams lp(int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        return p;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
