import java.security.*;
import java.util.Base64;
import java.nio.file.*;
public class GenKeys {
    public static void main(String[] a) throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        KeyPair k = g.generateKeyPair();
        var enc = Base64.getMimeEncoder(64, "\n".getBytes());
        String priv = "-----BEGIN PRIVATE KEY-----\n" + enc.encodeToString(k.getPrivate().getEncoded()) + "\n-----END PRIVATE KEY-----\n";
        String pub  = "-----BEGIN PUBLIC KEY-----\n" + enc.encodeToString(k.getPublic().getEncoded()) + "\n-----END PUBLIC KEY-----\n";
        Files.writeString(Path.of(a[0], "tmp-private.pem"), priv);
        Files.writeString(Path.of(a[0], "tmp-public.pem"), pub);
        System.out.println("written to " + a[0]);
    }
}