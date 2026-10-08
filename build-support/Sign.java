import com.android.apksig.ApkSigner;
import com.android.apksig.ApkVerifier;
import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Collections;

public class Sign {
    public static void main(String[] args) throws Exception {
        if (args[0].equals("verify")) {
            ApkVerifier.Result r = new ApkVerifier.Builder(new File(args[1])).build().verify();
            if (!r.isVerified()) throw new IllegalStateException(r.getErrors().toString());
            System.out.println("Signature verified: v1=" + r.isVerifiedUsingV1Scheme() + ", v2=" + r.isVerifiedUsingV2Scheme());
            return;
        }
        KeyStore store = KeyStore.getInstance("JKS");
        try (FileInputStream in = new FileInputStream(args[2])) { store.load(in, "android".toCharArray()); }
        PrivateKey key = (PrivateKey) store.getKey("module", "android".toCharArray());
        X509Certificate cert = (X509Certificate) store.getCertificate("module");
        ApkSigner.SignerConfig config = new ApkSigner.SignerConfig.Builder("module", key, Collections.singletonList(cert)).build();
        new ApkSigner.Builder(Collections.singletonList(config)).setInputApk(new File(args[0])).setOutputApk(new File(args[1]))
            .setMinSdkVersion(24).setV1SigningEnabled(true).setV2SigningEnabled(true).setV3SigningEnabled(false).build().sign();
        main(new String[]{"verify", args[1]});
    }
}
