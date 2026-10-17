# Acceptance evidence

This file separates what the repository verifies automatically from what still needs a
person. A green CI run is evidence for everything listed under "Automated", and for
nothing else. Every command below is also run by CI on the stated platform.

## 1. Automated, no human needed

### Build, tests, packaging (Ubuntu, Windows, macOS)

- `./gradlew --no-daemon spotlessCheck test jacocoTestCoverageVerification` — formatting,
  JUnit 6/AssertJ suite, 45 % line-coverage gate.
- `./gradlew --no-daemon build jpackageImage` — compile, test, assemble, native image.
- `python3 tools/app_smoke_test.py --image <image> --mode all` — packaged JAR resource
  inventory (six translation bundles, three themes, `bell.mp3`, `bell.wav`, JavaFX modules)
  and a real launch: the app writes `started` only after the primary stage is showing, then
  performs a normal shutdown. A timeout or missing marker is a failure.
- Linux additionally builds the AppImage and smoke-tests it; macOS builds the DMG and
  smoke-tests a `.app` copied out of it; Windows installs the MSI, smoke-tests it, and
  uninstalls it.

### Sound output, measured from captured samples (Linux)

`python3 tools/acceptance_check.py --image build/dist/TaskFlow` runs the **packaged**
application on a private Xvfb display with a disposable home/data directory and a private
ALSA file PCM (`tools/isolated_audio.py`). The generated `ALSA_CONFIG_PATH` contains only
the `file` plugin writing to a temporary raw-PCM file backed by the `null` device;
`PULSE_SERVER` and `PIPEWIRE_RUNTIME_DIR` point at non-existent private paths, so nothing
can reach the host's audio server or sound card. The harness re-asserts this configuration
before every launch and deletes it afterwards.

It automates (34 checks):

- first launch, schema `user_version = 2`, bell provisioning and asset hash;
- add, edit, single-active-timer autopause, manual complete, delete and restore;
- theme, always-on-top, all six languages, and geometry persistence across restarts;
- **captured audio** — with sound enabled, completing a timer must write non-silent
  samples to the private device (observed peaks ≈ 2500/32767); with sound disabled it must
  write no samples at all; a corrupt `bell.mp3` must not crash the app, must still archive
  the task and must ring through the bundled WAV fallback; deleting `bell.mp3` must
  re-provision it and produce samples again;
- decoding the packaged `bell.mp3` to PCM and requiring a non-silent peak (independent of
  playback).

Playback goes through JavaFX: WAV is decoded by the pure-Java platform, MP3 by the
GStreamer backend when the host supports it. On hosts whose FFmpeg is newer than the
bundled libav support (for example Fedora 44), MP3 decoding fails; `SoundService` detects
this synchronously and plays the bundled `assets/bell.wav`, which is why the captured
samples still prove that the completion sound generates audio.

What this does **not** prove: that a human hears it on real speakers or headphones, or
that the rendered UI looks correct. Those remain manual (sections 3 and 4).

### Schema v1 → v2 through the packaged startup path (all platforms)

`python3 tools/upgrade_check.py --image <packaged app>` builds a genuine v1 database from
`src/test/resources/schema-v1.sql` (the same fixture `DatabaseManagerTest` uses), fills it
with tasks, history and preferences, launches the packaged app with a disposable profile,
and verifies after a clean exit:

- `PRAGMA user_version = 2`, `legacy_sources` and the provenance columns/indexes exist;
- every task, history row, theme, always-on-top, language, sound and opacity preference is
  unchanged (geometry stays present and valid; the app may re-save it);
- pre-v2 rows keep `NULL` provenance and `legacy_sources` stays empty — origins are never
  invented;
- the application output contains no error lines.

### Data preservation across package removal (per platform)

`python3 tools/data_preservation_check.py prepare|verify --workdir <temp dir>` runs in two
phases with the packaging step in between:

| Platform | Automated sequence |
| --- | --- |
| Windows MSI | install MSI → `prepare` with isolated `%APPDATA%` → `msiexec /x` → assert launcher gone → `verify` |
| macOS DMG | mount DMG → copy `.app` to a disposable install directory → `prepare` with isolated `HOME` → delete the copy → `verify` |
| Linux AppImage | extract AppImage → `prepare` with temporary `XDG_DATA_HOME` → delete the `.AppImage` → `verify` |

`prepare` records SHA-256 hashes of `taskflow.db` and `bell.mp3` plus a sentinel task;
`verify` requires identical hashes, a passing `PRAGMA integrity_check`, the sentinel task,
and the persisted preferences. The check fails if the data directory moves inside the
bundle, if hashes change, or if any file disappears. Disposable directories only — no real
profile is read or modified.

## 2. What CI does not cover

- Windows/macOS upgrade from a v1 database through the packaged app: the check exists and
  runs on those platforms, but only Linux and the unit tests are exercised locally.
- Code signing and notarization: no certificates are configured, so packages are unsigned.
- Physical audio output through speakers or headphones.

## 3. Physical audio (human)

The application has **no in-app sound on/off control**; the persisted `sound_enabled`
preference is the only switch (the automated harness sets it directly).

- [ ] With sound enabled (default), let a task expire and confirm the bell is audible
      exactly once.
- [ ] Disable sound in a disposable profile:
      `sqlite3 "<data directory>/taskflow.db" "INSERT INTO preferences(pref_key, pref_value) VALUES('sound_enabled','false') ON CONFLICT(pref_key) DO UPDATE SET pref_value='false';"`
      restart, finish another task, and confirm silence.
- [ ] Replace `<data directory>/bell.mp3` with a different sound and confirm the completion
      uses it; with a corrupt file, confirm the app keeps working (the bundled WAV fallback
      may play).

## 4. Interactive GUI (human)

- [ ] Add, edit, complete, delete, and restore tasks; confirm the rendered dialogs and cards
      look correct (behavior is automated on Linux).
- [ ] Switch through all six languages and confirm open dialogs close, the live UI updates,
      and the choice survives a restart (persistence and switching are automated; rendered
      text is human).
- [ ] Toggle dark/light theme and confirm every open window updates without a restart.
- [ ] Toggle always-on-top, move/resize the window, adjust glass opacity, restart, and
      confirm the visual result (state and geometry are automated).

## 5. Signing and notarization

No signing/notarization credentials are configured, so packages are not signed. Verify
signatures only if you hold the platform certificates and tooling; never fabricate signing
evidence or commit secrets.
