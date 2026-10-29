package ma.chouitv.screensaver;

import ma.chouitv.home.R;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Outline;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/** Remote-friendly settings: every row is a button that opens a list. */
public class SettingsActivity extends Activity {
    private Prefs prefs;
    private LinearLayout list, target, sink;   // sink takes the rows of the tabs that are not open
    private int tab;
    private View previewView;   // «▶ معاينة»: left of the last tab
    private boolean firstRow;   // the next redraw puts the focus on the first row (a tab was chosen with left/right)
    private boolean rendering;   // while the rows are rebuilt Android may hand the focus to the first tab: that is not a choice of the user
    private TextView[] tabViews;
    private static final String[] TABS = {"الأساسيات", "الشاشة", "المحتوى", "الصلاة", "الرئيسية والنظام"};

    /** Arabic locale for this screen, so every dialog title, list and button is laid out right-to-left. */
    @Override
    protected void attachBaseContext(android.content.Context base) {
        android.content.res.Configuration c = new android.content.res.Configuration(base.getResources().getConfiguration());
        c.setLocale(new java.util.Locale("ar"));
        c.setLayoutDirection(new java.util.Locale("ar"));
        super.attachBaseContext(base.createConfigurationContext(c));
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new Prefs(this);
        UpdateJob.schedule(this);
        // the same design as the «تلاوة وأذان» screens: the dark green, a dimmed photo of ours behind, glass rows, the hint at the bottom
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF040E09);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        Bitmap photo = photo(2);
        if (photo != null) {
            ImageView bg = new ImageView(this);
            bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
            bg.setImageBitmap(photo);
            bg.setAlpha(0.42f);
            root.addView(bg, new FrameLayout.LayoutParams(-1, -1));
        }
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        FrameLayout frame = new FrameLayout(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        list.setPadding(dp(8), dp(10), dp(8), dp(16));
        int width = Math.min(getResources().getDisplayMetrics().widthPixels - dp(144), dp(1100));
        frame.addView(list, new FrameLayout.LayoutParams(width, -2, Gravity.CENTER_HORIZONTAL));
        scroll.addView(frame);
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);

        // the top bar stays in place: the title, the five tabs, and «▶ معاينة» at the far end of the tab row, whatever the tab
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        top.setPadding(dp(8), dp(26), dp(8), dp(4));
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText("تلفزيونك");
        title.setTextColor(0xFFF6EFE0);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 34);
        title.setTypeface(font("ThmanyahDisplay-Bold"));
        head.addView(title);
        TextView sub = new TextView(this);
        sub.setText("إعدادات شاشة التوقف");
        sub.setTextColor(0xFFC6BBA4);
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        sub.setTypeface(font("Thmanyah-Regular"));
        LinearLayout.LayoutParams sp0 = new LinearLayout.LayoutParams(-2, -2);
        sp0.setMarginStart(dp(18));
        head.addView(sub, sp0);
        top.addView(head);
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(0, dp(12), 0, dp(6));
        tabViews = new TextView[TABS.length];
        tab = 0;   // every time the settings open they start on the first tab (الأساسيات), the one at the right
        for (int i = 0; i < TABS.length; i++) {
            final int which = i;
            TextView v = new TextView(this);
            v.setText(TABS[i]);
            v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
            v.setTypeface(font("Thmanyah-Medium"));
            v.setTextColor(getColorStateList(R.color.saver_tab_text));
            v.setBackgroundResource(R.drawable.tab_pill);
            v.setGravity(Gravity.CENTER);
            v.setPadding(dp(20), 0, dp(20), 0);
            v.setFocusable(true);
            v.setClickable(true);
            v.setId(View.generateViewId());
            v.setSelected(i == tab);
            v.setOnClickListener(x -> selectTab(which));
            v.setOnFocusChangeListener((x, has) -> { if (has && which != tab && !rendering) selectTab(which); });   // left/right walks the tabs
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(42));
            if (i > 0) lp.setMarginStart(dp(8));
            bar.addView(v, lp);
            tabViews[i] = v;
        }
        View spacer = new View(this);
        bar.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        TextView preview = new TextView(this);
        previewView = preview;
        preview.setText("▶  معاينة");
        preview.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        preview.setTypeface(font("Thmanyah-Medium"));
        preview.setTextColor(getColorStateList(R.color.saver_tab_text));
        preview.setBackgroundResource(R.drawable.tab_pill_gold);
        preview.setGravity(Gravity.CENTER);
        preview.setPadding(dp(20), 0, dp(20), 0);
        preview.setId(View.generateViewId());
        preview.setFocusable(true);
        preview.setClickable(true);
        preview.setOnClickListener(x -> startActivity(new Intent(SettingsActivity.this, MainActivity.class)));
        bar.addView(preview, new LinearLayout.LayoutParams(-2, dp(42)));
        top.addView(bar);
        FrameLayout topFrame = new FrameLayout(this);
        topFrame.addView(top, new FrameLayout.LayoutParams(width, -2, Gravity.CENTER_HORIZONTAL));
        column.addView(topFrame, new LinearLayout.LayoutParams(-1, -2));
        column.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        TextView hint = new TextView(this);
        hint.setText("يمين/يسار بين الأقسام  •  أعلى/أسفل بين الصفوف  •  اختيار لفتح الصف  •  رجوع للخروج");
        hint.setTextColor(0xFFC6BBA4);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        hint.setTypeface(font("Thmanyah-Regular"));
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(dp(72), dp(10), dp(72), dp(18));
        column.addView(hint, new LinearLayout.LayoutParams(-1, -2));
        root.addView(column, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        render();
    }

    @Override
    protected void onResume() {   // back in front (from the TV's settings, or opened again over itself): show what is set now
        super.onResume();
        render();
    }

    /** Rows added after this go to the tab n: in the list when it is the open one, else to the sink. */
    private void sec(int n) { target = n == tab ? list : sink; }

    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent e) {
        int c = e.getKeyCode();
        View f = getCurrentFocus();
        if ((c == android.view.KeyEvent.KEYCODE_DPAD_LEFT || c == android.view.KeyEvent.KEYCODE_DPAD_RIGHT) && f != null && f.getParent() == list) {
            if (e.getAction() == android.view.KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) {
                int next = tab + (c == android.view.KeyEvent.KEYCODE_DPAD_RIGHT ? -1 : 1);   // RTL: the first tab is on the right
                if (next >= 0 && next < TABS.length) { firstRow = true; selectTab(next); }
                else if (next == TABS.length && previewView != null) previewView.requestFocus();   // one more to the left: the preview
            }
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    private boolean isTabView(View v) {
        for (View x : tabViews) if (x == v) return true;
        return false;
    }

    private void selectTab(int which) {
        if (which == tab) return;
        tab = which;
        prefs.setSettingsTab(which);
        for (int i = 0; i < tabViews.length; i++) tabViews[i].setSelected(i == tab);
        render();
        ScrollView sv = null;
        for (android.view.ViewParent p = list.getParent(); p != null && sv == null; p = p.getParent()) if (p instanceof ScrollView) sv = (ScrollView) p;
        if (sv != null) sv.scrollTo(0, 0);
    }

    /** After a redraw: the row that had the focus keeps it; a new tab, or nothing focused, goes to the first row; the tabs keep theirs. */
    private void focusRows(int keep, boolean tabFocus) {
        // up from the first row lands on the open tab, and down from any tab on that first row (not on whatever is nearest)
        View first = null;
        for (int i = 0; i < list.getChildCount() && first == null; i++) if (list.getChildAt(i).isFocusable()) first = list.getChildAt(i);
        if (first != null) {
            if (first.getId() == View.NO_ID) first.setId(View.generateViewId());
            first.setNextFocusUpId(tabViews[tab].getId());
            for (TextView x : tabViews) x.setNextFocusDownId(first.getId());
        }
        if (tabFocus) return;
        View v = keep >= 0 && keep < list.getChildCount() ? list.getChildAt(keep) : null;
        if (v == null || !v.isFocusable()) {
            v = null;
            for (int i = 0; i < list.getChildCount() && v == null; i++) if (list.getChildAt(i).isFocusable()) v = list.getChildAt(i);
        }
        if (v != null) v.requestFocus();
    }

    private void render() {
        latinDigits = DigitsText.isLatin(prefs.digits());
        View focused = getCurrentFocus();
        int keep = focused != null && focused.getParent() == list ? list.indexOfChild(focused) : -1;
        if (firstRow) { keep = -1; firstRow = false; }
        final boolean tabFocus = focused != null && isTabView(focused);
        rendering = true;
        list.removeAllViews();
        sink = new LinearLayout(this);
        target = list;
        sec(0);
        if (Migration.unfinished(this)) migrationRow();   // 4.0: the old app is still there, or its screensaver is still the chosen one
        header("الموقع");
        row("المدينة", prefs.city()[0], R.drawable.ic_place, new Runnable() { public void run() { pickCity(); } });
        choiceRow("المنطقة الزمنية", "tz_choice", "", Prefs.TZ_CHOICES);
        row("طريقة حساب الصلاة", prefs.prayerMethodLabel(), R.drawable.ic_eye, new Runnable() { public void run() {
            pickChoice("طريقة حساب الصلاة", "pm_choice", "auto", Prefs.PM_CHOICES); } });

        header("العرض");
        noConv = true;
        row("الأرقام", Prefs.digitsLabel(prefs.digits()), R.drawable.ic_language,
            new Runnable() { public void run() { pickDigits(); } });
        noConv = false;
        header("التوقيت");
        row("بدء شاشة التوقف بعد", idleLabel(), R.drawable.ic_screensaver,
            new Runnable() { public void run() { pickIdle(); } });

        sec(1);
        header("لمسة مغربية (أعلى اليسار)");
        row("ما يظهر هنا (يتناوب)", cornerLabel(), R.drawable.ic_eye, new Runnable() { public void run() { pickCorner(); } });
        if (prefs.corner().contains("family"))
            row("مدن أهلي (" + arabicNum(prefs.family().size()) + "/" + arabicNum(Prefs.MAX_FAMILY) + ")", familyLabel(),
                R.drawable.ic_place, new Runnable() { public void run() { pickFamily(); } });
        if (prefs.corner().contains("markets")) {
            row("الأسواق (" + arabicNum(prefs.markets().size()) + "/" + arabicNum(Prefs.MAX_MARKETS) + ")", marketsLabel(),
                R.drawable.ic_trophy, new Runnable() { public void run() { pickMarkets(); } });
            row("عرض الأسواق", Prefs.mkStyleLabel(prefs.mkStyle()), R.drawable.ic_eye,
                new Runnable() { public void run() { pickMkStyle(); } });
        }

        header("بجانب الساعة");
        row("المحتوى", Prefs.panelLabel(prefs.panel()), R.drawable.ic_eye, new Runnable() { public void run() { pickPanel(-1); } });
        row("شكل البطاقة", Prefs.cardStyleLabel(prefs.cardStyle()), R.drawable.ic_eye,
            new Runnable() { public void run() { pickCardStyle(); } });

        header("الخانات والمناسبات");
        row("شريط السنة تحت الساعة", prefs.yearStrip() ? "يعمل · نقاط السنة تحت الساعة، واليوم يلمع" : "متوقف", R.drawable.ic_eye,
            new Runnable() { public void run() { pickOnOff("شريط السنة تحت الساعة", prefs.yearStrip(), v -> prefs.setYearStrip(v)); } });
        row("المناسبات الوطنية", Prefs.nationalLabel(prefs.national()), R.drawable.ic_eye,
            new Runnable() { public void run() { pickNational(); } });
        row("الخانة اليسرى", Prefs.slotLabel(prefs.slot()), R.drawable.ic_eye,
            new Runnable() { public void run() { pickSlot(); } });
        if ("countdown".equals(prefs.slot()) || !prefs.countdownDate().isEmpty())
            row("العدّ التنازلي", countdownLabel(), R.drawable.ic_eye,
                new Runnable() { public void run() { manageCountdown(); } });

        header("الصور");
        row("تبديل الصورة كل", minutesLabel(Integer.parseInt(prefs.photoMinutes())), R.drawable.ic_sync,
            new Runnable() { public void run() { pickPhotoMinutes(); } });
        row("صوري (" + arabicNum(Own.count(this)) + "/" + arabicNum(Own.MAX) + ")", ownLabel(), R.drawable.ic_eye,
            new Runnable() { public void run() { pickOwn(); } });
        sec(2);
        header("المباريات");
        row("الفرق المتابعة (" + arabicNum(prefs.teams().size()) + "/" + arabicNum(Prefs.MAX_TEAMS) + ")", teamsLabel(),
            R.drawable.ic_trophy, new Runnable() { public void run() { manageTeams(); } });

        row("الدوريات كاملة", leaguesLabel(), R.drawable.ic_trophy,
            new Runnable() { public void run() { pickLeagues(); } });

        row("رياضات أخرى", labels(Prefs.EVENTS, prefs.list("events"), "بدون"), R.drawable.ic_trophy,
            new Runnable() { public void run() { pickMany("events", "رياضات أخرى", Prefs.EVENTS, "بدون اختيار = بدون"); } });

        if ("shows".equals(prefs.panel()) || prefs.homePanels().contains("shows")) {   // only matter for the shows card
        header("جديد المسلسلات");
        row("التصنيفات", labels(Prefs.GENRES, prefs.list("genres"), "الكل"),
            R.drawable.ic_category, new Runnable() { public void run() { pickMany("genres", "التصنيفات", Prefs.GENRES); } });
        row("اللغات", labels(Prefs.LANGS, prefs.list("langs"), "الكل"),
            R.drawable.ic_language, new Runnable() { public void run() { pickMany("langs", "اللغات", Prefs.LANGS); } });
        row("مسلسلات مختارة", showsLabel(), R.drawable.ic_star, new Runnable() { public void run() { manageShows(); } });
        row("أقل تقييم", "0".equals(prefs.minRating()) ? "بدون حد" : prefs.minRating(),
            R.drawable.ic_star, new Runnable() { public void run() { pickRating(); } });
        }

        if ("stocks".equals(prefs.panel()) || prefs.homePanels().contains("stocks")) {   // only matters for the stocks card
        header("الأسهم");
        row("قائمة الأسهم (" + arabicNum(prefs.stocks().size()) + "/" + arabicNum(Prefs.MAX_STOCKS) + ")", stocksLabel(),
            R.drawable.ic_star, new Runnable() { public void run() { askStocks(); } });
        }

        contentRows();
        sec(3);
        tasbihRows();
        header("الأذان");
        row("تنبيه قبل الأذان", prefs.adhanBar() ? "يعمل · شريط في أعلى الشاشة آخر ١٠ دقائق، ثم حتى الإقامة" : "متوقف", R.drawable.ic_eye,
            new Runnable() { public void run() { pickOnOff("تنبيه قبل الأذان", prefs.adhanBar(), v -> prefs.setAdhanBar(v)); } });
        row("تعديل وقت الصلاة", prefs.prayerAdjOn() ? "يعمل · لكل صلاة دقائقها، من −٢٠ إلى +٢٠ (الشروق لا يتغيّر)" : "متوقف · الأوقات كما تُحسب", R.drawable.ic_eye,
            new Runnable() { public void run() { pickOnOff("تعديل وقت الصلاة", prefs.prayerAdjOn(), v -> prefs.setPrayerAdjOn(v)); } });
        if (prefs.prayerAdjOn()) for (int k = 0; k < PrayerAdj.COUNT; k++) adjRow(k);
        sec(4);
        header("الشاشة الرئيسية (تلفزيونك)");
        // up to three cards left of the clock; each place takes any card, or «بدون» (the ones after it move up)
        for (int k = 0; k < Prefs.HOME_CARDS; k++) {
            final int place = k;
            row(HOME_CARD_NAMES[k], Prefs.panelLabel(prefs.homePanelAt(k), "بدون"), R.drawable.ic_eye,
                new Runnable() { public void run() { pickPanel(place); } });
        }
        row("التحية والطقس", prefs.greeting() ? "تعمل" : "متوقفة", R.drawable.ic_eye,
            new Runnable() { public void run() { pickOnOff("التحية والطقس", prefs.greeting(), v -> prefs.setGreeting(v)); } });
        row("ترتيب التطبيقات المفضلة", "قائمة ترتّب فيها المفضلة بالأسهم", R.drawable.ic_star,
            new Runnable() { public void run() { openDockSettings(); } });

        header("نسخ الإعدادات");
        row("تصدير الإعدادات", "يحفظ إعداداتك كلها في ملف chouitv-settings.json بمجلد التنزيلات", R.drawable.ic_sync,
            new Runnable() { public void run() { exportSettings(); } });
        row("استيراد الإعدادات", "يقرأ الملف من التنزيلات ويعرض ما سيتغيّر قبل التطبيق", R.drawable.ic_sync,
            new Runnable() { public void run() { importSettings(); } });
        if (SettingsBackup.hasUndo(this)) {
            row("التراجع عن آخر استيراد", "يعيد الإعدادات كما كانت قبله", R.drawable.ic_sync,
                new Runnable() { public void run() { undoImport(); } });
        }

        // the updates come last in the tab, just above the version line
        header("التحديث");
        row("التحديث التلقائي للصفحة", prefs.autoUpdate() ? "يعمل · عند بدء الشاشة، كل ٦ ساعات" : "متوقف", R.drawable.ic_sync, new Runnable() { public void run() { pickOnOff("التحديث التلقائي للصفحة", prefs.autoUpdate(), v -> prefs.setAutoUpdate(v)); } });
        View last = row("تحديث التطبيق والشاشة الآن", "ينزّل أحدث إصدار من التطبيقين ومن الشاشة", R.drawable.ic_sync, new Runnable() { public void run() { updateNow(); } });
        noConv = true;   // a version number stays in Latin digits
        note(last, Updater.versionLines(this));   // at the very end: only the last row jumps to the bottom, not one in the middle
        noConv = false;

        focusRows(keep, tabFocus);
        list.post(() -> rendering = false);
    }

    // ---------- «تصدير الإعدادات / استيراد الإعدادات»: one JSON file, SettingsBackup ----------

    private void exportSettings() {
        new Thread(() -> {
            String where = null;
            try { where = SettingsBackup.save(this, SettingsBackup.export(this).toString(2)); } catch (Exception ignored) { }
            final String w = where;
            runOnUiThread(() -> toast(w != null ? "تم التصدير: " + w : "تعذّر حفظ الملف"));
        }).start();
    }

    private void importSettings() {
        new Thread(() -> {
            org.json.JSONObject file = null;
            String problem = "لا يوجد ملف chouitv-settings.json في مجلد التنزيلات";
            try {
                String text = SettingsBackup.load(this);
                if (text != null) {
                    file = new org.json.JSONObject(text);
                    if (SettingsBackup.validate(file) != null) { file = null; problem = "الملف ليس ملف إعدادات ChouiTV صالحاً"; }
                }
            } catch (Exception e) { file = null; problem = "الملف ليس ملف إعدادات ChouiTV صالحاً"; }
            final org.json.JSONObject f = file;
            final String p = problem;
            runOnUiThread(() -> { if (f == null) toast(p); else confirmImport(f, "استيراد الإعدادات"); });
        }).start();
    }

    private void undoImport() {
        final org.json.JSONObject before = SettingsBackup.readBefore(this);
        if (before == null) { toast("لا توجد نسخة سابقة"); return; }
        confirmImport(before, "التراجع عن آخر استيراد");
    }

    /** What would change, in a window, and the change only when the person says so. */
    private void confirmImport(final org.json.JSONObject file, String title) {
        final List<String> changes = SettingsBackup.diff(this, file);
        if (changes.isEmpty()) { toast("الإعدادات مطابقة للملف، لا شيء يتغيّر"); return; }
        StringBuilder b = new StringBuilder();
        int shown = Math.min(changes.size(), 12);
        for (int k = 0; k < shown; k++) b.append("• ").append(changes.get(k)).append('\n');
        if (changes.size() > shown) b.append("… و").append(changes.size() - shown).append(" أخرى");
        new Dlg(this).setTitle(title + ": " + changes.size() + " تغييراً").setMessage(b.toString().trim())
            .setPositiveButton("تطبيق", (d, i) -> new Thread(() -> {
                String done;
                try { done = "تم: " + SettingsBackup.apply(this, file) + " تغييراً"; } catch (Exception e) { done = "تعذّر التطبيق، لم يتغيّر شيء"; }
                final String msg = done;
                runOnUiThread(() -> { toast(msg); render(); });
            }).start())
            .setNegativeButton("إلغاء", null).show();
    }

    // ---------- pickers ----------

    private void pickCity() {
        final Cities.Region[] regions = Cities.REGIONS;
        String[] labels = new String[regions.length + 2];
        labels[0] = "بحث عن مدينة أو قرية…";
        labels[1] = "موقعي الحالي (تلقائي)";
        for (int i = 0; i < regions.length; i++) labels[i + 2] = regions[i].name + "  (" + regions[i].cities.length + ")";
        new Dlg(this).setTitle("المدينة")
            .setItems(labels, (d, i) -> { if (i == 0) searchCity(); else if (i == 1) useMyLocation(); else pickCityIn(regions[i - 2]); })
            .show();
    }

    private void pickCityIn(final Cities.Region r) {
        String[] names = new String[r.cities.length];
        int sel = -1;
        for (int i = 0; i < names.length; i++) {
            names[i] = r.cities[i][0];
            if (names[i].equals(prefs.city()[0])) sel = i;
        }
        new Dlg(this).setTitle(r.name)
            .setSingleChoiceItems(names, sel, (d, i) -> {
                prefs.setCity(r.cities[i][0], r.cities[i][1], r.cities[i][2], r.cities[i].length > 3 ? r.cities[i][3] : "");
                d.dismiss();
                render();
            }).show();
    }

    // ---- «موقعي الحالي»: the place from the TV's own location (its network provider, with the Location permission) and, when the TV cannot say,
    // from its Internet address (coarser: the town of the provider). The city is named after the nearest known one within 25 km, else by a reverse geocoder.
    private static final int REQ_LOCATION = 41;

    private void useMyLocation() {
        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {android.Manifest.permission.ACCESS_FINE_LOCATION}, REQ_LOCATION);
            return;
        }
        toast("جارٍ تحديد موقعك…");
        new Thread(() -> {
            String how = "موقع التلفزيون";
            double[] ll = deviceLocation();
            if (ll == null) { ll = ipLocation(); how = "عنوان الإنترنت (تقريبي)"; }
            if (ll == null) { runOnUiThread(() -> toast("تعذر تحديد الموقع")); return; }
            final double lat = ll[0], lon = ll[1];
            final String via = how;
            String name = nearestKnown(lat, lon);
            String zone = "";
            try {
                zone = new JSONObject(httpGet("https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon
                    + "&current=temperature_2m&timezone=auto")).optString("timezone", "");
            } catch (Exception ignored) { }
            if (name == null) {
                try {
                    JSONObject r = new JSONObject(httpGet("https://api.bigdatacloud.net/data/reverse-geocode-client?latitude=" + lat
                        + "&longitude=" + lon + "&localityLanguage=ar"));
                    for (String k : new String[] {"locality", "city", "principalSubdivision"}) {
                        if (name == null && !r.optString(k).isEmpty()) name = r.optString(k);
                    }
                } catch (Exception ignored) { }
            }
            final String place = name == null ? "موقعي" : name, tz = zone;
            runOnUiThread(() -> new Dlg(this).setTitle("تعيين المدينة")
                .setMessage(place + "\n(" + via + ")")
                .setPositiveButton("تعيين", (d, w) -> {
                    prefs.setCity(place, String.format(java.util.Locale.US, "%.4f", lat), String.format(java.util.Locale.US, "%.4f", lon), tz);
                    render();
                })
                .setNegativeButton("إلغاء", null).show());
        }).start();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code == REQ_LOCATION && results.length > 0 && results[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) useMyLocation();
        else if (code == REQ_LOCATION) toast("بدون إذن الموقع، اختر المدينة من القائمة");
    }

    /** the newest location the TV knows, else one asked of its network provider (up to 10 s); null when it has none */
    private double[] deviceLocation() {
        try {
            final android.location.LocationManager lm = (android.location.LocationManager) getSystemService(LOCATION_SERVICE);
            android.location.Location best = null;
            for (String p : new String[] {android.location.LocationManager.NETWORK_PROVIDER, android.location.LocationManager.GPS_PROVIDER,
                    android.location.LocationManager.PASSIVE_PROVIDER}) {
                try {
                    if (!lm.isProviderEnabled(p)) continue;
                    android.location.Location l = lm.getLastKnownLocation(p);
                    if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
                } catch (RuntimeException ignored) { }
            }
            if (best != null && System.currentTimeMillis() - best.getTime() < 7L * 24 * 3600 * 1000) return new double[] {best.getLatitude(), best.getLongitude()};
            if (!lm.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)) return null;
            final double[][] got = new double[1][];
            final java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
            final android.location.LocationListener listener = new android.location.LocationListener() {
                @Override public void onLocationChanged(android.location.Location l) { got[0] = new double[] {l.getLatitude(), l.getLongitude()}; done.countDown(); }
                @Override public void onStatusChanged(String provider, int status, android.os.Bundle extras) { }
                @Override public void onProviderEnabled(String provider) { }
                @Override public void onProviderDisabled(String provider) { }
            };
            runOnUiThread(() -> {
                try { lm.requestSingleUpdate(android.location.LocationManager.NETWORK_PROVIDER, listener, android.os.Looper.getMainLooper()); }
                catch (RuntimeException e) { done.countDown(); }
            });
            done.await(10, java.util.concurrent.TimeUnit.SECONDS);
            runOnUiThread(() -> { try { lm.removeUpdates(listener); } catch (RuntimeException ignored) { } });
            return got[0];
        } catch (Exception e) {
            return null;
        }
    }

    private double[] ipLocation() {
        try {
            JSONObject j = new JSONObject(httpGet("https://ipwho.is/"));
            if (j.optBoolean("success", false)) return new double[] {j.getDouble("latitude"), j.getDouble("longitude")};
        } catch (Exception ignored) { }
        return null;
    }

    /** the name of the nearest city of the built-in lists when it is within 25 km, else null */
    private String nearestKnown(double lat, double lon) {
        String best = null;
        double bestKm = 25;
        for (Cities.Region r : Cities.REGIONS) {
            for (String[] c : r.cities) {
                double dLat = Math.toRadians(Double.parseDouble(c[1]) - lat), dLon = Math.toRadians(Double.parseDouble(c[2]) - lon);
                double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(Math.toRadians(lat)) * Math.cos(Math.toRadians(Double.parseDouble(c[1])))
                    * Math.sin(dLon / 2) * Math.sin(dLon / 2);
                double km = 6371 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
                if (km < bestKm) { bestKm = km; best = c[0]; }
            }
        }
        return best;
    }

    private static String httpGet(String url) throws java.io.IOException {
        java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(15000);
        try (java.io.InputStream in = c.getInputStream()) {
            java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            return b.toString("UTF-8");
        } finally {
            c.disconnect();
        }
    }

    // any place in the world, from Open-Meteo's GeoNames search (it was Saudi Arabia only)
    private void searchCity() {
        ask("اسم المدينة أو القرية", q -> fetch("https://geocoding-api.open-meteo.com/v1/search?count=20&language=ar&name="
            + Uri.encode(q), json -> {
                JSONArray a = new JSONObject(json).optJSONArray("results");
                if (a == null || a.length() == 0) { toast("لا توجد نتائج"); return; }
                final int n = a.length();
                final String[] names = new String[n], lats = new String[n], lons = new String[n], zones = new String[n], rows = new String[n];
                for (int i = 0; i < n; i++) {
                    JSONObject c = a.getJSONObject(i);
                    names[i] = c.optString("name");
                    lats[i] = String.valueOf(c.optDouble("latitude"));
                    lons[i] = String.valueOf(c.optDouble("longitude"));
                    zones[i] = c.optString("timezone", "");   // the place's own; empty: the TV's (Prefs.cityTz)
                    String region = c.optString("admin1", ""), country = c.optString("country", "");
                    rows[i] = names[i] + (region.isEmpty() ? "" : "  ·  " + region) + (country.isEmpty() ? "" : "  ·  " + country);
                }
                new Dlg(this).setTitle("اختر المكان")
                    .setItems(rows, (d, i) -> { prefs.setCity(names[i], lats[i], lons[i], zones[i]); render(); }).show();
            }));
    }

    /** Followed teams: untick to remove, or add one (presets, league browsing, search). Up to 20. */
    private void manageTeams() {
        final List<String> cur = prefs.teams();
        String[] labels = new String[cur.size()];
        final boolean[] keep = new boolean[cur.size()];
        for (int i = 0; i < cur.size(); i++) { labels[i] = cur.get(i).split("\\|", 2)[1]; keep[i] = true; }
        AlertDialog.Builder b = new Dlg(this)
            .setTitle(cur.isEmpty() ? "لا توجد فرق متابعة" : "الفرق المتابعة (ألغِ التحديد للحذف)")
            .setPositiveButton("حفظ", (d, w) -> {
                List<String> v = new ArrayList<>();
                for (int i = 0; i < cur.size(); i++) if (keep[i]) v.add(cur.get(i));
                prefs.setList("teams", v);
                render();
            })
            .setNegativeButton("إلغاء", null);
        if (cur.size() < Prefs.MAX_TEAMS) b.setNeutralButton("إضافة فريق", (d, w) -> pickTeam(0));
        if (!cur.isEmpty()) b.setMultiChoiceItems(labels, keep, (d, i, checked) -> keep[i] = checked);
        b.show();
    }

    private void addTeam(String id, String name) {
        if (prefs.addTeam(id, name)) toast("تمت متابعة " + name);
        else toast(prefs.teams().size() >= Prefs.MAX_TEAMS ? "الحد الأعلى ٢٠ فريقاً" : "متابَع مسبقاً");
        render();
    }

    private void pickTeam(final int slot) {
        final List<String[]> opts = new ArrayList<>();
        for (String[] t : Prefs.MA_TEAMS) opts.add(t);
        for (String[] t : Prefs.TEAM_PRESETS) opts.add(t);
        String[] labels = new String[opts.size() + 2];
        for (int i = 0; i < opts.size(); i++) labels[i] = opts.get(i)[1];
        labels[opts.size()] = "تصفح الدوريات…";
        labels[opts.size() + 1] = "بحث عن أي فريق أو منتخب…";
        new Dlg(this).setTitle("إضافة فريق")
            .setItems(labels, (d, i) -> {
                if (i < opts.size()) addTeam(opts.get(i)[0], opts.get(i)[1]);
                else if (i == opts.size()) pickLeague(slot);
                else searchTeam(slot);
            }).show();
    }

    /** Our bundled list first (every club of these leagues, offline); TheSportsDB only if that is missing. */
    private void pickLeague(final int slot) {
        final String[] labels = new String[Prefs.LEAGUES.length + 1];
        for (int i = 0; i < Prefs.LEAGUES.length; i++) labels[i] = Prefs.LEAGUES[i][1];
        labels[Prefs.LEAGUES.length] = "المنتخبات";
        new Dlg(this).setTitle("الدوري")
            .setItems(labels, (d, i) -> {
                String league = i < Prefs.LEAGUES.length ? Prefs.LEAGUES[i][0] : Teams.NATIONAL;
                List<TeamSearch.Team> clubs = Teams.inLeague(this, league);
                if (!clubs.isEmpty()) showOurs(clubs, labels[i], false);
                else if (i < Prefs.LEAGUES.length)
                    fetch(SPORTS + "search_all_teams.php?l=" + Uri.encode(league), json -> showTeams(slot, json, labels[i], league));
                else toast("تعذّر تحميل القائمة، جرّب البحث عن الفريق");
            }).show();
    }

    private void searchTeam(final int slot) {
        ask("اسم الفريق بالعربية أو الإنجليزية (مثل الهلال أو \u2066Real Madrid\u2069)", q -> {
            List<TeamSearch.Team> found = Teams.search(this, q);
            if (!found.isEmpty()) showOurs(found.subList(0, Math.min(found.size(), 30)), "اختر الفريق", true);
            else if (!Arabic.hasArabic(q))   // a club outside our list: TheSportsDB's search, in English only
                fetch(SPORTS + "searchteams.php?t=" + Uri.encode(q), json -> showTeams(slot, json, "اختر الفريق", null));
            else toast("لا توجد نتائج، جرّب اسم الفريق بالإنجليزية");
        });
    }

    /** Clubs from our list: their Arabic names are already there, so following one needs no network. */
    private void showOurs(final List<TeamSearch.Team> clubs, String title, boolean withLeague) {
        String[] rows = new String[clubs.size()];
        for (int i = 0; i < rows.length; i++) {
            TeamSearch.Team t = clubs.get(i);
            rows[i] = Teams.label(this, t);
            if (withLeague && t.leagues.length > 0)
                rows[i] += "  ·  " + (Teams.NATIONAL.equals(t.leagues[0]) ? "منتخب" : leagueAr(t.leagues[0]));
        }
        new Dlg(this).setTitle(title)
            .setItems(rows, (d, i) -> {
                TeamSearch.Team t = clubs.get(i);
                addTeam(t.id, t.ar.isEmpty() ? Arabic.translit(t.en) : t.ar);
            }).show();
    }

    private static final String SPORTS = "https://www.thesportsdb.com/api/v1/json/123/";

    /** TheSportsDB's answer, when our list has nothing. league != null: keep only clubs that really play in it. */
    private void showTeams(final int slot, String json, String title, String league) throws Exception {
        JSONArray all = new JSONObject(json).optJSONArray("teams");
        JSONArray a = new JSONArray();
        for (int i = 0; all != null && i < all.length(); i++) {
            JSONObject t = all.getJSONObject(i);
            if (!"Soccer".equalsIgnoreCase(t.optString("strSport", "Soccer"))) continue;
            boolean inLeague = league == null;
            for (int k = 1; !inLeague && k <= 7; k++)
                inLeague = league.equalsIgnoreCase(t.optString(k == 1 ? "strLeague" : "strLeague" + k));
            if (inLeague) a.put(t);
        }
        if (a.length() == 0) {
            toast(league == null ? "لا توجد نتائج" : "تعذّر تحميل هذا الدوري، جرّب البحث عن الفريق");
            return;
        }
        final int n = Math.min(a.length(), 60);
        final String[] ids = new String[n], names = new String[n], rows = new String[n];
        final TeamSearch.Team[] ours = new TeamSearch.Team[n];
        for (int i = 0; i < n; i++) {
            JSONObject t = a.getJSONObject(i);
            ids[i] = t.optString("idTeam");
            names[i] = t.optString("strTeam");
            ours[i] = Teams.byId(this, ids[i]);
            rows[i] = (ours[i] != null ? Teams.label(this, ours[i]) : Arabic.team(this, names[i]))
                + "  ·  " + leagueAr(t.optString("strLeague", ""));
        }
        new Dlg(this).setTitle(title)
            .setItems(rows, (d, i) -> {
                if (ours[i] != null && !ours[i].ar.isEmpty()) addTeam(ids[i], ours[i].ar);
                else inArabic(names[i], Arabic.TEAM, ar -> addTeam(ids[i], ar));
            })
            .show();
    }

    private void pickMany(final String key, String title, final String[][] options) {
        pickMany(key, title, options, "بدون اختيار = الكل");
    }

    private void pickMany(final String key, String title, final String[][] options, String hint) {
        List<String> cur = prefs.list(key);
        String[] labels = new String[options.length];
        final boolean[] on = new boolean[options.length];
        for (int i = 0; i < options.length; i++) { labels[i] = options[i][1]; on[i] = cur.contains(options[i][0]); }
        new Dlg(this).setTitle(title + " (" + hint + ")")
            .setMultiChoiceItems(labels, on, (d, i, checked) -> on[i] = checked)
            .setPositiveButton("حفظ", (d, w) -> {
                List<String> v = new ArrayList<>();
                for (int i = 0; i < options.length; i++) if (on[i]) v.add(options[i][0]);
                prefs.setList(key, v);
                render();
            })
            .setNegativeButton("إلغاء", null).show();
    }

    /** Whole leagues: every game of every club in them is shown, up to Prefs.MAX_LEAGUES leagues. */
    private String leaguesLabel() {
        List<String> out = new ArrayList<>();
        for (String en : prefs.leagues()) out.add(leagueAr(en));
        return out.isEmpty() ? "بدون" : Prefs.join(out, "، ");
    }

    private void pickLeagues() {
        final List<String> cur = prefs.leagues();
        final String[] labels = new String[Prefs.LEAGUES.length];
        final boolean[] on = new boolean[Prefs.LEAGUES.length];
        for (int i = 0; i < labels.length; i++) { labels[i] = Prefs.LEAGUES[i][1]; on[i] = cur.contains(Prefs.LEAGUES[i][0]); }
        new Dlg(this)
            .setTitle("الدوريات كاملة (حتى " + arabicNum(Prefs.MAX_LEAGUES) + "، بدون اختيار = بدون)")
            .setMultiChoiceItems(labels, on, (d, i, checked) -> {
                int n = 0;
                for (boolean b : on) if (b) n++;
                if (checked && n > Prefs.MAX_LEAGUES) {
                    on[i] = false;
                    ((AlertDialog) d).getListView().setItemChecked(i, false);
                    toast("حتى " + arabicNum(Prefs.MAX_LEAGUES) + " دوريات");
                }
            })
            .setPositiveButton("حفظ", (d, w) -> {
                List<String> v = new ArrayList<>();
                for (int i = 0; i < on.length; i++) if (on[i]) v.add(Prefs.LEAGUES[i][0]);
                prefs.setList("leagues", v);
                render();
            })
            .setNegativeButton("إلغاء", null).show();
    }

    private void manageShows() {
        final List<String> cur = prefs.shows();
        String[] labels = new String[cur.size()];
        final boolean[] keep = new boolean[cur.size()];
        for (int i = 0; i < cur.size(); i++) { labels[i] = cur.get(i).split("\\|", 2)[1]; keep[i] = true; }
        AlertDialog.Builder b = new Dlg(this)
            .setTitle(cur.isEmpty() ? "لا توجد مسلسلات مختارة" : "المسلسلات المختارة (ألغِ التحديد للحذف)")
            .setPositiveButton("حفظ", (d, w) -> {
                List<String> v = new ArrayList<>();
                for (int i = 0; i < cur.size(); i++) if (keep[i]) v.add(cur.get(i));
                prefs.setList("shows", v);
                render();
            })
            .setNeutralButton("إضافة مسلسل", (d, w) -> searchShow())
            .setNegativeButton("إلغاء", null);
        if (!cur.isEmpty()) b.setMultiChoiceItems(labels, keep, (d, i, checked) -> keep[i] = checked);
        b.show();
    }

    private void searchShow() {
        ask("اسم المسلسل", q -> fetch("https://api.tvmaze.com/search/shows?q=" + Uri.encode(q), json -> {
            JSONArray a = new JSONArray(json);
            if (a.length() == 0) { toast("لا توجد نتائج"); return; }
            final int n = Math.min(a.length(), 15);
            final String[] ids = new String[n], names = new String[n], rows = new String[n];
            for (int i = 0; i < n; i++) {
                JSONObject s = a.getJSONObject(i).getJSONObject("show");
                ids[i] = String.valueOf(s.optInt("id"));
                names[i] = s.optString("name").replace("|", " ").replace(";", " ");
                String year = s.optString("premiered", "");
                rows[i] = names[i] + (year.length() >= 4 ? " (" + year.substring(0, 4) + ")" : "")
                    + "، " + labelOf(Prefs.LANGS, s.optString("language", ""));
            }
            new Dlg(this).setTitle("اختر المسلسل")
                .setItems(rows, (d, i) -> {
                    for (String s : prefs.shows()) if (s.startsWith(ids[i] + "|")) { toast("مضاف مسبقاً"); return; }
                    List<String> v = prefs.shows();
                    v.add(ids[i] + "|" + names[i]);
                    prefs.setList("shows", v);
                    toast("تمت إضافة " + names[i]);
                    render();
                }).show();
        }));
    }

    /** «ترتيب المفضلة» lives in the launcher (it owns the row). */
    private void openDockSettings() {
        try {
            startActivity(new Intent().setClassName("ma.chouitv.home", "ma.chouitv.home.DockActivity"));
        } catch (RuntimeException e) {
            toast("ثبّت الواجهة الرئيسية «تلفزيونك» أولاً");
        }
    }

    private static final String[] HOME_CARD_NAMES = {"البطاقة الأولى", "البطاقة الثانية", "البطاقة الثالثة"};

    /** The card beside the screensaver's clock (place -1), or place 0 to 2 of the cards on the launcher's home screen. */
    private void pickPanel(final int place) {
        final boolean home = place >= 0;
        String current = home ? prefs.homePanelAt(place) : prefs.panel();
        // the home places can also be left empty; «لا شيء» is that choice there, so it is «بدون» in the list
        String[] labels = new String[Prefs.PANELS.length];
        int sel = 0;
        for (int i = 0; i < labels.length; i++) {
            labels[i] = home && "none".equals(Prefs.PANELS[i][0]) ? "بدون" : Prefs.PANELS[i][1];
            if (Prefs.PANELS[i][0].equals(current)) sel = i;
        }
        String[] values = new String[labels.length], about = new String[labels.length];
        for (int i = 0; i < labels.length; i++) { values[i] = Prefs.PANELS[i][0]; about[i] = Prefs.about(Prefs.PANEL_ABOUT, values[i]); }
        pickWithPreview(home ? HOME_CARD_NAMES[place] : "بجانب الساعة", values, labels, about, sel,
            v -> prefs.previewUrl(this, v, prefs.cardStyle()),
            v -> { if (home) prefs.setHomePanelAt(place, v); else prefs.setPanel(v); });
    }

    private interface Url { String of(String value); }
    private interface Pick { void on(String value); }

    /**
     * A picker with the list on one side, one plain line under each name, and on the other side the focused choice drawn live by the
     * page itself: the card, over a dimmed photo of ours as it looks on the screen, in a box that is only as tall as the card (measured
     * from the page once it has drawn). The dialog is in the screen's glass design (Dlg). The WebView is made when the picker opens and
     * destroyed when it closes; it keeps the TV's text size out of it (setTextZoom(100) in ScreenView) like every other one of ours.
     */
    private void pickWithPreview(String title, final String[] values, final String[] labels, final String[] about, int selected,
                                 final Url urlFor, final Pick onPick) {
        final int previewW = 480, viewH = 300, pad = 22;   // dp: the page is laid out in a box like the home screen's, 480 wide
        final LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        box.setPadding(dp(12), dp(4), dp(12), dp(4));
        final android.widget.ListView lv = new android.widget.ListView(this);
        lv.setDividerHeight(0);
        lv.setSelector(getDrawable(R.drawable.glass_focus));
        lv.setAdapter(new android.widget.BaseAdapter() {
            @Override public int getCount() { return values.length; }
            @Override public Object getItem(int i) { return values[i]; }
            @Override public long getItemId(int i) { return i; }
            @Override public View getView(int i, View old, android.view.ViewGroup parent) {
                LinearLayout r = new LinearLayout(SettingsActivity.this);
                r.setOrientation(LinearLayout.VERTICAL);
                r.setPadding(dp(16), dp(10), dp(16), dp(10));
                TextView n = new TextView(SettingsActivity.this);
                n.setText(conv(labels[i]));
                n.setTextColor(0xFFF6EFE0);
                n.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19);
                n.setTypeface(font("Thmanyah-Medium"));
                r.addView(n);
                TextView a = new TextView(SettingsActivity.this);
                a.setText(conv(about[i]));
                a.setTextColor(0xFFC6BBA4);
                a.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                a.setTypeface(font("Thmanyah-Regular"));
                r.addView(a);
                return r;
            }
        });
        box.addView(lv, new LinearLayout.LayoutParams(0, dp(330), 1f));
        // the stage: our photo, dimmed, with the card on it; as tall as the card needs (setStageHeight), round-cornered, no empty space
        final FrameLayout stage = new FrameLayout(this);
        stage.setBackgroundColor(0xFF07150E);
        Bitmap photo = photo(3);
        if (photo != null) {
            ImageView bgPhoto = new ImageView(this);
            bgPhoto.setScaleType(ImageView.ScaleType.CENTER_CROP);
            bgPhoto.setImageBitmap(photo);
            bgPhoto.setColorFilter(new PorterDuffColorFilter(0x8C000000, PorterDuff.Mode.SRC_OVER));   // dim, as the screen's own veil does
            stage.addView(bgPhoto, new FrameLayout.LayoutParams(-1, -1));
        }
        stage.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(18)); }
        });
        stage.setClipToOutline(true);
        final LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(dp(previewW), dp(2 * pad + 150));
        sp.setMarginStart(dp(16));
        sp.gravity = Gravity.CENTER_VERTICAL;
        box.addView(stage, sp);

        final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        final android.webkit.WebView[] web = new android.webkit.WebView[1];
        final int[] shown = {-1}, loads = {0};
        // the card's height in CSS px (= dp) once the page has drawn it: asked twice, the data of some cards comes a moment later
        final Runnable measure = new Runnable() { public void run() {
            final android.webkit.WebView w = web[0];
            if (w == null) return;
            w.evaluateJavascript("(function(){var c=document.querySelector('.week');return c?Math.ceil(c.getBoundingClientRect().height):0})()", value -> {
                int h;
                try { h = Integer.parseInt(value.trim()); } catch (RuntimeException e) { return; }
                if (web[0] != w || h <= 0) return;
                sp.height = dp(Math.min(2 * pad + h, 2 * pad + viewH));
                stage.setLayoutParams(sp);
            });
        } };
        final Runnable update = new Runnable() { public void run() {
            int i = lv.getSelectedItemPosition();
            if (i < 0) i = Math.max(0, selected);   // nothing selected yet (touch mode): the current choice
            if (i == shown[0]) return;
            shown[0] = i;
            String u = urlFor.of(values[i]);
            // a different query each time makes it a real navigation: with only the #fragment changed it is the same document
            int hash = u.indexOf('#');
            u = hash < 0 ? u + "?p=" + (++loads[0]) : u.substring(0, hash) + "?p=" + (++loads[0]) + u.substring(hash);
            if (web[0] == null) {
                web[0] = ScreenView.createPreview(SettingsActivity.this, u);
                FrameLayout.LayoutParams wp = new FrameLayout.LayoutParams(-1, dp(viewH));   // the page's own box: a fixed one, the card is at its top
                wp.topMargin = dp(pad);
                wp.leftMargin = wp.rightMargin = dp(pad);
                stage.addView(web[0], wp);
            } else {   // the page reads its settings once, when it starts: load it again, no reload() (that would redo the old URL)
                web[0].loadUrl(u);
            }
            handler.removeCallbacks(measure);
            handler.postDelayed(measure, 900);
            handler.postDelayed(measure, 2600);
        } };
        lv.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) {
                handler.removeCallbacks(update);
                handler.postDelayed(update, 200);   // a long press of the arrow key goes past choices without drawing each
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p) { }
        });
        final AlertDialog dialog = new Dlg(this).setTitle(title).setView(box)
            .setNegativeButton("إلغاء", null).create();
        lv.setOnItemClickListener((p, v, pos, id) -> { onPick.on(values[pos]); dialog.dismiss(); render(); });
        dialog.setOnShowListener(d -> {
            dress(dialog);
            dialog.getWindow().setLayout(Math.min(getResources().getDisplayMetrics().widthPixels - dp(48), dp(940)), android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
            lv.requestFocus();
            lv.setSelection(Math.max(0, selected));
            handler.postDelayed(update, 50);
        });
        dialog.setOnDismissListener(d -> {   // nothing of the preview outlives the picker
            handler.removeCallbacks(update);
            handler.removeCallbacks(measure);
            if (web[0] != null) { ScreenView.release(web[0]); web[0] = null; }
        });
        dialog.show();
    }

    private void pickRating() {
        String[] labels = Prefs.MIN_RATINGS.clone();
        labels[0] = "بدون حد";
        int sel = 0;
        for (int i = 0; i < Prefs.MIN_RATINGS.length; i++) if (Prefs.MIN_RATINGS[i].equals(prefs.minRating())) sel = i;
        new Dlg(this).setTitle("أقل تقييم")
            .setSingleChoiceItems(labels, sel, (d, i) -> { prefs.setMinRating(Prefs.MIN_RATINGS[i]); d.dismiss(); render(); })
            .show();
    }

    private String cornerLabel() {
        List<String> out = new ArrayList<>();
        for (String k : prefs.corner()) out.add(labelOf(Prefs.CORNER, k));
        return Prefs.join(out, "، ");
    }

    /** One or more of Prefs.CORNER; with several, the corner turns from one to the next every 12 seconds. */
    private void pickCorner() {
        List<String> cur = prefs.corner();
        String[] labels = new String[Prefs.CORNER.length];
        final boolean[] on = new boolean[labels.length];
        for (int i = 0; i < labels.length; i++) { labels[i] = Prefs.CORNER[i][1]; on[i] = cur.contains(Prefs.CORNER[i][0]); }
        new Dlg(this).setTitle("ما يظهر أعلى اليسار (يتناوب كل ١٢ ثانية)")
            .setMultiChoiceItems(labels, on, (d, i, checked) -> on[i] = checked)
            .setPositiveButton("حفظ", (d, w) -> {
                List<String> v = new ArrayList<>();
                for (int i = 0; i < on.length; i++) if (on[i]) v.add(Prefs.CORNER[i][0]);
                if (v.isEmpty()) { toast("اختر خيارًا واحدًا على الأقل"); return; }
                prefs.setCorner(v);
                render();
            })
            .setNegativeButton("إلغاء", null).show();
    }

    private String familyLabel() { return Prefs.join(prefs.family(), "، "); }

    private void pickFamily() {
        final Cities.Region[] regions = Cities.REGIONS;
        String[] labels = new String[regions.length];
        for (int i = 0; i < regions.length; i++) labels[i] = regions[i].name;
        new Dlg(this).setTitle("مدن أهلي (حتى " + arabicNum(Prefs.MAX_FAMILY) + ") - اختر الجهة")
            .setItems(labels, (d, i) -> pickFamilyIn(regions[i])).show();
    }

    private void pickFamilyIn(final Cities.Region r) {
        final List<String> cur = prefs.family();
        final String[] names = new String[r.cities.length];
        final boolean[] on = new boolean[names.length];
        for (int i = 0; i < names.length; i++) { names[i] = r.cities[i][0]; on[i] = cur.contains(names[i]); }
        new Dlg(this).setTitle(r.name)
            .setMultiChoiceItems(names, on, (d, i, checked) -> on[i] = checked)
            .setPositiveButton("حفظ", (d, w) -> {
                List<String> v = new ArrayList<>(cur);
                for (int i = 0; i < names.length; i++) { v.remove(names[i]); if (on[i]) v.add(names[i]); }
                if (v.isEmpty() || v.size() > Prefs.MAX_FAMILY) {
                    toast("اختر من مدينة إلى " + arabicNum(Prefs.MAX_FAMILY) + " مدن");
                    return;
                }
                prefs.setFamily(v);
                render();
            })
            .setNegativeButton("إلغاء", null).show();
    }

    private String marketsLabel() {
        List<String> out = new ArrayList<>();
        for (String k : prefs.markets()) out.add(labelOf(Prefs.MARKETS, k));
        return Prefs.join(out, "، ");
    }

    /** Up to MAX_MARKETS; the order they are shown in is the order of the list. */
    private void pickMarkets() {
        List<String> cur = prefs.markets();
        String[] labels = new String[Prefs.MARKETS.length];
        final boolean[] on = new boolean[Prefs.MARKETS.length];
        for (int i = 0; i < labels.length; i++) { labels[i] = Prefs.MARKETS[i][1]; on[i] = cur.contains(Prefs.MARKETS[i][0]); }
        new Dlg(this).setTitle("الأسواق (حتى " + arabicNum(Prefs.MAX_MARKETS) + ")")
            .setMultiChoiceItems(labels, on, (d, i, checked) -> on[i] = checked)
            .setPositiveButton("حفظ", (d, w) -> {
                List<String> v = new ArrayList<>();
                for (int i = 0; i < on.length; i++) if (on[i]) v.add(Prefs.MARKETS[i][0]);
                if (v.isEmpty() || v.size() > Prefs.MAX_MARKETS) {
                    toast("اختر من سوق إلى " + arabicNum(Prefs.MAX_MARKETS) + " أسواق");
                    return;
                }
                prefs.setMarkets(v);
                render();
            })
            .setNegativeButton("إلغاء", null).show();
    }

    private String stocksLabel() { return Prefs.join(prefs.stocks(), "، "); }

    /** Codes (2222 = أرامكو) and US tickers (AAPL), separated by spaces or commas. */
    private void askStocks() {
        final EditText in = new EditText(this);
        in.setHint("مثال: \u20662222 1120 AAPL NVDA\u2069");
        in.setText(Prefs.join(prefs.stocks(), " "));
        in.setSingleLine(true);
        in.setInputType(InputType.TYPE_CLASS_TEXT);
        new Dlg(this).setTitle("قائمة الأسهم (رمز تداول من ٤ أرقام أو رمز أمريكي، حتى " + arabicNum(Prefs.MAX_STOCKS) + ")")
            .setView(in)
            .setPositiveButton("حفظ", (d, w) -> {
                List<String> v = new ArrayList<>();
                for (String s : in.getText().toString().split("[\\s,،]+")) {
                    s = s.trim().toUpperCase(java.util.Locale.ROOT);
                    if (s.isEmpty()) continue;
                    if (!s.matches(Prefs.STOCK_PATTERN)) { toast("رمز غير صالح: " + s); return; }
                    if (!v.contains(s)) v.add(s);
                }
                if (v.isEmpty() || v.size() > Prefs.MAX_STOCKS) {
                    toast("أدخل من رمز إلى " + arabicNum(Prefs.MAX_STOCKS) + " رموز");
                    return;
                }
                prefs.setStocks(v);
                render();
            })
            .setNegativeButton("إلغاء", null).show();
        in.requestFocus();
    }

    private void pickMkStyle() {
        String[] labels = new String[Prefs.MK_STYLES.length];
        int sel = 0;
        for (int i = 0; i < labels.length; i++) {
            labels[i] = Prefs.MK_STYLES[i][1];
            if (Prefs.MK_STYLES[i][0].equals(prefs.mkStyle())) sel = i;
        }
        new Dlg(this).setTitle("عرض الأسواق")
            .setSingleChoiceItems(labels, sel, (d, i) -> { prefs.setMkStyle(Prefs.MK_STYLES[i][0]); d.dismiss(); render(); })
            .show();
    }

    private void pickCardStyle() {
        String[] labels = new String[Prefs.CARD_STYLES.length];
        int sel = 0;
        for (int i = 0; i < labels.length; i++) {
            labels[i] = Prefs.CARD_STYLES[i][1];
            if (Prefs.CARD_STYLES[i][0].equals(prefs.cardStyle())) sel = i;
        }
        String[] values = new String[labels.length], about = new String[labels.length];
        for (int i = 0; i < labels.length; i++) { values[i] = Prefs.CARD_STYLES[i][0]; about[i] = Prefs.about(Prefs.CARD_STYLE_ABOUT, values[i]); }
        // the preview is the card you have now, in the style under the cursor (the prayer times when there is no card)
        final String card = "none".equals(prefs.panel()) ? "prayers" : prefs.panel();
        pickWithPreview("شكل البطاقة", values, labels, about, sel, v -> prefs.previewUrl(this, card, v), v -> prefs.setCardStyle(v));
    }

    private void pickSlot() {
        String[] labels = new String[Prefs.SLOTS.length];
        int sel = 0;
        for (int i = 0; i < labels.length; i++) {
            labels[i] = Prefs.SLOTS[i][1];
            if (Prefs.SLOTS[i][0].equals(prefs.slot())) sel = i;
        }
        new Dlg(this).setTitle("الخانة اليسرى")
            .setSingleChoiceItems(labels, sel, (d, i) -> { prefs.setSlot(Prefs.SLOTS[i][0]); d.dismiss(); render(); })
            .show();
    }

    private String countdownLabel() {
        if (prefs.countdownDate().isEmpty()) return "لم يُحدَّد";
        String name = prefs.countdownName();
        return CountdownText.label(prefs.countdownDate(), name);
    }

    /** The date is set from the computer (optimize/saver.sh countdown); here it can be cleared. */
    private void manageCountdown() {
        if (prefs.countdownDate().isEmpty()) {
            new Dlg(this).setTitle("العدّ التنازلي")
                .setMessage("لم يُحدَّد تاريخ. يحدده المساعد على الكمبيوتر.")
                .setPositiveButton("حسنًا", null).show();
            return;
        }
        new Dlg(this).setTitle("العدّ التنازلي")
            .setMessage(countdownLabel())
            .setPositiveButton("مسح", (d, w) -> { prefs.setCountdown("", ""); render(); })
            .setNegativeButton("إلغاء", null).show();
    }

    private void pickDigits() {
        String[] labels = new String[Prefs.DIGITS.length];
        int sel = 0;
        for (int i = 0; i < labels.length; i++) {
            labels[i] = Prefs.DIGITS[i][1];
            if (Prefs.DIGITS[i][0].equals(prefs.digits())) sel = i;
        }
        new Dlg(this).raw().setTitle("الأرقام")
            .setSingleChoiceItems(labels, sel, (d, i) -> { prefs.setDigits(Prefs.DIGITS[i][0]); d.dismiss(); render(); })
            .show();
    }

    /** An on/off setting is a window like the others: «يعمل» or «متوقف», the current one marked. */
    private void pickOnOff(String title, boolean on, final java.util.function.Consumer<Boolean> set) {
        new Dlg(this).setTitle(title)
            .setSingleChoiceItems(new String[] {"يعمل", "متوقف"}, on ? 0 : 1, (d, i) -> { set.accept(i == 0); d.dismiss(); render(); })
            .show();
    }

    // ---- the content cards: each has an on/off window and its own choices ----

    private void contentRows() {
        header("الأخبار");
        boolean on = prefs.flag("news_on", false);
        flagRow("الأخبار", "news_on", false, on ? "يعمل · " + labelOf(Prefs.NEWS_SRC, prefs.choice("news_src", "hespress")) : "متوقف");
        if (on) {
            choiceRow("المصدر", "news_src", "hespress", Prefs.NEWS_SRC);
            if ("hespress".equals(prefs.choice("news_src", "hespress"))) choiceRow("الصنف", "news_cat", "all", Prefs.NEWS_CAT);
            choiceRow("تحديث الأخبار", "news_ref", "30", Prefs.NEWS_REF);
            choiceRow("تبديل العنوان", "news_rot", "20", Prefs.NEWS_ROT);
            choiceRow("مكان الأخبار", "news_at", "E", Prefs.PLACES);
        }
        header("حكمة من التاريخ");
        on = prefs.flag("wis_on", true);
        flagRow("حكمة من التاريخ", "wis_on", on ? "يعمل · " + labelOf(Prefs.WIS_CAT, prefs.choice("wis_cat", "all")) : "متوقف");
        if (on) {
            choiceRow("الفئة", "wis_cat", "all", Prefs.WIS_CAT);
            choiceRow("طول النص", "wis_len", "one", Prefs.WIS_LEN);
            choiceRow("تبديل الحكمة", "wis_rot", "300", Prefs.WIS_ROT);
            choiceRow("مكان الحكمة", "wis_at", "B", Prefs.PLACES);
        }
    }

    // ---- «التسبيح»: the guided tasbih ----

    private void tasbihRows() {
        header("التسبيح");
        boolean on = prefs.flag("ts_on", true);
        flagRow("التسبيح", "ts_on", true, on ? "يعمل · " + labelOf(Prefs.TS_WHEN, prefs.choice("ts_when", "always")) + " · "
            + labelOf(Prefs.TS_TYPE, prefs.choice("ts_type", "all")) : "متوقف");
        if (!on) return;
        choiceRow("النوع", "ts_type", "all", Prefs.TS_TYPE);
        if ("all".equals(prefs.choice("ts_type", "all"))) {
            row("تسبيحات دورة «الكل»", prefs.tsSet().size() == 12 ? "الاثنتا عشرة كلها" : arabicNum(prefs.tsSet().size()) + " من 12",
                R.drawable.ic_eye, new Runnable() { public void run() { pickTsSet(); } });
        }
        choiceRow("العدد", "ts_cnt", "auto", Prefs.TS_CNT);
        choiceRow("الإيقاع", "ts_rate", "auto", Prefs.TS_RATE);
        choiceRow("التوقيت", "ts_when", "always", Prefs.TS_WHEN);
        if (!"always".equals(prefs.choice("ts_when", "always"))) choiceRow("مدة العرض", "ts_post", "10", Prefs.TS_POST);
        choiceRow("الاستراحة بين الدورات", "ts_rest", "1", Prefs.TS_REST);
        flagRow("إظهار الفضل والمصدر", "ts_fadl", true, prefs.flag("ts_fadl", true) ? "يعمل" : "متوقف");
        choiceRow("شكل العدّاد", "ts_shape", "beads", Prefs.TS_SHAPE);
        choiceRow("الحركة", "ts_motion", "normal", Prefs.TS_MOTION);
        choiceRow("مكان التسبيح", "ts_at", "E", Prefs.PLACES);
    }

    /** The formulas of the «الكل» round: at least one. */
    private void pickTsSet() {
        final List<String> cur = prefs.tsSet();
        String[] labels = new String[Prefs.TS_LIST.length];
        final boolean[] on = new boolean[labels.length];
        for (int i = 0; i < labels.length; i++) { labels[i] = Prefs.TS_LIST[i][1]; on[i] = cur.contains(Prefs.TS_LIST[i][0]); }
        new Dlg(this).setTitle("تسبيحات دورة «الكل»")
            .setMultiChoiceItems(labels, on, (d, i, checked) -> on[i] = checked)
            .setPositiveButton("حفظ", (d, w) -> {
                List<String> v = new ArrayList<>();
                for (int i = 0; i < on.length; i++) if (on[i]) v.add(Prefs.TS_LIST[i][0]);
                if (v.isEmpty()) { toast("اختر تسبيحة واحدة على الأقل"); return; }
                prefs.setTsSet(v);
                render();
            })
            .setNegativeButton("إلغاء", null).show();
    }

    private void flagRow(final String title, final String key, String value) { flagRow(title, key, true, value); }

    private void flagRow(final String title, final String key, final boolean def, String value) {
        row(title, value, R.drawable.ic_eye, new Runnable() { public void run() {
            pickOnOff(title, prefs.flag(key, def), v -> prefs.setFlag(key, v)); } });
    }

    private void choiceRow(final String title, final String key, final String def, final String[][] options) {
        row(title, labelOf(options, prefs.choice(key, def)), R.drawable.ic_eye, new Runnable() { public void run() {
            pickChoice(title, key, def, options); } });
    }

    private void pickChoice(String title, final String key, String def, final String[][] options) {
        String cur = prefs.choice(key, def);
        String[] labels = new String[options.length];
        int sel = 0;
        for (int i = 0; i < labels.length; i++) { labels[i] = options[i][1]; if (options[i][0].equals(cur)) sel = i; }
        new Dlg(this).setTitle(title)
            .setSingleChoiceItems(labels, sel, (d, i) -> { prefs.setChoice(key, options[i][0]); d.dismiss(); render(); })
            .show();
    }

    private void pickNational() {
        String[] labels = new String[Prefs.NATIONAL.length];
        int sel = 0;
        for (int i = 0; i < labels.length; i++) {
            labels[i] = Prefs.NATIONAL[i][1];
            if (Prefs.NATIONAL[i][0].equals(prefs.national())) sel = i;
        }
        new Dlg(this).setTitle("المناسبات الوطنية")
            .setSingleChoiceItems(labels, sel, (d, i) -> { prefs.setNational(Prefs.NATIONAL[i][0]); d.dismiss(); render(); })
            .show();
    }

    private void pickPhotoMinutes() {
        String[] labels = new String[Prefs.PHOTO_MINUTES.length];
        int sel = 0;
        for (int i = 0; i < labels.length; i++) {
            labels[i] = minutesLabel(Integer.parseInt(Prefs.PHOTO_MINUTES[i]));
            if (Prefs.PHOTO_MINUTES[i].equals(prefs.photoMinutes())) sel = i;
        }
        new Dlg(this).setTitle("تبديل الصورة كل")
            .setSingleChoiceItems(labels, sel, (d, i) -> { prefs.setPhotoMinutes(Prefs.PHOTO_MINUTES[i]); d.dismiss(); render(); })
            .show();
    }

    private String ownLabel() {
        if (Own.count(this) == 0) return "لا توجد صور · يضيفها مساعدك الذكي";
        return Own.ONLY.equals(Own.mode(this)) ? "صوري فقط" : "تُضاف إلى صور التطبيق";
    }

    /** «صوري»: photos only arrive through the owner's assistant; here the owner picks how they show, or removes them all. */
    private void pickOwn() {
        if (Own.count(this) == 0) {
            toast("اطلب من مساعدك الذكي إضافة صورك (حتى ١٠)");
            return;
        }
        String[] items = {"تُضاف إلى صور التطبيق", "صوري فقط", "حذف كل صوري"};
        new Dlg(this).setTitle("صوري")
            .setSingleChoiceItems(items, Own.ONLY.equals(Own.mode(this)) ? 1 : 0, (d, i) -> {
                d.dismiss();
                if (i == 2) { confirmClearOwn(); return; }
                prefs.setOwnMode(i == 1 ? Own.ONLY : Own.MIX);
                prefs.bumpOwnRev();
                render();
            })
            .show();
    }

    private void confirmClearOwn() {
        new Dlg(this).setTitle("حذف كل صوري؟")
            .setPositiveButton("حذف", (d, i) -> { Own.clear(this); render(); })
            .setNegativeButton("إلغاء", null)
            .show();
    }

    /** The TV's own idle delay. Writing it needs WRITE_SETTINGS, which TVs rarely grant: then its own screen opens. */
    private void pickIdle() {
        if (!Prefs.canWriteSettings(this)) {
            toast("غيّرها من إعدادات التلفاز، أو بأمر من مساعدك");
            openDreamSettings();
            return;
        }
        String[] labels = new String[Prefs.IDLE_MINUTES.length];
        int sel = -1;
        long now = Prefs.idleMs(this);
        for (int i = 0; i < labels.length; i++) {
            labels[i] = minutesLabel(Integer.parseInt(Prefs.IDLE_MINUTES[i]));
            if (Long.parseLong(Prefs.IDLE_MINUTES[i]) * 60000 == now) sel = i;
        }
        new Dlg(this).setTitle("بدء شاشة التوقف بعد")
            .setSingleChoiceItems(labels, sel, (d, i) -> {
                try {
                    Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT,
                        Integer.parseInt(Prefs.IDLE_MINUTES[i]) * 60000);
                } catch (SecurityException e) {
                    toast("غيّرها من إعدادات التلفاز، أو بأمر من مساعدك");
                }
                d.dismiss();
                render();
            })
            .show();
    }

    private String idleLabel() {
        long ms = Prefs.idleMs(this);
        if (ms <= 0) return "يحدده التلفاز";
        if (ms % 60000 != 0) return arabicNum((int) (ms / 1000)) + " ثانية";
        return minutesLabel((int) (ms / 60000));
    }

    /** دقيقة واحدة · دقيقتان · ٥ دقائق · ١٥ دقيقة */
    private static String minutesLabel(int n) {
        if (n == 1) return "دقيقة واحدة";
        if (n == 2) return "دقيقتان";
        return arabicNum(n) + (n <= 10 ? " دقائق" : " دقيقة");
    }

    /**
     * «تحديث التطبيق والشاشة الآن»: the page of the screen first (as before), then the two apps: the home app is asked to check the
     * newest version now (it downloads both APKs, checks them and asks for one OK each). Before, this only refreshed the page, so a TV
     * that said «يتوفر 3.175» answered that everything was up to date.
     */
    private void updateNow() {
        toast("جارٍ التحقق…");
        new Thread(() -> {
            final String page = Updater.check(getApplicationContext(), true);
            runOnUiThread(() -> {
                if (isFinishing()) return;
                render();
                askAppsUpdate(page);
            });
        }).start();
    }

    /**
     * Two steps. First the home app only says whether a newer version exists (dry). If there is one, the home screen is brought to the front and
     * only then is the install started: Android refuses to show its «install?» screen to an app that is in the background, and the settings of
     * this app are what is in front now.
     */
    private void askAppsUpdate(final String page) { askAppsUpdate(page, true); }

    private void askAppsUpdate(final String page, final boolean dry) {
        android.content.BroadcastReceiver answer = new android.content.BroadcastReceiver() {
            @Override public void onReceive(android.content.Context c, Intent i) {
                String data = getResultData();
                if (isFinishing()) return;
                if (data == null) toast("تعذّر الوصول إلى التطبيق الرئيسي: " + page);
                else if (data.startsWith("update: available")) {
                    toast("يتوفر إصدار جديد: جارٍ فتح الشاشة الرئيسية للتثبيت · اضغط OK عند السؤال");
                    try {
                        startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    } catch (RuntimeException ignored) { }
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> askAppsUpdate(page, false), 2500);
                }
                else if (data.startsWith("update: installing") || data.startsWith("update: started")) toast("جارٍ تنزيل الإصدار الجديد… في شاشة التثبيت حرّك الريموت يمينًا إلى «تثبيت» ثم اضغط OK");
                else if (data.startsWith("update: up to date")) toast("التطبيقان على أحدث إصدار · " + page);
                else if (data.startsWith("update: needs permission")) {   // Android needs the person's «allow» once for the home app
                    toast("فعّل «السماح بهذا المصدر» لتلفزيونك ثم عُد واضغط التحديث من جديد");
                    try {
                        startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, android.net.Uri.parse("package:ma.chouitv.home")));
                    } catch (RuntimeException e) {
                        toast("الأمر عبر ADB: appops set ma.chouitv.home REQUEST_INSTALL_PACKAGES allow");
                    }
                }
                else if (data.contains("busy") || data.contains("in progress")) toast("التحديث جارٍ بالفعل");
                else toast("تعذّر تحديث التطبيق: " + data.replace("update: ", ""));
            }
        };
        Intent ask = new Intent("ma.chouitv.home.UPDATE_NOW").setPackage("ma.chouitv.home").putExtra("dry", dry);
        try {
            sendOrderedBroadcast(ask, null, answer, null, 0, null, null);
        } catch (RuntimeException e) {
            toast("التطبيق الرئيسي غير مثبّت · " + page);
        }
    }

    // ---------- the move to the one-app version (4.0) ----------

    /** At the very top while the person has a step left: the data came over by itself, the two steps below are theirs to do. */
    private void migrationRow() {
        boolean old = Migration.installed(this, MigrationCore.OLD_PACKAGE);
        row("إكمال الانتقال إلى النسخة الجديدة", old ? "انتقلت إعداداتك ومهامك وصورك · بقيت خطوتان: شاشة التوقف، ثم إزالة التطبيق القديم"
                : "بقيت خطوة واحدة: اختر شاشة التوقف الجديدة", R.drawable.ic_screensaver,
            new Runnable() { public void run() { migrationGuide(); } });
    }

    private boolean oldDreamChosen() {
        return MigrationCore.OLD_PACKAGE.equals(MigrationCore.dreamOwner(Migration.chosenDream(this)));
    }

    private void migrationGuide() {
        final boolean old = Migration.installed(this, MigrationCore.OLD_PACKAGE);
        final List<String> items = new ArrayList<>();
        items.add("١. اختر شاشة التوقف");
        if (old) items.add("٢. إزالة التطبيق القديم");
        new Dlg(this).setTitle("إكمال الانتقال إلى النسخة الجديدة")
            .setItems(items.toArray(new String[0]), (d, i) -> { if (i == 0) migrationPickDream(); else migrationRemoveOld(); })
            .setNegativeButton("لاحقاً", null).show();
    }

    private void migrationPickDream() {
        new Dlg(this).setTitle("اختر شاشة التوقف")
            .setMessage("انتقلت إعداداتك ومهامك وصورك إلى هذا التطبيق. ستفتح إعدادات التلفزيون: اختر «تفضيلات الجهاز» ثم «شاشة التوقف»."
                + " ستجد «شاشة تلفزيونك» مرتين: المحدَّدة الآن هي القديمة، فاختر الأخرى. هي شاشة هذا التطبيق نفسه."
                + " حتى تختارها تبقى الشاشة القديمة تعمل كما هي.")
            .setPositiveButton("فتح الإعدادات", (d, i) -> openDreamSettings())
            .setNegativeButton("إلغاء", null).show();
    }

    private void migrationRemoveOld() {
        String msg = "سيُزال التطبيق القديم «شاشة تلفزيونك». لن تضيع إعداداتك ومهامك وصورك: هي محفوظة في هذا التطبيق."
            + " سيسألك التلفزيون للتأكيد: اختر «موافق».";
        final Dlg b = new Dlg(this);
        b.setTitle("إزالة التطبيق القديم");
        if (oldDreamChosen()) {   // removing it now would leave the TV without its screensaver
            b.setMessage("شاشة التوقف الحالية ما زالت القديمة. اختر الجديدة أولاً حتى لا تتوقف الشاشة.\n\n" + msg)
                .setPositiveButton("اختر الشاشة أولاً", (d, i) -> migrationPickDream())
                .setNeutralButton("إزالة على أي حال", (d, i) -> deleteOldApp());
        } else {
            b.setMessage(msg).setPositiveButton("إزالة", (d, i) -> deleteOldApp());
        }
        b.setNegativeButton("إلغاء", null).show();
    }

    private void deleteOldApp() {
        try {
            startActivity(new Intent(Intent.ACTION_DELETE, android.net.Uri.parse("package:" + MigrationCore.OLD_PACKAGE)));
        } catch (RuntimeException e) {
            toast("تعذّر فتح شاشة الإزالة · أزل «شاشة تلفزيونك» القديم من إعدادات التلفزيون ← التطبيقات");
        }
    }

    private void openDreamSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_DREAM_SETTINGS));
        } catch (ActivityNotFoundException e) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    // ---------- labels ----------

    /** Wikidata's Arabic name (or a transliteration) fetched off the UI thread. */
    private void inArabic(final String en, final java.util.regex.Pattern kind, final Text then) {
        toast("جارٍ جلب الاسم بالعربية…");
        new Thread(() -> {
            String k = kind == Arabic.TEAM ? Arabic.known(getApplicationContext(), en) : null;
            final String ar = k != null ? k : Arabic.lookup(en, kind);
            runOnUiThread(() -> { if (!isFinishing()) then.got(ar); });
        }).start();
    }

    private static String leagueAr(String en) {
        for (String[] l : Prefs.LEAGUES) if (l[0].equalsIgnoreCase(en)) return l[1];
        return Arabic.translit(en);
    }

    private static String labelOf(String[][] options, String key) {
        for (String[] o : options) if (o[0].equalsIgnoreCase(key)) return o[1];
        return Arabic.translit(key);
    }

    private String teamsLabel() {
        List<String> names = new ArrayList<>();
        for (String t : prefs.teams()) names.add(t.split("\\|", 2)[1]);
        return names.isEmpty() ? "لا شيء" : Prefs.join(names, "، ");
    }

    /** the «الأرقام» setting as this screen draws it (read at every render): every number and label goes through conv() */
    private static volatile boolean latinDigits;
    private boolean noConv;   // the «الأرقام» row itself says «هندية ١٢٣» and «عربية 123»: both stay as they are

    private static String conv(String s) { return DigitsText.convert(s, latinDigits); }

    private static String arabicNum(int n) { return conv(String.valueOf(n)); }

    private String showsLabel() {
        List<String> v = prefs.shows();
        if (v.isEmpty()) return "لا شيء";
        List<String> names = new ArrayList<>();
        for (String s : v) names.add(s.split("\\|", 2)[1]);
        return Prefs.join(names, "، ");
    }

    private static String labels(String[][] options, List<String> picked, String none) {
        List<String> out = new ArrayList<>();
        for (String[] o : options) if (picked.contains(o[0])) out.add(o[1]);
        return out.isEmpty() ? none : Prefs.join(out, "، ");
    }

    // ---------- small UI helpers ----------

    private final java.util.Map<String, Typeface> fonts = new java.util.HashMap<>();

    /** Thmanyah, shared with the launcher's assets; falls back to the system font. */
    private Typeface font(String name) {
        Typeface tf = fonts.get(name);
        if (tf == null) {
            tf = FontSource.typeface(this, name);
            if (tf == null) tf = Typeface.DEFAULT;
            fonts.put(name, tf);
        }
        return tf;
    }

    /** the dialogs of this screen: the glass theme (styles.xml), and Thmanyah on every word of them once they show */
    private static final class Dlg extends AlertDialog.Builder {
        private final SettingsActivity owner;
        private boolean raw;   // its words keep their own digits (the «الأرقام» picker shows both kinds)

        Dlg(SettingsActivity a) { super(a, R.style.UrDialog); owner = a; }

        Dlg raw() { raw = true; return this; }

        @Override public AlertDialog create() {
            final AlertDialog d = super.create();
            d.setOnShowListener(x -> owner.dress(d, raw));
            return d;
        }
    }

    void dress(AlertDialog d) { dress(d, false); }

    void dress(AlertDialog d, boolean raw) { if (d.getWindow() != null) dressView(d.getWindow().getDecorView(), raw); }

    /** Thmanyah on every text under this view, and on the ones that come later (a list makes its rows when it is shown) */
    private void dressView(View v, final boolean raw) {
        if (v instanceof TextView) {
            TextView t = (TextView) v;
            t.setTypeface(font("Thmanyah-Medium"));
            if (!raw && !(v instanceof EditText)) {   // the numbers of the dialogs follow the «الأرقام» setting too
                String was = t.getText().toString(), now = conv(was);
                if (!now.equals(was)) t.setText(now);
            }
        }
        if (!(v instanceof ViewGroup)) return;
        ViewGroup g = (ViewGroup) v;
        for (int i = 0; i < g.getChildCount(); i++) dressView(g.getChildAt(i), raw);
        g.setOnHierarchyChangeListener(new ViewGroup.OnHierarchyChangeListener() {
            @Override public void onChildViewAdded(View parent, View child) { dressView(child, raw); }
            @Override public void onChildViewRemoved(View parent, View child) { }
        });
    }

    /** One of our backgrounds (assets/backgrounds, the screen's own photos), a different one each day, at 1/sample of its size; null if none can be read */
    private Bitmap photo(int sample) {
        try {
            List<String> names = new ArrayList<>();
            String[] all = getAssets().list("backgrounds");
            if (all != null) for (String n : all) if (n.endsWith(".jpg")) names.add(n);
            if (names.isEmpty()) return null;
            java.util.Collections.sort(names);
            InputStream in = getAssets().open("backgrounds/" + names.get((int) ((System.currentTimeMillis() / 86400000L) % names.size())));
            try {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inSampleSize = sample;
                return BitmapFactory.decodeStream(in, null, o);
            } finally { try { in.close(); } catch (Exception ignored) { } }
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }

    private void header(String text) {
        TextView t = new TextView(this);
        t.setText(conv(text));
        t.setTextColor(0xFFD6AF6B);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        t.setTypeface(font("Thmanyah-Medium"));
        t.setPadding(dp(10), dp(18), dp(10), dp(6));
        target.addView(t);
    }

    /** Plain lines at the end, in no button: nothing to focus, so the last row scrolls them into view. */
    private void note(View last, String... lines) {
        TextView t = new TextView(this);
        t.setText(noConv ? android.text.TextUtils.join("\n", lines) : conv(android.text.TextUtils.join("\n", lines)));
        t.setTextColor(0xFFC6BBA4);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        t.setTypeface(font("Thmanyah-Regular"));   // a version line has digits and Latin letters: Thmanyah has them
        t.setLineSpacing(dp(4), 1f);
        t.setGravity(Gravity.START);
        t.setTextDirection(View.TEXT_DIRECTION_RTL);
        t.setPadding(dp(6), dp(18), dp(6), dp(24));
        target.addView(t);
        final View.OnFocusChangeListener was = last.getOnFocusChangeListener();
        last.setOnFocusChangeListener((v, has) -> {
            if (was != null) was.onFocusChange(v, has);
            if (!has) return;
            for (android.view.ViewParent p = list.getParent(); p != null; p = p.getParent())   // list sits in a frame in the scroll view
                if (p instanceof ScrollView) { final ScrollView sv = (ScrollView) p; v.post(() -> sv.fullScroll(View.FOCUS_DOWN)); break; }
        });
    }

    /**
     * One prayer of «تعديل وقت الصلاة»: «الفجر +٢ د». Left adds a minute and right takes one off (the reading direction of an Arabic
     * screen runs leftwards), from −20 to +20; OK puts it back to zero. The text changes where it is, so the focus and the scroll stay.
     */
    private void adjRow(final int k) {
        row(PrayerAdj.label(k, prefs.prayerAdj()[k]), "اضغط لاختيار الدقائق", R.drawable.ic_eye, new Runnable() { public void run() { pickAdj(k); } });
    }

    private void pickAdj(final int k) {
        final int[] cur = prefs.prayerAdj();
        String[] labels = new String[41];
        for (int v = -20; v <= 20; v++) labels[v + 20] = v == 0 ? "بدون تعديل" : (v > 0 ? "+" + v : "\u2212" + (-v)) + " د";
        new Dlg(this).setTitle(PrayerAdj.NAMES[k] + " (دقائق من −٢٠ إلى +٢٠)")
            .setSingleChoiceItems(labels, cur[k] + 20, (d, i) -> {
                int[] a = prefs.prayerAdj();
                a[k] = i - 20;
                prefs.setPrayerAdj(a);
                d.dismiss();
                render();
            }).show();
    }

    /** A card row: icon, label and its current value on a second line, chevron; gold when focused. */
    private View row(String label, String value, int icon, final Runnable onClick) {
        final LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        card.setPadding(dp(24), dp(14), dp(20), dp(14));
        card.setFocusable(true);
        card.setClickable(true);
        card.setBackgroundResource(R.drawable.glass_row);   // glass; the gold-tinted one while it has the focus

        final android.widget.ImageView ic = new android.widget.ImageView(this);
        final android.graphics.drawable.Drawable d = getDrawable(icon).mutate();
        d.setTint(0xFFD6AF6B);
        ic.setImageDrawable(d);
        card.addView(ic, new LinearLayout.LayoutParams(dp(28), dp(28)));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        final TextView name = new TextView(this);
        name.setText(noConv ? label : conv(label));
        name.setTextColor(0xFFF6EFE0);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 21);
        name.setTypeface(font("Thmanyah-Medium"));
        name.setSingleLine(true);
        texts.addView(name);
        final TextView val = new TextView(this);
        if (value != null) {
            val.setText(noConv ? value : conv(value));
            val.setTextColor(0xFFC6BBA4);
            val.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            val.setTypeface(font("Thmanyah-Regular"));
            val.setSingleLine(true);
            val.setEllipsize(android.text.TextUtils.TruncateAt.END);
            texts.addView(val);
        }
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, -2, 1f);
        tp.setMarginStart(dp(18));
        card.addView(texts, tp);

        final TextView chevron = new TextView(this);
        chevron.setText("›");   // shown mirrored (‹) in the RTL row
        chevron.setTextColor(0x99C6BBA4);
        chevron.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        card.addView(chevron);

        if (onClick != null) card.setOnClickListener(v -> onClick.run());
        card.setOnFocusChangeListener((v, has) -> {
            // No scaling: a scaled rounded card overlapped its neighbours and blurred the text. The glass changes by itself (glass_row).
            chevron.setTextColor(has ? 0xFFD6AF6B : 0x99C6BBA4);
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(9);
        target.addView(card, lp);
        return card;
    }

    interface Text { void got(String s); }
    interface Json { void got(String json) throws Exception; }

    private void ask(String hint, final Text then) {
        final EditText in = new EditText(this);
        in.setHint(hint);
        in.setSingleLine(true);
        in.setInputType(InputType.TYPE_CLASS_TEXT);
        new Dlg(this).setTitle("بحث").setView(in)
            .setPositiveButton("بحث", (d, w) -> {
                String q = in.getText().toString().trim();
                if (!q.isEmpty()) then.got(q);
            })
            .setNegativeButton("إلغاء", null).show();
        in.requestFocus();
    }

    /** GET on a background thread, result handled on the UI thread. */
    private void fetch(final String url, final Json then) {
        toast("جارٍ البحث…");
        new Thread(() -> {
            String body = null;
            HttpURLConnection con = null;
            try {
                con = (HttpURLConnection) new URL(url).openConnection();
                con.setConnectTimeout(12000);
                con.setReadTimeout(15000);
                InputStream in = con.getInputStream();
                ByteArrayOutputStream b = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
                in.close();
                body = b.toString("UTF-8");
            } catch (Exception ignored) {
            } finally {
                if (con != null) con.disconnect();
            }
            final String result = body;
            runOnUiThread(() -> {
                if (isFinishing()) return;
                if (result == null) { toast("لا يوجد اتصال"); return; }
                try { then.got(result); } catch (Exception e) { toast("تعذرت قراءة النتائج"); }
            });
        }).start();
    }

    private void toast(String s) { Toast.makeText(this, conv(s), Toast.LENGTH_SHORT).show(); }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
