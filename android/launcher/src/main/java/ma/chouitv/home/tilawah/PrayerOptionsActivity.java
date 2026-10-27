package ma.chouitv.home.tilawah;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import ma.chouitv.home.R;

import java.util.ArrayList;
import java.util.List;

/**
 * One prayer's own options, opened with OK on it in the adhan section: its minutes («تعديل وقت الصلاة», left later, right earlier, saved in the
 * screensaver's settings so the page and the adhan follow), its adhan on or off, a trial of the adhan, and a way back to the calculated time.
 */
public class PrayerOptionsActivity extends Activity {
    static final String EXTRA_SLOT = "slot";
    private static final int ROW_ADJUST = 0, ROW_ADHAN = 1, ROW_TEST = 2, ROW_RESET = 3;

    private Prefs prefs;
    private int slot, prayer;
    private int[] adj = new int[PrayerAdjust.COUNT];
    private ListView list;
    private Rows adapter;
    private View lastSelected;

    private static final class Row {
        final int kind;
        final String title, detail;
        Row(int kind, String title, String detail) { this.kind = kind; this.title = title; this.detail = detail; }
    }

    private final List<Row> rows = new ArrayList<>();

    @Override public boolean dispatchKeyEvent(android.view.KeyEvent e) {
        if (AdhanStop.onKey(this, e)) return true;   // any key ends a ringing adhan
        return super.dispatchKeyEvent(e);
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        ma.chouitv.home.Digits.init(this);   // the screensaver's «الأرقام» setting
        setContentView(R.layout.activity_adhan);
        prefs = new Prefs(this);
        slot = Math.max(0, Math.min(PrayerTimes.PRAYERS.length - 1, getIntent().getIntExtra(EXTRA_SLOT, 0)));
        prayer = PrayerTimes.PRAYERS[slot];

        TextView title = findViewById(R.id.title);
        Ui.text(title, "ThmanyahDisplay-Bold.otf", 40, R.color.sand);
        title.setText(PrayerTimes.NAMES[prayer]);
        TextView hint = findViewById(R.id.hint);
        Ui.text(hint, "Thmanyah-Regular.otf", 14, R.color.dim);
        hint.setText(R.string.prayer_hint);

        list = findViewById(R.id.list);
        adapter = new Rows();
        list.setAdapter(adapter);
        list.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { highlight(view); }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        list.setOnItemClickListener((parent, view, position, id) -> {
            switch (rows.get(position).kind) {
                case ROW_ADHAN:
                    prefs.setAdhanPrayerOn(slot, !prefs.adhanPrayerOn(slot));
                    AdhanScheduler.schedule(this);   // the alarm is for the next prayer whose adhan is on
                    refresh();
                    break;
                case ROW_TEST:
                    int voice = prefs.playVoice(this);
                    MusicService.adhan(this, voice, Adhan.name(voice), 12);
                    break;
                case ROW_RESET:
                    save(new int[PrayerAdjust.COUNT]);
                    break;
                default:   // the minutes are set with left/right
                    break;
            }
        });
        refresh();
        list.post(() -> {
            list.requestFocus();
            highlight(list.getSelectedView());
        });
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        int at = list.getSelectedItemPosition();
        boolean onMinutes = at >= 0 && at < rows.size() && rows.get(at).kind == ROW_ADJUST;
        if (onMinutes && (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)) {
            int v = Math.max(-PrayerAdjust.MAX, Math.min(PrayerAdjust.MAX, adj[slot] + (keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? 1 : -1)));
            if (v != adj[slot]) {
                int[] next = adj.clone();
                next[slot] = v;
                save(next);
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void save(int[] next) {
        if (!Place.saveAdjust(this, next)) {
            Toast.makeText(this, R.string.prayer_save_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        AdhanSync.reschedule(this);   // the alarm was set for the old minutes
        refresh();
    }

    /** The rows as the settings are now; the selected row stays selected. */
    private void refresh() {
        Place place = Place.get(this);
        adj = place.adj.clone();
        PrayerTimes times = PrayerTimes.today(this, place);
        String now = PrayerTimes.clock12(times.clock[prayer]);
        rows.clear();
        rows.add(new Row(ROW_ADJUST, getString(R.string.prayer_adjust), adj[slot] == 0
                ? now + "  •  " + getString(R.string.prayer_as_calculated)
                : now + "  •  " + PrayerTimes.signedMinutes(adj[slot]) + "  •  "
                    + getString(R.string.prayer_calculated, PrayerTimes.clock12(times.clockWithout(prayer, adj[slot])))));
        rows.add(new Row(ROW_ADHAN, getString(R.string.prayer_adhan_title),
                getString(prefs.adhanPrayerOn(slot) ? R.string.adhan_on : R.string.adhan_off)));
        rows.add(new Row(ROW_TEST, getString(R.string.prayer_test_title), getString(R.string.prayer_test_detail)));
        if (adj[slot] != 0) rows.add(new Row(ROW_RESET, getString(R.string.prayer_reset_title), getString(R.string.prayer_reset_detail)));
        int selected = list == null ? 0 : Math.max(0, Math.min(list.getSelectedItemPosition(), rows.size() - 1));
        adapter.notifyDataSetChanged();
        if (list != null) {
            list.setSelection(selected);
            list.post(() -> highlight(list.getSelectedView()));
        }
    }

    private void highlight(View view) {
        if (lastSelected != null && lastSelected != view) resetRow(lastSelected);
        if (view == null) { lastSelected = null; return; }
        view.setBackgroundResource(R.drawable.row_selected);
        View cursor = view.findViewById(R.id.cursor);
        if (cursor != null) cursor.setVisibility(View.VISIBLE);
        lastSelected = view;
    }

    private void resetRow(View view) {
        view.setBackgroundResource(R.drawable.row_bg);
        View cursor = view.findViewById(R.id.cursor);
        if (cursor != null) cursor.setVisibility(View.INVISIBLE);
    }

    private final class Rows extends BaseAdapter {
        @Override public int getCount() { return rows.size(); }
        @Override public Object getItem(int position) { return rows.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView != null ? convertView
                    : LayoutInflater.from(parent.getContext()).inflate(R.layout.item_row, parent, false);
            resetRow(view);
            Row row = rows.get(position);
            TextView name = view.findViewById(R.id.name);
            TextView detail = view.findViewById(R.id.detail);
            name.setText(row.title);
            name.setTypeface(Ui.font(PrayerOptionsActivity.this, "Thmanyah-Medium.otf"));
            detail.setText(row.detail);
            detail.setTypeface(Ui.font(PrayerOptionsActivity.this, "Thmanyah-Light.otf"));
            return view;
        }
    }
}
