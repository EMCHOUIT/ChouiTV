package ma.chouitv.home.tilawah;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.TextView;

import ma.chouitv.home.R;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The «الإذاعات» tab: the countries the person chose as chips under the tabs (the favourites first, the way to the settings last),
 * the stations of the one chosen in a grid of four, and a bar at the bottom while a station plays (OK on it stops).
 * ▲ from the first row of stations goes to the chips, ▲ from the chips to the tabs. OK plays, a long OK is a favourite.
 * Only the cards on screen are built, so 120 stations cost nothing.
 */
final class RadioPane extends LinearLayout {

    interface Host {
        void openPlayer();
        void openSettings();
        void focusTabs();
    }

    private static final int C_FAV = 0, C_COUNTRY = 1, C_CHOOSE = 2;
    private static final int S_STATION = 1, S_NOTE = 2, S_UPDATE = 3;

    private static final class CRow {
        final int kind;
        final String cc, title;
        CRow(int kind, String cc, String title) { this.kind = kind; this.cc = cc; this.title = title; }
    }

    private static final class SRow {
        final int kind, index;
        final String title, detail;
        SRow(int kind, int index, String title, String detail) { this.kind = kind; this.index = index; this.title = title; this.detail = detail; }
    }

    private final Host host;
    private final Catalog cat;
    private final LinearLayout chips;
    private final GridView grid;
    private final TextView bar;
    private final List<CRow> cRows = new ArrayList<>();
    private final List<SRow> sRows = new ArrayList<>();
    private final List<TextView> chipViews = new ArrayList<>();
    private final BaseAdapter adapter;
    private final Handler tick = new Handler(Looper.getMainLooper());
    private int active;          // the chip whose stations are shown
    private boolean updating;
    private String barShown = "";

    RadioPane(Context context, Host host) {
        super(context);
        this.host = host;
        this.cat = Catalog.get(context);
        setOrientation(VERTICAL);
        setLayoutDirection(View.LAYOUT_DIRECTION_RTL);   // the chips and the cards start at the right, as the tabs

        chips = new LinearLayout(context);
        chips.setOrientation(HORIZONTAL);
        addView(chips, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        grid = new GridView(context);
        grid.setNumColumns(4);
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setHorizontalSpacing(dp(12));
        grid.setVerticalSpacing(dp(12));
        grid.setSelector(android.R.color.transparent);
        grid.setVerticalScrollBarEnabled(false);
        grid.setFadingEdgeLength(0);
        grid.setClipToPadding(false);
        grid.setPadding(dp(2), dp(4), dp(2), dp(8));
        grid.setFocusable(true);
        adapter = new Cards();
        grid.setAdapter(adapter);
        LayoutParams gp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        gp.topMargin = dp(12);
        addView(grid, gp);

        bar = new TextView(context);
        bar.setTextSize(15);
        bar.setTypeface(Ui.font(context, "Thmanyah-Medium.otf"));
        bar.setTextColor(getResources().getColor(R.color.sand));
        bar.setGravity(android.view.Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(18), 0, dp(18), 0);
        bar.setFocusable(true);
        bar.setVisibility(View.GONE);
        paintBar(false);
        bar.setOnFocusChangeListener((v, has) -> paintBar(has));
        bar.setOnClickListener(v -> { MusicService.stopAction(getContext()); bar.postDelayed(this::refresh, 400); });
        LayoutParams bp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        bp.topMargin = dp(8);
        addView(bar, bp);

        grid.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int position, long id) { adapter.notifyDataSetChanged(); }
            @Override public void onNothingSelected(AdapterView<?> p) { }
        });
        grid.setOnFocusChangeListener((v, has) -> adapter.notifyDataSetChanged());
        grid.setOnItemClickListener((p, v, position, id) -> {
            SRow row = sRows.get(position);
            if (row.kind == S_STATION) {
                MusicService.playRadio(getContext(), row.index);
                host.openPlayer();
            } else if (row.kind == S_UPDATE) {
                update();
            }
        });
        grid.setOnItemLongClickListener((p, v, position, id) -> {
            SRow row = sRows.get(position);
            if (row.kind != S_STATION) return false;
            RadioStore.toggleFavourite(getContext(), cat.stations.get(row.index).url);
            int keep = position;
            refresh();
            grid.setSelection(Math.min(keep, sRows.size() - 1));
            return true;
        });
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        tick.post(ticker);
    }

    @Override protected void onDetachedFromWindow() {
        tick.removeCallbacks(ticker);
        super.onDetachedFromWindow();
    }

    /** Once a second: the bar and the «مباشر» mark follow what plays (a station stopped from the home card, a new one started). */
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            String now = playingSignature();
            if (!now.equals(barShown)) { barShown = now; buildStations(); updateBar(); }
            tick.postDelayed(this, 1000);
        }
    };

    private String playingSignature() {
        boolean on = MusicService.currentSource() == Prefs.SOURCE_RADIO && MusicService.isPlaying();
        return on ? "r" + MusicService.currentStation() : "-";
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    // ---- what is shown ----

    /** Reads everything again (the countries chosen, the lists, the favourites, what plays) and keeps the place where it can. */
    void refresh() {
        Context c = getContext();
        String keepCc = active < cRows.size() ? cRows.get(active).cc : null;
        cRows.clear();
        cRows.add(new CRow(C_FAV, "*", "★  " + c.getString(R.string.radio_favourites)));
        for (String cc : RadioStore.chosen(c)) {
            int n = 0;
            for (Catalog.Station s : cat.stations) if (cc.equals(s.group)) n++;
            cRows.add(new CRow(C_COUNTRY, cc, RadioStore.countryName(cc) + (n == 0 ? "" : "  " + Ui.arabic(n))));
        }
        cRows.add(new CRow(C_CHOOSE, "", "⚙"));
        active = 1;
        for (int i = 0; i < cRows.size(); i++) if (cRows.get(i).kind != C_CHOOSE && cRows.get(i).cc.equals(keepCc)) active = i;
        buildChips();
        buildStations();
        barShown = playingSignature();
        updateBar();
    }

    private void buildChips() {
        chips.removeAllViews();
        chipViews.clear();
        for (int i = 0; i < cRows.size(); i++) {
            final int at = i;
            TextView t = new TextView(getContext());
            t.setText(cRows.get(i).title);
            t.setTextSize(17);
            t.setTypeface(Ui.font(getContext(), "Thmanyah-Medium.otf"));
            t.setGravity(android.view.Gravity.CENTER);
            t.setPadding(dp(20), 0, dp(20), 0);
            t.setFocusable(true);
            t.setOnFocusChangeListener((v, has) -> {
                if (has && cRows.get(at).kind != C_CHOOSE && at != active) {   // moving over a country shows its stations
                    active = at;
                    buildStations();
                    grid.setSelection(0);
                }
                paintChips();
            });
            t.setOnClickListener(v -> {
                if (cRows.get(at).kind == C_CHOOSE) host.openSettings();
                else if (sRows.size() > 0) grid.requestFocus();
            });
            LayoutParams lp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40));
            lp.setMarginEnd(dp(8));
            chips.addView(t, lp);
            chipViews.add(t);
        }
        paintChips();
    }

    private void paintChips() {
        for (int i = 0; i < chipViews.size(); i++) {
            TextView t = chipViews.get(i);
            boolean isActive = i == active && cRows.get(i).kind != C_CHOOSE;
            boolean focused = t.hasFocus();
            GradientDrawable d = new GradientDrawable();
            d.setCornerRadius(dp(20));
            d.setColor(isActive ? 0xE6DCB86F : 0x14FFFFFF);
            d.setStroke(dp(2), focused ? 0xFFDCB86F : isActive ? 0xE6DCB86F : 0x00000000);
            t.setBackground(d);
            t.setTextColor(getResources().getColor(isActive ? R.color.bg : R.color.sand));
        }
    }

    private void paintBar(boolean focused) {
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(dp(23));
        d.setColor(0x66000000);
        d.setStroke(dp(2), focused ? 0xFFDCB86F : 0x73DCB86F);
        bar.setBackground(d);
    }

    private void updateBar() {
        boolean on = MusicService.currentSource() == Prefs.SOURCE_RADIO;
        int index = MusicService.currentStation();
        if (!on || index < 0 || index >= cat.stations.size()) {
            if (bar.hasFocus()) grid.requestFocus();
            bar.setVisibility(View.GONE);
            return;
        }
        bar.setText("■   " + Ui.digits(getContext().getString(R.string.radio_now_bar, cat.stations.get(index).name)));
        bar.setVisibility(View.VISIBLE);
    }

    /** The country of the radio that plays, so the tab opens on it; else the first country. */
    void selectPlayingCountry() {
        int playing = MusicService.currentSource() == Prefs.SOURCE_RADIO ? MusicService.currentStation() : -1;
        active = Math.min(1, cRows.size() - 1);
        if (playing >= 0 && playing < cat.stations.size()) {
            String cc = cat.stations.get(playing).group;
            for (int i = 0; i < cRows.size(); i++) if (cRows.get(i).kind == C_COUNTRY && cRows.get(i).cc.equals(cc)) active = i;
        }
        paintChips();
        buildStations();
        int at = stationRow(playing);
        grid.setSelection(at >= 0 ? at : 0);
    }

    private int stationRow(int index) {
        for (int i = 0; i < sRows.size(); i++) if (sRows.get(i).kind == S_STATION && sRows.get(i).index == index) return i;
        return -1;
    }

    private List<Integer> favouriteIndexes() {
        List<Integer> out = new ArrayList<>();
        List<String> chosen = RadioStore.chosen(getContext());
        for (int i = 0; i < cat.stations.size(); i++) {
            Catalog.Station s = cat.stations.get(i);
            if (chosen.contains(s.group) && RadioStore.favourite(getContext(), s.url)) out.add(i);
        }
        sortByName(out);
        return out;
    }

    private void sortByName(List<Integer> out) {
        final Collator collator = Collator.getInstance(new Locale("ar"));
        collator.setStrength(Collator.PRIMARY);
        Collections.sort(out, (a, b) -> collator.compare(sortKey(cat.stations.get(a).name), sortKey(cat.stations.get(b).name)));
    }

    /** The name without «راديو», «إذاعة» and «ال», so the order is the one an Arabic reader expects. */
    private static String sortKey(String name) {
        String s = name.trim();
        for (String prefix : new String[] {"إذاعة ", "راديو ", "الشيخ ", "القارئ "}) {
            if (s.startsWith(prefix)) s = s.substring(prefix.length());
        }
        if (s.startsWith("ال") && s.length() > 3) s = s.substring(2);
        return s.replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا');
    }

    private void buildStations() {
        Context c = getContext();
        sRows.clear();
        CRow country = active < cRows.size() ? cRows.get(active) : null;
        if (country != null && country.kind == C_FAV) {
            List<Integer> favs = favouriteIndexes();
            if (favs.isEmpty()) sRows.add(new SRow(S_NOTE, -1, c.getString(R.string.radio_no_favourites), null));
            for (int i : favs) addStation(i, true);
        } else if (country != null && country.kind == C_COUNTRY) {
            List<Integer> mine = new ArrayList<>();
            for (int i = 0; i < cat.stations.size(); i++) if (country.cc.equals(cat.stations.get(i).group)) mine.add(i);
            if (country.cc.equals(RadioChoice.HOME)) sortByName(mine);
            if (mine.isEmpty()) {
                sRows.add(new SRow(S_NOTE, -1, c.getString(R.string.radio_not_downloaded, RadioStore.countryName(country.cc)), null));
                sRows.add(new SRow(S_UPDATE, -1, updating ? c.getString(R.string.radio_updating) : c.getString(R.string.radio_update), null));
            }
            for (int i : mine) addStation(i, false);
        }
        adapter.notifyDataSetChanged();
    }

    private void addStation(int index, boolean withCountry) {
        Catalog.Station s = cat.stations.get(index);
        boolean live = MusicService.currentSource() == Prefs.SOURCE_RADIO && MusicService.isPlaying() && MusicService.currentStation() == index;
        StringBuilder d = new StringBuilder();
        if (live) d.append("●  ").append(getContext().getString(R.string.live));
        if (withCountry) d.append(d.length() > 0 ? "  ·  " : "").append(RadioStore.countryName(s.group));
        if (!s.section.isEmpty()) d.append(d.length() > 0 ? "  ·  " : "").append(RadioStore.sectionName(s.section));
        if (!s.kind.isEmpty()) d.append(d.length() > 0 ? "  ·  " : "").append(s.kind);
        String title = (RadioStore.favourite(getContext(), s.url) ? "★  " : "") + s.name;
        sRows.add(new SRow(S_STATION, index, title, d.toString()));
    }

    private void update() {
        if (updating) return;
        updating = true;
        buildStations();
        RadioStore.update(getContext(), (ok, n) -> {
            updating = false;
            refresh();
            if (n == 0) android.widget.Toast.makeText(getContext(), R.string.radio_update_failed, android.widget.Toast.LENGTH_LONG).show();
        });
    }

    // ---- focus, for the activity ----

    boolean hasListFocus() {
        if (grid.hasFocus() || bar.hasFocus()) return true;
        for (TextView t : chipViews) if (t.hasFocus()) return true;
        return false;
    }

    /** the stations (or the bar) have the focus, not the chips: «رجوع» then goes up to the chips */
    boolean stationsFocused() { return grid.hasFocus() || bar.hasFocus(); }

    void focusCountries() {
        if (active < chipViews.size()) chipViews.get(active).requestFocus();
    }

    /** From the tabs: the stations (the playing one when it is here), else the chips. */
    void focusFirst() {
        if (sRows.size() > 0 && grid.getSelectedItemPosition() < 0) grid.setSelection(0);
        if (sRows.size() > 0) grid.requestFocus(); else focusCountries();
    }

    /** True when ▲ would leave the pane: the chips have the focus. */
    boolean atTop() {
        for (TextView t : chipViews) if (t.hasFocus()) return true;
        return false;
    }

    // ---- the cards ----

    private final class Cards extends BaseAdapter {
        @Override public int getCount() { return sRows.size(); }
        @Override public Object getItem(int p) { return sRows.get(p); }
        @Override public long getItemId(int p) { return p; }
        @Override public boolean areAllItemsEnabled() { return false; }
        @Override public boolean isEnabled(int p) { int k = sRows.get(p).kind; return k == S_STATION || k == S_UPDATE; }

        @Override public View getView(int position, View convert, ViewGroup parent) {
            SRow r = sRows.get(position);
            LinearLayout box;
            TextView name, sub;
            if (convert instanceof LinearLayout && convert.getTag() != null) {
                box = (LinearLayout) convert;
                name = (TextView) box.getChildAt(0);
                sub = (TextView) box.getChildAt(1);
            } else {
                box = new LinearLayout(getContext());
                box.setOrientation(VERTICAL);
                box.setPadding(dp(16), dp(12), dp(14), dp(12));
                box.setMinimumHeight(dp(78));
                name = new TextView(getContext());
                name.setTextSize(18);
                name.setTypeface(Ui.font(getContext(), "Thmanyah-Medium.otf"));
                name.setMaxLines(2);
                name.setEllipsize(android.text.TextUtils.TruncateAt.END);
                sub = new TextView(getContext());
                sub.setTextSize(12);
                sub.setTypeface(Ui.font(getContext(), "Thmanyah-Light.otf"));
                sub.setTextColor(getResources().getColor(R.color.dim));
                sub.setSingleLine(true);
                sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
                box.addView(name);
                box.addView(sub);
                box.setTag(Boolean.TRUE);
            }
            boolean sel = grid.hasFocus() && position == grid.getSelectedItemPosition();
            boolean live = r.detail != null && r.detail.startsWith("●");
            box.setBackgroundResource(sel ? R.drawable.row_selected : R.drawable.row_bg);
            name.setText(Ui.digits(r.title));
            name.setTextColor(getResources().getColor(live || r.kind == S_UPDATE ? R.color.gold : R.color.sand));
            boolean none = r.detail == null || r.detail.isEmpty();
            sub.setVisibility(none ? View.GONE : View.VISIBLE);
            sub.setText(none ? "" : Ui.digits(r.detail));
            box.setAlpha(r.kind == S_NOTE ? 0.8f : 1f);
            return box;
        }
    }
}
