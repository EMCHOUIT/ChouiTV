package ma.chouitv.home.tilawah;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import ma.chouitv.home.R;

/**
 * The «تلاوة» screen: two sections that never share a page, picked from the tab bar. «القرآن الكريم» (QuranPane: the reciters, the live
 * Quran stations, and the favourites and saved lists, as three chips) and «الإذاعات» (RadioPane: the countries as chips, the stations
 * as cards). Both are built the same way: chips under the tabs, a grid of four cards under the chips. The adhan is not here any more;
 * its screens (AdhanActivity, PrayerOptionsActivity) are reached from the settings.
 *
 * Keys: ◀ ▶ on the tab bar change the section, ▼ goes to the chips, ◀ ▶ move between the chips (or the cards when the cards have the
 * focus), ▼ again goes into the cards, ▲ from the first row of cards goes back to the chips and ▲ from the chips to the tab bar.
 */
public class MainActivity extends Activity {

    private static final int TAB_QURAN = 0, TAB_RADIO = 1;

    private ViewGroup tabs;
    private TextView[] tabViews;
    private TextView hint;
    private int tab = TAB_QURAN;
    private QuranPane quranPane;   // the «القرآن الكريم» tab
    private RadioPane radioPane;   // the «الإذاعات» tab: countries beside stations, its own two lists

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        ma.chouitv.home.Digits.init(this);   // the screensaver's «الأرقام» setting
        setContentView(R.layout.activity_main);

        Ui.text((TextView) findViewById(R.id.title), "ThmanyahDisplay-Bold.otf", 44, R.color.sand);
        Ui.text((TextView) findViewById(R.id.tagline), "Thmanyah-Regular.otf", 17, R.color.dim);
        hint = findViewById(R.id.hint);
        Ui.text(hint, "Thmanyah-Regular.otf", 14, R.color.dim);

        tabs = findViewById(R.id.tabs);
        tabViews = new TextView[]{findViewById(R.id.tab_reciters), findViewById(R.id.tab_stations)};

        quranPane = new QuranPane(this, new QuranPane.Host() {
            @Override public void openPlayer() { MainActivity.this.openPlayer(); }
            @Override public void hint(int chip) {
                if (tab == TAB_QURAN) {
                    hint.setText(chip == Playlists.CHIP_FAV ? R.string.hint_quran_fav
                            : chip == Playlists.CHIP_LIVE ? R.string.hint_quran_live : R.string.hint_quran_reciters);
                }
            }
        });
        radioPane = new RadioPane(this, new RadioPane.Host() {
            @Override public void openPlayer() { MainActivity.this.openPlayer(); }
            @Override public void openSettings() { startActivity(new Intent(MainActivity.this, RadioCountriesActivity.class)); }
            @Override public void focusTabs() { tabViews[tab].requestFocus(); }
        });
        ViewGroup column = (ViewGroup) hint.getParent();
        int at = column.indexOfChild(hint);
        android.widget.LinearLayout.LayoutParams paneParams = new android.widget.LinearLayout.LayoutParams(-1, 0, 1f);
        paneParams.topMargin = (int) (16 * getResources().getDisplayMetrics().density);
        column.addView(quranPane, at, paneParams);
        column.addView(radioPane, at + 1, new android.widget.LinearLayout.LayoutParams(paneParams));

        for (int i = 0; i < tabViews.length; i++) {
            final int which = i;
            tabViews[i].setTypeface(Ui.font(this, "Thmanyah-Medium.otf"));
            tabViews[i].setTextSize(19);
            tabViews[i].setOnClickListener(v -> {
                selectTab(which);
                focusChips();
            });
        }
        selectTab(TAB_QURAN);

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }

        // the adhan is not shown here any more, but it still has to ring: today's times from Aladhan when the network answers, the recordings
        // kept for offline, and the alarm put back for the new times
        PrayerTimes.refresh(this, () -> { });
        Adhan.prefetchAll(this);
        AdhanScheduler.schedule(this);
    }

    private boolean radioTab() { return tab == TAB_RADIO; }

    private boolean paneHasFocus() { return radioTab() ? radioPane.hasListFocus() : quranPane.hasListFocus(); }
    private boolean paneAtTop() { return radioTab() ? radioPane.atTop() : quranPane.atTop(); }
    private boolean cardsFocused() { return radioTab() ? radioPane.stationsFocused() : quranPane.cardsFocused(); }
    private void focusChips() { if (radioTab()) radioPane.focusCountries(); else quranPane.focusChips(); }

    @Override protected void onResume() {
        super.onResume();
        // back from the player, a surah screen or the settings: the lists are read again, the focus stays where it was
        if (radioTab()) {
            boolean had = radioPane.hasListFocus();
            radioPane.refresh();
            if (!had) radioPane.focusFirst();
        } else {
            boolean had = quranPane.hasListFocus();
            quranPane.refresh();
            if (!had) quranPane.focusFirst();
        }
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && !radioTab() && !paneHasFocus() && !tabs.hasFocus()) quranPane.focusFirst();   // (the radios keep their own focus)
    }

    /** The tab changes which pane is on screen; the pane keeps its chip and its place. */
    private void selectTab(int which) {
        tab = Math.max(TAB_QURAN, Math.min(TAB_RADIO, which));
        quranPane.setVisibility(radioTab() ? View.GONE : View.VISIBLE);
        radioPane.setVisibility(radioTab() ? View.VISIBLE : View.GONE);
        for (int i = 0; i < tabViews.length; i++) tabViews[i].setSelected(i == tab);
        if (radioTab()) {
            hint.setText(R.string.hint_radio);
            radioPane.refresh();
            radioPane.selectPlayingCountry();
        } else {
            quranPane.refresh();   // (sets the hint of its chip)
        }
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (AdhanStop.onKey(this, event)) return true;   // any key ends a ringing adhan
        // ▲ on the chips goes to the tab bar (the cards go up to the chips by themselves)
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0
                && event.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP
                && paneHasFocus() && paneAtTop()) {
            tabViews[tab].requestFocus();
            return true;
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0
                && event.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP && !radioTab() && quranPane.upFromCards()) {
            return true;
        }
        // ▼ from the chips lands on the card that was selected (the first after a chip change), not on the one nearest the chip
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0
                && event.getKeyCode() == KeyEvent.KEYCODE_DPAD_DOWN && !radioTab() && quranPane.atTop()) {
            quranPane.focusFirst();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override public void onBackPressed() {
        if (cardsFocused()) {   // cards → chips → out
            focusChips();
            return;
        }
        super.onBackPressed();
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        // in a pane ◀ ▶ belong to the chips and the cards (the tab bar is reached with ▲); at the edge nothing moves
        if (paneHasFocus() && (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)) {
            return super.onKeyDown(keyCode, event);
        }
        // on the tab bar ◀ ▶ change the section: the focus stays on the tab bar, so the person sees the section change and enters it
        // when they choose (▼), instead of landing in the new one by surprise
        if (tabs.hasFocus() && (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)) {
            // RTL: the first tab sits on the right, so right is «previous»
            int next = Math.max(TAB_QURAN, Math.min(TAB_RADIO, tab + (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ? -1 : 1)));
            if (next == tab) return true;
            selectTab(next);
            tabViews[next].requestFocus();
            return true;
        }
        if (tabs.hasFocus()) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                    || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                focusChips();   // the chips row first
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void openPlayer() {
        startActivity(new Intent(this, PlayerActivity.class));
    }
}
