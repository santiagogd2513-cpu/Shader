import com.android.apksig.ApkSigner;
import java.io.*; import java.security.*; import java.security.cert.*; import java.util.*;
public class Sign {
  public static void main(String[] a) throws Exception {
    KeyStore ks = KeyStore.getInstance("PKCS12");
    ks.load(new FileInputStream(a[0]), a[1].toCharArray());
    String alias = ks.aliases().nextElement();
    PrivateKey k = (PrivateKey) ks.getKey(alias, a[1].toCharArray());
    X509Certificate c = (X509Certificate) ks.getCertificate(alias);
    ApkSigner.SignerConfig sc = new ApkSigner.SignerConfig.Builder("shizuku", k, Collections.singletonList(c)).build();
    new ApkSigner.Builder(Collections.singletonList(sc)).setInputApk(new File(a[2])).setOutputApk(new File(a[3]))
      .setMinSdkVersion(24).setV1SigningEnabled(false).setV2SigningEnabled(true).build().sign();
    System.out.println("signed");
  }
}
