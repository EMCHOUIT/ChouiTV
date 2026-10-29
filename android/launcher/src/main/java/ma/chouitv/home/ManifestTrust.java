package ma.chouitv.home;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Who may announce a new version. latest.json is read from GitHub, so anyone who got into the repository could point the TVs at other files;
 * the owner therefore signs the file on their own computer (optimize/sign-manifest.sh) with a key that never leaves it, and the TVs only
 * believe a latest.json whose signature (latest.json.sig, next to it) matches the public key below. No Android here: tests/manifest_trust_test.py runs it.
 */
final class ManifestTrust {
    private ManifestTrust() {}

    /** The public half of the owner's manifest key (ECDSA P-256, X.509, base64). Same text as optimize/manifest-pub.b64. */
    static final String PUBLIC_KEY =
            "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAElwR4egMT3VnU3EmrGp5he7b0hR0lJS5q+07NXc2sDTG2AvgOxrQW41/rE4ZSolH0k+bE3ier7FJxRQ/6eZ/4ew==";

    /** True only when `signature` (base64 of the DER ECDSA signature) is the owner's signature of exactly these bytes. */
    static boolean verify(byte[] manifest, String signature) { return verify(manifest, signature, PUBLIC_KEY); }

    static boolean verify(byte[] manifest, String signature, String publicKey) {
        if (manifest == null || signature == null || publicKey == null) return false;
        try {
            PublicKey key = KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(publicKey.trim())));
            Signature s = Signature.getInstance("SHA256withECDSA");
            s.initVerify(key);
            s.update(manifest);
            return s.verify(Base64.getMimeDecoder().decode(signature.trim().getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception anythingWrong) {
            return false;   // a signature that cannot even be read is not a signature
        }
    }
}
