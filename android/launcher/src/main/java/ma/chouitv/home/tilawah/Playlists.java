package ma.chouitv.home.tilawah;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The saved lists of surahs, the favourite reciters' order and the chip the «القرآن الكريم» tab opens on. No Android here, so
 * tests/tilawah_lists_test.py can run it. A list belongs to one reciter (the recitation files are per reciter), has a name («قائمة ١»,
 * «قائمة ٢»… numbered per reciter) and its surahs in surah order. The text form is one list per line:  reciter|name|3,5,9
 */
final class Playlists {
    static final int CHIP_FAV = 0, CHIP_RECITERS = 1, CHIP_LIVE = 2;
    static final int TOTAL = 114, MAX_LISTS = 100, MAX_NAME = 40, MAX_ID = 40;
    private static final String BASE_NAME = "قائمة ";
    private static final Pattern NUMBERED = Pattern.compile("^" + BASE_NAME + "([0-9٠-٩]+)$");
    private static final Pattern ID = Pattern.compile("^[a-z0-9_\\-]{1," + MAX_ID + "}$");

    private Playlists() {}

    static final class Item {
        final String reciter, name;
        final int[] surahs;
        Item(String reciter, String name, int[] surahs) { this.reciter = reciter; this.name = name; this.surahs = surahs; }
    }

    // ---- the text form ----

    /** Reads the lists; a line that is not a list (no reciter, no name, no surah) is left out, the others stay. Surahs come back in order. */
    static List<Item> parse(String text) {
        List<Item> out = new ArrayList<>();
        if (text == null || text.isEmpty()) return out;
        for (String line : text.split("\n")) {
            String[] f = line.split("\\|", -1);
            if (f.length != 3) continue;
            String reciter = f[0].trim(), name = f[1].trim();
            int[] surahs = SurahQueue.parse(f[2], TOTAL);
            if (reciter.isEmpty() || name.isEmpty() || surahs.length == 0 || out.size() >= MAX_LISTS) continue;
            out.add(new Item(reciter, name, surahs));
        }
        return out;
    }

    static String serialize(List<Item> items) {
        StringBuilder sb = new StringBuilder();
        for (Item it : items) {
            sb.append(sb.length() == 0 ? "" : "\n").append(it.reciter).append('|').append(it.name).append('|').append(SurahQueue.csv(it.surahs));
        }
        return sb.toString();
    }

    // ---- naming and editing ----

    /** «قائمة ١», then the number after the biggest one this reciter already has (a name the person gave does not count). */
    static String nextName(List<Item> items, String reciter) {
        int max = 0;
        for (Item it : items) {
            if (!it.reciter.equals(reciter)) continue;
            Matcher m = NUMBERED.matcher(it.name);
            if (m.matches()) {
                try { max = Math.max(max, Integer.parseInt(latin(m.group(1)))); } catch (NumberFormatException ignored) { }
            }
        }
        return BASE_NAME + indic(String.valueOf(max + 1));
    }

    /** Adds a list of these surahs to the reciter and returns it; the list that already holds exactly these surahs is returned as it is; null for no surah or no room. */
    static Item add(List<Item> items, String reciter, int[] surahs) {
        if (surahs == null || surahs.length == 0 || reciter == null || reciter.isEmpty()) return null;
        for (Item it : items) if (it.reciter.equals(reciter) && java.util.Arrays.equals(it.surahs, surahs)) return it;
        if (items.size() >= MAX_LISTS) return null;
        Item made = new Item(reciter, nextName(items, reciter), surahs);
        items.add(made);
        return made;
    }

    /** Replaces the surahs of a list, keeping its name and place; false when there is no such list or no surah. */
    static boolean update(List<Item> items, String reciter, String name, int[] surahs) {
        if (surahs == null || surahs.length == 0) return false;
        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            if (it.reciter.equals(reciter) && it.name.equals(name)) { items.set(i, new Item(reciter, name, surahs)); return true; }
        }
        return false;
    }

    static boolean remove(List<Item> items, String reciter, String name) {
        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            if (it.reciter.equals(reciter) && it.name.equals(name)) { items.remove(i); return true; }
        }
        return false;
    }

    /** The lists of one reciter, in the order they were made. */
    static List<Item> forReciter(List<Item> items, String reciter) {
        List<Item> out = new ArrayList<>();
        for (Item it : items) if (it.reciter.equals(reciter)) out.add(it);
        return out;
    }

    // ---- words ----

    /** «سورة واحدة», «سورتان», «٣ سور» to «١٠ سور», «١١ سورة»…: the number is in latin digits (Ui.digits turns them into the screen's). */
    static String surahCount(int n) {
        if (n == 1) return "سورة واحدة";
        if (n == 2) return "سورتان";
        return n + (n >= 3 && n <= 10 ? " سور" : " سورة");
    }

    // ---- the tab ----

    /** The indexes (in the catalogue's order) of the reciters that are favourites; an id the catalogue does not know is ignored. */
    static List<Integer> favouriteOrder(List<String> reciterIds, Collection<String> favourites) {
        List<Integer> out = new ArrayList<>();
        if (favourites == null) return out;
        for (int i = 0; i < reciterIds.size(); i++) if (favourites.contains(reciterIds.get(i))) out.add(i);
        return out;
    }

    /** The chip the «القرآن الكريم» tab opens on: المفضلة when it holds something (a reciter or a list), else القرّاء. */
    static int defaultChip(boolean anyFavouriteReciter, boolean anyList) {
        return anyFavouriteReciter || anyList ? CHIP_FAV : CHIP_RECITERS;
    }

    // ---- the settings file ----

    /**
     * What the settings file may say, checked whole before anything is written: `favourites` (reciter ids, commas) and `lists` (the text
     * form). null when both are fine (blank is fine: none), else why not.
     */
    static String check(String favourites, String lists) {
        if (favourites != null && !favourites.isEmpty()) {
            String[] ids = favourites.split(",");
            if (ids.length > 200) return "reciters fav: too many";
            for (String id : ids) if (!ID.matcher(id).matches()) return "reciters fav: " + id + " is not a reciter id";
        }
        if (lists != null && !lists.isEmpty()) {
            String[] lines = lists.split("\n");
            if (lines.length > MAX_LISTS) return "reciters lists: too many";
            for (String line : lines) {
                String[] f = line.split("\\|", -1);
                if (f.length != 3) return "reciters lists: not a list";
                if (!ID.matcher(f[0]).matches()) return "reciters lists: reciter";
                if (f[1].trim().isEmpty() || f[1].length() > MAX_NAME) return "reciters lists: name";
                if (!strictSurahs(f[2])) return "reciters lists: surahs";
            }
        }
        return null;
    }

    /** 1..114, in order, no duplicate, nothing else. */
    private static boolean strictSurahs(String csv) {
        if (csv.isEmpty()) return false;
        int last = 0;
        for (String part : csv.split(",", -1)) {
            int n;
            try { n = Integer.parseInt(part); } catch (NumberFormatException e) { return false; }
            if (n <= last || n > TOTAL) return false;
            last = n;
        }
        return true;
    }

    /** the favourites of the file as a set of ids (blank = none) */
    static void splitFavourites(String text, Set<String> into) {
        if (text == null || text.isEmpty()) return;
        for (String id : text.split(",")) if (!id.trim().isEmpty()) into.add(id.trim());
    }

    // ---- digits ----

    private static String indic(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) sb.append(c >= '0' && c <= '9' ? (char) ('٠' + (c - '0')) : c);
        return sb.toString();
    }

    private static String latin(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) sb.append(c >= '٠' && c <= '٩' ? (char) ('0' + (c - '٠')) : c);
        return sb.toString();
    }
}
