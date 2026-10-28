package ma.chouitv.installer;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Every text of the installer in Arabic, French and English. The language is chosen on the first screen. */
final class Strings {
    static String lang = "ar";
    private static final Map<String, String[]> M = new HashMap<>();

    private static void k(String key, String ar, String fr, String en) { M.put(key, new String[] {ar, fr, en}); }

    static String t(String key) {
        String[] v = M.get(key);
        if (v == null) return key;
        return v[lang.equals("fr") ? 1 : lang.equals("en") ? 2 : 0];
    }

    static String t(String key, Object... args) { return String.format(Locale.ENGLISH, t(key), args); }

    static boolean rtl() { return lang.equals("ar"); }

    static {
        k("tagline", "تلفزيونك بلمسة مغربية", "Votre télé, avec une touche marocaine", "Your TV, with a Moroccan touch");
        k("lang_title", "اختر لغتك", "Choisissez votre langue", "Choose your language");
        k("continue", "متابعة", "Continuer", "Continue");
        k("next", "التالي", "Suivant", "Next");
        k("prep_title", "جهّز التلفزيون", "Préparez la télé", "Prepare your TV");
        k("prep_sub", "ثلاث خطوات تستغرق دقيقة", "Trois étapes, une minute", "Three steps, one minute");
        k("prep1a", "الإعدادات ← النظام ← حول", "Paramètres → Système → À propos", "Settings → System → About");
        k("prep1b", "اضغط OK على Build سبع مرات", "Appuyez 7 fois sur OK sur « Build »", "Press OK on Build seven times");
        k("prep2a", "خيارات المطوّر", "Options pour les développeurs", "Developer options");
        k("prep2b", "فعّل «تصحيح USB»", "Activez « Débogage USB »", "Turn on \"USB debugging\"");
        k("prep3a", "تصحيح الشبكة (Network debugging)", "Débogage réseau (Network debugging)", "Network debugging");
        k("prep3b", "واترك التلفزيون على نفس الواي فاي", "Gardez la télé sur le même Wi-Fi", "Keep the TV on the same Wi-Fi");
        k("conn_title", "اتصل بالتلفزيون", "Connectez-vous à la télé", "Connect to your TV");
        k("conn_sub", "ابحث عنه على الواي فاي", "Recherche sur le Wi-Fi", "Looking for it on Wi-Fi");
        k("found", "تلفزيونات وُجدت", "Télés trouvées", "TVs found");
        k("searching", "جارٍ البحث على الواي فاي…", "Recherche en cours…", "Searching…");
        k("none_found", "لم يُعثر على تلفزيون. تأكد أن «تصحيح الشبكة» مفعّل، أو اكتب العنوان.",
            "Aucune télé trouvée. Vérifiez que le « Débogage réseau » est activé, ou saisissez l'adresse.",
            "No TV found. Check that \"Network debugging\" is on, or type the address.");
        k("tv", "تلفزيون", "Télé", "TV");
        k("or_type", "أو اكتب العنوان", "Ou saisissez l'adresse", "Or type the address");
        k("on_tv", "على التلفزيون", "Sur la télé", "On your TV");
        k("allow1", "اضغط «السماح» عند ظهور النافذة", "Appuyez sur « Autoriser » quand la fenêtre apparaît", "Press \"Allow\" when the window appears");
        k("allow2", "وفعّل «السماح دائمًا من هذا الجهاز» لتتذكّره", "Cochez « Toujours autoriser » pour qu'elle s'en souvienne", "Tick \"Always allow\" so it remembers this phone");
        k("connect", "اتصال", "Se connecter", "Connect");
        k("pick_tv", "اختر تلفزيونًا أو اكتب عنوانه", "Choisissez une télé ou saisissez son adresse", "Pick a TV or type its address");
        k("connecting", "جارٍ الاتصال… اقبل الطلب على شاشة التلفزيون (%d ث)", "Connexion… Acceptez la demande sur la télé (%d s)", "Connecting… Accept the request on the TV (%d s)");
        k("err_allow", "لم يصل رد من التلفزيون. تأكد أنك ضغطت «السماح» على شاشته، ثم أعد المحاولة.",
            "La télé n'a pas répondu. Vérifiez que vous avez appuyé sur « Autoriser » sur son écran, puis réessayez.",
            "The TV did not answer. Make sure you pressed \"Allow\" on its screen, then try again.");
        k("err_unreach", "تعذّر الوصول إلى %s. تأكد أن التلفزيون يعمل وعلى نفس الواي فاي وأن «تصحيح الشبكة» مفعّل.",
            "Impossible de joindre %s. Vérifiez que la télé est allumée, sur le même Wi-Fi, avec le « Débogage réseau » activé.",
            "Cannot reach %s. Check that the TV is on, on the same Wi-Fi, with \"Network debugging\" on.");
        k("err_net", "تعذّر التنزيل. تأكد أن الهاتف متصل بالإنترنت ثم أعد المحاولة.",
            "Téléchargement impossible. Vérifiez que le téléphone a Internet, puis réessayez.",
            "Download failed. Check that the phone has internet, then try again.");
        k("err_generic", "حدث خطأ: %s", "Une erreur est survenue : %s", "Something went wrong: %s");
        k("install_title", "تثبيت ChouiTV", "Installation de ChouiTV", "Installing ChouiTV");
        k("install_sub", "لا تُغلق التطبيق", "Ne fermez pas l'application", "Do not close the app");
        k("row_home", "الواجهة الرئيسية", "Écran d'accueil", "Home screen");
        k("row_saver", "شاشة التوقف والإعدادات", "Écran de veille et réglages", "Screensaver and settings");
        k("row_channels", "تطبيق القنوات", "Application Chaînes", "Channels app");
        k("ch_sub", "311 قناة", "311 chaînes", "311 channels");
        k("row_config", "ضبط الشاشة والصلاحيات", "Réglage de l'écran et permissions", "Screen setup and permissions");
        k("version", "الإصدار", "Version", "Version");
        k("installing", "جارٍ التثبيت…", "Installation…", "Installing…");
        k("retry", "أعد المحاولة", "Réessayer", "Try again");
        k("err_install", "تعذّر التثبيت. %s", "Échec de l'installation. %s", "Install failed. %s");
        k("custom_title", "خصّصه لبيتك", "Personnalisez", "Make it yours");
        k("custom_sub", "يمكنك تغييرها لاحقًا", "Modifiable plus tard", "You can change this later");
        k("city", "المدينة", "Ville", "City");
        k("tz", "المنطقة الزمنية", "Fuseau horaire", "Time zone");
        k("method", "طريقة حساب الصلاة", "Méthode de calcul de la prière", "Prayer calculation method");
        k("adhan", "الأذان التلقائي", "Adhan automatique", "Automatic adhan");
        k("on", "يعمل", "Activé", "On");
        k("off", "متوقف", "Désactivé", "Off");
        k("import", "استيراد ملف إعدادات", "Importer un fichier de réglages", "Import a settings file");
        k("import_sub", "chouitv-settings.json من تلفزيون آخر", "chouitv-settings.json d'une autre télé", "chouitv-settings.json from another TV");
        k("import_ready", "جاهز: يُطبَّق عند الضغط على «تطبيق»", "Prêt : appliqué au clic sur « Appliquer »", "Ready: applied when you press \"Apply\"");
        k("apply", "تطبيق", "Appliquer", "Apply");
        k("applying", "جارٍ التطبيق…", "Application…", "Applying…");
        k("region", "الجهة", "Région", "Region");
        k("cities_unavail", "قائمة المدن غير متاحة", "Liste des villes indisponible", "City list unavailable");
        k("will_change", "سيتغيّر على التلفزيون", "Ce qui va changer sur la télé", "What will change on the TV");
        k("no_change", "لا شيء يتغيّر", "Rien ne change", "Nothing changes");
        k("use_file", "استخدم هذا الملف", "Utiliser ce fichier", "Use this file");
        k("cancel", "إلغاء", "Annuler", "Cancel");
        k("err_apply", "تعذّر التطبيق: %s", "Échec de l'application : %s", "Could not apply: %s");
        k("err_read", "تعذّر قراءة الملف: %s", "Lecture du fichier impossible : %s", "Could not read the file: %s");
        k("file_big", "الملف كبير جدًا", "Fichier trop gros", "File too large");
        k("done_ready", "ChouiTV جاهز", "ChouiTV est prêt", "ChouiTV is ready");
        k("done_sub", "التلفزيون يعمل الآن بإعداداتك\nوالتحديثات القادمة تصلك وحدها",
            "La télé fonctionne avec vos réglages\net les prochaines mises à jour arrivent seules",
            "Your TV now runs with your settings\nand future updates arrive by themselves");
        k("tip", "نصيحة", "Conseil", "Tip");
        k("tip_text", "أغلق «تصحيح الشبكة» على التلفزيون عند الانتهاء", "Désactivez le « Débogage réseau » sur la télé une fois fini", "Turn \"Network debugging\" off on the TV when you are done");
        k("open_tv", "افتح على التلفزيون", "Ouvrir sur la télé", "Open on the TV");
        k("step_of", "الخطوة %d من %d", "Étape %d sur %d", "Step %d of %d");
        k("eta_lt1", "أقل من دقيقة", "moins d'une minute", "under a minute");
        k("eta_min", "نحو %d د", "environ %d min", "about %d min");
        k("mb", "م.ب", "Mo", "MB");
        k("s_check", "فحص التلفزيون", "Vérification de la télé", "Checking the TV");
        k("s_download", "تنزيل الإصدار %s", "Téléchargement de la version %s", "Downloading version %s");
        k("s_saver", "شاشة التوقف والإعدادات", "Écran de veille et réglages", "Screensaver and settings");
        k("s_channels", "تطبيق القنوات", "Application Chaînes", "Channels app");
        k("s_home", "الواجهة الرئيسية", "Écran d'accueil", "Home screen");
        k("s_perms", "الصلاحيات", "Permissions", "Permissions");
        k("s_defaults", "الواجهة الافتراضية", "Accueil par défaut", "Default home");
        k("s_verify", "التحقق من التثبيت", "Vérification de l'installation", "Checking the install");
        k("sub_send", "إرسال %d%%", "Envoi %d%%", "Sending %d%%");
        k("sub_install", "تثبيت…", "Installation…", "Installing…");
        k("sub_perms", "الموقع · المنبّهات", "Position · alarmes", "Location · alarms");
        k("sub_defaults", "مع شاشة التوقف", "et écran de veille", "and screensaver");
        k("sub_verify", "الإصدارات والواجهة", "Versions et accueil", "Versions and home");
        k("thanks_title", "شكرًا لاختيارك ChouiTV", "Merci d'avoir choisi ChouiTV", "Thank you for choosing ChouiTV");
        k("thanks_sub", "تلفزيونك جاهز، والتحديثات تصلك وحدها", "Votre télé est prête, les mises à jour arrivent seules", "Your TV is ready and updates arrive by themselves");
        k("star_title", "أعجبك التطبيق؟", "ChouiTV vous plaît ?", "Enjoying ChouiTV?");
        k("star_text", "نجمة على GitHub تدعمنا وتشجّعنا على الاستمرار", "Une étoile sur GitHub nous soutient et nous encourage à continuer", "A star on GitHub supports us and keeps us going");
        k("star_btn", "ضع نجمة للمشروع", "Mettre une étoile au projet", "Star the project");
        k("z_Africa/Casablanca", "المغرب · GMT (Africa/Casablanca)", "Maroc · GMT (Africa/Casablanca)", "Morocco · GMT (Africa/Casablanca)");
        // time zones and prayer methods
        k("z_Africa/Abidjan", "المغرب · GMT (Africa/Abidjan)", "Maroc · GMT (Africa/Abidjan)", "Morocco · GMT (Africa/Abidjan)");
        k("z_city", "حسب المدينة", "Selon la ville", "By city");
        k("z_Africa/Algiers", "الجزائر", "Algérie", "Algeria");
        k("z_Africa/Tunis", "تونس", "Tunisie", "Tunisia");
        k("z_Africa/Cairo", "مصر", "Égypte", "Egypt");
        k("z_Asia/Riyadh", "السعودية", "Arabie saoudite", "Saudi Arabia");
        k("z_Asia/Dubai", "الإمارات", "Émirats", "UAE");
        k("z_Europe/Paris", "فرنسا", "France", "France");
        k("m_21", "وزارة الأوقاف المغربية", "Ministère des Habous (Maroc)", "Moroccan Ministry of Awqaf");
        k("m_auto", "تلقائي حسب المنطقة", "Automatique selon la zone", "Automatic by zone");
        k("m_19", "الجزائر", "Algérie", "Algeria");
        k("m_18", "تونس", "Tunisie", "Tunisia");
        k("m_5", "الهيئة المصرية", "Autorité égyptienne", "Egyptian authority");
        k("m_4", "أم القرى", "Oumm al-Qura", "Umm al-Qura");
        k("m_3", "رابطة العالم الإسلامي", "Ligue islamique mondiale", "Muslim World League");
    }
}
