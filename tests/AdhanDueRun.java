package ma.chouitv.home.tilawah;

/** For tests/adhan_due_test.py: AdhanDue.stale(due, now, exactAllowed) for each "due now exact" line on stdin-free args:
 *  args are triples (due, now, exact); one answer per triple, "stale" or "play". */
public final class AdhanDueRun {
    public static void main(String[] args) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i + 2 < args.length; i += 3) {
            boolean stale = AdhanDue.stale(Long.parseLong(args[i]), Long.parseLong(args[i + 1]), Boolean.parseBoolean(args[i + 2]));
            out.append(stale ? "stale" : "play").append('\n');
        }
        System.out.print(out);
    }
}
