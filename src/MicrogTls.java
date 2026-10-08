package local.tts;

import android.content.Context;
import android.util.Log;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.Provider;
import java.security.Security;
import javax.net.ssl.SSLContext;

/** Narrow adapter for the inspected BYD provider. Does not change TLS trust policy. */
public final class MicrogTls {
    private static final String PACKAGE = "app.revanced.android.gms";
    private static final String PROVIDER = "GmsCore_OpenSSL";
    // Pin executable code to the inspected official MicroG-RE 7.2.1 ARM64 APK.
    private static final String APK_SHA256 =
            "3425e46e95cd45012892cfaa23061b70db0a25affffcfcb84e3df4511d63eb82";
    private static boolean ready;

    public static synchronized void install(Context caller) throws IOException {
        if (ready && Security.getProvider(PROVIDER) != null) return;
        try {
            String path = caller.getPackageManager().getApplicationInfo(PACKAGE, 0).sourceDir;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (FileInputStream input = new FileInputStream(path)) {
                byte[] buffer = new byte[65536];
                int count;
                while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
            }
            StringBuilder hash = new StringBuilder();
            for (byte value : digest.digest()) hash.append(String.format("%02x", value & 255));
            if (!APK_SHA256.equals(hash.toString())) {
                throw new IOException("MicroG APK differs from inspected official 7.2.1 provider");
            }
            Context remote = caller.createPackageContext(PACKAGE,
                    Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
            remote.getClassLoader()
                    .loadClass("com.google.android.gms.providerinstaller.ProviderInstallerImpl")
                    .getMethod("insertProvider", Context.class).invoke(null, remote);
            Provider provider = Security.getProvider(PROVIDER);
            // microG catches initialization errors internally: a normal return is insufficient.
            if (provider == null || !provider.getClass().getName()
                    .startsWith("com.google.android.gms.org.conscrypt.")) {
                throw new IOException("MicroG Conscrypt provider was not installed");
            }
            if (!PROVIDER.equals(SSLContext.getDefault().getProvider().getName())) {
                throw new IOException("Default TLS context did not select MicroG Conscrypt");
            }
            ready = true;
            Log.i("TtsMicrogTls", "MicroG Conscrypt ready; platform trust and hostname checks retained");
        } catch (Exception | LinkageError failure) {
            Log.e("TtsMicrogTls", "SSL provider initialization failed", failure);
            throw new IOException("MicroG SSL provider unavailable", failure);
        }
    }
}
