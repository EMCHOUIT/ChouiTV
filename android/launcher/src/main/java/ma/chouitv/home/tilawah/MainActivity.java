package ma.chouitv.home.tilawah;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ListView;
import android.widget.TextView;

import ma.chouitv.home.R;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import java.util.List;

/**
 * The «تلاوة» screen: three sections that never share a page. The tab bar switches between the
 * reciters, the live stations and the adhan (today's times, the switch and the muezzin).
 */
public class MainActivity extends Activity {

    private static final int TAB_RECITERS = 0, TAB_MA = 1, TAB_QURAN = 2, TAB_ADHAN = 3;

    private static final int KIND_RESUME = 0, KIND_RECITER = 1, KIND_STATION = 2,
            KIND_PRAYER = 3, KIND_ADHAN_TOGGLE = 4, KIND_VOICE = 5, KIND_VOLUME = 6, KIND_EXACT = 7;

    private Catalog cat;
    private Prefs prefs;
    private AbsListView list;   // whichever of the two is on screen
    private ListView listView;
    private GridView gridView;
    private ViewGroup tabs;
    private TextView[] tabViews;
    private int tab = TAB_RECITERS;
    private RowsAdapter adapter;
    private View lastSelected;
    private boolean focusedOnce;
    private RadioPane radioPane;   // the «الإذاعات» tab: countries beside stations, its own two lists

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        ma.chouitv.home.Digits.init(this);   // the screensaver's «الأرقام» setting
        setContentView(R.layout.activity_main);
        cat = Catalog.get(this);
        prefs = new Prefs(this);

        Ui.text((TextView) findViewById(R.id.title), "ThmanyahDisplay-Bold.otf", 44, R.color.sand);
        Ui.text((TextView) findViewById(R.id.tagline), "Thmanyah-Regular.otf", 17, R.color.dim);
        Ui.text((TextView) findViewById(R.id.hint), "Thmanyah-Regular.otf", 14, R.color.dim);

        listView = findViewById(R.id.list);
        gridView = findViewById(R.id.grid);
        list = listView;
        radioPane = new RadioPane(this, new RadioPane.Host() {
            @Override public void openPlayer() { MainActivity.this.openPlayer(); }
            @Override public void openSettings() { startActivity(new Intent(MainActivity.this, RadioCountriesActivity.class)); }
            @Override public void focusTabs() { tabViews[tab].requestFocus(); }
        });
        radioPane.setVisibility(View.GONE);
        ViewGroup column = (ViewGroup) gridView.getParent();
        android.widget.LinearLayout.LayoutParams paneParams = new android.widget.LinearLayout.LayoutParams(-1, 0, 1f);
        paneParams.topMargin = (int) (16 * getResources().getDisplayMetrics().density);
        column.addView(radioPane, column.indexOfChild(gridView) + 1, paneParams);
        adapter = new RowsAdapter();
        listView.setAdapter(adapter);
        gridView.setAdapter(adapter);
        AdapterView.OnItemSelectedListener selListener = new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (list.hasFocus() && position >= 0 && position < adapter.rows.size()
                        && !adapter.rows.get(position).header) {
                    highlight(view);
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        };
        listView.setOnItemSelectedListener(selListener);
        gridView.setOnItemSelectedListener(selListener);
        // one focus ring at a time: the row keeps its gold frame only while the list owns the focus
        View.OnFocusChangeListener focusListener = (v, hasFocus) -> {
            if (!hasFocus) {
                if (lastSelected != null) resetRow(lastSelected);
                lastSelected = null;
            } else {
                int selected = list.getSelectedItemPosition();
                if (selected >= 0 && selected < adapter.rows.size() && !adapter.rows.get(selected).header) {
                    highlight(list.getSelectedView());
                }
            }
        };
        listView.setOnFocusChangeListener(focusListener);
        gridView.setOnFocusChangeListener(focusListener);
        AdapterView.OnItemClickListener clickListener = (parent, view, position, id) -> {
            Row row = adapter.rows.get(position);
            if (row.kind == KIND_RESUME) {
                MusicService.resumeLast(this);
                openPlayer();
            } else if (row.kind == KIND_RECITER) {
                startActivity(new Intent(this, SurahActivity.class).putExtra("reciter", row.index));
            } else if (row.kind == KIND_STATION) {
                MusicService.playRadio(this, row.index);
                openPlayer();
            } else if (row.kind == KIND_PRAYER) {   // the prayer's own options: its minutes, its adhan on or off, a trial of the adhan
                startActivity(new Intent(this, PrayerOptionsActivity.class).putExtra(PrayerOptionsActivity.EXTRA_SLOT, PrayerTimes.slot(row.index)));
            } else if (row.kind == KIND_ADHAN_TOGGLE) {
                prefs.setAdhanOn(!prefs.adhanOn());
                AdhanScheduler.schedule(this);
                adapter.rebuild();
                focusList(list.getSelectedItemPosition());
            } else if (row.kind == KIND_VOICE) {
                startActivity(new Intent(this, AdhanActivity.class));
            } else if (row.kind == KIND_EXACT) {
                openExactAlarmSettings();
            }   // «صوت الأذان» is set with left/right (changeVolume); OK on it does nothing
        };
        listView.setOnItemClickListener(clickListener);
        gridView.setOnItemClickListener(clickListener);

        tabs = findViewById(R.id.tabs);
        tabViews = new TextView[]{
                findViewById(R.id.tab_reciters),
                findViewById(R.id.tab_stations),
                findViewById(R.id.tab_quran),
                findViewById(R.id.tab_adhan)
        };
        for (int i = 0; i < tabViews.length; i++) {
            final int which = i;
            tabViews[i].setTypeface(Ui.font(this, "Thmanyah-Medium.otf"));
            tabViews[i].setTextSize(19);
            tabViews[i].setOnClickListener(v -> {
                selectTab(which);
                if (radioTab()) radioPane.focusFirst(); else focusList(firstSelectable());
            });
        }
        selectTab(TAB_RECITERS);

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }

        // today's times from Aladhan when the network answers, and the recordings kept for offline
        // (refresh also puts the alarm back for the new times)
        PrayerTimes.refresh(this, () -> { adapter.rebuild(); focusList(list.getSelectedItemPosition()); });
        Adhan.prefetchAll(this);
        AdhanScheduler.schedule(this);
    }

    private boolean radioTab() { return tab == TAB_MA; }

    @Override protected void onResume() {
        super.onResume();
        if (radioTab()) {   // back from the player or the settings: the lists are read again, the focus stays where it was
            boolean had = radioPane.hasListFocus();
            radioPane.refresh();
            if (!had) radioPane.focusFirst();
            return;
        }
        adapter.rebuild();
        int target;
        if (!focusedOnce) {
            target = firstReciter();
            focusedOnce = true;
        } else {
            target = list.getSelectedItemPosition();
            if (target < 0 || target >= adapter.getCount()) {
                target = Math.min(Math.max(0, list.getFirstVisiblePosition()), adapter.getCount() - 1);
            }
        }
        while (target < adapter.getCount() && adapter.rows.get(target).header) target++;
        if (target >= adapter.getCount()) target = 0;
        focusList(target);
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && list != null && !radioTab()) {
            focusList(Math.max(0, list.getSelectedItemPosition()));
        }
    }

    /** The tab changes what the list holds; the selected row becomes the first one of that section. */
    private void selectTab(int which) {
        tab = Math.max(TAB_RECITERS, Math.min(TAB_ADHAN, which));
        boolean cards = isCards();
        boolean radio = radioTab();
        list = cards ? gridView : listView;
        listView.setVisibility(cards || radio ? View.GONE : View.VISIBLE);
        gridView.setVisibility(cards && !radio ? View.VISIBLE : View.GONE);
        if (radioPane != null) radioPane.setVisibility(radio ? View.VISIBLE : View.GONE);
        ((TextView) findViewById(R.id.hint)).setText(radio ? R.string.hint_radio : R.string.hint_main);
        lastSelected = null;
        for (int i = 0; i < tabViews.length; i++) tabViews[i].setSelected(i == tab);
        if (radio) {
            if (radioPane != null) { radioPane.refresh(); radioPane.selectPlayingCountry(); }
            return;
        }
        if (adapter != null) adapter.rebuild();
        if (list != null) select(firstSelectable());
    }

    /**
     * Selects a row and keeps the section header just above it in view: a plain setSelection puts the row at the top of the list
     * and pushes its header off the screen (the adhan section opens on «الصلوات اليوم»).
     */
    private void select(int position) {
        boolean headerAbove = position > 0 && position - 1 < adapter.rows.size() && adapter.rows.get(position - 1).header;
        if (headerAbove && list == listView) listView.setSelectionFromTop(position, (int) (46 * getResources().getDisplayMetrics().density));
        else list.setSelection(position);
    }

    /** The reciters and the two station sections are a grid of cards, four to a row; the adhan is a list. */
    private boolean isCards() { return tab != TAB_ADHAN; }

    private boolean inTopRow() {
        int at = list.getSelectedItemPosition();
        return list == gridView ? at < 4 : at <= firstSelectable();
    }

    private int firstSelectable() {
        int i = 0;
        while (i < adapter.getCount() && adapter.rows.get(i).header) i++;
        return Math.min(i, Math.max(0, adapter.getCount() - 1));
    }

    private int firstReciter() {
        for (int i = 0; i < adapter.rows.size(); i++) {
            if (adapter.rows.get(i).kind == KIND_RECITER) return i;
        }
        return firstSelectable();
    }

    private void focusList(int position) {
        select(position);
        list.post(() -> {
            list.requestFocus();
            int selected = list.getSelectedItemPosition();
            if (selected >= 0 && selected < adapter.rows.size() && !adapter.rows.get(selected).header) {
                highlight(list.getSelectedView());
            }
        });
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (AdhanStop.onKey(this, event)) return true;   // any key ends a ringing adhan
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0
                && event.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP
                && radioTab() && radioPane.hasListFocus() && radioPane.atTop()) {
            tabViews[tab].requestFocus();
            return true;
        }
        // the list swallows «up» even on its first row, so the jump back to the tabs happens here
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0
                && event.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP
                && list != null && list.hasFocus()
                && inTopRow()) {
            tabViews[tab].requestFocus();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override public void onBackPressed() {
        if (radioTab() && radioPane.stationsFocused()) {   // stations → countries → out
            radioPane.focusCountries();
            return;
        }
        super.onBackPressed();
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        // in the radios the two lists own ◀ ▶ (the tab bar is reached with ▲); at the edge nothing moves
        if (radioTab() && radioPane.hasListFocus()
                && (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)) {
            return super.onKeyDown(keyCode, event);
        }
        // on «صوت الأذان» left/right are the volume: left louder, right quieter (the bar fills from the right, like the page)
        if ((keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) && list.hasFocus()
                && tab == TAB_ADHAN && selectedKind() == KIND_VOLUME) {
            changeVolume(keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? 10 : -10);
            return true;
        }
        // in the cards left/right move between cards; up from the first row goes back to the tabs
        if (list == gridView && list.hasFocus()
                && (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)) {
            return super.onKeyDown(keyCode, event);
        }
        // left/right change the section from the tab bar and from the adhan list; the focus then goes to the tab bar, so the person sees
        // the section change and enters it when they choose (down), instead of landing in the new list by surprise. (The cards of a grid
        // and the adhan's volume have their own left/right above.)
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            // RTL: the first tab sits on the right, so right is «previous»
            int delta = keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ? -1 : 1;
            int next = Math.max(TAB_RECITERS, Math.min(TAB_ADHAN, tab + delta));
            if (next == tab) return true;
            selectTab(next);
            tabViews[next].requestFocus();
            return true;
        }
        if (tabs.hasFocus()) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                    || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                if (radioTab()) radioPane.focusFirst(); else focusList(firstSelectable());
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) return true;
        }
        // up from the first row lands on the tab bar instead of doing nothing
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP && list.hasFocus()
                && inTopRow()) {
            tabViews[tab].requestFocus();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    /** The TV is far away: the selected row gets a solid gold frame and a chevron, not just a hairline. */
    private void highlight(View view) {
        if (lastSelected != null && lastSelected != view) resetRow(lastSelected);
        if (view == null) {
            lastSelected = null;
            return;
        }
        view.setBackgroundResource(R.drawable.row_selected);
        View cursor = view.findViewById(R.id.cursor);
        if (cursor != null) cursor.setVisibility(View.VISIBLE);
        lastSelected = view;
    }

    private void resetRow(View view) {
        view.setBackgroundResource(R.drawable.row_bg);   // cards use the same glass
        View cursor = view.findViewById(R.id.cursor);
        if (cursor != null) cursor.setVisibility(View.INVISIBLE);
    }

    private int selectedKind() {
        int at = list.getSelectedItemPosition();
        return at >= 0 && at < adapter.rows.size() ? adapter.rows.get(at).kind : -1;
    }

    /** The adhan's volume in steps of 10 (10 to 100, no wrapping), heard for two seconds at the new level. */
    private void changeVolume(int step) {
        int was = prefs.adhanVolume(), now = Math.max(10, Math.min(100, was + step));
        if (now == was) return;
        prefs.setAdhanVolume(now);
        adapter.rebuild();
        focusList(list.getSelectedItemPosition());
        MusicService.adhan(this, prefs.voice(), Adhan.name(prefs.voice()), 2);
    }

    /** ٣٠٪ and a bar of ten: the filled part starts at the right, as the page does */
    private static String volumeText(int value) {
        int filled = Math.max(0, Math.min(10, value / 10));
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < 10; i++) bar.append(i < filled ? '█' : '░');
        return percent(value) + "  " + bar;
    }

    /** From Android 12 exact alarms are the user's to allow: the system screen for it, else the app's own page. */
    private void openExactAlarmSettings() {
        Uri app = Uri.parse("package:" + getPackageName());
        Intent[] tries = Build.VERSION.SDK_INT >= 31
                ? new Intent[]{new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, app),
                        new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, app)}
                : new Intent[]{new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, app)};
        for (Intent intent : tries) {
            try { startActivity(intent); return; } catch (RuntimeException ignored) { }
        }
    }

    private void openPlayer() {
        startActivity(new Intent(this, PlayerActivity.class));
    }

    /** ٣٠٪ (or 30%): the adhan volume in the digits the rest of the screen uses. */
    private static String percent(int value) {
        return Ui.digits(value + "%");
    }

    /** The indexes (into cat.stations) of one group, in Arabic alphabetical order of the names without «راديو», «إذاعة», «الشيخ» and «ال». */
    private List<Integer> alphabetical(String group) {
        final Collator collator = Collator.getInstance(new Locale("ar"));
        collator.setStrength(Collator.PRIMARY);
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < cat.stations.size(); i++) {
            if (group.equals(cat.stations.get(i).group)) out.add(i);
        }
        Collections.sort(out, (a, b) -> collator.compare(sortKey(cat.stations.get(a).name), sortKey(cat.stations.get(b).name)));
        return out;
    }

    private static String sortKey(String name) {
        String s = name.trim();
        for (String prefix : new String[] {"إذاعة ", "راديو ", "الشيخ ", "القارئ "}) {
            if (s.startsWith(prefix)) s = s.substring(prefix.length());
        }
        if (s.startsWith("ال") && s.length() > 3) s = s.substring(2);
        return s.replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا');
    }

    private static final class Row {
        final boolean header;
        final int kind, index;
        final String title, detail;
        final boolean next;
        Row(boolean header, int kind, String title, String detail, int index) {
            this(header, kind, title, detail, index, false);
        }
        Row(boolean header, int kind, String title, String detail, int index, boolean next) {
            this.header = header; this.kind = kind; this.title = title; this.detail = detail;
            this.index = index; this.next = next;
        }
        static Row header(String title) { return new Row(true, -1, title, null, -1); }
    }

    private final class RowsAdapter extends BaseAdapter {
        final List<Row> rows = new ArrayList<>();

        void rebuild() {
            rows.clear();
            if (tab == TAB_RECITERS) {
                if (prefs.source() == Prefs.SOURCE_SURAH) {
                    int index = cat.reciterIndex(prefs.reciter());
                    if (index >= 0) {
                        rows.add(new Row(false, KIND_RESUME, getString(R.string.continue_listening),
                                getString(R.string.continue_detail,
                                        cat.surahName(prefs.surah()), cat.reciters.get(index).name), index));
                    }
                }
                for (int i = 0; i < cat.reciters.size(); i++) {
                    Catalog.Reciter reciter = cat.reciters.get(i);
                    rows.add(new Row(false, KIND_RECITER, reciter.name,
                            getString(R.string.surah_count, Ui.arabic(reciter.surahs)), i));
                }
            } else if (tab == TAB_MA) {
                // the radios have their own two lists (RadioPane)
            } else if (tab == TAB_QURAN) {
                String group = "quran";
                if (prefs.source() == Prefs.SOURCE_RADIO && prefs.station() < cat.stations.size()
                        && group.equals(cat.stations.get(prefs.station()).group)) {
                    rows.add(new Row(false, KIND_RESUME, cat.stations.get(prefs.station()).name,
                            getString(R.string.continue_listening), -1, true));
                }
                boolean radioOn = MusicService.currentSource() == Prefs.SOURCE_RADIO && MusicService.isPlaying();
                for (int i : alphabetical(group)) {
                    boolean now = radioOn && MusicService.currentStation() == i;
                    rows.add(new Row(false, KIND_STATION, cat.stations.get(i).name, now ? "●  " + getString(R.string.live) : "", i));
                }
            } else {
                // the adhan section: the switch comes first, and everything under it shows only while the adhan is on (off, the prayers go;
                // on again, they come back under the switch): a warning when the alarm may be late, today's five times (OK opens a prayer's
                // own options), then the muezzin and the volume
                Place place = Place.get(MainActivity.this);
                PrayerTimes times = PrayerTimes.today(MainActivity.this, place);
                PrayerTimes.Next next = PrayerTimes.next(MainActivity.this);
                PrayerTimes.Next ringing = PrayerTimes.nextAdhan(MainActivity.this);
                String toggle = prefs.adhanOn() ? getString(R.string.adhan_on) : getString(R.string.adhan_off);
                if (prefs.adhanOn() && ringing != null) {
                    toggle += "  •  " + ringing.name() + " " + PrayerTimes.remaining(ringing.at - System.currentTimeMillis());
                }
                rows.add(new Row(false, KIND_ADHAN_TOGGLE, getString(R.string.adhan_auto), toggle, -1));
                if (prefs.adhanOn()) {
                    if (!AdhanScheduler.exactAllowed(MainActivity.this)) {   // the alarm may be minutes late
                        rows.add(new Row(false, KIND_EXACT, getString(R.string.adhan_exact_title), getString(R.string.adhan_exact_detail), -1));
                    }
                    rows.add(Row.header(getString(R.string.sec_today)));
                    for (int prayer : PrayerTimes.PRAYERS) {
                        int slot = PrayerTimes.slot(prayer);
                        boolean isNext = next != null && next.index == prayer;
                        String detail = PrayerTimes.clock12(times.clock[prayer]);
                        if (place.adj[slot] != 0) detail += "  •  " + PrayerTimes.signedMinutes(place.adj[slot]);
                        if (isNext) detail += "  •  " + PrayerTimes.remaining(next.at - System.currentTimeMillis());
                        if (!prefs.adhanPrayerOn(slot)) detail += "  •  " + getString(R.string.prayer_adhan_off);
                        rows.add(new Row(false, KIND_PRAYER, PrayerTimes.NAMES[prayer], detail, prayer, isNext));
                    }
                    rows.add(Row.header(getString(R.string.adhan)));
                    rows.add(new Row(false, KIND_VOICE, getString(R.string.adhan_voice), Adhan.label(prefs.playVoice(MainActivity.this)), -1));
                    rows.add(new Row(false, KIND_VOLUME, getString(R.string.adhan_volume),
                            volumeText(prefs.adhanVolume()), -1));
                }
            }
            notifyDataSetChanged();
        }

        @Override public int getCount() { return rows.size(); }
        @Override public Object getItem(int position) { return rows.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public boolean areAllItemsEnabled() { return false; }
        @Override public boolean isEnabled(int position) { return !rows.get(position).header; }
        @Override public int getViewTypeCount() { return 3; }
        @Override public int getItemViewType(int position) { return rows.get(position).header ? 0 : isCards() ? 2 : 1; }

        @Override public View getView(int position, View convertView, ViewGroup parent) {
            Row row = rows.get(position);
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            if (row.header) {
                TextView header = (TextView) (convertView != null ? convertView
                        : inflater.inflate(R.layout.item_header, parent, false));
                header.setText(row.title);
                header.setTypeface(Ui.font(MainActivity.this, "Thmanyah-Medium.otf"));
                return header;
            }
            View view = convertView != null ? convertView : inflater.inflate(isCards() ? R.layout.item_card : R.layout.item_row, parent, false);
            resetRow(view);
            TextView name = view.findViewById(R.id.name);
            TextView detail = view.findViewById(R.id.detail);
            name.setText(row.title);
            detail.setText(row.detail);
            name.setTypeface(Ui.font(MainActivity.this, "Thmanyah-Medium.otf"));
            detail.setTypeface(Ui.font(MainActivity.this, "Thmanyah-Light.otf"));
            name.setTextColor(getResources().getColor(row.next ? R.color.gold : R.color.sand));
            return view;
        }
    }
}
