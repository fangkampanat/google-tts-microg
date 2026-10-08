import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;
import com.android.tools.smali.dexlib2.*;
import com.android.tools.smali.dexlib2.iface.*;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.android.tools.smali.dexlib2.immutable.*;
import com.android.tools.smali.dexlib2.immutable.instruction.*;
import com.android.tools.smali.dexlib2.immutable.reference.*;
import com.android.tools.smali.dexlib2.writer.pool.DexPool;

/** Exact-input TLS hook and opt-in trace wrappers. Other APK payloads are copied unchanged. */
class PatchTts {
    static String hash(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
    static boolean target(Method m) {
        return m.getDefiningClass().equals("Lioi;") && m.getName().equals("a")
                && m.getParameterTypes().toString().equals("[Llma;]") && m.getReturnType().equals("V");
    }
    static String refs(Method m) {
        StringBuilder s = new StringBuilder();
        for (var i : m.getImplementation().getInstructions())
            if (i instanceof ReferenceInstruction r) s.append(r.getReference()).append('\n');
        return s.toString();
    }
    static boolean signature(String name) {
        return name.equals("stamp-cert-sha256") || name.matches("META-INF/[^/]+\\.(SF|RSA|DSA|EC)")
                || name.equals("META-INF/MANIFEST.MF");
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 3 && !(args.length == 4 && args[3].equals("--tls-only")))
            throw new IOException("Expected original, bridge, output and optional --tls-only");
        boolean tlsOnly = args.length == 4;
        Path original = Path.of(args[0]), bridge = Path.of(args[1]), output = Path.of(args[2]);
        if (Files.exists(output)) throw new IOException("Output already exists");
        if (!hash(Files.readAllBytes(original)).equals(
                "af7cde02aa8bc2a1e75f2b8aac5df0a3e06aeb538d88c7350abbafaf77443067"))
            throw new IOException("Unexpected input APK");
        var container = DexFileFactory.loadDexContainer(original.toFile(), Opcodes.getDefault());
        String dexName = null;
        List<ClassDef> patched = new ArrayList<>();
        int matches = 0;
        boolean contextField = false;
        Set<String> traceFields = new HashSet<>();
        int traceTargets = 0;
        for (String entry : container.getDexEntryNames()) {
            var dex = container.getEntry(entry).getDexFile();
            for (var c : dex.getClasses()) {
                if (!tlsOnly && (c.getType().equals("Ldhs;") || c.getType().equals("Leyx;") || c.getType().equals("Ldil;"))) {
                    if (!AccessFlags.PUBLIC.isSet(c.getAccessFlags())) throw new IOException("Nonpublic trace model");
                    for (var f : c.getFields()) if (AccessFlags.PUBLIC.isSet(f.getAccessFlags())) traceFields.add(f.toString());
                    for (var m : c.getMethods()) if (AccessFlags.PUBLIC.isSet(m.getAccessFlags())) traceFields.add(m.toString());
                }
                for (var m : c.getMethods()) if (!tlsOnly && TraceHooks.target(m)) {
                    if (!entry.equals("classes2.dex")) throw new IOException("Trace layout changed");
                    traceTargets++;
                    if (TraceHooks.synth(m) && !refs(m).contains("Ldib;->b(Ldhs;I)Leyx;"))
                        throw new IOException("Synthesis no longer uses expected selector");
                    if (TraceHooks.selected(m) && !refs(m).contains("No local or network voice found, failing dispatch"))
                        throw new IOException("Unexpected voice selector");
                }
                if (c.getType().equals("Llma;")) for (var f : c.getFields())
                    if (f.getName().equals("b") && f.getType().equals("Ljava/lang/Object;")
                            && AccessFlags.PUBLIC.isSet(f.getAccessFlags())) contextField = true;
                for (var m : c.getMethods()) if (target(m)) {
                    if (AccessFlags.STATIC.isSet(m.getAccessFlags())) throw new IOException("Static target");
                    String references = refs(m);
                    for (String required : List.of("Blocked unpatched use of SSL stack.",
                            "com.google.android.gms", "Llma;->b:Ljava/lang/Object;"))
                        if (!references.contains(required)) throw new IOException("Missing guard: " + required);
                    matches++;
                    dexName = entry;
                }
            }
        }
        if (matches != 1 || !contextField) throw new IOException("Hook/field mismatch: " + matches);
        if (!tlsOnly && (traceTargets != 7 || !traceFields.containsAll(List.of("Ldhs;->m:Ljava/lang/String;",
                "Leyx;->a:Ljava/lang/Object;", "Leyx;->b:Ljava/lang/Object;", "Ldil;->m()Ljava/lang/String;"))))
            throw new IOException("Trace hooks/models mismatch");
        List<ClassDef> moved = new ArrayList<>();
        for (var c : container.getEntry(dexName).getDexFile().getClasses()) {
            if (!tlsOnly && (c.getType().equals(TraceHooks.SERVICE) || c.getType().equals("Ldib;"))) {
                moved.add(TraceHooks.wrap(c)); continue;
            }
            if (!c.getType().equals("Lioi;")) { patched.add(c); continue; }
            List<Method> methods = new ArrayList<>();
            for (var m : c.getMethods()) {
                if (!target(m)) { methods.add(m); continue; }
                var body = new ImmutableMethodImplementation(3, List.of(
                        new ImmutableInstruction22c(Opcode.IGET_OBJECT, 0, 2,
                                new ImmutableFieldReference("Llma;", "b", "Ljava/lang/Object;")),
                        new ImmutableInstruction21c(Opcode.CHECK_CAST, 0,
                                new ImmutableTypeReference("Landroid/content/Context;")),
                        new ImmutableInstruction35c(Opcode.INVOKE_STATIC, 1, 0, 0, 0, 0, 0,
                                new ImmutableMethodReference("Llocal/tts/MicrogTls;", "install",
                                        List.of("Landroid/content/Context;"), "V")),
                        new ImmutableInstruction10x(Opcode.RETURN_VOID)), List.of(), List.of());
                methods.add(new ImmutableMethod(m.getDefiningClass(), m.getName(), m.getParameters(),
                        m.getReturnType(), m.getAccessFlags(), m.getAnnotations(),
                        m.getHiddenApiRestrictions(), body));
            }
            moved.add(new ImmutableClassDef(c.getType(), c.getAccessFlags(), c.getSuperclass(),
                    c.getInterfaces(), c.getSourceFile(), c.getAnnotations(), c.getFields(), methods));
        }
        Path dexOut = output.resolveSibling(output.getFileName() + "-classes2.dex");
        if (Files.exists(dexOut)) throw new IOException("Intermediate already exists");
        DexPool.writeTo(dexOut.toString(), new ImmutableDexFile(Opcodes.getDefault(), patched));
        // Move the patched classes beside the helpers to avoid overflowing the original
        // DEX's 16-bit reference tables. No instructions in unrelated classes are rewritten.
        List<ClassDef> extra = new ArrayList<>(DexFileFactory.loadDexFile(bridge.toFile(),
                Opcodes.getDefault()).getClasses());
        extra.addAll(moved);
        Path extraOut = output.resolveSibling(output.getFileName() + "-bridge.dex");
        if (Files.exists(extraOut)) throw new IOException("Extra DEX already exists");
        DexPool.writeTo(extraOut.toString(), new ImmutableDexFile(Opcodes.getDefault(), extra));
        int nextDex = container.getDexEntryNames().size() + 1;
        String extension = "classes" + nextDex + ".dex";
        try (ZipFile input = new ZipFile(original.toFile());
             ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(output))) {
            if (input.getEntry(extension) != null) throw new IOException("DEX name collision");
            for (var e : Collections.list(input.entries())) {
                if (signature(e.getName())) continue;
                byte[] bytes = e.getName().equals(dexName) ? Files.readAllBytes(dexOut)
                        : input.getInputStream(e).readAllBytes();
                put(zip, e.getName(), bytes, e.getMethod());
            }
            put(zip, extension, Files.readAllBytes(extraOut), ZipEntry.STORED);
        }
        // Verify copied payloads and the actual emitted hook, not just patcher completion.
        try (ZipFile before = new ZipFile(original.toFile()); ZipFile after = new ZipFile(output.toFile())) {
            for (var e : Collections.list(before.entries())) {
                if (signature(e.getName()) || e.getName().equals(dexName)) continue;
                if (!Arrays.equals(before.getInputStream(e).readAllBytes(),
                        after.getInputStream(after.getEntry(e.getName())).readAllBytes()))
                    throw new IOException("Unexpected payload change: " + e.getName());
            }
        }
        var result = DexFileFactory.loadDexContainer(output.toFile(), Opcodes.getDefault());
        int hook = 0, helper = 0, traces = 0, originals = 0;
        for (String e : result.getDexEntryNames()) for (var c : result.getEntry(e).getDexFile().getClasses()) {
            if (c.getType().equals("Llocal/tts/MicrogTls;")) helper++;
            for (var m : c.getMethods()) {
                if (target(m) && refs(m).contains("Llocal/tts/MicrogTls;->install")) hook++;
                if (TraceHooks.target(m) && refs(m).contains(TraceHooks.TRACE)) traces++;
                if (m.getName().startsWith(TraceHooks.PREFIX)) originals++;
            }
        }
        int expectedTraces = tlsOnly ? 0 : 7;
        if (hook != 1 || helper != 1 || traces != expectedTraces || originals != expectedTraces)
            throw new IOException("Emitted hooks mismatch");
        System.out.println("PASS: one SSL hook, " + expectedTraces + " trace wrappers; other ZIP payloads unchanged; " + extension);
    }
    static void put(ZipOutputStream zip, String name, byte[] bytes, int method) throws IOException {
        ZipEntry e = new ZipEntry(name);
        e.setTime(1609459200000L);
        e.setMethod(method);
        if (method == ZipEntry.STORED) {
            CRC32 crc = new CRC32(); crc.update(bytes); e.setCrc(crc.getValue());
            e.setSize(bytes.length); e.setCompressedSize(bytes.length);
        }
        zip.putNextEntry(e); zip.write(bytes); zip.closeEntry();
    }
}
