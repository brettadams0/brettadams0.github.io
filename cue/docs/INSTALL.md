# Installing Cue

Two halves, installed separately. Neither is in a store, which is deliberate:
§3.1 keeps every dependency free, and store listings cost $25 (Play) and $5
(Chrome Web Store) respectively. Sideloading also sidesteps the fact that §15's
M8 would disqualify a Play listing outright.

**Nothing here has been run on a real device yet.** See the [status
table](../README.md#status) before following these steps expecting them to work.

---

## Android

### Build it

You need a JDK 17 or newer and an Android SDK with API 35.

```sh
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew :app:assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

Without an SDK the build still works for everything that matters:

```sh
./gradlew test                          # 132 tests, JVM only
./gradlew verifyArchitecturalInvariants # §2.1 and §2.3
```

`settings.gradle.kts` detects the missing SDK and configures only the pure-JVM
modules, so a machine that has never installed Android tooling can still run the
suite that decides draft quality.

### The model file

The app has no `INTERNET` permission, so it cannot download one. That is the
point (§2.3) and it makes acquiring the file a separate, one-time act.

Pick a tier by free RAM — the app measures this itself, but you have to supply
the matching file:

| Free RAM | Model | Size |
|---|---|---|
| ≥ 6 GB | `gemma-3n-E4B-it-int4` | ~3.6 GB |
| 3–6 GB | `gemma-3n-E2B-it-int4` | ~2.6 GB |
| < 3 GB | `gemma-3-1b-it-int4` | ~529 MB |

Download from the HuggingFace `litert-community` organisation on a computer, then
push it into the app's own storage:

```sh
adb push gemma-3n-E2B-it-int4.task \
  /sdcard/Android/data/dev.cue.app/files/models/
```

Settings → **Model** shows the exact directory it looks in, and reports which tier
loaded. With no file it says so and uses the template opener path (§6.5), which is
a supported state rather than a broken one.

### First run

1. **Settings → Add screenshots.** Screenshot 15–20 of your own past
   conversations and pick them. Cue reads only text, on-device, and discards the
   images immediately.
2. **Confirm which column is you.** This is the highest-stakes step in the app
   (§4.2): a profile built from her messages produces drafts that sound like the
   person you are talking to, which is subtly wrong and very hard to diagnose. Both
   columns are shown side by side with a swap control. Nothing is stored until you
   confirm.
3. **Fifty messages.** Below that the profile is not trusted and a calibrating
   banner stays up — three messages ending in "lol" would otherwise become a law.
4. **Use it.** Screenshot a conversation → share → Cue. Three drafts, ten seconds,
   copy the one you want and paste it yourself.

### If something goes wrong

| Symptom | What it means |
|---|---|
| "No text was recognised" | OCR found nothing. Retake without the keyboard open (§13) |
| Drafts read like somebody else | The columns were probably swapped at onboarding. Redo it |
| A variant says "held back" | §7.2 caught it inventing something twice. A missing option is better than a hallucinated one |
| "OFF-VOICE" badge | §7.1 gave up after two retries. The draft is still editable |
| Warm phone, slow drafts | §12's twenty-per-hour budget. The template option costs nothing |
| Model says "not installed" | Push the `.task` file to the directory Settings names |

---

## Chrome (Tinder web only)

Hinge has no desktop version, so there is nothing to cover there (§3.4).

See [`extension/README.md`](../extension/README.md) — load unpacked, import your
voice profile as JSON, and optionally vendor WebLLM. Without WebLLM the panel
reads the conversation and explains why it cannot draft, which is §13's specified
behaviour for a browser without WebGPU.
