package ma.chouitv.home.tilawah;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The person's own choices in «القرآن الكريم»: the favourite reciters (a set of ids, like the radios' favourites) and the saved lists of surahs
 * (Playlists holds the rules). Kept in their own file; the settings backup carries both (RadioBackup).
 */
final class ReciterStore {
    private ReciterStore() {}

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("reciters", Context.MODE_PRIVATE);
    }

    // ---- favourite reciters ----

    static Set<String> favourites(Context c) { return new HashSet<>(sp(c).getStringSet("fav", new HashSet<String>())); }

    static void setFavourites(Context c, Set<String> ids) {
        if (ids.isEmpty()) sp(c).edit().remove("fav").commit(); else sp(c).edit().putStringSet("fav", new HashSet<>(ids)).commit();
    }

    static boolean favourite(Context c, String id) { return sp(c).getStringSet("fav", new HashSet<String>()).contains(id); }

    /** Adds the reciter to the favourites or takes it out; true when it is one now. */
    static boolean toggleFavourite(Context c, String id) {
        Set<String> set = favourites(c);
        boolean now = set.add(id);
        if (!now) set.remove(id);
        sp(c).edit().putStringSet("fav", set).apply();
        return now;
    }

    // ---- saved lists ----

    static List<Playlists.Item> lists(Context c) { return Playlists.parse(sp(c).getString("lists", "")); }

    static void saveLists(Context c, List<Playlists.Item> items) {
        String text = Playlists.serialize(items);
        if (text.isEmpty()) sp(c).edit().remove("lists").apply(); else sp(c).edit().putString("lists", text).apply();
    }

    /** Saves the surahs as a new list of the reciter (named «قائمة ١», «قائمة ٢»…), or finds the one that already holds them; null when there is nothing to save. */
    static Playlists.Item addList(Context c, String reciterId, int[] surahs) {
        List<Playlists.Item> items = lists(c);
        Playlists.Item made = Playlists.add(items, reciterId, surahs);
        if (made != null) saveLists(c, items);
        return made;
    }

    static boolean updateList(Context c, String reciterId, String name, int[] surahs) {
        List<Playlists.Item> items = lists(c);
        boolean ok = Playlists.update(items, reciterId, name, surahs);
        if (ok) saveLists(c, items);
        return ok;
    }

    static boolean removeList(Context c, String reciterId, String name) {
        List<Playlists.Item> items = lists(c);
        boolean ok = Playlists.remove(items, reciterId, name);
        if (ok) saveLists(c, items);
        return ok;
    }

    static Playlists.Item list(Context c, String reciterId, String name) {
        for (Playlists.Item it : lists(c)) if (it.reciter.equals(reciterId) && it.name.equals(name)) return it;
        return null;
    }
}
