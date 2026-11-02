package ma.chouitv.home.tilawah;

/**
 * The short lines the settings screen shows for the adhan, with no Android in them (tests/AdhanSettingsTextRun.java runs it).
 * The numbers are written with latin digits; the caller turns them into the screen's digits (Ui.digits).
 */
final class AdhanSettingsText {
    private AdhanSettingsText() { }

    /** «بعد 38 د», «بعد 2 س و15 د», «بعد 3 س», «الآن»: the time left to the next adhan, short enough for a row's second line. */
    static String shortRemaining(long millis) {
        long minutes = Math.max(0, (millis + 30_000) / 60_000);
        if (minutes == 0) return "الآن";
        long h = minutes / 60, m = minutes % 60;
        if (h == 0) return "بعد " + m + " د";
        return "بعد " + h + " س" + (m > 0 ? " و" + m + " د" : "");
    }

    /** «مفعّل · العصر بعد 38 د»; «مفعّل» alone when no prayer is left to ring (all five switched off); «متوقف» when the adhan is off. */
    static String state(boolean on, String nextName, long millisToNext) {
        if (!on) return "متوقف";
        if (nextName == null) return "مفعّل";
        return "مفعّل · " + nextName + " " + shortRemaining(millisToNext);
    }

    /** «الخمس كلها», or «3 من 5 · بدون الفجر والعصر», or «بدون صلاة» when every adhan is off. */
    static String prayers(String[] names, boolean[] ringing) {
        int count = 0;
        StringBuilder off = new StringBuilder();
        for (int i = 0; i < ringing.length; i++) {
            if (ringing[i]) { count++; continue; }
            if (off.length() > 0) off.append(" و");
            off.append(names[i]);
        }
        if (count == ringing.length) return "الخمس كلها";
        if (count == 0) return "بدون صلاة";
        return count + " من " + ringing.length + " · بدون " + off;
    }

    /** The volume as a percent, «30٪» in latin digits: «30%». */
    static String percent(int value) { return value + "%"; }
}
