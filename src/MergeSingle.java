import java.io.*;
import java.nio.file.*;
import app.morphe.patcher.apk.ApkMerger;

/** Reuse Morphe's production split merger; inputs were hash-verified by the build script. */
class MergeSingle {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IOException("Expected split directory and new output");
        File input = new File(args[0]), output = new File(args[1]);
        if (!input.isDirectory() || output.exists()) throw new IOException("Invalid input/output");
        var files = input.listFiles((dir, name) -> name.endsWith(".apk"));
        if (files == null || files.length != 20) throw new IOException("Expected 20 verified splits");
        // Retain resource paths where valid; the merger validates collisions and module identities.
        new ApkMerger().merge(input, output, true, null, true, null, true);
        if (!output.isFile()) throw new IOException("Merger did not produce APK");
        System.out.println("Merged verified Stable split set using bundled Morphe ApkMerger");
    }
}
