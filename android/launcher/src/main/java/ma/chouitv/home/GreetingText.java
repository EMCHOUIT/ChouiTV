package ma.chouitv.home;

/**
 * The words of the home screen's greeting and its weather line. No Android in here, so
 * tests/greeting_test.py runs it on tests/greeting_cases.tsv.
 */
final class GreetingText {
    static final int MORNING = 0, DAY = 1, EVENING = 2, NIGHT = 3;

    private GreetingText() { }

    /** Fajr to Dhuhr, Dhuhr to Asr, Asr to Isha, and the night from Isha to the next Fajr. Times in millis. */
    static int part(long now, long fajr, long dhuhr, long asr, long isha) {
        if (now < fajr) return NIGHT;       // still the night before
        if (now < dhuhr) return MORNING;
        if (now < asr) return DAY;
        if (now < isha) return EVENING;
        return NIGHT;
    }

    // Moroccan darija: a few ways to say it for each part of the day, one after the other by the hour; «جمعة مباركة» on Friday morning and day
    private static final String[][] WORDS = {
        {"صباح الخير", "صباح النور", "نهارك مبروك", "مرحبا بكم في دارنا"},          // MORNING
        {"نهارك مبروك", "نهاركم سعيد", "مرحبا بكم في دارنا", "الله يعطيكم الصحة"},    // DAY
        {"مساء الخير", "مسا النور", "مساكم مبروك", "مرحبا بكم في دارنا"},           // EVENING
        {"تصبحوا على خير", "ليلة سعيدة", "الله يسعد ليلتكم", "نعسو على خير"},        // NIGHT
    };

    static String greeting(int part, long now) {
        java.util.Calendar c = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("GMT"));   // Morocco's own time
        c.setTimeInMillis(now);
        if (c.get(java.util.Calendar.DAY_OF_WEEK) == java.util.Calendar.FRIDAY && (part == MORNING || part == DAY)) return "جمعة مباركة";
        String[] w = WORDS[part];
        return w[(int) ((now / 3600000L) % w.length)];
    }

    static String greeting(int part) { return greeting(part, System.currentTimeMillis()); }

    /** the plain greeting (no prayer times known): by the TV's hour */
    static String plain(int hour) { return greeting(hour < 4 ? NIGHT : hour < 12 ? MORNING : EVENING); }

    /** Open-Meteo's WMO weather code, as a short phrase that goes before «في المدينة». */
    static String sky(int code) {
        switch (code) {
            case 0: return "سماء صافية";
            case 1: return "سماء صافية غالبًا";
            case 2: return "غائم جزئيًا";
            case 3: return "سماء غائمة";
            case 45: case 48: return "ضباب";
            case 51: case 53: case 55: case 56: case 57: return "رذاذ";
            case 61: case 63: case 80: case 81: return "أمطار";
            case 65: case 82: return "أمطار غزيرة";
            case 66: case 67: return "مطر متجمد";
            case 71: case 73: case 75: case 77: case 85: case 86: return "ثلوج";
            case 95: return "عواصف رعدية";
            case 96: case 99: return "عواصف رعدية وبرد";
            default: return "";
        }
    }

    /** PM10 or dust in µg/m³ from which the air is «غبار», and «غبار كثيف»: the page's thresholds (it overrides its condition word) */
    static final double DUST = 150, HEAVY_DUST = 400;

    /** The sky's word, or «غبار» / «غبار كثيف» when the dust level (the larger of Open-Meteo's pm10 and dust) says so. */
    static String skyWord(int code, double dust) {
        if (dust >= HEAVY_DUST) return "غبار كثيف";
        if (dust >= DUST) return "غبار";
        return sky(code);
    }

    /** «سماء صافية في الرياض، ٣١°» (no «·» beside a digit: the TV's font draws it as ٠); a code we have no word for gives just «الرياض، ٣١°»; dust (0 when unknown) takes the word over. */
    static String weather(int code, double dust, String city, double temp, boolean latin) {
        String sky = skyWord(code, dust);
        String place = sky.isEmpty() ? city : sky + " في " + city;
        return place + "، " + digits(Math.round(temp) + "°", latin);
    }

    /** «الغروب ٥:٥٧ م» from "17:57". */
    static String sun(String label, String hhmm, boolean latin) {
        int colon = hhmm.indexOf(':');
        int h = Integer.parseInt(hhmm.substring(0, colon));
        String m = hhmm.substring(colon + 1, colon + 3);
        int h12 = h % 12 == 0 ? 12 : h % 12;
        return label + " " + digits(h12 + ":" + m, latin) + " " + (h < 12 ? "ص" : "م");
    }

    /** Arabic-Indic digits (as the page's #digits=hindi), or the Latin ones left alone. */
    static String digits(String s, boolean latin) {
        return DigitsText.convert(s, latin);
    }
}
