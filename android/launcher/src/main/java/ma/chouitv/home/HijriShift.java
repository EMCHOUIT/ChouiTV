package ma.chouitv.home;

/**
 * «تعديل التاريخ الهجري»: the Hijri date moves by whole days, from -2 to +2 (the moon is sighted on another day in another country). With no
 * Android in it (tests/HijriShiftRun.java runs it). The page reads the same number from the fragment (#hijri=); the prayer times never move.
 */
public final class HijriShift {
    private HijriShift() { }

    public static final int MAX = 2;
    /** the choices of the settings row: the value stored, and its label */
    public static final String[][] CHOICES = {
        {"-2", "يومان أقل"}, {"-1", "يوم أقل"}, {"0", "بلا تعديل"}, {"1", "يوم أكثر"}, {"2", "يومان أكثر"},
    };

    /** "-1", "+1", "2", " 0 " → the number held to -2..+2; anything else (empty, 1.5, "abc", null) is 0. */
    public static int parse(String s) {
        if (s == null) return 0;
        String t = s.trim();
        if (!t.matches("[+-]?\\d{1,3}")) return 0;
        int v = Integer.parseInt(t.startsWith("+") ? t.substring(1) : t);
        return Math.max(-MAX, Math.min(MAX, v));
    }

    /** "&hijri=-1": what the page and the home screen read. */
    public static String fragment(int days) { return "&hijri=" + Math.max(-MAX, Math.min(MAX, days)); }

    /** The shift in a settings fragment ("city=..&hijri=-1&.."); 0 when it is not there or not a number. */
    public static int fromFragment(String fragment) {
        if (fragment == null) return 0;
        for (String part : fragment.split("&")) if (part.startsWith("hijri=")) return parse(part.substring(6));
        return 0;
    }

    public static String label(int days) {
        String v = String.valueOf(Math.max(-MAX, Math.min(MAX, days)));
        for (String[] c : CHOICES) if (c[0].equals(v)) return c[1];
        return "بلا تعديل";
    }

    public static long millis(int days) { return days * 86_400_000L; }
}
