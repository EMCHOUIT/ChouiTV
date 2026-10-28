package ma.chouitv.installer;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Everything that talks to the network but not to the TV: the release files, and the search for TVs on the Wi-Fi. */
final class Net {
    static final String MANIFEST = "https://raw.githubusercontent.com/EMCHOUIT/ChouiTV/main/latest.json";
    static final String CHANNELS = "https://github.com/EMCHOUIT/ChouiTV/releases/download/channels/channels-release.apk";

    /** One APK of the release: package, URL and SHA-256 from latest.json. */
    static final class Apk { String pkg, url, sha; }

    static final class Release { String version; List<Apk> apks = new ArrayList<>(); }

    static Release latest() throws Exception {
        JSONObject j = new JSONObject(new String(fetch(MANIFEST), "UTF-8"));
        Release r = new Release();
        r.version = j.getString("versionName");
        JSONArray a = j.getJSONArray("apks");
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.getJSONObject(i);
            Apk k = new Apk();
            k.pkg = o.getString("package"); k.url = o.getString("url"); k.sha = o.optString("sha256", "");
            r.apks.add(k);
        }
        return r;
    }

    static byte[] fetch(String url) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        HttpURLConnection c = open(url);
        try (InputStream i = c.getInputStream()) { copy(i, b); } finally { c.disconnect(); }
        return b.toByteArray();
    }

    /** Downloads to a file; checks the SHA-256 when one is given. The progress callback gets bytes done and the total (or -1). */
    static File download(String url, File to, String sha, Adb.Progress p) throws Exception {
        HttpURLConnection c = open(url);
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        long total = c.getContentLengthLong(), done = 0;
        try (InputStream i = c.getInputStream(); FileOutputStream o = new FileOutputStream(to)) {
            byte[] buf = new byte[32768];
            int n;
            while ((n = i.read(buf)) > 0) {
                o.write(buf, 0, n); md.update(buf, 0, n); done += n;
                if (p != null) p.at(done, total);
            }
        } finally { c.disconnect(); }
        if (sha != null && !sha.isEmpty()) {
            StringBuilder h = new StringBuilder();
            for (byte x : md.digest()) h.append(String.format("%02x", x));
            if (!h.toString().equalsIgnoreCase(sha)) { to.delete(); throw new IOException("the downloaded file is not the published one (checksum)"); }
        }
        if (to.length() < 100000) { to.delete(); throw new IOException("the downloaded file is too small"); }
        return to;
    }

    private static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new java.net.URL(url).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(30000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "ChouiTV-Installer");
        if (c.getResponseCode() / 100 != 2) throw new IOException("HTTP " + c.getResponseCode() + " " + url);
        return c;
    }

    private static void copy(InputStream i, java.io.OutputStream o) throws IOException {
        byte[] buf = new byte[16384];
        int n;
        while ((n = i.read(buf)) > 0) o.write(buf, 0, n);
    }

    /** Hosts of this phone's Wi-Fi network (a /24) that answer on the ADB port, found in about two seconds. */
    static List<String> findTvs(int port) {
        List<String> found = Collections.synchronizedList(new ArrayList<String>());
        String base = null, self = null;
        try {
            for (Enumeration<NetworkInterface> e = NetworkInterface.getNetworkInterfaces(); e.hasMoreElements(); ) {
                NetworkInterface ni = e.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    String h = a.getHostAddress();
                    if (a instanceof java.net.Inet4Address && a.isSiteLocalAddress()) {
                        self = h; base = h.substring(0, h.lastIndexOf('.') + 1);
                    }
                }
            }
        } catch (Exception ignored) { }
        if (base == null) return found;
        ExecutorService ex = Executors.newFixedThreadPool(48);
        List<Future<?>> fs = new ArrayList<>();
        for (int i = 1; i < 255; i++) {
            final String ip = base + i;
            if (ip.equals(self)) continue;
            fs.add(ex.submit(new Callable<Void>() {
                public Void call() {
                    try (Socket s = new Socket()) { s.connect(new InetSocketAddress(ip, port), 600); found.add(ip); } catch (Exception ignored) { }
                    return null;
                }
            }));
        }
        for (Future<?> f : fs) { try { f.get(); } catch (Exception ignored) { } }
        ex.shutdown();
        List<String> sorted = new ArrayList<>(found);
        Collections.sort(sorted, new java.util.Comparator<String>() {
            public int compare(String a, String b) { return Integer.parseInt(a.substring(a.lastIndexOf('.') + 1)) - Integer.parseInt(b.substring(b.lastIndexOf('.') + 1)); }
        });
        return sorted;
    }
}
