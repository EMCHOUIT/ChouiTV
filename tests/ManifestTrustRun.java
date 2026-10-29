package ma.chouitv.home;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/** For tests/manifest_trust_test.py: prints true/false for ManifestTrust.verify(FILE, SIGFILE [, PUBFILE]). */
public final class ManifestTrustRun {
    public static void main(String[] a) throws Exception {
        byte[] file = Files.readAllBytes(Paths.get(a[0]));
        String sig = new String(Files.readAllBytes(Paths.get(a[1])), StandardCharsets.US_ASCII);
        boolean ok = a.length > 2
                ? ManifestTrust.verify(file, sig, new String(Files.readAllBytes(Paths.get(a[2])), StandardCharsets.US_ASCII))
                : ManifestTrust.verify(file, sig);
        System.out.println(ok);
    }
}
