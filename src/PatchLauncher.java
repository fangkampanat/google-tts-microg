import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;
import com.reandroid.arsc.chunk.xml.*;

/** Add a launcher entry to the existing exported voice-download activity only. */
class PatchLauncher {
    static final String TARGET =
            "com.google.android.apps.speech.tts.googletts.local.voicepack.ui.VoiceDataInstallActivity";
    static final String EXPECTED =
            "daa33058e97a629e1faf4a80e5281945c17b6d5d0ddc62e61f22537d80b12b5e";
    static final int NAME = 0x01010003, LABEL = 0x01010001, EXPORTED = 0x01010010;

    static String hash(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
    static String name(ResXmlElement element) {
        var value = element.searchAttributeByResourceId(NAME);
        return value == null ? null : value.getValueAsString();
    }
    static boolean signature(String name) {
        return name.equals("stamp-cert-sha256") || name.equals("META-INF/MANIFEST.MF")
                || name.matches("META-INF/[^/]+\\.(SF|RSA|DSA|EC)");
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IOException("Expected verified dev.5 input and new output");
        Path input = Path.of(args[0]), output = Path.of(args[1]);
        if (Files.exists(output)) throw new IOException("Output already exists");
        if (!EXPECTED.equals(hash(Files.readAllBytes(input)))) throw new IOException("Unexpected input APK");
        byte[] manifest;
        try (var zip = new ZipFile(input.toFile())) {
            manifest = zip.getInputStream(zip.getEntry("AndroidManifest.xml")).readAllBytes();
        }
        var document = AndroidManifestBlock.load(new ByteArrayInputStream(manifest));
        if (!"com.google.android.tts".equals(document.getPackageName())
                || document.getVersionCode() != 210673049 || document.getIconResourceId() == 0)
            throw new IOException("Unexpected package/version/icon");
        ResXmlElement target = null;
        var elements = document.getApplicationElement().getElements("activity");
        while (elements.hasNext()) {
            var element = elements.next();
            if (TARGET.equals(name(element))) {
                if (target != null) throw new IOException("Ambiguous voice activity");
                target = element;
            }
        }
        if (target == null || target.searchAttributeByResourceId(EXPORTED) == null
                || !target.searchAttributeByResourceId(EXPORTED).getValueAsBoolean()
                || target.searchAttributeByResourceId(LABEL) == null
                || target.searchAttributeByName("enabled") != null
                || target.searchAttributeByName("permission") != null)
            throw new IOException("Voice activity unavailable or layout changed");
        boolean installAction = false;
        var all = document.getDocumentElement().recursiveElements();
        while (all.hasNext()) {
            var element = (ResXmlElement) all.next();
            if ("android.intent.category.LAUNCHER".equals(name(element)))
                throw new IOException("Input already has launcher entry");
        }
        var filters = target.getElements("intent-filter");
        while (filters.hasNext()) {
            var actions = filters.next().getElements("action");
            while (actions.hasNext()) if ("android.speech.tts.engine.INSTALL_TTS_DATA".equals(name(actions.next())))
                installAction = true;
        }
        if (!installAction) throw new IOException("Voice installation action missing");
        target.searchAttributeByResourceId(LABEL).setValueAsString("Google TTS");
        var filter = target.newElement("intent-filter");
        filter.newElement("action").getOrCreateAndroidAttribute("name", NAME)
                .setValueAsString("android.intent.action.MAIN");
        filter.newElement("category").getOrCreateAndroidAttribute("name", NAME)
                .setValueAsString("android.intent.category.LAUNCHER");
        document.refreshFull();
        byte[] patchedManifest = document.getBytes();
        try (var source = new ZipFile(input.toFile());
             var dest = new ZipOutputStream(Files.newOutputStream(output))) {
            for (var old : Collections.list(source.entries())) {
                if (signature(old.getName())) continue;
                byte[] data = old.getName().equals("AndroidManifest.xml") ? patchedManifest
                        : source.getInputStream(old).readAllBytes();
                var entry = new ZipEntry(old.getName());
                entry.setTime(old.getTime());
                entry.setMethod(old.getMethod());
                if (old.getMethod() == ZipEntry.STORED) {
                    var crc = new CRC32(); crc.update(data);
                    entry.setSize(data.length); entry.setCompressedSize(data.length); entry.setCrc(crc.getValue());
                }
                dest.putNextEntry(entry); dest.write(data); dest.closeEntry();
            }
        }
        System.out.println("Added Google TTS launcher entry to existing voice-download activity; DEX/resources unchanged");
    }
}
