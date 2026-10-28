package ma.chouitv.installer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ChouiTV Installer: five screens (prepare, connect, install, personalise, done) in ChouiTV's own look: dark green, gold,
 * glass cards, Thmanyah. It talks to the TV over classic ADB (Network debugging, port 5555).
 */
public class MainActivity extends Activity {
    private static final int GOLD = 0xFFD6AF6B, SAND = 0xFFF6EFE0, MUTED = 0xFFC6BBA4, DARK = 0xFF040E09;
    private static final int PICK_SETTINGS = 7;

    private final ExecutorService bg = Executors.newSingleThreadExecutor();
    private Typeface display, medium, regular;
    private FrameLayout root;
    private int step;
    private Adb tv;
    private Flow flow;
    private String host = "", model = "";
    private volatile boolean busy;

    // personalisation
    private String city = "الدار البيضاء", tz = "Africa/Abidjan", method = "21";
    private boolean adhanOn = true;
    private List<String[]> cityList = new ArrayList<>();
    private String importedB64;
    private TextView cityV, tzV, methodV, adhanV, importV;

    private static final String[][] ZONES = {{"Africa/Abidjan", "المغرب · GMT (Africa/Abidjan)"}, {"city", "حسب المدينة"}, {"Africa/Algiers", "الجزائر"},
        {"Africa/Tunis", "تونس"}, {"Africa/Cairo", "مصر"}, {"Asia/Riyadh", "السعودية"}, {"Asia/Dubai", "الإمارات"}, {"Europe/Paris", "فرنسا"}};
    private static final String[][] METHODS = {{"21", "وزارة الأوقاف المغربية"}, {"auto", "تلقائي حسب المنطقة"}, {"19", "الجزائر"}, {"18", "تونس"},
        {"5", "الهيئة المصرية"}, {"4", "أم القرى"}, {"3", "رابطة العالم الإسلامي"}};

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        display = Typeface.createFromAsset(getAssets(), "fonts/ThmanyahDisplay-Bold.otf");
        medium = Typeface.createFromAsset(getAssets(), "fonts/Thmanyah-Medium.otf");
        regular = Typeface.createFromAsset(getAssets(), "fonts/Thmanyah-Regular.otf");
        root = new FrameLayout(this);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setBackground(background());
        setContentView(root);
        show(0);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tv != null) tv.close();
        bg.shutdownNow();
    }

    @Override
    public void onBackPressed() {
        if (busy) return;
        if (step > 0 && step < 2) show(step - 1); else super.onBackPressed();
    }

    // ---------- the five screens ----------

    private void show(int n) {
        step = n;
        root.removeAllViews();
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(22), dp(44), dp(22), dp(18));
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));
        if (n < 4) {
            col.addView(dots(n));
            String[][] t = {{"جهّز التلفزيون", "ثلاث خطوات تستغرق دقيقة"}, {"اتصل بالتلفزيون", "ابحث عنه على الواي فاي"},
                {"تثبيت ChouiTV", "لا تُغلق التطبيق"}, {"خصّصه لبيتك", "يمكنك تغييرها لاحقًا"}};
            col.addView(text(t[n][0], display, 30, SAND), lp(0, 22, 0, 0));
            col.addView(text(t[n][1], regular, 15, MUTED), lp(0, 4, 0, 14));
        }
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        sv.addView(body, new ViewGroup.LayoutParams(-1, -2));
        col.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        TextView button = button("");
        col.addView(button, lp(0, 12, 0, 0));
        switch (n) {
            case 0: prepare(body, button); break;
            case 1: connect(body, button); break;
            case 2: install(body, button); break;
            case 3: personalise(body, button); break;
            default: done(body, button); break;
        }
    }

    private void prepare(LinearLayout body, TextView button) {
        String[][] s = {{"الإعدادات ← النظام ← حول", "اضغط OK على Build سبع مرات"}, {"خيارات المطوّر", "فعّل «تصحيح USB»"},
            {"تصحيح الشبكة (Network debugging)", "واترك التلفزيون على نفس الواي فاي"}};
        for (int i = 0; i < 3; i++) {
            LinearLayout c = card(false);
            c.setOrientation(LinearLayout.HORIZONTAL);
            c.setGravity(Gravity.CENTER_VERTICAL);
            TextView num = text(String.valueOf(i + 1), medium, 17, DARK);
            num.setGravity(Gravity.CENTER);
            num.setBackground(disc(GOLD));
            c.addView(num, new LinearLayout.LayoutParams(dp(34), dp(34)));
            LinearLayout t = new LinearLayout(this);
            t.setOrientation(LinearLayout.VERTICAL);
            t.addView(text(s[i][0], medium, 17, SAND));
            t.addView(text(s[i][1], regular, 14, MUTED));
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, -2, 1f);
            tp.setMarginStart(dp(14));
            c.addView(t, tp);
            body.addView(c, lp(0, 0, 0, 12));
        }
        button.setText("التالي");
        button.setOnClickListener(v -> show(1));
    }

    private void connect(final LinearLayout body, final TextView button) {
        final TextView found = text("تلفزيونات وُجدت", medium, 15, GOLD);
        body.addView(found, lp(0, 0, 0, 8));
        final LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        body.addView(list);
        final TextView searching = text("جارٍ البحث على الواي فاي…", regular, 15, MUTED);
        list.addView(searching);
        body.addView(text("أو اكتب العنوان", medium, 15, GOLD), lp(0, 16, 0, 8));
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
        tip.addView(text("على التلفزيون", medium, 15, GOLD));
        tip.addView(text("اضغط «السماح» عند ظهور النافذة", regular, 14, SAND));
        tip.addView(text("وفعّل «السماح دائمًا من هذا الجهاز» لتتذكّره", regular, 13, MUTED));
        body.addView(tip, lp(0, 16, 0, 0));
        final TextView status = text("", regular, 14, GOLD);
        body.addView(status, lp(0, 12, 0, 0));
        button.setText("اتصال");
        button.setOnClickListener(v -> {
            if (busy) return;
            String ip = input.getText().toString().trim();
            if (ip.isEmpty() && !chosen.isEmpty()) ip = chosen;
            if (ip.isEmpty()) { Toast.makeText(this, "اختر تلفزيونًا أو اكتب عنوانه", Toast.LENGTH_SHORT).show(); return; }
            connectTo(ip, button, status);
        });
        chosen = "";
        bg.execute(() -> {
            final List<String> tvs = Net.findTvs(5555);
            runOnUiThread(() -> {
                if (step != 1) return;
                list.removeAllViews();
                if (tvs.isEmpty()) list.addView(text("لم يُعثر على تلفزيون. تأكد أن «تصحيح الشبكة» مفعّل، أو اكتب العنوان.", regular, 14, MUTED));
                for (final String ip : tvs) {
                    final LinearLayout c = card(false);
                    c.addView(text("تلفزيون", medium, 18, SAND));
                    TextView a = text(ip, regular, 14, MUTED);
                    a.setTextDirection(View.TEXT_DIRECTION_LTR);
                    a.setGravity(Gravity.START);
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

    private String chosen = "";

    private void connectTo(final String ip, final TextView button, final TextView status) {
        busy = true;
        button.setAlpha(0.6f);
        status.setText("جارٍ الاتصال… اقبل الطلب على شاشة التلفزيون");
        bg.execute(() -> {
            try {
                Adb a = Adb.connect(ip, 5555, new File(getFilesDir(), "keys"));
                tv = a; host = ip;
                flow = new Flow(a, getCacheDir());
                model = flow.model();
                runOnUiThread(() -> { busy = false; show(2); });
            } catch (final Exception e) {
                runOnUiThread(() -> {
                    busy = false;
                    button.setAlpha(1f);
                    status.setText("تعذّر الاتصال بـ " + ip + ": " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
                });
            }
        });
    }

    private void install(final LinearLayout body, final TextView button) {
        final String[][] rows = {{"الواجهة الرئيسية", "chouitv-home.apk"}, {"شاشة التوقف والإعدادات", "chouitv-screensaver.apk"},
            {"تطبيق القنوات", "311 قناة"}, {"ضبط الشاشة والصلاحيات", ""}};
        final TextView[] marks = new TextView[4], subs = new TextView[4];
        final LinearLayout[] cards = new LinearLayout[4];
        for (int i = 0; i < 4; i++) {
            LinearLayout c = card(false);
            c.setOrientation(LinearLayout.HORIZONTAL);
            c.setGravity(Gravity.CENTER_VERTICAL);
            TextView m = text("", medium, 16, SAND);
            m.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(dp(34), dp(34));
            mp.setMarginEnd(dp(14));
            c.addView(m, mp);
            LinearLayout t = new LinearLayout(this);
            t.setOrientation(LinearLayout.VERTICAL);
            t.addView(text(rows[i][0], medium, 17, SAND));
            subs[i] = text(rows[i][1], regular, 13, MUTED);
            subs[i].setTextDirection(View.TEXT_DIRECTION_LTR);
            subs[i].setGravity(Gravity.START);
            t.addView(subs[i]);
            c.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
            body.addView(c, lp(0, 0, 0, 10));
            marks[i] = m; cards[i] = c;
            mark(m, c, "wait");
        }
        final ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(1000);
        bar.getProgressDrawable().setColorFilter(GOLD, android.graphics.PorterDuff.Mode.SRC_IN);
        body.addView(bar, lp(0, 10, 0, 0));
        final TextView info = text("", regular, 14, MUTED);
        body.addView(info, lp(0, 8, 0, 0));
        button.setText("جارٍ التثبيت…");
        button.setAlpha(0.6f);
        busy = true;
        // order on the TV: screensaver, channels, home (the new home only after the other two work), then the settings
        final int[] order = {1, 2, 0, 3};
        bg.execute(() -> {
            int doing = -1;
            try {
                final Net.Release rel = Net.latest();
                for (int k = 0; k < 4; k++) {
                    final int i = order[k];
                    doing = i;
                    final int base = k * 250;
                    runOnUiThread(() -> { mark(marks[i], cards[i], "run"); info.setText("الإصدار " + rel.version); });
                    if (i == 3) {
                        flow.configure();
                    } else {
                        String pkg = i == 0 ? Flow.HOME : i == 1 ? Flow.SAVER : Flow.CHANNELS;
                        String url = null, sha = "";
                        for (Net.Apk a : rel.apks) if (a.pkg.equals(pkg)) { url = a.url; sha = a.sha; }
                        if (i == 2) url = Net.CHANNELS;
                        if (url == null) throw new java.io.IOException("لا رابط للتطبيق " + pkg);
                        flow.installApk(pkg, url, sha,
                            (d, t) -> setBar(bar, base + (t > 0 ? (int) (125 * d / t) : 0)),
                            (d, t) -> setBar(bar, base + 125 + (t > 0 ? (int) (125 * d / t) : 0)));
                    }
                    runOnUiThread(() -> mark(marks[i], cards[i], "done"));
                    setBar(bar, base + 250);
                }
                try { cityList = flow.cities(); } catch (Exception ignored) { }
                runOnUiThread(() -> { busy = false; show(3); });
            } catch (final Exception e) {
                final int failed = doing;
                runOnUiThread(() -> {
                    busy = false;
                    if (failed >= 0) mark(marks[failed], cards[failed], "fail");
                    info.setTextColor(0xFFE8896B);
                    info.setText("تعذّر التثبيت: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
                    button.setText("أعد المحاولة");
                    button.setAlpha(1f);
                    button.setOnClickListener(v -> show(2));
                });
            }
        });
    }

    private void setBar(final ProgressBar bar, final int v) {
        runOnUiThread(() -> bar.setProgress(Math.min(1000, v)));
    }

    private void personalise(LinearLayout body, final TextView button) {
        cityV = addChoice(body, "المدينة", city, v -> pickCity());
        tzV = addChoice(body, "المنطقة الزمنية", zoneLabel(tz), v -> pick("المنطقة الزمنية", ZONES, tz, x -> { tz = x; tzV.setText(zoneLabel(tz)); }));
        methodV = addChoice(body, "طريقة حساب الصلاة", label(METHODS, method), v -> pick("طريقة حساب الصلاة", METHODS, method, x -> { method = x; methodV.setText(label(METHODS, method)); }));
        adhanV = addChoice(body, "الأذان التلقائي", adhanOn ? "يعمل" : "متوقف", v -> { adhanOn = !adhanOn; adhanV.setText(adhanOn ? "يعمل" : "متوقف"); });
        LinearLayout c = card(false);
        c.setBackground(cardBg(false, 0x1FD6AF6B, 0xCCD6AF6B));
        c.addView(text("استيراد ملف إعدادات", medium, 17, GOLD));
        importV = text("chouitv-settings.json من تلفزيون آخر", regular, 13, MUTED);
        c.addView(importV);
        c.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            startActivityForResult(i, PICK_SETTINGS);
        });
        body.addView(c, lp(0, 6, 0, 0));
        button.setText("تطبيق");
        button.setOnClickListener(v -> {
            if (busy) return;
            busy = true;
            button.setAlpha(0.6f);
            button.setText("جارٍ التطبيق…");
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
                    runOnUiThread(() -> { busy = false; show(4); });
                } catch (final Exception e) {
                    runOnUiThread(() -> {
                        busy = false;
                        button.setAlpha(1f);
                        button.setText("تطبيق");
                        Toast.makeText(this, "تعذّر التطبيق: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
                }
            });
        });
    }

    private void done(LinearLayout body, TextView button) {
        body.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView check = text("✓", medium, 64, GOLD);
        check.setGravity(Gravity.CENTER);
        GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        ring.setColor(0xFF142C1F);
        ring.setStroke(dp(3), GOLD);
        check.setBackground(ring);
        body.addView(check, lp(0, 90, 0, 0, dp(130), dp(130)));
        TextView t = text("ChouiTV جاهز", display, 30, SAND);
        t.setGravity(Gravity.CENTER);
        body.addView(t, lp(0, 28, 0, 6));
        TextView s = text("التلفزيون يعمل الآن بإعداداتك\nوالتحديثات القادمة تصلك وحدها", regular, 15, MUTED);
        s.setGravity(Gravity.CENTER);
        body.addView(s);
        LinearLayout tip = card(false);
        tip.setBackground(cardBg(false, 0x333F7A5C, 0xFF3F7A5C));
        tip.addView(text("نصيحة", medium, 15, GOLD));
        tip.addView(text("أغلق «تصحيح الشبكة» على التلفزيون عند الانتهاء", regular, 14, SAND));
        body.addView(tip, lp(0, 40, 0, 0, -1, -2));
        button.setText("افتح على التلفزيون");
        button.setOnClickListener(v -> bg.execute(() -> { try { flow.openOnTv(); } catch (Exception ignored) { } }));
    }

    // ---------- choosers ----------

    private void pickCity() {
        if (cityList.isEmpty()) { Toast.makeText(this, "قائمة المدن غير متاحة", Toast.LENGTH_SHORT).show(); return; }
        final String[] regions = new String[cityList.size()];
        for (int i = 0; i < regions.length; i++) regions[i] = cityList.get(i)[0];
        new AlertDialog.Builder(this).setTitle("الجهة").setItems(regions, (d, which) -> {
            final String[] cities = cityList.get(which)[1].split("،\\s*");
            new AlertDialog.Builder(this).setTitle(regions[which]).setItems(cities, (d2, w2) -> { city = cities[w2].trim(); cityV.setText(city); }).show();
        }).show();
    }

    private interface Pick { void got(String v); }

    private void pick(String title, final String[][] opts, String current, final Pick done) {
        String[] names = new String[opts.length];
        for (int i = 0; i < opts.length; i++) names[i] = opts[i][1];
        new AlertDialog.Builder(this).setTitle(title).setItems(names, (d, w) -> done.got(opts[w][0])).show();
    }

    private static String label(String[][] opts, String key) {
        for (String[] o : opts) if (o[0].equals(key)) return o[1];
        return key;
    }

    private static String zoneLabel(String z) { return label(ZONES, z); }

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
                if (o.size() > 200000) throw new java.io.IOException("الملف كبير جدًا");
                final String b64 = Base64.encodeToString(o.toByteArray(), Base64.NO_WRAP);
                final String preview = flow.importSettings(b64, false);   // nothing is applied yet: what would change
                runOnUiThread(() -> new AlertDialog.Builder(this).setTitle("سيتغيّر على التلفزيون")
                    .setMessage(preview.isEmpty() ? "لا شيء يتغيّر" : preview)
                    .setPositiveButton("استخدم هذا الملف", (d, w) -> { importedB64 = b64; if (importV != null) importV.setText("جاهز: يُطبَّق عند الضغط على «تطبيق»"); })
                    .setNegativeButton("إلغاء", null).show());
            } catch (final Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "تعذّر قراءة الملف: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    // ---------- views ----------

    private LinearLayout dots(int n) {
        LinearLayout d = new LinearLayout(this);
        d.setGravity(Gravity.START);
        for (int i = 0; i < 5; i++) {
            View v = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(dp(4));
            g.setColor(i <= n ? GOLD : 0xFF3C4840);
            v.setBackground(g);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(i == n ? 22 : 8), dp(5));
            p.setMarginStart(dp(6));
            d.addView(v, p);
        }
        return d;
    }

    private TextView addChoice(LinearLayout body, String label, String value, View.OnClickListener click) {
        LinearLayout c = card(false);
        c.addView(text(label, regular, 13, MUTED));
        TextView v = text(value, medium, 19, SAND);
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

    private GradientDrawable disc(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        return g;
    }

    private GradientDrawable background() {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] {0xFF0C1D12, DARK, 0xFF0A2016});
        return g;
    }

    private TextView text(String s, Typeface f, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTypeface(f);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setGravity(Gravity.START);
        t.setTextDirection(View.TEXT_DIRECTION_RTL);
        t.setLineSpacing(dp(3), 1f);
        return t;
    }

    private LinearLayout.LayoutParams lp(int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        return p;
    }

    private LinearLayout.LayoutParams lp(int l, int t, int r, int b, int w, int h) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        return p;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
