package ma.chouitv.home.tilawah;

/** For tests/adhan_settings_test.py: the lines of the adhan group in the settings (AdhanSettingsText.java) on a plain JVM. */
public final class AdhanSettingsTextRun {
    static int failed;

    static void check(String name, String got, String want) {
        boolean ok = want.equals(got);
        System.out.println((ok ? "PASS " : "FAIL ") + name + (ok ? "" : "  -> got [" + got + "] want [" + want + "]"));
        if (!ok) failed++;
    }

    static long min(int m) { return m * 60_000L; }

    public static void main(String[] args) {
        check("38 minutes", AdhanSettingsText.shortRemaining(min(38)), "بعد 38 د");
        check("rounds to the nearest minute", AdhanSettingsText.shortRemaining(min(38) - 20_000), "بعد 38 د");
        check("under a minute is now", AdhanSettingsText.shortRemaining(10_000), "الآن");
        check("negative is now", AdhanSettingsText.shortRemaining(-5), "الآن");
        check("one hour exactly", AdhanSettingsText.shortRemaining(min(60)), "بعد 1 س");
        check("hours and minutes", AdhanSettingsText.shortRemaining(min(135)), "بعد 2 س و15 د");
        check("59 minutes", AdhanSettingsText.shortRemaining(min(59)), "بعد 59 د");
        check("state: on with next", AdhanSettingsText.state(true, "العصر", min(38)), "مفعّل · العصر بعد 38 د");
        check("state: on, nothing left to ring", AdhanSettingsText.state(true, null, 0), "مفعّل");
        check("state: off", AdhanSettingsText.state(false, "العصر", min(38)), "متوقف");
        String[] names = {"الفجر", "الظهر", "العصر", "المغرب", "العشاء"};
        check("prayers: all", AdhanSettingsText.prayers(names, new boolean[]{true, true, true, true, true}), "الخمس كلها");
        check("prayers: none", AdhanSettingsText.prayers(names, new boolean[5]), "بدون صلاة");
        check("prayers: one off", AdhanSettingsText.prayers(names, new boolean[]{false, true, true, true, true}), "4 من 5 · بدون الفجر");
        check("prayers: two off", AdhanSettingsText.prayers(names, new boolean[]{false, true, false, true, true}), "3 من 5 · بدون الفجر والعصر");
        check("percent", AdhanSettingsText.percent(30), "30%");
        System.out.println(failed == 0 ? "all ok" : failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }
}
