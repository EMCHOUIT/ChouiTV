package ma.chouitv.home.tilawah;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;

/**
 * The surahs of a list of one reciter, played one after the other: always in surah order, no duplicate, only 1..total. No Android here,
 * so tests/tilawah_lists_test.py can run it. When the last surah of the queue ends, the next one is the first again (the list loops).
 */
final class SurahQueue {
    private SurahQueue() {}

    /** The surahs ticked as a queue: sorted, unique, inside 1..total. */
    static int[] normalize(Collection<Integer> picked, int total) {
        TreeSet<Integer> set = new TreeSet<>();
        if (picked != null) for (Integer n : picked) if (n != null && n >= 1 && n <= total) set.add(n);
        int[] out = new int[set.size()];
        int i = 0;
        for (int n : set) out[i++] = n;
        return out;
    }

    /** «3,5,9» to a queue; anything that is not a surah is left out. */
    static int[] parse(String csv, int total) {
        List<Integer> raw = new ArrayList<>();
        if (csv != null) {
            for (String part : csv.split(",")) {
                try { raw.add(Integer.parseInt(part.trim())); } catch (NumberFormatException ignored) { }
            }
        }
        return normalize(raw, total);
    }

    static String csv(int[] queue) {
        StringBuilder sb = new StringBuilder();
        for (int n : queue) sb.append(sb.length() == 0 ? "" : ",").append(n);
        return sb.toString();
    }

    /** The place of a surah in the queue, or -1 when it is not in it (or the queue is empty). */
    static int indexOf(int[] queue, int surah) {
        if (queue == null) return -1;
        for (int i = 0; i < queue.length; i++) if (queue[i] == surah) return i;
        return -1;
    }

    /**
     * The place after (delta +1) or before (delta -1) `index`, going round at both ends; the first place when `index` is not a place of
     * the queue; -1 for an empty queue. A queue of one surah stays on it.
     */
    static int step(int[] queue, int index, int delta) {
        if (queue == null || queue.length == 0) return -1;
        if (index < 0 || index >= queue.length) return 0;
        int n = queue.length;
        return ((index + delta) % n + n) % n;
    }
}
