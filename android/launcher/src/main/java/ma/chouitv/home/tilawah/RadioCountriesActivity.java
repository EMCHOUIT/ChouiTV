package ma.chouitv.home.tilawah;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import ma.chouitv.home.R;

import java.util.ArrayList;
import java.util.List;

/**
 * «الإذاعات · البلدان»: up to three countries, ticked in a grid of four; «تحديث الإذاعات» at the top downloads their lists with a bar and
 * the state of each country; the date of the last update under it; under the grid, the way to each downloaded country's stations.
 */
public class RadioCountriesActivity extends Activity {

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final List<String> all = new ArrayList<>();
    private GridView grid;
    private TextView title, counter, update, status, steps, date, hint;
    private LinearLayout chipsRow, buttons, progressBox;
    private Bar bar;
    private boolean updating;
    private final java.util.Map<String, Integer> states = new java.util.LinkedHashMap<>();

    /** The progress bar: a rounded track and a gold fill. */
    private static final class Bar extends View {
        private float fraction;
        private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        Bar(Context c) { super(c); }
        void set(float f) { fraction = Math.max(0f, Math.min(1f, f)); invalidate(); }
        @Override protected void onDraw(android.graphics.Canvas canvas) {
            float h = getHeight(), w = getWidth(), r = h / 2;
            paint.setColor(0x24FFFFFF);
            canvas.drawRoundRect(0, 0, w, h, r, r, paint);
            if (fraction > 0f) {
                paint.setColor(0xFFDCB86F);
                // the fill grows from the right, the way the screen reads
                canvas.drawRoundRect(w - Math.max(h, w * fraction), 0, w, h, r, r, paint);
            }
        }
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        ma.chouitv.home.Digits.init(this);
        all.add(RadioChoice.HOME);
        for (String cc : RadioStore.OFFERED) all.add(cc);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundResource(R.color.bg);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        ImageView back = new ImageView(this);
        back.setImageResource(R.drawable.bg);
        back.setScaleType(ImageView.ScaleType.CENTER_CROP);
        back.setAlpha(0.42f);
        root.addView(back, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(72), dp(36), dp(72), dp(20));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        title = new TextView(this);
        Ui.text(title, "ThmanyahDisplay-Bold.otf", 38, R.color.sand);
        title.setText(R.string.radio_settings_title);
        words.addView(title);
        counter = new TextView(this);
        Ui.text(counter, "Thmanyah-Regular.otf", 15, R.color.dim);
        words.addView(counter);
        head.addView(words, new LinearLayout.LayoutParams(0, -2, 1f));
        update = new TextView(this);
        update.setFocusable(true);
        update.setGravity(Gravity.CENTER);
        update.setPadding(dp(26), 0, dp(26), 0);
        update.setTypeface(Ui.font(this, "Thmanyah-Medium.otf"));
        update.setTextSize(18);
        update.setOnClickListener(v -> startUpdate());
        update.setOnFocusChangeListener((v, has) -> paintUpdate());
        head.addView(update, new LinearLayout.LayoutParams(-2, dp(48)));
        col.addView(head);

        chipsRow = new LinearLayout(this);
        chipsRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.topMargin = dp(10);
        col.addView(chipsRow, cp);

        progressBox = new LinearLayout(this);
        progressBox.setOrientation(LinearLayout.VERTICAL);
        progressBox.setVisibility(View.GONE);
        bar = new Bar(this);
        progressBox.addView(bar, new LinearLayout.LayoutParams(-1, dp(10)));
        status = new TextView(this);
        Ui.text(status, "Thmanyah-Medium.otf", 15, R.color.sand);
        progressBox.addView(status, topMargin(6));
        steps = new TextView(this);
        Ui.text(steps, "Thmanyah-Regular.otf", 14, R.color.dim);
        progressBox.addView(steps, topMargin(2));
        col.addView(progressBox, topMargin(10));

        date = new TextView(this);
        Ui.text(date, "Thmanyah-Regular.otf", 14, R.color.dim);
        col.addView(date, topMargin(6));

        grid = new GridView(this);
        grid.setNumColumns(4);
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setHorizontalSpacing(dp(12));
        grid.setVerticalSpacing(dp(10));
        grid.setSelector(android.R.color.transparent);
        grid.setVerticalScrollBarEnabled(false);
        grid.setFadingEdgeLength(0);
        grid.setClipToPadding(false);
        grid.setPadding(dp(2), dp(4), dp(2), dp(6));
        grid.setFocusable(true);
        grid.setAdapter(new Cards());
        grid.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> p, View v, int position, long id) { ((BaseAdapter) grid.getAdapter()).notifyDataSetChanged(); }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p) { }
        });
        grid.setOnFocusChangeListener((v, has) -> ((BaseAdapter) grid.getAdapter()).notifyDataSetChanged());
        grid.setOnItemClickListener((p, v, position, id) -> toggle(all.get(position)));
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(-1, 0, 1f);
        gp.topMargin = dp(8);
        col.addView(grid, gp);

        buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        col.addView(buttons, topMargin(6));

        hint = new TextView(this);
        Ui.text(hint, "Thmanyah-Regular.otf", 13, R.color.dim);
        hint.setGravity(Gravity.CENTER);
        hint.setText(R.string.radio_countries_hint);
        col.addView(hint, topMargin(8));

        root.addView(col);
        setContentView(root);
        render();
        grid.requestFocus();
    }

    private LinearLayout.LayoutParams topMargin(int dpValue) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(dpValue);
        return p;
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    @Override protected void onResume() {
        super.onResume();
        render();   // back from a country's stations: the counts changed
    }

    // ---- what is shown ----

    private void render() {
        Context c = this;
        List<String> chosen = RadioStore.chosen(c);
        counter.setText(Ui.digits(getString(R.string.radio_settings_count, Ui.arabic(chosen.size()), Ui.arabic(RadioChoice.MAX))));
        paintUpdate();
        chipsRow.removeAllViews();
        for (String cc : chosen) {
            TextView chip = new TextView(this);
            chip.setText(RadioStore.countryName(cc) + "  ✕");
            chip.setTextSize(14);
            chip.setTypeface(Ui.font(this, "Thmanyah-Medium.otf"));
            chip.setTextColor(0xFFDCB86F);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(16), 0, dp(16), 0);
            GradientDrawable d = new GradientDrawable();
            d.setCornerRadius(dp(16));
            d.setColor(0x24DCB86F);
            d.setStroke(dp(1), 0x99DCB86F);
            chip.setBackground(d);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(30));
            lp.setMarginEnd(dp(8));
            chipsRow.addView(chip, lp);
        }
        long at = RadioStore.updatedAt(c);
        date.setText(at == 0 ? getString(R.string.radio_never) : Ui.digits(getString(R.string.radio_updated, RadioStore.stamp(at))));
        buttons.removeAllViews();
        for (final String cc : chosen) {
            if (cc.equals(RadioChoice.HOME) || !RadioStore.downloaded(c, cc)) continue;
            TextView b = button(getString(R.string.radio_stations_of, RadioStore.countryName(cc)), false);
            b.setOnClickListener(v -> startActivity(new Intent(c, RadioSettingsActivity.class).putExtra(RadioSettingsActivity.EXTRA_COUNTRY, cc)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(40));
            lp.setMarginEnd(dp(10));
            buttons.addView(b, lp);
        }
        ((BaseAdapter) grid.getAdapter()).notifyDataSetChanged();
    }

    private TextView button(String label, boolean gold) {
        final TextView b = new TextView(this);
        b.setText(label);
        b.setFocusable(true);
        b.setGravity(Gravity.CENTER);
        b.setTextSize(15);
        b.setTypeface(Ui.font(this, "Thmanyah-Medium.otf"));
        b.setPadding(dp(20), 0, dp(20), 0);
        b.setOnFocusChangeListener((v, has) -> paintButton(b, has, gold));
        paintButton(b, false, gold);
        return b;
    }

    private void paintButton(TextView b, boolean focused, boolean gold) {
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(dp(20));
        d.setColor(focused ? 0x40DCB86F : 0x14FFFFFF);
        d.setStroke(dp(2), focused ? 0xFFDCB86F : 0x59DCB86F);
        b.setBackground(d);
        b.setTextColor(0xFFF6EFE0);
    }

    private int resultTint = 0xFFDCB86F;
    private String updateLabel = "";

    private void paintUpdate() {
        String label = updateLabel.isEmpty() ? "↻  " + getString(R.string.radio_update) : updateLabel;
        update.setText(label);
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(dp(24));
        d.setColor(update.hasFocus() ? 0x40DCB86F : 0x1ADCB86F);
        d.setStroke(dp(2), update.hasFocus() ? 0xFFDCB86F : (updating ? 0x66DCB86F : resultTint));
        update.setBackground(d);
        update.setTextColor(updating ? 0x99DCB86F : resultTint);
    }

    // ---- ticking countries ----

    private void toggle(String cc) {
        if (updating) return;   // the lists are being downloaded for the countries as they are
        List<String> chosen = RadioStore.chosen(this);
        List<String> next = RadioChoice.toggle(chosen, cc);
        if (next == null) {
            Toast.makeText(this, chosen.contains(cc) ? R.string.radio_keep_one : R.string.radio_full, Toast.LENGTH_LONG).show();
            return;
        }
        RadioStore.setChosen(this, next);
        updateLabel = "";
        resultTint = 0xFFDCB86F;
        render();
        if (!cc.equals(RadioChoice.HOME) && next.contains(cc) && !RadioStore.downloaded(this, cc)) startUpdate();   // a new country: its list comes now
    }

    // ---- the update, with its progress ----

    private void startUpdate() {
        if (updating) return;
        final List<String> targets = RadioStore.toDownload(this);
        progressBox.setVisibility(View.VISIBLE);
        if (targets.isEmpty()) {   // only Morocco: its radios are in the app
            bar.set(1f);
            status.setText(R.string.radio_only_home);
            steps.setText("");
            return;
        }
        updating = true;
        updateLabel = getString(R.string.radio_updating);
        resultTint = 0xFFDCB86F;
        states.clear();
        for (String cc : targets) states.put(cc, RadioStore.Progress.WAITING);
        bar.set(0f);
        status.setText("");
        paintUpdate();
        drawSteps();
        RadioStore.update(this, new RadioStore.Progress() {
            @Override public void step(String cc, int state, int finished, int total) {
                if (isFinishing()) return;
                states.put(cc, state);
                bar.set(total == 0 ? 1f : (finished + (state == LOADING ? 0.4f : 0f)) / total);
                if (state == LOADING) {
                    status.setText(Ui.digits(getString(R.string.radio_loading_country, RadioStore.countryName(cc), Ui.arabic(finished + 1), Ui.arabic(total))));
                }
                drawSteps();
            }

            @Override public void done(int got, List<String> failed, int total) {
                updating = false;
                if (isFinishing()) return;
                bar.set(failed.isEmpty() ? 1f : (float) got / Math.max(1, total));
                if (failed.isEmpty()) {
                    updateLabel = "✓  " + getString(R.string.radio_update_done);
                    resultTint = 0xFF57D294;
                    status.setText(Ui.digits(getString(R.string.radio_loaded, countriesPhrase(got), Ui.arabic(RadioStore.downloadedStations(RadioCountriesActivity.this)))));
                } else if (got > 0) {
                    updateLabel = "!  " + getString(R.string.radio_update_partial);
                    resultTint = 0xFFDCB86F;
                    status.setText(getString(R.string.radio_failed_for, joinNames(failed)));
                } else {
                    updateLabel = "✕  " + getString(R.string.radio_update_failed_title);
                    resultTint = 0xFFEE6A50;
                    status.setText(R.string.radio_update_failed_hint);
                }
                render();
                paintUpdate();
                // the button says again what it does, a few seconds later; the line under it keeps the date
                ui.postDelayed(() -> { if (!updating && !isFinishing()) { updateLabel = ""; resultTint = 0xFFDCB86F; paintUpdate(); } }, 6000);
            }
        });
    }

    private void drawSteps() {
        StringBuilder sb = new StringBuilder();
        for (java.util.Map.Entry<String, Integer> e : states.entrySet()) {
            String mark = e.getValue() == RadioStore.Progress.DONE ? "✓" : e.getValue() == RadioStore.Progress.FAILED ? "✕"
                    : e.getValue() == RadioStore.Progress.LOADING ? "↻" : "○";
            sb.append(sb.length() == 0 ? "" : "     ").append(mark).append(' ').append(RadioStore.countryName(e.getKey()));
        }
        steps.setText(sb.toString());
    }

    private static String joinNames(List<String> ccs) {
        StringBuilder sb = new StringBuilder();
        for (String cc : ccs) sb.append(sb.length() == 0 ? "" : "، ").append(RadioStore.countryName(cc));
        return sb.toString();
    }

    /** «بلد واحد», «بلدان», «٣ بلدان» */
    private static String countriesPhrase(int n) {
        if (n == 1) return "بلد واحد";
        if (n == 2) return "بلدان";
        return n + " بلدان";
    }

    // ---- the grid of countries ----

    private final class Cards extends BaseAdapter {
        @Override public int getCount() { return all.size(); }
        @Override public Object getItem(int p) { return all.get(p); }
        @Override public long getItemId(int p) { return p; }

        @Override public View getView(int position, View convert, ViewGroup parent) {
            String cc = all.get(position);
            List<String> chosen = RadioStore.chosen(RadioCountriesActivity.this);
            boolean on = chosen.contains(cc), full = RadioChoice.full(chosen);
            LinearLayout box;
            if (convert instanceof LinearLayout) {
                box = (LinearLayout) convert;
            } else {
                box = new LinearLayout(RadioCountriesActivity.this);
                box.setOrientation(LinearLayout.HORIZONTAL);
                box.setGravity(Gravity.CENTER_VERTICAL);
                box.setPadding(dp(16), dp(10), dp(14), dp(10));
                TextView mark = new TextView(RadioCountriesActivity.this);
                mark.setTextSize(24);
                mark.setGravity(Gravity.CENTER);
                box.addView(mark, new LinearLayout.LayoutParams(dp(30), dp(30)));
                LinearLayout words = new LinearLayout(RadioCountriesActivity.this);
                words.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(0, -2, 1f);
                wp.setMarginStart(dp(10));
                box.addView(words, wp);
                TextView name = new TextView(RadioCountriesActivity.this);
                name.setTextSize(17);
                name.setTypeface(Ui.font(RadioCountriesActivity.this, "Thmanyah-Medium.otf"));
                name.setTextColor(getResources().getColor(R.color.sand));
                name.setSingleLine(true);
                words.addView(name);
                TextView sub = new TextView(RadioCountriesActivity.this);
                sub.setTextSize(11);
                sub.setTypeface(Ui.font(RadioCountriesActivity.this, "Thmanyah-Light.otf"));
                sub.setTextColor(getResources().getColor(R.color.dim));
                sub.setSingleLine(true);
                words.addView(sub);
            }
            TextView mark = (TextView) box.getChildAt(0);
            LinearLayout words = (LinearLayout) box.getChildAt(1);
            TextView name = (TextView) words.getChildAt(0), sub = (TextView) words.getChildAt(1);
            mark.setText(on ? "☑" : "☐");
            mark.setTextColor(getResources().getColor(on ? R.color.gold : R.color.dim));
            name.setText(RadioStore.countryName(cc));
            String detail = cc.equals(RadioChoice.HOME) ? getString(R.string.radio_bundled_short)
                    : on && !RadioStore.downloaded(RadioCountriesActivity.this, cc) ? getString(R.string.radio_not_downloaded_short) : "";
            sub.setText(detail.isEmpty() ? " " : detail);
            sub.setVisibility(detail.isEmpty() ? View.INVISIBLE : View.VISIBLE);   // the line keeps its room: all the cards are one height
            box.setAlpha(full && !on ? 0.4f : 1f);
            boolean selected = grid.hasFocus() && position == grid.getSelectedItemPosition();
            box.setBackgroundResource(selected ? R.drawable.row_selected : R.drawable.row_bg);
            return box;
        }
    }
}
