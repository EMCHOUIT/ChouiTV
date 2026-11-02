package ma.chouitv.home.tilawah;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import ma.chouitv.home.R;

import java.util.ArrayList;
import java.util.List;

/**
 * The surahs of one reciter, six columns, made for the remote's arrows. OK plays the surah. A long OK ticks it for a list; with something
 * ticked a bar under the grid plays the ticked surahs one after the other (surah order, round to the first at the end), ticks them all, clears,
 * and saves them as a list of this reciter (a list opened from «المفضلة» comes back ticked, and saving then updates it). The ★ button
 * makes the reciter a favourite.
 */
public class SurahActivity extends Activity {

    private static final int COLUMNS = 6;

    private Catalog cat;
    private Catalog.Reciter reciter;
    private GridView grid;
    private TextView favButton, hint, choose;
    private LinearLayout bar;
    private TextView playButton, allButton, clearButton, saveButton;
    private final boolean[] ticked = new boolean[Playlists.TOTAL + 1];
    private SurahAdapter adapter;
    /** the list this screen edits (opened from a list's menu, or just saved), else null */
    private String listName;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        ma.chouitv.home.Digits.init(this);   // the screensaver's «الأرقام» setting
        cat = Catalog.get(this);
        int index = getIntent().getIntExtra("reciter", -1);
        if (index < 0 || index >= cat.reciters.size()) { finish(); return; }
        reciter = cat.reciters.get(index);
        setContentView(R.layout.activity_surah);

        TextView name = findViewById(R.id.reciter);
        name.setText(reciter.name);
        Ui.text(name, "ThmanyahDisplay-Bold.otf", 36, R.color.sand);
        TextView count = findViewById(R.id.count);
        count.setText(getString(R.string.surah_count, Ui.arabic(reciter.surahs)));
        Ui.text(count, "Thmanyah-Regular.otf", 14, R.color.dim);
        choose = findViewById(R.id.choose);
        Ui.text(choose, "Thmanyah-Regular.otf", 16, R.color.gold);
        hint = findViewById(R.id.hint);
        Ui.text(hint, "Thmanyah-Regular.otf", 14, R.color.dim);

        favButton = findViewById(R.id.fav_btn);
        favButton.setTypeface(Ui.font(this, "Thmanyah-Medium.otf"));
        favButton.setOnClickListener(v -> { ReciterStore.toggleFavourite(this, reciter.id); paintFav(); });
        favButton.setOnFocusChangeListener((v, has) -> paintFav());
        paintFav();

        // a list opened from «المفضلة»: its surahs come ticked
        String wanted = getIntent().getStringExtra("list");
        if (wanted != null) {
            Playlists.Item item = ReciterStore.list(this, reciter.id, wanted);
            if (item != null) {
                for (int n : item.surahs) ticked[n] = true;
                listName = item.name;
            }
        }
        showListName();

        adapter = new SurahAdapter();
        grid = findViewById(R.id.grid);
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((parent, view, position, id) -> {
            MusicService.playSurah(this, reciter.id, position + 1);   // one surah alone, whatever is ticked
            startActivity(new Intent(this, PlayerActivity.class));
        });
        grid.setOnItemLongClickListener((parent, view, position, id) -> {   // a long OK ticks or unticks
            ticked[position + 1] = !ticked[position + 1];
            adapter.notifyDataSetChanged();
            updateBar();
            return true;
        });

        bar = findViewById(R.id.bar);
        playButton = barButton(v -> playTicked());
        allButton = barButton(v -> { for (int n = 1; n <= reciter.surahs; n++) ticked[n] = true; changed(); });
        allButton.setText(R.string.tick_all);
        clearButton = barButton(v -> { for (int n = 1; n <= Playlists.TOTAL; n++) ticked[n] = false; changed(); });
        clearButton.setText(R.string.tick_clear);
        saveButton = barButton(v -> save());
        updateBar();

        Prefs prefs = new Prefs(this);
        if (prefs.source() == Prefs.SOURCE_SURAH && reciter.id.equals(prefs.reciter())) {
            int last = Math.max(0, prefs.surah() - 1);
            grid.post(() -> grid.setSelection(last));
        }
        grid.post(() -> grid.requestFocus());
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && grid != null && getCurrentFocus() == null) grid.post(() -> grid.requestFocus());
    }

    // ---- the ★ button ----

    private void paintFav() {
        boolean fav = ReciterStore.favourite(this, reciter.id);
        favButton.setText(fav ? R.string.fav_reciter_on : R.string.fav_reciter_off);
        chipStyle(favButton, fav, favButton.hasFocus());
    }

    /** the radios' chip: glass, or solid gold when on; a gold ring while the focus is on it */
    private void chipStyle(TextView t, boolean on, boolean focused) {
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(dp(22));
        d.setColor(on ? 0xE6DCB86F : 0x14FFFFFF);
        d.setStroke(dp(2), focused ? 0xFFDCB86F : on ? 0xE6DCB86F : 0x33FFFFFF);
        t.setBackground(d);
        t.setTextColor(getResources().getColor(on ? R.color.bg : R.color.sand));
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    // ---- the list ----

    private TextView barButton(View.OnClickListener click) {
        final TextView t = new TextView(this);
        t.setTextSize(17);
        t.setTypeface(Ui.font(this, "Thmanyah-Medium.otf"));
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(22), 0, dp(22), 0);
        t.setFocusable(true);
        t.setOnClickListener(click);
        t.setOnFocusChangeListener((v, has) -> chipStyle(t, false, has));
        chipStyle(t, false, false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44));
        lp.setMarginEnd(dp(10));
        bar.addView(t, lp);
        return t;
    }

    private int tickedCount() {
        int n = 0;
        for (int i = 1; i <= Playlists.TOTAL; i++) if (ticked[i]) n++;
        return n;
    }

    private int[] queue() {
        List<Integer> picked = new ArrayList<>();
        for (int i = 1; i <= reciter.surahs; i++) if (ticked[i]) picked.add(i);
        return SurahQueue.normalize(picked, reciter.surahs);
    }

    private void changed() {
        adapter.notifyDataSetChanged();
        updateBar();
    }

    /** The bar shows while something is ticked; its words follow the count. */
    private void updateBar() {
        int n = tickedCount();
        boolean had = bar.hasFocus();
        playButton.setText(Ui.digits(getString(R.string.play_ticked, String.valueOf(n))));
        saveButton.setText(listName != null ? R.string.save_list_changes : R.string.save_list);
        bar.setVisibility(n > 0 ? View.VISIBLE : View.GONE);
        hint.setText(n > 0 ? R.string.hint_surah_ticked : R.string.hint_surah);
        if (n == 0 && had) grid.requestFocus();
    }

    private void showListName() {
        choose.setText(listName != null ? Ui.digits(listName) : getString(R.string.choose_surah));
    }

    private void playTicked() {
        int[] q = queue();
        if (q.length == 0) return;
        MusicService.playQueue(this, reciter.id, q, 0);
        startActivity(new Intent(this, PlayerActivity.class));
    }

    private void save() {
        int[] q = queue();
        if (q.length == 0) return;
        if (listName != null && ReciterStore.updateList(this, reciter.id, listName, q)) {
            Toast.makeText(this, getString(R.string.list_updated, Ui.digits(listName)), Toast.LENGTH_SHORT).show();
            return;
        }
        int before = ReciterStore.lists(this).size();
        Playlists.Item made = ReciterStore.addList(this, reciter.id, q);
        if (made == null) return;
        boolean isNew = ReciterStore.lists(this).size() > before;
        Toast.makeText(this, getString(isNew ? R.string.list_saved : R.string.list_exists, Ui.digits(made.name)), Toast.LENGTH_SHORT).show();
        listName = made.name;   // saving again updates this list instead of making another
        showListName();
        updateBar();
    }

    // ---- keys ----

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        if (AdhanStop.onKey(this, e)) return true;   // any key ends a ringing adhan
        if (e.getAction() == KeyEvent.ACTION_DOWN && grid != null) {
            int code = e.getKeyCode();
            boolean inGrid = grid.hasFocus(), inBar = bar.hasFocus(), onFav = favButton.hasFocus();
            if (code == KeyEvent.KEYCODE_DPAD_UP) {
                if (inGrid && e.getRepeatCount() == 0 && grid.getSelectedItemPosition() < COLUMNS) { favButton.requestFocus(); return true; }
                if (inBar) { grid.requestFocus(); return true; }
                if (onFav) return true;
            } else if (code == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (onFav) { grid.requestFocus(); return true; }
                if (inBar) return true;
                if (inGrid && bar.getVisibility() == View.VISIBLE
                        && grid.getSelectedItemPosition() / COLUMNS >= (grid.getCount() - 1) / COLUMNS) {
                    playButton.requestFocus();
                    return true;
                }
            } else if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (onFav) return true;
                // the grid reads from the right, so the cells are walked by hand: right is right
                if (inGrid && e.getRepeatCount() == 0 && moveCell(code == KeyEvent.KEYCODE_DPAD_RIGHT)) return true;
            }
        }
        return super.dispatchKeyEvent(e);
    }

    /** Moves the focus to the cell on the right (true) or on the left (false) of the focused one. */
    private boolean moveCell(boolean right) {
        View focused = getCurrentFocus();
        if (focused == null) return false;
        int child = grid.indexOfChild(focused);
        if (child < 0) return false;
        int position = grid.getFirstVisiblePosition() + child;
        // the first cell of the grid sits on the right, so the cell before is the one on the right
        int next = right ? position - 1 : position + 1;
        if (next < 0 || next >= grid.getCount()) return false;
        View cell = grid.getChildAt(next - grid.getFirstVisiblePosition());
        if (cell == null) {
            grid.setSelection(next);
            return true;
        }
        cell.requestFocus();
        return true;
    }

    private final class SurahAdapter extends BaseAdapter {
        @Override public int getCount() { return reciter.surahs; }
        @Override public Object getItem(int position) { return position + 1; }
        @Override public long getItemId(int position) { return position + 1; }

        @Override public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView != null ? convertView
                    : LayoutInflater.from(parent.getContext()).inflate(R.layout.item_surah, parent, false);
            TextView number = view.findViewById(R.id.number);
            TextView name = view.findViewById(R.id.name);
            number.setText(Ui.arabic(position + 1));
            name.setText(cat.surahName(position + 1));
            number.setTypeface(Ui.font(SurahActivity.this, "Thmanyah-Regular.otf"));
            name.setTypeface(Ui.font(SurahActivity.this, "Thmanyah-Medium.otf"));
            boolean on = ticked[position + 1];
            ((ImageView) view.findViewById(R.id.box)).setImageResource(on ? R.drawable.ic_box_on : R.drawable.ic_box);
            view.setBackgroundResource(on ? R.drawable.card_ticked : R.drawable.card_bg);
            return view;
        }
    }
}
