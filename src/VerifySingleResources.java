import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;
import com.reandroid.apk.ApkModule;
import com.reandroid.arsc.chunk.xml.*;
import com.reandroid.arsc.value.*;

/** Compare resource IDs/configurations/values independently of the merge operation. */
class VerifySingleResources {
    static String digest(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
    static String value(ValueItem value, ZipFile zip) throws Exception {
        if (value.getValueType() != ValueType.STRING) return value.getValueType() + ":" + value.getData();
        String text = value.getValueAsString();
        var file = zip.getEntry(text);
        if (file != null && !file.isDirectory()) return "FILE_SHA256:" + digest(zip.getInputStream(file).readAllBytes());
        return "STRING:" + text;
    }
    static Map<String, String> snapshot(File file, Set<Integer> excluded) throws Exception {
        Map<String, String> result = new TreeMap<>();
        try (var module = ApkModule.loadApkFile(file); var zip = new ZipFile(file)) {
            if (!module.hasTableBlock()) return result;
            var packages = module.getTableBlock().getPackages();
            while (packages.hasNext()) {
                var pkg = packages.next();
                for (var pair : pkg.listSpecTypePairs()) for (var type : pair) for (var entry : type) {
                    if (entry == null || entry.isNull() || excluded.contains(entry.getResourceId())) continue;
                    String key = Integer.toHexString(entry.getResourceId()) + "/" + entry.getResConfig().getQualifiers();
                    String content = pkg.getName() + "/" + entry.getTypeName() + "/" + entry.getName()
                            + "/public=" + entry.getHeader().isPublic() + "/weak=" + entry.getHeader().isWeak();
                    if (entry.isComplex()) {
                        var compound = entry.getResTableMapEntry();
                        content += "/parent=" + compound.getParentId();
                        for (var item : compound) content += "/" + item.getNameId() + "=" + value(item, zip);
                    } else content += "/" + value(entry.getResValue(), zip);
                    String previous = result.put(key, content);
                    if (previous != null && !previous.equals(content)) throw new IOException("Conflicting resource " + key);
                }
            }
        }
        return result;
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IOException("Expected split directory, merged APK, evidence file");
        File input = new File(args[0]);
        Set<Integer> excluded = new HashSet<>();
        try (var base = ApkModule.loadApkFile(new File(input, "com.google.android.tts.apk"))) {
            var elements = base.getAndroidManifest().getApplicationElement().getElements("meta-data");
            while (elements.hasNext()) {
                var meta = elements.next();
                if ("com.android.vending.splits".equals(AndroidManifestBlock.getAndroidNameValue(meta))) {
                    var id = meta.searchAttributeByResourceId(0x01010024);
                    if (id == null) id = meta.searchAttributeByResourceId(0x01010025);
                    if (id == null || id.getValueType() != ValueType.REFERENCE) throw new IOException("Invalid split metadata");
                    excluded.add(id.getData());
                }
            }
        }
        Map<String, String> expected = new TreeMap<>();
        var files = input.listFiles((dir, name) -> name.endsWith(".apk"));
        if (files == null || files.length != 20) throw new IOException("Invalid split set");
        for (var file : files) for (var entry : snapshot(file, excluded).entrySet()) {
            var previous = expected.put(entry.getKey(), entry.getValue());
            if (previous != null && !previous.equals(entry.getValue()))
                throw new IOException("Different source values for " + entry.getKey());
        }
        var actual = snapshot(new File(args[1]), excluded);
        List<String> differences = new ArrayList<>();
        for (var entry : expected.entrySet()) if (!entry.getValue().equals(actual.get(entry.getKey())))
            differences.add("MISSING/CHANGED " + entry.getKey() + "\n before=" + entry.getValue() + "\n after=" + actual.get(entry.getKey()));
        for (var key : actual.keySet()) if (!expected.containsKey(key)) differences.add("ADDED " + key);
        Path evidence = Path.of(args[2]);
        if (Files.exists(evidence)) throw new IOException("Evidence already exists");
        Files.writeString(evidence, "Expected " + expected.size() + "; actual " + actual.size()
                + "; split-index IDs excluded " + excluded + "\n" + String.join("\n", differences));
        if (!differences.isEmpty()) throw new IOException("Resource preservation failed: " + differences.size());
        System.out.println("PASS resource IDs/configurations/values preserved: " + expected.size()
                + "; excluded only obsolete split-index resource IDs " + excluded);
    }
}
