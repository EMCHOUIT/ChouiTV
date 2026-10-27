package ma.chouitv.home;

import android.content.Context;
import android.graphics.Typeface;

import java.util.HashMap;
import java.util.Map;

/** The type of the screens: the font files in assets/fonts, one Typeface per weight, made once. */
public final class FontSource {
    private static final Map<String, Typeface> CACHE = new HashMap<>();

    private FontSource() { }

    /** "Thmanyah-Medium", "Thmanyah-Medium.otf" -> "Thmanyah-Medium" */
    static String base(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    /** The typeface of a weight; null when the file cannot be read (the caller then uses the system font). */
    public static synchronized Typeface typeface(Context c, String name) {
        String b = base(name);
        Typeface t = CACHE.get(b);
        if (t == null) {
            try { t = Typeface.createFromAsset(c.getAssets(), "fonts/" + b + ".otf"); } catch (RuntimeException e) { return null; }
            CACHE.put(b, t);
        }
        return t;
    }
}
