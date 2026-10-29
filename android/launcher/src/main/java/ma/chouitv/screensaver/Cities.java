package ma.chouitv.screensaver;

/** Moroccan cities by region (the 12 regions, north to south); the names inside a region are alphabetical. The fourth value is the time zone the page shows:
 *  Morocco keeps GMT all year and the TV's own time-zone data still says +1 for Africa/Casablanca, so Africa/Abidjan (GMT, no summer time) is used. */
final class Cities {
    static final class Region {
        final String name;
        final String[][] cities;   // name, latitude, longitude, time zone
        Region(String name, String[][] cities) { this.name = name; this.cities = cities; }
    }

    static final Region[] REGIONS = {
        new Region("طنجة - تطوان - الحسيمة", new String[][] {
            {"أصيلة", "35.4652", "-6.0342", "Africa/Abidjan"},
            {"تطوان", "35.5784", "-5.3684", "Africa/Abidjan"},
            {"الحسيمة", "35.2516", "-3.9372", "Africa/Abidjan"},
            {"شفشاون", "35.1688", "-5.2636", "Africa/Abidjan"},
            {"طنجة", "35.7673", "-5.7998", "Africa/Abidjan"},
            {"العرائش", "35.1932", "-6.1557", "Africa/Abidjan"},
            {"الفنيدق", "35.8486", "-5.3569", "Africa/Abidjan"},
            {"القصر الكبير", "35.0004", "-5.9038", "Africa/Abidjan"},
            {"مرتيل", "35.6166", "-5.2752", "Africa/Abidjan"},
            {"المضيق", "35.6844", "-5.3256", "Africa/Abidjan"},
            {"وزان", "34.7958", "-5.5785", "Africa/Abidjan"},
        }),
        new Region("الشرق", new String[][] {
            {"بركان", "34.92", "-2.32", "Africa/Abidjan"},
            {"بني أنصار", "35.2564", "-2.9242", "Africa/Abidjan"},
            {"بوعرفة", "32.5338", "-1.9621", "Africa/Abidjan"},
            {"تاوريرت", "34.4073", "-2.8973", "Africa/Abidjan"},
            {"جرادة", "34.31", "-2.16", "Africa/Abidjan"},
            {"جرسيف", "34.2257", "-3.3536", "Africa/Abidjan"},
            {"الدريوش", "34.977", "-3.379", "Africa/Abidjan"},
            {"زايو", "34.9428", "-2.7329", "Africa/Abidjan"},
            {"العروي", "34.9944", "-2.9486", "Africa/Abidjan"},
            {"فكيك", "32.1089", "-1.2286", "Africa/Abidjan"},
            {"الناظور", "35.1681", "-2.9335", "Africa/Abidjan"},
            {"وجدة", "34.6814", "-1.9086", "Africa/Abidjan"},
        }),
        new Region("فاس - مكناس", new String[][] {
            {"أزرو", "33.4344", "-5.2213", "Africa/Abidjan"},
            {"إفران", "33.5267", "-5.1102", "Africa/Abidjan"},
            {"بولمان", "33.3638", "-4.729", "Africa/Abidjan"},
            {"تازة", "34.21", "-4.01", "Africa/Abidjan"},
            {"تاونات", "34.5366", "-4.6401", "Africa/Abidjan"},
            {"الحاجب", "33.6879", "-5.371", "Africa/Abidjan"},
            {"صفرو", "33.8319", "-4.828", "Africa/Abidjan"},
            {"فاس", "34.0331", "-5.0003", "Africa/Abidjan"},
            {"مكناس", "33.8935", "-5.5473", "Africa/Abidjan"},
        }),
        new Region("الرباط - سلا - القنيطرة", new String[][] {
            {"تمارة", "33.9287", "-6.9066", "Africa/Abidjan"},
            {"تيفلت", "33.8947", "-6.3065", "Africa/Abidjan"},
            {"الخميسات", "33.824", "-6.0663", "Africa/Abidjan"},
            {"الرباط", "34.0132", "-6.8326", "Africa/Abidjan"},
            {"سلا", "34.0531", "-6.7985", "Africa/Abidjan"},
            {"سيدي سليمان", "34.2648", "-5.926", "Africa/Abidjan"},
            {"سيدي قاسم", "34.2215", "-5.7077", "Africa/Abidjan"},
            {"الصخيرات", "33.8527", "-7.0317", "Africa/Abidjan"},
            {"عين عودة", "33.7917", "-6.7872", "Africa/Abidjan"},
            {"القنيطرة", "34.261", "-6.5802", "Africa/Abidjan"},
        }),
        new Region("الدار البيضاء - سطات", new String[][] {
            {"ابن أحمد", "33.0663", "-7.2416", "Africa/Abidjan"},
            {"برشيد", "33.2655", "-7.5875", "Africa/Abidjan"},
            {"بن سليمان", "33.6139", "-7.1306", "Africa/Abidjan"},
            {"بوزنيقة", "33.7894", "-7.1597", "Africa/Abidjan"},
            {"بوسكورة", "33.4498", "-7.6524", "Africa/Abidjan"},
            {"الجديدة", "33.2568", "-8.5088", "Africa/Abidjan"},
            {"حد السوالم", "33.4197", "-7.8524", "Africa/Abidjan"},
            {"الدار البيضاء", "33.5883", "-7.6114", "Africa/Abidjan"},
            {"الدروة", "33.3749", "-7.5356", "Africa/Abidjan"},
            {"سطات", "33.001", "-7.6166", "Africa/Abidjan"},
            {"سيدي بنور", "32.6524", "-8.4277", "Africa/Abidjan"},
            {"سيدي رحال", "33.4774", "-7.9364", "Africa/Abidjan"},
            {"المحمدية", "33.6861", "-7.383", "Africa/Abidjan"},
            {"مديونة", "33.4516", "-7.5199", "Africa/Abidjan"},
            {"مولاي عبد الله", "33.1966", "-8.5881", "Africa/Abidjan"},
            {"النواصر", "33.3798", "-7.6193", "Africa/Abidjan"},
        }),
        new Region("بني ملال - خنيفرة", new String[][] {
            {"أبي الجعد", "32.7586", "-6.4092", "Africa/Abidjan"},
            {"أزيلال", "31.9616", "-6.5711", "Africa/Abidjan"},
            {"بني ملال", "32.3372", "-6.3498", "Africa/Abidjan"},
            {"خريبكة", "32.8811", "-6.9063", "Africa/Abidjan"},
            {"دمنات", "31.7344", "-7.005", "Africa/Abidjan"},
            {"سوق السبت", "32.3", "-6.7167", "Africa/Abidjan"},
            {"الفقيه بن صالح", "32.5", "-6.6833", "Africa/Abidjan"},
            {"وادي زم", "32.8627", "-6.5736", "Africa/Abidjan"},
        }),
        new Region("مراكش - آسفي", new String[][] {
            {"آسفي", "32.2994", "-9.2372", "Africa/Abidjan"},
            {"بن جرير", "32.2409", "-7.954", "Africa/Abidjan"},
            {"شيشاوة", "31.5435", "-8.7628", "Africa/Abidjan"},
            {"الصويرة", "31.5125", "-9.77", "Africa/Abidjan"},
            {"قلعة السراغنة", "32.0522", "-7.4081", "Africa/Abidjan"},
            {"مراكش", "31.6342", "-7.9999", "Africa/Abidjan"},
            {"اليوسفية", "32.2463", "-8.5294", "Africa/Abidjan"},
        }),
        new Region("درعة - تافيلالت", new String[][] {
            {"تنغير", "31.5147", "-5.5328", "Africa/Abidjan"},
            {"الرشيدية", "31.9314", "-4.4266", "Africa/Abidjan"},
            {"زاكورة", "30.3324", "-5.8384", "Africa/Abidjan"},
            {"ميدلت", "32.6852", "-4.7451", "Africa/Abidjan"},
            {"ورزازات", "30.9189", "-6.8934", "Africa/Abidjan"},
        }),
        new Region("سوس - ماسة", new String[][] {
            {"أكادير", "30.4202", "-9.5982", "Africa/Abidjan"},
            {"إنزكان", "30.3554", "-9.5364", "Africa/Abidjan"},
            {"أيت ملول", "30.3416", "-9.5036", "Africa/Abidjan"},
            {"تارودانت", "30.4703", "-8.877", "Africa/Abidjan"},
            {"تيزنيت", "29.6974", "-9.7316", "Africa/Abidjan"},
        }),
        new Region("كلميم - واد نون", new String[][] {
            {"سيدي إفني", "29.3798", "-10.173", "Africa/Abidjan"},
            {"طانطان", "28.438", "-11.1032", "Africa/Abidjan"},
            {"كلميم", "28.987", "-10.0574", "Africa/Abidjan"},
        }),
        new Region("العيون - الساقية الحمراء", new String[][] {
            {"بوجدور", "26.1307", "-14.4851", "Africa/Abidjan"},
            {"السمارة", "26.7384", "-11.6719", "Africa/Abidjan"},
            {"العيون", "27.1536", "-13.2033", "Africa/Abidjan"},
        }),
        new Region("الداخلة - وادي الذهب", new String[][] {
            {"الداخلة", "23.6848", "-15.9579", "Africa/Abidjan"},
        }),
    };
}
