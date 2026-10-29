package ma.chouitv.home.tilawah;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * ADB control for agents and optimize/music.sh: GET_PLAYER state, play, pause, next, sleep, and the
 * adhan (play now, switch it on or off, pick the voice, the person's own recording, set its volume).
 *
 *   ADHAN_OWN --es op info | add --ei size BYTES [--es path /data/local/tmp/urtv-own/in.m4a] | clear
 *   VOICE --ei voice 1..21 (a recording of ours) or 0 (the person's own, when there is one)
 */
public class MusicReceiver extends BroadcastReceiver {

    @Override public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;

        if ("ma.chouitv.home.GET_PLAYER".equals(action)) {
            setResultData(status(context));
            return;
        }

        Prefs prefs = new Prefs(context);
        switch (action) {
            case "ma.chouitv.home.ADHAN": {
                int voice = intent.getIntExtra(MusicService.EXTRA_VOICE, prefs.playVoice(context));
                if (voice == Prefs.OWN && !OwnAdhan.exists(context)) return;   // nothing of theirs to play
                MusicService.adhan(context, voice, null, 0);
                return;
            }
            case "ma.chouitv.home.ADHAN_RESCHEDULE":   // optimize/saver.sh prayeradj: the alarm for the minutes as they are now
                AdhanSync.reschedule(context);
                setResultData("rescheduled");
                return;
            case "ma.chouitv.home.ADHAN_ON":
            case "ma.chouitv.home.ADHAN_OFF":
                prefs.setAdhanOn("ma.chouitv.home.ADHAN_ON".equals(action));
                AdhanScheduler.schedule(context);
                return;
            case "ma.chouitv.home.ADHAN_PRAYER": {   // optimize/music.sh adhan prayer isha off: the adhan of one prayer on or off
                String[] names = {"fajr", "dhuhr", "asr", "maghrib", "isha"};
                String which = String.valueOf(intent.getStringExtra("prayer")).toLowerCase(java.util.Locale.ROOT);
                String state = String.valueOf(intent.getStringExtra("state")).toLowerCase(java.util.Locale.ROOT);
                int slot = java.util.Arrays.asList(names).indexOf(which);
                if (slot < 0 || (!"on".equals(state) && !"off".equals(state))) {
                    setResultData("error: prayer is one of fajr dhuhr asr maghrib isha, state on or off");
                    return;
                }
                prefs.setAdhanPrayerOn(slot, "on".equals(state));
                AdhanScheduler.schedule(context);   // the alarm is for the next prayer whose adhan is on
                setResultData("saved\n" + prayersOff(prefs));
                return;
            }
            case "ma.chouitv.home.ADHAN_VOLUME": {   // optimize/music.sh adhan volume 60
                int percent = intent.getIntExtra("percent", -1);
                if (percent < 10 || percent > 100) return;
                prefs.setAdhanVolume(percent);
                return;
            }
            case "ma.chouitv.home.VOICE": {
                int voice = intent.getIntExtra(MusicService.EXTRA_VOICE, prefs.voice());
                if (voice == Prefs.OWN) {
                    if (!OwnAdhan.exists(context)) { setResultData("error: no recording of your own yet (optimize/music.sh adhan add FILE)"); return; }
                    prefs.setOwnChosen(true);
                } else if (voice < 1 || voice > Adhan.VOICES) {
                    setResultData("error: the voice is 1-" + Adhan.VOICES + " or 0 for your own recording");
                    return;
                } else {
                    prefs.setVoice(voice);
                    prefs.setOwnChosen(false);
                }
                setResultData("saved\n" + ownListing(context, prefs));
                return;
            }
            case "ma.chouitv.home.ADHAN_OWN":
                setResultData(own(context, prefs, intent));
                return;
            case "ma.chouitv.home.RADIO_COUNTRIES": {   // optimize/music.sh radio-countries ma,fr: the radios' countries (two at most); no value = show them
                String csv = intent.getStringExtra("countries");
                if (csv != null && !csv.trim().isEmpty()) {
                    java.util.List<String> asked = new java.util.ArrayList<>();
                    for (String part : csv.split(",")) if (!part.trim().isEmpty()) asked.add(part.trim().toLowerCase(java.util.Locale.ROOT));
                    if (asked.size() > RadioChoice.MAX) {
                        setResultData("error: " + RadioChoice.MAX + " countries at most");
                        return;
                    }
                    java.util.List<String> offered = new java.util.ArrayList<>(java.util.Arrays.asList(RadioStore.OFFERED));
                    offered.add(RadioChoice.HOME);
                    for (String cc : asked) {
                        if (!offered.contains(cc)) { setResultData("error: " + cc + " is not offered (" + offered + ")"); return; }
                    }
                    RadioStore.setChosen(context, asked);
                }
                StringBuilder sb = new StringBuilder("countries=" + RadioChoice.join(RadioStore.chosen(context)));
                for (String cc : RadioStore.chosen(context)) {
                    sb.append("\n").append(cc).append(": ").append(RadioChoice.HOME.equals(cc) ? "bundled" : RadioStore.downloaded(context, cc) ? "downloaded" : "not downloaded (radio-update)");
                }
                setResultData(sb.toString());
                return;
            }
            case "ma.chouitv.home.RADIO_UPDATE":   // optimize/music.sh radio-update: download the lists of the chosen countries now
                RadioStore.update(context, (RadioStore.Done) null);
                setResultData("updating the lists of " + RadioChoice.join(RadioStore.chosen(context)));
                return;
            default:
                break;
        }

        Catalog cat = Catalog.get(context);
        Intent service = new Intent(context, MusicService.class).setAction(action);
        switch (action) {
            case MusicService.ACTION_PLAY_SURAH:
                service.putExtra(MusicService.EXTRA_RECITER, intent.getStringExtra("reciter"));
                service.putExtra(MusicService.EXTRA_SURAH, intent.getIntExtra("surah", 1));
                break;
            case MusicService.ACTION_PLAY_RADIO: {
                int index = intent.getIntExtra("station", -1);
                String name = intent.getStringExtra("station");
                if (index < 0 && name != null) index = cat.stationIndex(name);
                if (index < 0) return;
                service.putExtra(MusicService.EXTRA_STATION, index);
                break;
            }
            case MusicService.ACTION_SLEEP:
                service.putExtra(MusicService.EXTRA_MINUTES, intent.getIntExtra("minutes", 0));
                break;
            case MusicService.ACTION_SEEK_BY:
                service.putExtra(MusicService.EXTRA_DELTA, intent.getIntExtra("delta", 0));
                break;
            default:
                break;
        }
        MusicService.start(context, service);
    }

    /** «أذان من ملفي», for optimize/music.sh: info, add (the shell has just written the file, or it is in /data/local/tmp/urtv-own/), clear. */
    private static String own(Context context, Prefs prefs, Intent intent) {
        String op = String.valueOf(intent.getStringExtra("op"));
        switch (op) {
            case "info":
                return ownListing(context, prefs);
            case "add": {
                String path = intent.getStringExtra("path");
                java.io.File src = OwnAdhan.incoming(context);
                if (path != null) {
                    if (!path.matches("/data/local/tmp/urtv-own/[A-Za-z0-9_.-]+") || path.contains(".."))
                        return "error: path must be a file in /data/local/tmp/urtv-own/";
                    src = new java.io.File(path);
                }
                int size = intent.getIntExtra("size", -1);   // what the shell sent: a short or stale file is refused
                String why = size >= 0 && src.length() != size
                        ? "the file is not the one sent (" + src.length() + " of " + size + " bytes)"
                        : OwnAdhan.keep(context, src);
                if (why != null && src.equals(OwnAdhan.incoming(context))) src.delete();
                if (why != null) return "error: " + why;
                prefs.setOwnChosen(true);   // a recording just added is the one the person wants
                return "saved\n" + ownListing(context, prefs);
            }
            case "clear":
                OwnAdhan.clear(context);
                prefs.setOwnChosen(false);
                return "saved\n" + ownListing(context, prefs);
            default:
                return "error: op is one of info add clear";
        }
    }

    private static String ownListing(Context context, Prefs prefs) {
        return OwnAdhan.exists(context)
                ? "own: " + OwnAdhanText.size(OwnAdhan.bytes(context)) + " (" + (prefs.playVoice(context) == Prefs.OWN ? "in use" : "kept, not in use") + ")"
                : "own: none";
    }

    private String status(Context context) {
        Catalog cat = Catalog.get(context);
        Prefs prefs = new Prefs(context);
        int source = MusicService.currentSource();
        boolean playing = MusicService.isPlaying();
        int reciter = MusicService.reciterIndex();
        int surah = MusicService.currentSurah();
        int station = MusicService.currentStation();
        int position = MusicService.currentPosition();
        int duration = MusicService.currentDuration();
        String error = MusicService.currentError();
        long sleepAt = MusicService.sleepAt();

        if (source == Prefs.SOURCE_NONE) {
            source = prefs.source();
            surah = prefs.surah();
            station = prefs.station();
            position = prefs.position();
            reciter = cat.reciterIndex(prefs.reciter());
            duration = 0;
            error = null;
            sleepAt = 0;
        }

        String reciterId = reciter >= 0 && reciter < cat.reciters.size() ? cat.reciters.get(reciter).id : "";
        String reciterName = reciter >= 0 && reciter < cat.reciters.size() ? cat.reciters.get(reciter).name : "";
        String stationName = station >= 0 && station < cat.stations.size() ? cat.stations.get(station).name : "";
        long sleepLeft = sleepAt > 0 ? Math.max(0, (sleepAt - System.currentTimeMillis()) / 1000) : 0;
        PrayerTimes.Next next = PrayerTimes.next(context);

        return "playing=" + (playing ? 1 : 0)
                + "|source=" + (source == Prefs.SOURCE_SURAH ? "surah"
                        : source == Prefs.SOURCE_RADIO ? "radio"
                        : source == Prefs.SOURCE_ADHAN ? "adhan" : "none")
                + "|reciter=" + reciterId
                + "|reciter_name=" + reciterName
                + "|surah=" + (source == Prefs.SOURCE_SURAH ? surah : 0)
                + "|surah_name=" + (source == Prefs.SOURCE_SURAH ? cat.surahName(surah) : "")
                + "|station=" + (source == Prefs.SOURCE_RADIO ? station : -1)
                + "|station_name=" + (source == Prefs.SOURCE_RADIO ? stationName : "")
                + "|pos=" + position
                + "|dur=" + duration
                + "|sleep=" + sleepLeft
                + "|adhan=" + (prefs.adhanOn() ? "on" : "off")
                + "|voice=" + (prefs.playVoice(context) == Prefs.OWN ? "own" : String.valueOf(prefs.voice()))
                + "|adhan_volume=" + prefs.adhanVolume()
                + "|adhan_exact=" + (AdhanScheduler.exactAllowed(context) ? 1 : 0)
                + "|own_bytes=" + (OwnAdhan.exists(context) ? OwnAdhan.bytes(context) : 0)
                + "|adhan_next=" + (next == null ? 0 : next.at)
                + "|adhan_next_name=" + (next == null ? "" : next.name())
                + "|adhan_next_clock=" + (next == null ? "" : next.clock)
                + "|adhan_alarm=" + AdhanScheduler.scheduledAt(context)   // the moment the alarm is set for: the next prayer whose adhan is on (0 when the adhan is off)
                + "|adhan_prayers_off=" + prayersOff(prefs)
                + "|adhan_last=" + prefs.lastAdhan()
                + "|error=" + (error == null ? "" : error);
    }

    /** The prayers whose own adhan is switched off, as «fajr,isha»; empty when none. */
    private static String prayersOff(Prefs prefs) {
        String[] names = {"fajr", "dhuhr", "asr", "maghrib", "isha"};
        StringBuilder off = new StringBuilder();
        for (int k = 0; k < names.length; k++) {
            if (!prefs.adhanPrayerOn(k)) off.append(off.length() > 0 ? "," : "").append(names[k]);
        }
        return off.toString();
    }
}
