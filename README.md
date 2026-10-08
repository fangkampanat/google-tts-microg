# Google TTS for MicroG-RE

Patched Google TTS for BYD DiLink 3.0. Fixes voice downloads with the tested MicroG-RE provider, adds a **Google TTS** launcher icon, and installs as one ARM64 APK.

The Maps setup now uses official MicroG-RE 7.2.1 after our sign-in and location fixes were merged upstream in [PR #272](https://github.com/MorpheApp/MicroG-RE/pull/272). The separate BYD provider fork is no longer maintained. Google TTS still needs the voice-download patch provided here.

## Download and install

1. Install official [`microg-7.2.1-icon-arm64-v8a.apk`](https://github.com/MorpheApp/MicroG-RE/releases/download/7.2.1/microg-7.2.1-icon-arm64-v8a.apk). The patch checks this exact file's SHA-256; other variants and versions are unsupported.
2. Download [Google TTS APK](https://github.com/fangkampanat/google-tts-microg/releases/download/v20260817.01_p0.966249458/Google-TTS-MicroG-20260817.01_p0.966249458-arm64-v8a.apk). If stock Google TTS is installed, uninstall it first because the signing keys differ, then download voices again. Existing builds signed with this project's key can be updated in place.
3. On BYD, open **Disable Autostart** and turn OFF **Speech Recognition and Synthesis from Google** (unchecked, gray, switch left). OFF allows automatic startup. Apply the same setting to **MicroG RE**.
4. On BYD, add Google TTS to the Doze whitelist:

   ```bash
   adb shell dumpsys deviceidle whitelist +com.google.android.tts
   ```

5. Open **Google TTS** and download **Thai** and **English (US)** voices. Play a sample to check each download.
6. For street names in [patched Google Maps](https://github.com/fangkampanat/gmaps-patches), select **Default (language)** in navigation voice settings. Maps needs its own navigation TTS patch; installing this engine alone does not enable street names in every Maps build.

## Tested scope

BYD Dolphin / DiLink 3.0 / Android 10 / ARM64 / 4 KiB memory pages, with official MicroG-RE 7.2.1. The latest tested Maps version is `26.39.06.984891338`, patched with bundle `1.2.2`; the user reported normal use and TTS working.

Thai downloads and street-name speech, English downloads and place-name speech, and speech after vehicle restart passed on the preceding split build. The single APK preserves its five DEX files; installation, retained voices, launcher access and audible speech were confirmed after merging. A further restart and separate TH/EN maneuver checks were not repeated on the single APK. Other firmware, 16 KiB devices, English-script street-name maneuvers and automatic voice timing while driving are unverified.

## Patch source

The [source guide](SOURCE.md) describes the exact tools used for this build. TLS initialization loads the hash-checked MicroG Conscrypt provider and retains certificate and hostname checks. Speech synthesis and language selection are unchanged. This repository does not provide a Morphe `.mpp` bundle.

## File identity

| File | SHA-256 |
|---|---|
| Google TTS APK | `dc06b9020696c060a3d40aa2d9ac75b933169b55d533ca9b70f2fa2439f72a76` |
| Required MicroG-RE APK | `3425e46e95cd45012892cfaa23061b70db0a25affffcfcb84e3df4511d63eb82` |

Package: `com.google.android.tts`. Version code: `210673049`. APK size: `50,397,342` bytes. Signer SHA-256: `4cf39605b9b5bff806de9335a45fbb469800fb87d12887ef31de046bed853913`.

## Credits and license

[Morphe](https://github.com/MorpheApp) supplies the APK merge and patching libraries; [MicroG-RE](https://github.com/MorpheApp/MicroG-RE) supplies the TLS provider.

The patch source is licensed under [GPL-3.0](LICENSE). This license does not apply to Google's app or voice data. This is an unofficial project, unaffiliated with Google, BYD or Morphe.
