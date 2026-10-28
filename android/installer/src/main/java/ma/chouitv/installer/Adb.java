package ma.chouitv.installer;

import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

/**
 * A small ADB client over TCP (the classic port 5555, «Network debugging»): the handshake, the RSA authorisation the TV asks
 * the person to accept, a shell command, and a file streamed to «cmd package install». One stream at a time.
 */
final class Adb implements AutoCloseable {
    private static final int A_CNXN = 0x4e584e43, A_AUTH = 0x48545541, A_OPEN = 0x4e45504f, A_OKAY = 0x59414b4f,
        A_CLSE = 0x45534c43, A_WRTE = 0x45545257;
    private static final int VERSION = 0x01000001, MAX_DATA = 256 * 1024;
    /** DigestInfo of SHA-1: ADB signs the 20-byte token as if it were already a digest. */
    private static final byte[] SHA1_PREFIX = {0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14};

    interface Progress { void at(long done, long total); }

    private final Socket socket;
    private final java.io.InputStream in;
    private final OutputStream out;
    private int maxData = 4096;
    private int nextLocal = 1;
    /** What the TV says about itself in its CNXN banner, e.g. device::ro.product.model=...; */
    String banner = "";

    private static void log(String m) { }

    private Adb(Socket s) throws IOException {
        socket = s;
        in = s.getInputStream();
        out = s.getOutputStream();
    }

    /** Connects and authorises. The first time, the TV shows «Allow USB debugging?»: this waits up to 70 s for the answer. */
    static Adb connect(String host, int port, File keyDir) throws Exception {
        Socket s = new Socket();
        s.connect(new InetSocketAddress(host, port), 4000);
        s.setTcpNoDelay(true);
        s.setSoTimeout(70000);
        Adb a = new Adb(s);
        KeyPair kp = loadOrMakeKey(keyDir);
        a.send(A_CNXN, VERSION, MAX_DATA, "host::features=cmd,stat_v2\0".getBytes("UTF-8"));
        boolean sentKey = false;
        for (int i = 0; i < 8; i++) {
            Packet p = a.read();
            if (p.cmd == A_CNXN) {
                a.maxData = (int) Math.min(Math.max(p.arg1, 4096), MAX_DATA);
                a.banner = new String(p.data, "UTF-8");
                log("connected, maxData=" + a.maxData + " banner=" + a.banner);
                s.setSoTimeout(30000);
                return a;
            }
            if (p.cmd != A_AUTH || p.arg0 != 1) throw new IOException("unexpected answer from the TV");
            if (!sentKey && i == 0) {
                a.send(A_AUTH, 2, 0, sign(kp.getPrivate(), p.data));   // a key the TV already knows needs no question
            } else {
                a.send(A_AUTH, 3, 0, publicKeyText(kp));   // the TV asks the person to accept it
                sentKey = true;
            }
        }
        throw new IOException("the TV did not accept the connection");
    }

    @Override public void close() {
        try { socket.close(); } catch (IOException ignored) { }
    }

    // ---------- streams ----------

    /** Runs a shell command and returns everything it printed (stdout and stderr together). */
    String shell(String cmd) throws IOException {
        return service("shell:" + cmd, null, -1, null);
    }

    /** Streams a file into «cmd package install -S size -r»; returns pm's answer («Success» when it worked). */
    String install(File apk, Progress progress) throws IOException {
        try {
            return service("exec:cmd package install -r -S " + apk.length(), apk, apk.length(), progress);
        } catch (IOException refused) {
            if (refused.getMessage() == null || !refused.getMessage().contains("refused")) throw refused;
        }
        // this TV's adbd answers no exec: stream and stalls on sync: the file goes up in base64 pieces through the shell,
        // the way optimize/tv.py does it, then «pm install»
        String remote = "/data/local/tmp/chouitv-" + apk.getName();
        pushShell(apk, remote, progress);
        try {
            return runInBackground("pm install -r " + remote, 300);
        } finally {
            try { shell("rm -f " + remote); } catch (IOException ignored) { }
        }
    }

    /**
     * Runs a command that can take minutes in the background on the TV and looks at it every few seconds with short commands
     * (this TV's adbd refuses «pm install» as a direct shell command, and a shell that prints nothing for long is taken for dead).
     */
    String runInBackground(String cmd, int limitSeconds) throws IOException {
        String job = "/data/local/tmp/chouitv-job";
        shell("rm -f " + job + ".rc " + job + ".out");
        shell("nohup sh -c '(" + cmd + ") > " + job + ".out 2>&1; echo $? > " + job + ".rc' > /dev/null 2>&1 &");
        for (int waited = 0; waited < limitSeconds; waited += 3) {
            try { Thread.sleep(3000); } catch (InterruptedException e) { throw new IOException("interrupted"); }
            if (shell("[ -f " + job + ".rc ] && echo done").contains("done")) {
                String out = shell("cat " + job + ".out");
                shell("rm -f " + job + ".rc " + job + ".out");
                return out;
            }
        }
        throw new IOException("the TV is still working after " + limitSeconds + " seconds");
    }

    /** Uploads a file as base64 pieces appended through the shell; checks the size at the end. Works on any adbd. */
    void pushShell(File f, String remote, Progress progress) throws IOException {
        shell("rm -f " + remote);
        byte[] buf = new byte[48 * 1024];
        long sent = 0, total = f.length();
        try (InputStream in = new FileInputStream(f)) {
            int n;
            while ((n = in.read(buf)) > 0) {
                String b64 = Base64.encodeToString(buf, 0, n, Base64.NO_WRAP);
                String out = shell("echo -n " + b64 + " | base64 -d >> " + remote);
                if (!out.isEmpty()) throw new IOException("upload failed: " + out);
                sent += n;
                if (progress != null) progress.at(sent, total);
            }
        }
        String size = shell("wc -c < " + remote).trim();
        if (!size.equals(String.valueOf(total))) throw new IOException("upload incomplete: " + size + " of " + total + " bytes");
    }

    /** Sends a file to the TV with the sync protocol («SEND», blocks of up to 64 KB, «DONE»). */
    void push(File f, String remote, Progress progress) throws IOException {
        int local = nextLocal++;
        log("push " + f + " -> " + remote);
        send(A_OPEN, local, 0, "sync:\0".getBytes("UTF-8"));
        Packet p = read();
        while (p.cmd != A_OKAY) {
            if (p.cmd == A_CLSE) throw new IOException("the TV refused sync:");
            p = read();
        }
        int rem = p.arg0;
        log("sync open ok, remote=" + rem);
        ByteArrayOutputStream inbox = new ByteArrayOutputStream();
        String target = remote + ",33188";   // 0100644
        byte[] t = target.getBytes("UTF-8");
        syncWrite(local, rem, inbox, syncHeader("SEND", t.length));
        syncWrite(local, rem, inbox, t);
        try (InputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[64 * 1024];
            long sent = 0, total = f.length();
            int n;
            while ((n = in.read(buf)) > 0) {
                syncWrite(local, rem, inbox, syncHeader("DATA", n));
                syncWrite(local, rem, inbox, java.util.Arrays.copyOf(buf, n));
                sent += n;
                if (sent % (512 * 1024) == 0) log("pushed " + sent);
                if (progress != null) progress.at(sent, total);
            }
        }
        syncWrite(local, rem, inbox, syncHeader("DONE", (int) (f.lastModified() / 1000)));
        log("waiting for the sync answer");
        byte[] head = syncRead(local, rem, inbox, 8);
        log("sync answer " + new String(head, 0, 4, "US-ASCII"));
        String id = new String(head, 0, 4, "US-ASCII");
        if (!id.equals("OKAY")) {
            int len = ByteBuffer.wrap(head, 4, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
            String msg = len > 0 && len < 4096 ? new String(syncRead(local, rem, inbox, len), "UTF-8") : id;
            throw new IOException("push failed: " + msg);
        }
        try { syncWrite(local, rem, inbox, syncHeader("QUIT", 0)); } catch (IOException ignored) { }
        try { send(A_CLSE, local, rem, new byte[0]); } catch (IOException ignored) { }
    }

    private static byte[] syncHeader(String id, int v) throws IOException {
        ByteBuffer b = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        b.put(id.getBytes("US-ASCII")).putInt(v);
        return b.array();
    }

    /** One write on a sync stream, in blocks the TV accepts; its own output (if any) is kept in the inbox. */
    private void syncWrite(int local, int rem, ByteArrayOutputStream inbox, byte[] data) throws IOException {
        int o = 0;
        while (o < data.length) {
            int n = Math.min(maxData, data.length - o);
            send(A_WRTE, local, rem, java.util.Arrays.copyOfRange(data, o, o + n));
            o += n;
            if (data.length > 100000 || o == n) log("wrote " + n + " bytes (" + o + "/" + data.length + ")");
            while (true) {
                Packet q = read();
                log("got " + Integer.toHexString(q.cmd) + " arg0=" + q.arg0 + " arg1=" + q.arg1 + " len=" + q.data.length);
                if (q.cmd == A_OKAY) break;
                if (q.cmd == A_WRTE) { inbox.write(q.data); send(A_OKAY, local, rem, new byte[0]); }
                else if (q.cmd == A_CLSE) throw new IOException("the TV closed the transfer");
            }
        }
    }

    private byte[] syncRead(int local, int rem, ByteArrayOutputStream inbox, int n) throws IOException {
        while (inbox.size() < n) {
            Packet q = read();
            if (q.cmd == A_WRTE) { inbox.write(q.data); send(A_OKAY, local, rem, new byte[0]); }
            else if (q.cmd == A_CLSE) throw new IOException("the TV closed the transfer");
        }
        byte[] all = inbox.toByteArray();
        inbox.reset();
        inbox.write(all, n, all.length - n);
        return java.util.Arrays.copyOf(all, n);
    }

    private String service(String name, File upload, long size, Progress progress) throws IOException {
        int local = nextLocal++;
        log("open " + (name.length() > 70 ? name.substring(0, 70) + "..." : name));
        send(A_OPEN, local, 0, (name + "\0").getBytes("UTF-8"));
        Packet p = readFor(local);
        while (p.cmd != A_OKAY) {
            if (p.cmd == A_CLSE) throw new IOException("the TV refused " + name);
            p = readFor(local);
        }
        int remote = p.arg0;
        ByteArrayOutputStream res = new ByteArrayOutputStream();
        boolean closed = false;
        if (upload != null) {
            try (InputStream f = new FileInputStream(upload)) {
                byte[] buf = new byte[Math.min(maxData, 64 * 1024)];
                long sent = 0;
                int n;
                while ((n = f.read(buf)) > 0) {
                    send(A_WRTE, local, remote, java.util.Arrays.copyOf(buf, n));
                    sent += n;
                    // the TV answers every block with OKAY; its output (if any already) arrives as WRTE meanwhile
                    while (true) {
                        Packet q = readFor(local);
                        if (q.cmd == A_OKAY) break;
                        if (q.cmd == A_WRTE) { res.write(q.data); send(A_OKAY, local, remote, new byte[0]); }
                        else if (q.cmd == A_CLSE) { closed = true; break; }
                    }
                    if (progress != null) progress.at(sent, size);
                    if (closed) break;
                }
            }
        }
        while (!closed) {
            Packet q = readFor(local);
            if (q.cmd == A_WRTE) { res.write(q.data); send(A_OKAY, local, remote, new byte[0]); }
            else if (q.cmd == A_CLSE) closed = true;
        }
        try { send(A_CLSE, local, remote, new byte[0]); } catch (IOException ignored) { }
        return res.toString("UTF-8").replace("\r", "").trim();
    }

    // ---------- packets ----------

    private static final class Packet { int cmd, arg0, arg1; byte[] data; }

    private synchronized void send(int cmd, int arg0, int arg1, byte[] data) throws IOException {
        ByteBuffer h = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
        h.putInt(cmd).putInt(arg0).putInt(arg1).putInt(data.length).putInt(0).putInt(cmd ^ 0xffffffff);
        out.write(h.array());
        if (data.length > 0) out.write(data);
        out.flush();
    }

    /** The next packet for this stream: a late packet of an earlier, already closed stream is skipped, not taken for an answer. */
    private Packet readFor(int local) throws IOException {
        while (true) {
            Packet p = read();
            if (p.arg1 == local) return p;
        }
    }

    private Packet read() throws IOException {
        byte[] h = new byte[24];
        readFully(h);
        ByteBuffer b = ByteBuffer.wrap(h).order(ByteOrder.LITTLE_ENDIAN);
        Packet p = new Packet();
        p.cmd = b.getInt(); p.arg0 = b.getInt(); p.arg1 = b.getInt();
        int len = b.getInt();
        if (len < 0 || len > 4 * 1024 * 1024) throw new IOException("bad packet");
        p.data = new byte[len];
        if (len > 0) readFully(p.data);
        return p;
    }

    private void readFully(byte[] b) throws IOException {
        int o = 0;
        while (o < b.length) {
            int n = in.read(b, o, b.length - o);
            if (n < 0) throw new IOException("the TV closed the connection");
            o += n;
        }
    }

    // ---------- keys ----------

    private static KeyPair loadOrMakeKey(File dir) throws Exception {
        File priv = new File(dir, "adbkey.pk8"), pub = new File(dir, "adbkey.x509");
        if (priv.exists() && pub.exists()) {
            try {
                KeyFactory kf = KeyFactory.getInstance("RSA");
                return new KeyPair(kf.generatePublic(new X509EncodedKeySpec(readAll(pub))), kf.generatePrivate(new PKCS8EncodedKeySpec(readAll(priv))));
            } catch (Exception ignored) { }
        }
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        KeyPair kp = g.generateKeyPair();
        dir.mkdirs();
        try (FileOutputStream o = new FileOutputStream(priv)) { o.write(kp.getPrivate().getEncoded()); }
        try (FileOutputStream o = new FileOutputStream(pub)) { o.write(kp.getPublic().getEncoded()); }
        return kp;
    }

    private static byte[] readAll(File f) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        try (InputStream i = new FileInputStream(f)) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = i.read(buf)) > 0) b.write(buf, 0, n);
        }
        return b.toByteArray();
    }

    private static byte[] sign(PrivateKey key, byte[] token) throws Exception {
        Signature s = Signature.getInstance("NONEwithRSA");
        s.initSign(key);
        s.update(SHA1_PREFIX);
        s.update(token);
        return s.sign();
    }

    /** The public key in ADB's own layout (Android's «mincrypt» struct), base64, with a name, ending in NUL. */
    private static byte[] publicKeyText(KeyPair kp) throws Exception {
        RSAPublicKey k = (RSAPublicKey) kp.getPublic();
        BigInteger n = k.getModulus();
        BigInteger two32 = BigInteger.ONE.shiftLeft(32);
        int n0inv = n.mod(two32).modInverse(two32).negate().mod(two32).intValue();
        BigInteger rr = BigInteger.ONE.shiftLeft(4096).mod(n);
        ByteBuffer b = ByteBuffer.allocate(4 + 4 + 256 + 256 + 4).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(64).putInt(n0inv).put(littleEndian(n, 256)).put(littleEndian(rr, 256)).putInt(k.getPublicExponent().intValue());
        String text = Base64.encodeToString(b.array(), Base64.NO_WRAP) + " installer@chouitv\0";
        return text.getBytes("UTF-8");
    }

    private static byte[] littleEndian(BigInteger v, int len) {
        byte[] be = v.toByteArray();   // big-endian, maybe with a leading zero
        byte[] le = new byte[len];
        for (int i = 0; i < len && i < be.length; i++) le[i] = be[be.length - 1 - i];
        return le;
    }
}
