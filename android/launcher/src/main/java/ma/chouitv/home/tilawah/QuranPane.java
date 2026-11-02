package ma.chouitv.home.tilawah;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import ma.chouitv.home.R;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The «القرآن الكريم» tab: three chips under the tabs (★ المفضلة · القرّاء · مباشر) and a grid of four cards under them, built the way the
 * radios' tab is (RadioPane): the chip the focus is on shows its cards, ▲ from the chips goes to the tabs, ▼ goes into the cards.
 * المفضلة = the favourite reciters, then the saved lists of surahs; القرّاء = every reciter (and «متابعة»); مباشر = the live Quran stations.
 * OK opens a reciter (its surahs) or plays a list or a station; a long OK makes a reciter a favourite, or opens a list's menu (edit, delete).
 */
final class QuranPane extends LinearLayout {

    interface Host {
        void openPlayer();
        void hint(int chip);
    }

    private static final int R_RESUME = 0, R_RECITER = 1, R_LIST = 2, R_STATION = 3;

    private static final class Row {
        final int kind, index;
        final String title, detail;
        final boolean fav, live;
        final Playlists.Item list;
        Row(int kind, int index, String title, String detail, boolean fav, boolean live, Playlists.Item list) {
            this.kind = kind; this.index = index; this.title = title; this.detail = detail;
            this.fav = fav; this.live = live; this.list = list;
        }
    }

    private final Host host;
    private final Catalog cat;
    private final LinearLayout chips;
    private GridView grid;
    private final TextView note;   // «لا مفضلات بعد…»: shown instead of the cards when المفضلة is empty
    private final List<Row> rows = new ArrayList<>();
    private final List<TextView> chipViews = new ArrayList<>();
    private final BaseAdapter adapter;
    private int active = Playlists.CHIP_RECITERS;   // the chip whose cards are shown
    private boolean opened;

    QuranPane(Context context, Host host) {
        super(context);
        this.host = host;
        this.cat = Catalog.get(context);
        setOrientation(VERTICAL);
        setLayoutDirection(View.LAYOUT_DIRECTION_RTL);   // the chips and the cards start at the right, as the tabs

        chips = new LinearLayout(context);
        chips.setOrientation(HORIZONTAL);
        addView(chips, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        String[] names = {"★ " + context.getString(R.string.quran_fav), context.getString(R.string.quran_lists), context.getString(R.string.quran_reciters), context.getString(R.string.quran_live)};
        for (int i = 0; i < names.length; i++) {
            final int at = i;
            TextView t = new TextView(context);
            t.setText(names[i]);
            t.setTextSize(17);
            t.setTypeface(Ui.font(context, "Thmanyah-Medium.otf"));
            t.setGravity(Gravity.CENTER);
            t.setPadding(dp(20), 0, dp(20), 0);
            t.setFocusable(true);
            t.setOnFocusChangeListener((v, has) -> {
                if (has && at != active) {   // moving over a chip shows its cards
                    active = at;
                    buildRows();
                    grid.setSelection(0);
                    host.hint(active);
                }
                paintChips();
            });
            t.setOnClickListener(v -> { if (hasEnabled()) grid.requestFocus(); });
            LayoutParams lp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40));
            lp.setMarginEnd(dp(8));
            chips.addView(t, lp);
            chipViews.add(t);
        }

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

        note = new TextView(context);
        note.setTextSize(20);
        note.setLineSpacing(0, 1.2f);
        note.setTypeface(Ui.font(context, "Thmanyah-Regular.otf"));
        note.setTextColor(getResources().getColor(R.color.dim));
        note.setGravity(Gravity.CENTER);
        note.setPadding(dp(60), dp(40), dp(60), 0);
                note.setVisibility(View.GONE);
        addView(note, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        grid.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int position, long id) { adapter.notifyDataSetChanged(); }
            @Override public void onNothingSelected(AdapterView<?> p) { }
        });
        grid.setOnFocusChangeListener((v, has) -> adapter.notifyDataSetChanged());
        grid.setOnItemClickListener((p, v, position, id) -> open(rows.get(position)));
        grid.setOnItemLongClickListener((p, v, position, id) -> {
            Row row = rows.get(position);
            if (row.kind == R_RECITER) {
                ReciterStore.toggleFavourite(getContext(), cat.reciters.get(row.index).id);
                refreshKeeping(position);
                return true;
            }
            if (row.kind == R_LIST) {
                listMenu(row.list);
                return true;
            }
            return false;
        });
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    private void open(Row row) {
        Context c = getContext();
        if (row.kind == R_RESUME) {
            MusicService.resumeLast(c);
            host.openPlayer();
        } else if (row.kind == R_RECITER) {
            c.startActivity(new Intent(c, SurahActivity.class).putExtra("reciter", row.index));
        } else if (row.kind == R_LIST) {   // plays the list from its first surah; «تعديل القائمة» (long OK) opens it ticked
            MusicService.playQueue(c, row.list.reciter, row.list.surahs, 0);
            host.openPlayer();
        } else if (row.kind == R_STATION) {
            MusicService.playRadio(c, row.index);
            host.openPlayer();
        }
    }

    // ---- what is shown ----

    /** Reads everything again (the favourites, the lists, what plays); the first time it also picks the chip the tab opens on. */
    void refresh() {
        if (!opened) {
            opened = true;
            Context c = getContext();
            active = Playlists.defaultChip(!ReciterStore.favourites(c).isEmpty(), !ReciterStore.lists(c).isEmpty());
        }
        paintChips();
        buildRows();
        host.hint(active);
    }

    /** refresh() that keeps the selection near where it was (a card may have gone). */
    private void refreshKeeping(int position) {
        buildRows();
        if (rows.size() > 0) grid.setSelection(Math.min(position, rows.size() - 1));
    }

    private void paintChips() {
        for (int i = 0; i < chipViews.size(); i++) {
            TextView t = chipViews.get(i);
            boolean isActive = i == active;
            GradientDrawable d = new GradientDrawable();
            d.setCornerRadius(dp(20));
            d.setColor(isActive ? 0xE6DCB86F : 0x14FFFFFF);
            d.setStroke(dp(2), t.hasFocus() ? 0xFFDCB86F : isActive ? 0xE6DCB86F : 0x00000000);
            t.setBackground(d);
            t.setTextColor(getResources().getColor(isActive ? R.color.bg : R.color.sand));
        }
    }

    private void buildRows() {
        Context c = getContext();
        Prefs prefs = new Prefs(c);
        rows.clear();
        if (active == Playlists.CHIP_FAV) {
            List<String> ids = new ArrayList<>();
            for (Catalog.Reciter r : cat.reciters) ids.add(r.id);
            for (int i : Playlists.favouriteOrder(ids, ReciterStore.favourites(c))) addReciter(i);
        } else if (active == Playlists.CHIP_LISTS) {
            for (Playlists.Item it : ReciterStore.lists(c)) {
                int r = cat.reciterIndex(it.reciter);
                if (r < 0) continue;   // a list of a reciter this version does not have
                rows.add(new Row(R_LIST, r, it.name, Playlists.surahCount(it.surahs.length) + "  ·  " + cat.reciters.get(r).name, false, false, it));
            }
        } else if (active == Playlists.CHIP_RECITERS) {
            if (prefs.source() == Prefs.SOURCE_SURAH) {
                int index = cat.reciterIndex(prefs.reciter());
                if (index >= 0) {
                    rows.add(new Row(R_RESUME, index, c.getString(R.string.continue_listening),
                            c.getString(R.string.continue_detail, cat.surahName(prefs.surah()), cat.reciters.get(index).name), false, false, null));
                }
            }
            for (int i = 0; i < cat.reciters.size(); i++) addReciter(i);
        } else {
            String group = "quran";
            int saved = prefs.stationIn(cat);
            if (prefs.source() == Prefs.SOURCE_RADIO && saved >= 0 && saved < cat.stations.size() && group.equals(cat.stations.get(saved).group)) {
                rows.add(new Row(R_RESUME, -1, cat.stations.get(saved).name, c.getString(R.string.continue_listening), false, false, null));
            }
            boolean radioOn = MusicService.currentSource() == Prefs.SOURCE_RADIO && MusicService.isPlaying();
            for (int i : alphabetical(group)) {
                boolean now = radioOn && MusicService.currentStation() == i;
                rows.add(new Row(R_STATION, i, cat.stations.get(i).name, now ? "●  " + c.getString(R.string.live) : "", false, now, null));
            }
        }
        boolean empty = rows.isEmpty();   // only the two favourites chips can be empty
        note.setText(active == Playlists.CHIP_LISTS ? R.string.quran_no_lists : R.string.quran_no_favourites);
        boolean cardsHadFocus = grid.hasFocus();
        grid.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (empty && cardsHadFocus) focusChips();   // the last favourite was just taken out: the focus does not stay on cards that are gone
        note.setVisibility(empty ? View.VISIBLE : View.GONE);
        adapter.notifyDataSetChanged();
    }

    private void addReciter(int i) {
        Catalog.Reciter r = cat.reciters.get(i);
        rows.add(new Row(R_RECITER, i, r.name, getContext().getString(R.string.surah_count, Ui.arabic(r.surahs)),
                ReciterStore.favourite(getContext(), r.id), false, null));
    }

    private List<Integer> alphabetical(String group) {
        final Collator collator = Collator.getInstance(new Locale("ar"));
        collator.setStrength(Collator.PRIMARY);
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < cat.stations.size(); i++) if (group.equals(cat.stations.get(i).group)) out.add(i);
        Collections.sort(out, (a, b) -> collator.compare(sortKey(cat.stations.get(a).name), sortKey(cat.stations.get(b).name)));
        return out;
    }

    /** The name without «راديو», «إذاعة», «الشيخ» and «ال», so the order is the one an Arabic reader expects. */
    private static String sortKey(String name) {
        String s = name.trim();
        for (String prefix : new String[] {"إذاعة ", "راديو ", "الشيخ ", "القارئ "}) {
            if (s.startsWith(prefix)) s = s.substring(prefix.length());
        }
        if (s.startsWith("ال") && s.length() > 3) s = s.substring(2);
        return s.replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا');
    }

    // ---- a list's menu ----

    private void listMenu(final Playlists.Item it) {
        final Context c = getContext();
        int r = cat.reciterIndex(it.reciter);
        if (r < 0) return;
        String sub = Ui.digits(Playlists.surahCount(it.surahs.length)) + "  ·  " + cat.reciters.get(r).name;
        final int reciterIndex = r;
        choices(Ui.digits(it.name), sub,
                new String[] {c.getString(R.string.list_menu_edit), c.getString(R.string.list_menu_rename), c.getString(R.string.list_menu_delete), c.getString(R.string.list_menu_close)},
                new int[] {R.color.sand, R.color.sand, R.color.red, R.color.sand},
                new Runnable[] {
                        () -> c.startActivity(new Intent(c, SurahActivity.class).putExtra("reciter", reciterIndex).putExtra("list", it.name)),
                        () -> NameDialog.ask(c, c.getString(R.string.name_title), it.name, name -> {
                            ReciterStore.renameList(c, it.reciter, it.name, name);
                            refreshKeeping(Math.max(0, grid.getSelectedItemPosition()));
                        }),
                        () -> confirmDelete(it),
                        null});
    }

    /** The question comes with «إلغاء» first, so a second press of OK by habit keeps the list. */
    private void confirmDelete(final Playlists.Item it) {
        final Context c = getContext();
        choices(c.getString(R.string.delete_list_title, Ui.digits(it.name)), c.getString(R.string.delete_list_sub),
                new String[] {c.getString(R.string.delete_list_no), c.getString(R.string.delete_list_yes)},
                new int[] {R.color.sand, R.color.red},
                new Runnable[] {
                        null,
                        () -> {
                            ReciterStore.removeList(c, it.reciter, it.name);
                            Toast.makeText(c, R.string.list_deleted, Toast.LENGTH_SHORT).show();
                            refreshKeeping(Math.max(0, grid.getSelectedItemPosition()));
                        }});
    }

    /** A small dialog of choices in the look of the sleep timer's: the first one has the focus; a null action only closes it. */
    private void choices(String title, String sub, String[] labels, int[] colors, final Runnable[] actions) {
        final Dialog dialog = new Dialog(getContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_sleep);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.62f);
            window.setLayout(dp(620), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        TextView t = dialog.findViewById(R.id.sleep_title), s = dialog.findViewById(R.id.sleep_subtitle);
        t.setText(title);
        s.setText(sub);
        Ui.text(t, "ThmanyahDisplay-Bold.otf", 27, R.color.sand);
        Ui.text(s, "Thmanyah-Regular.otf", 14, R.color.dim);
        LinearLayout items = dialog.findViewById(R.id.sleep_items);
        for (int i = 0; i < labels.length; i++) {
            TextView row = (TextView) LayoutInflater.from(getContext()).inflate(R.layout.item_sleep, items, false);
            row.setText(labels[i]);
            Ui.text(row, "Thmanyah-Medium.otf", 20, colors[i]);
            final Runnable action = actions[i];
            row.setOnClickListener(v -> {
                dialog.dismiss();
                if (action != null) action.run();
            });
            items.addView(row);
        }
        dialog.setOnShowListener(d -> { View first = items.getChildAt(0); if (first != null) first.requestFocus(); });
        dialog.show();
    }

    // ---- focus, for the activity ----

    private boolean hasEnabled() {
        return !rows.isEmpty();
    }

    boolean hasListFocus() {
        if (grid.hasFocus()) return true;
        for (TextView t : chipViews) if (t.hasFocus()) return true;
        return false;
    }

    /** the cards have the focus, not the chips: «رجوع» then goes up to the chips */
    boolean cardsFocused() { return grid.hasFocus(); }

    void focusChips() {
        if (active < chipViews.size()) chipViews.get(active).requestFocus();
    }

    /** From the tabs: the first card, else the chips. */
    void focusFirst() {
        if (hasEnabled() && grid.getSelectedItemPosition() < 0) grid.setSelection(0);
        if (hasEnabled()) grid.requestFocus(); else focusChips();
    }

    /** ▲ from the first row of cards goes to the chip that is open (not to the one nearest above, which would change what is shown). */
    boolean upFromCards() {
        if (!grid.hasFocus() || grid.getSelectedItemPosition() >= 4) return false;
        focusChips();
        return true;
    }

    /** True when ▲ would leave the pane: a chip has the focus. */
    boolean atTop() {
        for (TextView t : chipViews) if (t.hasFocus()) return true;
        return false;
    }

    // ---- the cards ----

    private final class Cards extends BaseAdapter {
        @Override public int getCount() { return rows.size(); }
        @Override public Object getItem(int p) { return rows.get(p); }
        @Override public long getItemId(int p) { return p; }
        @Override public boolean areAllItemsEnabled() { return false; }

        @Override public View getView(int position, View convert, ViewGroup parent) {
            Row r = rows.get(position);
            LinearLayout box;
            TextView name, sub;
            if (convert instanceof LinearLayout && convert.getTag() != null) {
                box = (LinearLayout) convert;
                name = (TextView) box.getChildAt(0);
                sub = (TextView) box.getChildAt(1);
            } else {
                box = new LinearLayout(getContext());
                box.setOrientation(VERTICAL);
                box.setPadding(dp(16), dp(10), dp(14), dp(10));
                box.setMinimumHeight(dp(90));
                name = new TextView(getContext());
                name.setTextSize(18);
                name.setTypeface(Ui.font(getContext(), "Thmanyah-Medium.otf"));
                name.setMaxLines(2);
                name.setEllipsize(android.text.TextUtils.TruncateAt.END);
                name.setCompoundDrawablePadding(dp(8));
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
            box.setBackgroundResource(sel ? R.drawable.row_selected : R.drawable.row_bg);
            // a favourite reciter has a small gold star before its name; a list has the list icon
            SpannableStringBuilder title = new SpannableStringBuilder(Ui.digits(r.title));
            if (r.fav) {
                title.insert(0, "★  ");
                title.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.gold)), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            name.setText(title);
            name.setCompoundDrawablesRelativeWithIntrinsicBounds(r.kind == R_LIST ? R.drawable.ic_list : 0, 0, 0, 0);
            name.setTextColor(getResources().getColor(r.live || r.kind == R_RESUME ? R.color.gold : R.color.sand));
            boolean none = r.detail == null || r.detail.isEmpty();
            sub.setVisibility(none ? View.GONE : View.VISIBLE);
            sub.setText(none ? "" : Ui.digits(r.detail));
            return box;
        }
    }
}
