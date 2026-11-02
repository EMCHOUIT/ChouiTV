package ma.chouitv.home;

/** For tests/hijri_shift_test.py: «تعديل التاريخ الهجري» (HijriShift.java) on a plain JVM. */
public final class HijriShiftRun {
    static int failed;

    static void check(String name, Object got, Object want) {
        boolean ok = String.valueOf(want).equals(String.valueOf(got));
        System.out.println((ok ? "PASS " : "FAIL ") + name + (ok ? "" : "  -> got [" + got + "] want [" + want + "]"));
        if (!ok) failed++;
    }

    public static void main(String[] args) {
        check("parse -1", HijriShift.parse("-1"), -1);
        check("parse +1", HijriShift.parse("+1"), 1);
        check("parse 2", HijriShift.parse("2"), 2);
        check("parse spaces", HijriShift.parse(" 0 "), 0);
        check("held to +2", HijriShift.parse("9"), 2);
        check("held to -2", HijriShift.parse("-30"), -2);
        check("garbage is 0", HijriShift.parse("abc") + HijriShift.parse("1.5") + HijriShift.parse("") + HijriShift.parse(null), 0);
        check("fragment", HijriShift.fragment(-1), "&hijri=-1");
        check("fragment held", HijriShift.fragment(7), "&hijri=2");
        check("from fragment", HijriShift.fromFragment("city=x&hijri=-2&digits=latin"), -2);
        check("from fragment: first key too", HijriShift.fromFragment("hijri=1&city=x"), 1);
        check("from fragment: absent", HijriShift.fromFragment("city=x&digits=latin"), 0);
        check("from fragment: not a prefix match", HijriShift.fromFragment("xhijri=2"), 0);
        check("from fragment: null", HijriShift.fromFragment(null), 0);
        check("label 0", HijriShift.label(0), "بلا تعديل");
        check("label -1", HijriShift.label(-1), "يوم أقل");
        check("label -2", HijriShift.label(-2), "يومان أقل");
        check("label +1", HijriShift.label(1), "يوم أكثر");
        check("label +2", HijriShift.label(2), "يومان أكثر");
        check("millis", HijriShift.millis(-2), -172_800_000L);
        System.out.println(failed == 0 ? "all ok" : failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }
}
