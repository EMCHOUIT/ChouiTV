package ma.chouitv.home.tilawah;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import ma.chouitv.home.R;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The «الإذاعات» tab: the countries the person chose in a narrow column (with the favourites above and the way to the settings below),
 * and the stations of the one under the focus beside it. ◀ ▶ move between the two, ▲ ▼ inside one, OK plays, a long OK is a favourite.
 * Two plain lists, only the rows on screen are built, so 120 stations cost nothing.
 */
final class RadioPane extends LinearLayout {

    interface Host {
        void openPlayer();
        void openSettings();
        void focusTabs();
    }

    private static final int C_FAV = 0, C_COUNTRY = 1, C_CHOOSE = 2;
    private static final int S_HEADER = 0, S_STATION = 1, S_NOTE = 2, S_UPDATE = 3;

    private static final class CRow {
        final int kind;
        final String cc, title, detail;
        CRow(int kind, String cc, String title, String detail) { this.kind = kind; this.cc = cc; this.title = title; this.detail = detail; }
    }

    private static final class SRow {
        final int kind, index;
        final String title, detail;
        SRow(int kind, int index, String title, String detail) { this.kind = kind; this.index = index; this.title = title; this.detail = detail; }
    }

    private final Host host;
    private final Catalog cat;
    private final ListView countries, stations;
    private final List<CRow> cRows = new ArrayList<>();
    private final List<SRow> sRows = new ArrayList<>();
    private final BaseAdapter cAdapter, sAdapter;
    private int active;          // the country row whose stations are shown
    private boolean updating;

    RadioPane(Context context, Host host) {
        super(context);
        this.host = host;
        this.cat = Catalog.get(context);
        setOrientation(HORIZONTAL);
        setLayoutDirection(View.LAYOUT_DIRECTION_RTL);   // the countries on the right, as the tabs
        countries = makeList(context);
        stations = makeList(context);
        cAdapter = new CAdapter();
        sAdapter = new SAdapter();
        countries.setAdapter(cAdapter);
        stations.setAdapter(sAdapter);
        addView(countries, new LayoutParams(dp(250), ViewGroup.LayoutParams.MATCH_PARENT));
        LayoutParams sp = new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        sp.setMarginEnd(dp(8));
        sp.setMarginStart(dp(16));
        addView(stations, sp);

        countries.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int position, long id) {
                if (position >= 0 && position < cRows.size() && cRows.get(position).kind != C_CHOOSE && position != active) {
                    active = position;
                    buildStations();
                    stations.setSelectionFromTop(0, 0);   // each country opens at its top, its first section's title included
                }
                cAdapter.notifyDataSetChanged();
            }
            @Override public void onNothingSelected(AdapterView<?> p) { }
        });
        stations.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int position, long id) {
                // a row under a section title keeps the title in view (a plain selection scrolls the row to the very top)
                if (position > 0 && position - 1 < sRows.size() && sRows.get(position - 1).kind == S_HEADER
                        && stations.getFirstVisiblePosition() >= position) {
                    final int at = position;
                    stations.post(() -> stations.setSelectionFromTop(at, dp(46)));
                }
                sAdapter.notifyDataSetChanged();
            }
            @Override public void onNothingSelected(AdapterView<?> p) { }
        });
        OnFocusChangeListener repaint = (v, has) -> { cAdapter.notifyDataSetChanged(); sAdapter.notifyDataSetChanged(); };
        countries.setOnFocusChangeListener(repaint);
        stations.setOnFocusChangeListener(repaint);

        countries.setOnItemClickListener((p, v, position, id) -> {
            CRow row = cRows.get(position);
            if (row.kind == C_CHOOSE) host.openSettings();
            else stations.requestFocus();
        });
        stations.setOnItemClickListener((p, v, position, id) -> {
            SRow row = sRows.get(position);
            if (row.kind == S_STATION) {
                MusicService.playRadio(getContext(), row.index);
                host.openPlayer();
            } else if (row.kind == S_UPDATE) {
                update();
            }
        });
        stations.setOnItemLongClickListener((p, v, position, id) -> {
            SRow row = sRows.get(position);
            if (row.kind != S_STATION) return false;
            RadioStore.toggleFavourite(getContext(), cat.stations.get(row.index).url);
            int keep = position;
            refresh();
            stations.setSelection(Math.min(keep, sRows.size() - 1));
            return true;
        });
    }

    private ListView makeList(Context c) {
        ListView l = new ListView(c);
        l.setDivider(null);
        l.setDividerHeight(dp(9));
        l.setSelector(android.R.color.transparent);
        l.setFadingEdgeLength(0);
        l.setVerticalScrollBarEnabled(false);
        l.setClipToPadding(false);
        l.setPadding(0, 0, 0, dp(8));
        l.setItemsCanFocus(false);
        l.setFocusable(true);
        return l;
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    // ---- what is shown ----

    /** Reads everything again (the countries chosen, the lists, the favourites, what plays) and keeps the place where it can. */
    void refresh() {
        Context c = getContext();
        String keepCc = active < cRows.size() ? cRows.get(active).cc : null;
        cRows.clear();
        int favs = favouriteIndexes().size();
        cRows.add(new CRow(C_FAV, "*", "★  " + c.getString(R.string.radio_favourites) + (favs == 0 ? "" : "  ·  " + Ui.arabic(favs)), ""));
        for (String cc : RadioStore.chosen(c)) {
            int n = 0;
            for (Catalog.Station s : cat.stations) if (cc.equals(s.group)) n++;
            cRows.add(new CRow(C_COUNTRY, cc, RadioStore.countryName(cc) + (n == 0 ? "" : "  ·  " + Ui.arabic(n)), ""));   // one line: the four rows fit without scrolling
        }
        cRows.add(new CRow(C_CHOOSE, "", "⚙  " + c.getString(R.string.radio_choose), ""));
        active = 1;
        for (int i = 0; i < cRows.size(); i++) if (cRows.get(i).kind != C_CHOOSE && cRows.get(i).cc.equals(keepCc)) active = i;
        cAdapter.notifyDataSetChanged();
        buildStations();
    }

    /** The country of the radio that plays, so the tab opens on it; else the first country. */
    void selectPlayingCountry() {
        int playing = MusicService.currentSource() == Prefs.SOURCE_RADIO ? MusicService.currentStation() : -1;
        if (playing >= 0 && playing < cat.stations.size()) {
            String cc = cat.stations.get(playing).group;
            for (int i = 0; i < cRows.size(); i++) {
                if (cRows.get(i).kind == C_COUNTRY && cRows.get(i).cc.equals(cc)) {
                    active = i;
                    countries.setSelection(i);
                    buildStations();
                    int at = stationRow(playing);
                    if (at >= 0) stations.setSelection(at);
                    return;
                }
            }
        }
        active = Math.min(1, cRows.size() - 1);
        countries.setSelection(active);
        buildStations();
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
            if ((chosen.contains(s.group)) && RadioStore.favourite(getContext(), s.url)) out.add(i);
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
                sRows.add(new SRow(S_NOTE, -1, c.getString(R.string.radio_not_downloaded, country.title), null));
                sRows.add(new SRow(S_UPDATE, -1, updating ? c.getString(R.string.radio_updating) : c.getString(R.string.radio_update), null));
            }
            String section = null;
            for (int i : mine) {
                Catalog.Station s = cat.stations.get(i);
                if (!s.section.isEmpty() && !s.section.equals(section)) {
                    section = s.section;
                    sRows.add(new SRow(S_HEADER, -1, RadioStore.sectionName(section), null));
                }
                addStation(i, false);
            }
        }
        sAdapter.notifyDataSetChanged();
    }

    private void addStation(int index, boolean withCountry) {
        Catalog.Station s = cat.stations.get(index);
        boolean live = MusicService.currentSource() == Prefs.SOURCE_RADIO && MusicService.isPlaying() && MusicService.currentStation() == index;
        StringBuilder d = new StringBuilder();
        if (live) d.append("●  ").append(getContext().getString(R.string.live));
        if (withCountry) d.append(d.length() > 0 ? "  •  " : "").append(RadioStore.countryName(s.group));
        if (!s.kind.isEmpty()) d.append(d.length() > 0 ? "  •  " : "").append(s.kind);
        if (!s.place.isEmpty()) d.append(d.length() > 0 ? "  •  " : "").append(s.place);
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

    boolean hasListFocus() { return countries.hasFocus() || stations.hasFocus(); }

    boolean stationsFocused() { return stations.hasFocus(); }

    void focusCountries() { countries.requestFocus(); }

    void focusFirst() { if (sRows.size() > 0 && countries.getSelectedItemPosition() < 0) countries.setSelection(active); countries.requestFocus(); }

    /** True when ▲ would leave the list: the first row, or the row under the first header. */
    boolean atTop() {
        ListView l = stations.hasFocus() ? stations : countries;
        int at = l.getSelectedItemPosition();
        if (l == stations) {
            int first = 0;
            while (first < sRows.size() && sRows.get(first).kind == S_HEADER) first++;
            return at <= first;
        }
        return at <= 0;
    }

    // ---- adapters ----

    private TextView text(int sp, String font, int color) {
        TextView t = new TextView(getContext());
        t.setTextSize(sp);
        t.setTypeface(Ui.font(getContext(), font));
        t.setTextColor(getResources().getColor(color));
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        return t;
    }

    private View row(View convert, String title, String detail, boolean selected, boolean current) {
        LinearLayout box;
        TextView name, sub;
        if (convert instanceof LinearLayout && convert.getTag() != null) {
            box = (LinearLayout) convert;
            name = (TextView) box.getChildAt(0);
            sub = (TextView) box.getChildAt(1);
        } else {
            box = new LinearLayout(getContext());
            box.setOrientation(VERTICAL);
            box.setPadding(dp(20), dp(9), dp(16), dp(9));
            name = text(20, "Thmanyah-Medium.otf", R.color.sand);
            sub = text(13, "Thmanyah-Light.otf", R.color.dim);
            box.addView(name);
            box.addView(sub);
            box.setTag(Boolean.TRUE);
        }
        box.setBackgroundResource(selected ? R.drawable.row_selected : R.drawable.row_bg);
        name.setText(Ui.digits(title));
        name.setTextColor(getResources().getColor(current ? R.color.gold : R.color.sand));
        boolean none = detail == null || detail.isEmpty();
        sub.setVisibility(none ? View.GONE : View.VISIBLE);
        sub.setText(none ? "" : Ui.digits(detail));
        return box;
    }

    private final class CAdapter extends BaseAdapter {
        @Override public int getCount() { return cRows.size(); }
        @Override public Object getItem(int p) { return cRows.get(p); }
        @Override public long getItemId(int p) { return p; }
        @Override public View getView(int position, View convert, ViewGroup parent) {
            CRow r = cRows.get(position);
            boolean sel = countries.hasFocus() && position == countries.getSelectedItemPosition();
            return row(convert, r.title, r.detail, sel, position == active && r.kind != C_CHOOSE);
        }
    }

    private final class SAdapter extends BaseAdapter {
        @Override public int getCount() { return sRows.size(); }
        @Override public Object getItem(int p) { return sRows.get(p); }
        @Override public long getItemId(int p) { return p; }
        @Override public boolean areAllItemsEnabled() { return false; }
        @Override public boolean isEnabled(int p) { int k = sRows.get(p).kind; return k == S_STATION || k == S_UPDATE; }
        @Override public int getViewTypeCount() { return 2; }
        @Override public int getItemViewType(int p) { return sRows.get(p).kind == S_HEADER ? 0 : 1; }
        @Override public View getView(int position, View convert, ViewGroup parent) {
            SRow r = sRows.get(position);
            if (r.kind == S_HEADER) {
                TextView h = convert instanceof TextView ? (TextView) convert : text(16, "Thmanyah-Medium.otf", R.color.gold);
                h.setPadding(dp(10), dp(12), dp(10), dp(4));
                h.setText(r.title);
                h.setGravity(Gravity.START);
                return h;
            }
            boolean sel = stations.hasFocus() && position == stations.getSelectedItemPosition();
            boolean live = r.detail != null && r.detail.startsWith("●");
            return row(convert, r.title, r.detail, sel, live);
        }
    }
}
