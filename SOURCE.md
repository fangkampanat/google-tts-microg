# Source guide

These are the original patch tools used for the tested APK. Inputs and intermediate hashes are intentionally pinned; this is not a portable, one-command build pipeline.

## Inputs and tools

- Google TTS XAPK `googletts.google-speech-apk_20260817.01_p0.966249458`, SHA-256 `f950df801695b24bd26e86a1bde07dc153d1dcafbeab4e20778dc6a02351ca84`.
- Its 20 APKs, including `com.google.android.tts.apk`, SHA-256 `af7cde02aa8bc2a1e75f2b8aac5df0a3e06aeb538d88c7350abbafaf77443067`.
- Morphe Desktop 1.18.1 all-in-one JAR, Java 21, Android SDK platform 36 and build tools 36.0.0.
- A signing key. The release uses the maintainer's existing key; private keys and credentials are not distributed.

## Tested production sequence

1. Compile `MicrogTls.java` for Android with Java 8 source/target and Android's boot class path, then convert it to DEX using D8 with minimum API 26.
2. Compile the host tools against the Morphe JAR. Run `PatchTts original-base.apk helper.dex tls-unsigned.apk --tls-only`. This requires the exact original base hash, one matching SSL initializer and its expected Context field. The remaining ZIP payloads are verified unchanged.
3. Align the TLS base to 4 KiB, sign it, and verify it with `apksigner`. The tested signed intermediate hash is `daa33058e97a629e1faf4a80e5281945c17b6d5d0ddc62e61f22537d80b12b5e`.
4. Run `PatchLauncher signed-tls-base.apk launcher-unsigned.apk`. It requires that exact intermediate hash, adds MAIN/LAUNCHER to the existing exported voice-download activity and sets its label to Google TTS. Align/sign the base and re-sign all 19 original splits with the same key.
5. Run `MergeSingle split-directory single-unsigned.apk`. It uses Morphe's split merger and requires 20 APKs. Align to 4 KiB and sign the resulting single APK.
6. Run `VerifySingleResources split-directory single-signed.apk evidence.txt`, verify the signature and alignment, and inspect DEX/native/manifest/ZIP preservation before device testing. Resource comparison excludes only the obsolete split-index resource.

Run Java host tools with `-Duser.language=en -Duser.country=US` to avoid Windows Buddhist-calendar ZIP timestamps. Keep outputs separate from inputs; the tools refuse existing output files.

`TraceHooks.java` is a compile-time dependency of the retained patcher. Production used `--tls-only`, with zero tracing wrappers in the APK. The diagnostic helper is not included in this repository.

The launcher tool's signed-intermediate hash will differ with another signing key. Supporting a different input requires inspecting that input and reviewing its guards; removing checks merely to make a build succeed is insufficient. Rebuilding and signing do not reproduce the published APK byte-for-byte or its runtime evidence.
