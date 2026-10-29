package ma.chouitv.home.tilawah;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import ma.chouitv.home.R;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * «الإذاعات» settings: the countries (two at most, one tick each), the lists' update, and for each chosen country the stations to keep.
 * One screen, two pages: the countries, then (EXTRA_COUNTRY) the stations of one of them.
 */
public class RadioSettingsActivity extends Activity {

    static final String EXTRA_COUNTRY = "country";

    private static final int K_COUNTRY = 0, K_UPDATE = 1, K_STATIONS = 2, K_STATION = 3, K_ALL = 4, K_NONE = 5, K_HEAD = 6, K_NOTE = 7;

    private static final class Row {
        final int kind;
        final String key, title, detail;
        final boolean on, dim;
        Row(int kind, String key, String title, String detail, boolean on, boolean dim) {
            this.kind = kind; this.key = key; this.title = title; this.detail = detail; this.on = on; this.dim = dim;
        }
    }

    private final List<Row> rows = new ArrayList<>();
    private ListView list;
    private TextView title, hint;
    private String country;   // null = the countries page
    private boolean updating;
    private BaseAdapter adapter;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        ma.chouitv.home.Digits.init(this);
        country = getIntent().getStringExtra(EXTRA_COUNTRY);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundResource(R.color.bg);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(72), dp(44), dp(72), dp(24));
        title = new TextView(this);
        Ui.text(title, "ThmanyahDisplay-Bold.otf", 40, R.color.sand);
        col.addView(title);
        hint = new TextView(this);
        Ui.text(hint, "Thmanyah-Regular.otf", 16, R.color.dim);
        col.addView(hint);
        list = new ListView(this);
        list.setDivider(null);
        list.setDividerHeight(dp(9));
        list.setSelector(android.R.color.transparent);
        list.setVerticalScrollBarEnabled(false);
        list.setFadingEdgeLength(0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 0, 1f);
        lp.topMargin = dp(16);
        col.addView(list, lp);
        TextView foot = new TextView(this);
        Ui.text(foot, "Thmanyah-Regular.otf", 13, R.color.dim);
        foot.setGravity(android.view.Gravity.CENTER);
        foot.setText(R.string.radio_settings_hint);
        col.addView(foot);
        root.addView(col);
        setContentView(root);
        adapter = new Adapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((p, v, position, id) -> click(rows.get(position), position));
        list.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> p, View v, int position, long id) { adapter.notifyDataSetChanged(); }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p) { }
        });
        build();
        list.requestFocus();
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    private void build() {
        rows.clear();
        Context c = this;
        List<String> chosen = RadioStore.chosen(c);
        if (country == null) {
            title.setText(R.string.radio_settings_title);
            hint.setText(getString(R.string.radio_settings_count, Ui.arabic(chosen.size()), Ui.arabic(RadioChoice.MAX)));
            boolean full = RadioChoice.full(chosen);
            rows.add(new Row(K_COUNTRY, RadioChoice.HOME, RadioStore.countryName(RadioChoice.HOME), getString(R.string.radio_builtin), chosen.contains(RadioChoice.HOME), false));
            for (String cc : RadioStore.OFFERED) {
                boolean on = chosen.contains(cc);
                rows.add(new Row(K_COUNTRY, cc, RadioStore.countryName(cc), on && !RadioStore.downloaded(c, cc) ? getString(R.string.radio_not_downloaded_short) : "", on, full && !on));
            }
            rows.add(new Row(K_HEAD, "", getString(R.string.radio_lists), "", false, false));
            long at = RadioStore.updatedAt(c);
            String when = at == 0 ? getString(R.string.radio_never) : DateFormat.getDateInstance(DateFormat.MEDIUM).format(new Date(at));
            rows.add(new Row(K_UPDATE, "", updating ? getString(R.string.radio_updating) : getString(R.string.radio_update), getString(R.string.radio_updated, Ui.digits(when)), false, false));
            for (String cc : chosen) {
                if (!cc.equals(RadioChoice.HOME)) rows.add(new Row(K_STATIONS, cc, getString(R.string.radio_stations_of, RadioStore.countryName(cc)), "", false, false));
            }
        } else {
            title.setText(RadioStore.countryName(country));
            List<Catalog.Station> all = RadioStore.read(c, country);
            int on = 0;
            for (Catalog.Station s : all) if (!RadioStore.off(c, country, s.id)) on++;
            hint.setText(getString(R.string.radio_stations_count, Ui.arabic(on), Ui.arabic(all.size())));
            if (all.isEmpty()) {
                rows.add(new Row(K_NOTE, "", getString(R.string.radio_not_downloaded, RadioStore.countryName(country)), "", false, false));
            } else {
                rows.add(new Row(K_ALL, "", getString(R.string.radio_tick_all), "", false, false));
                rows.add(new Row(K_NONE, "", getString(R.string.radio_untick_all), "", false, false));
                String section = null;
                for (Catalog.Station s : all) {
                    if (!s.section.equals(section)) {
                        section = s.section;
                        rows.add(new Row(K_HEAD, "", RadioStore.sectionName(section), "", false, false));
                    }
                    rows.add(new Row(K_STATION, s.id, s.name, s.kind + (s.place.isEmpty() ? "" : "  •  " + s.place), !RadioStore.off(c, country, s.id), false));
                }
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void click(Row r, int position) {
        Context c = this;
        switch (r.kind) {
            case K_COUNTRY: {
                List<String> next = RadioChoice.toggle(RadioStore.chosen(c), r.key);
                if (next == null) {
                    Toast.makeText(c, RadioStore.chosen(c).contains(r.key) ? R.string.radio_keep_one : R.string.radio_full, Toast.LENGTH_LONG).show();
                    return;
                }
                RadioStore.setChosen(c, next);
                if (!r.key.equals(RadioChoice.HOME) && next.contains(r.key) && !RadioStore.downloaded(c, r.key)) startUpdate();
                keep(position);
                return;
            }
            case K_UPDATE: startUpdate(); return;
            case K_STATIONS: startActivity(new Intent(c, RadioSettingsActivity.class).putExtra(EXTRA_COUNTRY, r.key)); return;
            case K_STATION: RadioStore.setOff(c, country, r.key, r.on); keep(position); return;
            case K_ALL: case K_NONE: {
                for (Catalog.Station s : RadioStore.read(c, country)) RadioStore.setOff(c, country, s.id, r.kind == K_NONE);
                keep(position);
                return;
            }
            default:
        }
    }

    private void keep(int position) {
        build();
        list.setSelection(Math.min(position, rows.size() - 1));
    }

    private void startUpdate() {
        if (updating) return;
        updating = true;
        build();
        RadioStore.update(this, (ok, n) -> {
            updating = false;
            if (!isFinishing()) {
                if (n == 0) Toast.makeText(this, R.string.radio_update_failed, Toast.LENGTH_LONG).show();
                keep(list.getSelectedItemPosition() < 0 ? 0 : list.getSelectedItemPosition());
            }
        });
    }

    @Override protected void onResume() {
        super.onResume();
        build();   // coming back from the stations page: the counts changed
    }

    private final class Adapter extends BaseAdapter {
        @Override public int getCount() { return rows.size(); }
        @Override public Object getItem(int p) { return rows.get(p); }
        @Override public long getItemId(int p) { return p; }
        @Override public boolean areAllItemsEnabled() { return false; }
        @Override public boolean isEnabled(int p) { int k = rows.get(p).kind; return k != K_HEAD && k != K_NOTE; }
        @Override public int getViewTypeCount() { return 2; }
        @Override public int getItemViewType(int p) { return rows.get(p).kind == K_HEAD ? 0 : 1; }

        @Override public View getView(int position, View convert, ViewGroup parent) {
            Row r = rows.get(position);
            if (r.kind == K_HEAD) {
                TextView h = convert instanceof TextView ? (TextView) convert : new TextView(RadioSettingsActivity.this);
                Ui.text(h, "Thmanyah-Medium.otf", 16, R.color.gold);
                h.setPadding(dp(10), dp(14), dp(10), dp(4));
                h.setText(r.title);
                return h;
            }
            LinearLayout box;
            if (convert instanceof LinearLayout) {
                box = (LinearLayout) convert;
            } else {
                box = new LinearLayout(RadioSettingsActivity.this);
                box.setOrientation(LinearLayout.HORIZONTAL);
                box.setGravity(android.view.Gravity.CENTER_VERTICAL);
                box.setPadding(dp(20), dp(11), dp(16), dp(11));
                TextView mark = new TextView(RadioSettingsActivity.this);
                mark.setGravity(android.view.Gravity.CENTER);
                box.addView(mark, new LinearLayout.LayoutParams(dp(34), dp(34)));
                LinearLayout words = new LinearLayout(RadioSettingsActivity.this);
                words.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(0, -2, 1f);
                wp.setMarginStart(dp(14));
                box.addView(words, wp);
                TextView name = new TextView(RadioSettingsActivity.this);
                Ui.text(name, "Thmanyah-Medium.otf", 20, R.color.sand);
                name.setSingleLine(true);
                words.addView(name);
                TextView sub = new TextView(RadioSettingsActivity.this);
                Ui.text(sub, "Thmanyah-Light.otf", 13, R.color.dim);
                sub.setSingleLine(true);
                words.addView(sub);
            }
            TextView mark = (TextView) box.getChildAt(0);
            LinearLayout words = (LinearLayout) box.getChildAt(1);
            TextView name = (TextView) words.getChildAt(0), sub = (TextView) words.getChildAt(1);
            boolean tick = r.kind == K_COUNTRY || r.kind == K_STATION;
            mark.setText(tick ? (r.on ? "☑" : "☐") : "");
            mark.setTextSize(26);
            mark.setTextColor(getResources().getColor(r.on ? R.color.gold : R.color.dim));
            mark.setVisibility(tick ? View.VISIBLE : View.GONE);
            name.setText(Ui.digits(r.title + (r.kind == K_STATIONS ? "  ›" : "")));
            sub.setText(Ui.digits(r.detail));
            sub.setVisibility(r.detail == null || r.detail.isEmpty() ? View.GONE : View.VISIBLE);
            box.setAlpha(r.dim ? 0.4f : 1f);
            boolean selected = list.hasFocus() && position == list.getSelectedItemPosition();
            box.setBackgroundResource(selected ? R.drawable.row_selected : R.drawable.row_bg);
            return box;
        }
    }
}
