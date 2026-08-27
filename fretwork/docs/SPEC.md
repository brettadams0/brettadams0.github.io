# Fretwork — Build Specification

A free, offline-capable, microphone-listening guitar trainer. Built as a PWA, hosted on a free tier, no backend, no recurring cost.

**This document is the brief for Claude Code.** Hand it the whole file. It is written to be executed in phases, each independently deployable and testable.

---

## 0. Constraints that shape every decision

| Constraint | Consequence |
|---|---|
| Must cost $0 forever | No backend, no database, no paid APIs. All DSP runs client-side. Static hosting only. |
| Must access the microphone | Requires HTTPS. Rules out `file://` and plain-HTTP local serving. Free static hosts provide HTTPS. |
| Target device: Samsung S20 FE, Chrome/Android | Android voice-processing must be explicitly disabled or pitch detection fails. See §3.1. |
| Single developer, no QA, no ears in the loop | DSP must be unit-testable against synthesised signals, not by listening. See §10. |
| Cannot ship copyrighted tab or recordings | Song content is public domain, original, or user-imported. See §8. |

**What this app will do well:** tune, verify single notes with cents accuracy, tell you exactly which string in a chord is muted, time your chord changes automatically, score your rhythm against a click, and gate progression on measured skill.

**What it will not do well:** judge tone quality, detect a full six-string strum note-by-note, or hear a ghost strum (silence is silence). §9 covers how each of these is worked around rather than faked.

---

## 1. Stack

```
Vite + React 18 + TypeScript
Tailwind CSS
Zustand              state
Dexie                IndexedDB wrapper — all persistence is local
vite-plugin-pwa      manifest + service worker + offline
Vitest               DSP unit tests
```

No audio libraries. The DSP is ~400 lines written from scratch; every off-the-shelf pitch library either wraps the same algorithms or makes assumptions that break on a low E string.

**Hosting:** Vercel Hobby, Cloudflare Pages, or GitHub Pages. All free, all HTTPS. Vercel is already connected in the user's Claude project — `npx vercel deploy` from the repo root.

**Project layout**

```
src/
  audio/
    engine.ts           mic capture, AudioContext lifecycle, permission
    yin.ts              monophonic pitch detection
    chroma.ts           pitch-class profile + chord matching
    onset.ts            spectral-flux onset detection
    notes.ts            frequency ↔ MIDI ↔ note name ↔ cents
    worklet/
      capture.worklet.ts   ring buffer, posts frames to main thread
  engine/
    scheduler.ts        sample-accurate metronome + rhythm timeline
    scoring.ts          hit windows, streaks, stars
    progression.ts      XP, levels, curriculum stage gating
  content/
    chords.ts           chord shapes with per-string note data
    curriculum.ts       stages, exit checks, unlock rules
    riffs/              exercise riffs as JSON
    songs/              public-domain + original song data
  games/
    Tuner.tsx
    StringSniper.tsx
    FretTrainer.tsx
    ChordCheck.tsx
    ChangeRace.tsx
    GhostStrum.tsx
    RiffRunner.tsx
    PowerBlitz.tsx
  ui/                   shared components, design tokens
  store/                zustand slices
  db/                   Dexie schema + migrations
tests/
  fixtures/             synthesised + recorded WAVs
```

---

## 2. The audio pipeline

```
getUserMedia
   ↓  (raw, unprocessed — see §3.1)
MediaStreamAudioSourceNode
   ↓
AudioWorkletNode  ── ring buffer, 48 kHz mono
   ↓  postMessage every 512 samples (10.7 ms hop)
Main thread frame handler
   ↓
   ├─→ YIN          → f0, confidence          → tuner, single-note games
   ├─→ Chroma       → 12-bin profile          → chord recognition
   ├─→ Onset        → attack timestamps       → rhythm scoring
   └─→ RMS          → level meter, gate       → "is anything playing"
```

Frames are 2048 samples with a 512-sample hop, so windows overlap 75%. 2048 samples at 48 kHz is 42.7 ms — long enough to contain three full periods of an 82 Hz low E, which is the floor for reliable detection.

### 2.1 Why an AudioWorklet and not ScriptProcessor

`ScriptProcessorNode` is deprecated and runs on the main thread, so a React re-render stalls audio capture and the rhythm game desyncs. AudioWorklet runs on the audio thread at fixed 128-sample quanta. Buffer there, post to main thread in larger frames, do the maths on the main thread.

---

## 3. Pitch detection — the part that must be correct

### 3.1 Microphone constraints (do not skip this)

Android applies voice-call DSP by default: echo cancellation, noise suppression, and automatic gain control. All three destroy guitar signal. Noise suppression treats sustained harmonic tones as noise. AGC pumps the envelope, which breaks onset detection.

```ts
const stream = await navigator.mediaDevices.getUserMedia({
  audio: {
    echoCancellation: false,
    noiseSuppression: false,
    autoGainControl: false,
    channelCount: 1,
    sampleRate: 48000,
    latency: 0
  }
});
```

Verify after acquisition — Android sometimes ignores the request:

```ts
const settings = stream.getAudioTracks()[0].getSettings();
if (settings.echoCancellation || settings.noiseSuppression) {
  // surface a warning in the UI; detection will be degraded but usable
}
```

If a track reports processing still enabled, show a one-line banner rather than failing. Do not silently proceed.

### 3.2 YIN

Naive FFT peak-picking and raw autocorrelation both fail on guitar in the same way: on a plucked string the second harmonic is frequently louder than the fundamental, so the detector reports one octave too high. On the low E this happens constantly. YIN's cumulative mean normalised difference function exists specifically to suppress that error, which is why it is worth implementing by hand.

```ts
export function yin(
  buf: Float32Array,
  sampleRate: number,
  threshold = 0.12
): { frequency: number; confidence: number } | null {

  const W = Math.floor(buf.length / 2);
  const diff = new Float32Array(W);

  // Step 1 — difference function
  for (let tau = 1; tau < W; tau++) {
    let sum = 0;
    for (let i = 0; i < W; i++) {
      const d = buf[i] - buf[i + tau];
      sum += d * d;
    }
    diff[tau] = sum;
  }

  // Step 2 — cumulative mean normalised difference
  const cmnd = new Float32Array(W);
  cmnd[0] = 1;
  let running = 0;
  for (let tau = 1; tau < W; tau++) {
    running += diff[tau];
    cmnd[tau] = running === 0 ? 1 : diff[tau] * tau / running;
  }

  // Step 3 — absolute threshold, first local minimum below it
  let tau = -1;
  for (let t = 2; t < W - 1; t++) {
    if (cmnd[t] < threshold) {
      while (t + 1 < W && cmnd[t + 1] < cmnd[t]) t++;
      tau = t;
      break;
    }
  }
  if (tau === -1) return null;

  // Step 4 — parabolic interpolation for sub-sample precision
  const x0 = tau > 1 ? tau - 1 : tau;
  const x2 = tau + 1 < W ? tau + 1 : tau;
  let betterTau = tau;
  if (x0 !== tau && x2 !== tau) {
    const s0 = cmnd[x0], s1 = cmnd[tau], s2 = cmnd[x2];
    const denom = 2 * (2 * s1 - s2 - s0);
    if (denom !== 0) betterTau = tau + (s2 - s0) / denom;
  }

  return {
    frequency: sampleRate / betterTau,
    confidence: 1 - cmnd[tau]
  };
}
```

**Tuning parameters.** Threshold 0.12 is a good default. Lower is stricter (fewer false positives, more dropouts). Reject any result with `confidence < 0.85` or `frequency` outside 70–1400 Hz — that band covers open low E down to a semitone flat, up to fret 24 on the high E.

**Pre-processing before YIN:**
1. Gate on RMS — if `rms < 0.008`, return null. Stops the tuner chasing room noise.
2. High-pass at 65 Hz (single-pole biquad) to kill handling rumble and mains hum.
3. Do **not** window the buffer. YIN operates on the raw time-domain signal; a Hann window biases the difference function.

**Stability filter.** Raw frame-by-frame output jitters. Hold a 5-frame median of the last frequencies and only report a note as "settled" when 4 of the last 5 frames agree within 30 cents. This is what makes the tuner needle feel solid instead of nervous.

### 3.3 Note maths

```ts
export const midiFromFreq = (f: number) => 69 + 12 * Math.log2(f / 440);
export const freqFromMidi = (m: number) => 440 * Math.pow(2, (m - 69) / 12);
export const centsOff = (f: number, target: number) => 1200 * Math.log2(f / target);

const NAMES = ['C','C#','D','D#','E','F','F#','G','G#','A','A#','B'];
export function noteName(f: number) {
  const m = Math.round(midiFromFreq(f));
  return { name: NAMES[m % 12], octave: Math.floor(m / 12) - 1, midi: m };
}
```

Standard tuning reference frequencies:

| String | Note | Hz |
|---|---|---|
| 6 | E2 | 82.41 |
| 5 | A2 | 110.00 |
| 4 | D3 | 146.83 |
| 3 | G3 | 196.00 |
| 2 | B3 | 246.94 |
| 1 | E4 | 329.63 |

"In tune" = within ±5 cents. "Close" = ±15 cents. Beyond that, sharp or flat.

---

## 4. Chord verification — the flagship feature

### 4.1 Arpeggio mode (primary, reliable)

This is the mode that fixes the user's actual problem. It mirrors the Stage 2 chord-check drill exactly.

**Flow**
1. App displays the chord diagram and highlights string 6.
2. User forms the chord and picks string 6 only.
3. Onset detector fires; the app runs YIN on the following 3 frames.
4. Compare detected pitch to the expected note for that string in that shape.
5. Classify: `correct` / `wrong pitch` / `dead` (onset detected but no stable pitch — the signature of a muted string) / `buzz` (pitch present but confidence low and RMS decays in under 200 ms).
6. Advance to string 5. Repeat through string 1.
7. Show a six-row result strip with a specific diagnosis per string.

Because only one string sounds at a time, this is monophonic detection — the reliable case. Accuracy here should be near-perfect on a quiet room.

**Diagnosis text is where the teaching happens.** Map each failure to a physical cause drawn from the curriculum's known failure modes:

```ts
const DIAGNOSIS = {
  'dead:1': 'String 1 is muted. The finger on string 2 is lying flat. Arch it and press with the very tip.',
  'dead:4': 'String 4 is muted. Check nothing from a higher finger is leaning on it.',
  'buzz:any': 'Buzzing means the finger is too far behind the fret, or not pressing hard enough. Slide it forward until it almost touches the fret wire.',
  'allDead':  'Everything is muted. Your thumb has crept over the top of the neck — bring it back down behind the middle.'
};
```

### 4.2 Strum mode (secondary, confidence-scored)

For gameplay speed, a full strum gets checked with a chroma profile. This answers "was that roughly an Em?" — not "which string is dead."

```ts
export function chroma(spectrum: Float32Array, sampleRate: number, fftSize: number) {
  const c = new Float32Array(12);
  for (let k = 1; k < spectrum.length; k++) {
    const freq = k * sampleRate / fftSize;
    if (freq < 75 || freq > 2200) continue;
    const midi = midiFromFreq(freq);
    const pc = ((Math.round(midi) % 12) + 12) % 12;
    c[pc] += spectrum[k] * spectrum[k];   // power, not magnitude
  }
  // L2 normalise
  const norm = Math.hypot(...c) || 1;
  for (let i = 0; i < 12; i++) c[i] /= norm;
  return c;
}
```

Match by cosine similarity against binary chord templates (Em = {E, G, B} → indices 4, 7, 11). Accept above 0.80 similarity. Below that, tell the user it did not recognise the chord rather than guessing wrong — a confidently wrong answer destroys trust faster than an honest miss.

**Harmonic bleed is the known weakness.** A ringing low E puts energy on E's octaves and its fifth, which biases the profile toward major. Mitigate with a simple harmonic-weighted subtraction: after finding the strongest pitch class, subtract 0.4× its energy from its perfect fifth bin before re-normalising. This is a heuristic, not a solution. Strum mode stays advisory; arpeggio mode is the source of truth for diagnosis.

### 4.3 Chord data model

Every chord carries per-string data so the arpeggio checker knows exactly what to expect.

```ts
type ChordShape = {
  id: string;
  name: string;
  strings: Array<{
    string: 1|2|3|4|5|6;
    fret: number | 'x';        // 'x' = don't play
    finger: 0|1|2|3|4;         // 0 = open
    midi: number;              // expected pitch
  }>;
  stage: number;               // curriculum stage that unlocks it
  difficulty: 1|2|3|4|5;
};
```

Ship Stage 2's set first: Em, Am, D, E, A, G, C. Then Dm, Cadd9, Em7, Asus2. Then barre shapes and power chords, which are generated procedurally from a root note and a shape template rather than hand-authored.

---

## 5. Rhythm engine

### 5.1 Scheduling

Never use `setInterval` for musical timing — it drifts and stalls under load. Use the lookahead scheduler pattern against `AudioContext.currentTime`, which is sample-accurate.

```ts
const LOOKAHEAD_MS = 25;
const SCHEDULE_AHEAD_S = 0.15;

function tick() {
  while (nextNoteTime < ctx.currentTime + SCHEDULE_AHEAD_S) {
    scheduleClick(nextNoteTime);
    visualQueue.push({ beat, time: nextNoteTime });
    nextNoteTime += (60 / bpm) / subdivision;
    beat++;
  }
}
setInterval(tick, LOOKAHEAD_MS);
```

Visuals sync separately in `requestAnimationFrame`, popping from `visualQueue` when `ctx.currentTime` passes each entry's timestamp. Audio and video are scheduled independently against the same clock — never derive one from the other.

### 5.2 Onset detection

```ts
// spectral flux, half-wave rectified
export function flux(cur: Float32Array, prev: Float32Array) {
  let sum = 0;
  for (let k = 0; k < cur.length; k++) {
    const d = cur[k] - prev[k];
    if (d > 0) sum += d;
  }
  return sum;
}
```

Peak-pick with an adaptive threshold: an onset is `flux[n] > median(last 24 frames) * 1.6 + 0.01`, with a 60 ms refractory period so one pick doesn't register twice.

### 5.3 Latency calibration

Mic capture, OS buffering, and display all add delay. Without calibration, every hit reads as late and the user gets told they have bad timing when they don't.

**Calibration flow:** play 8 clicks. Ask the user to pick a muted string exactly on each one. Measure the mean offset between detected onsets and scheduled click times, discard outliers beyond 1.5 SD, store the remainder as `inputLatencyMs`. Subtract it from every subsequent timestamp. Re-run from settings; prompt automatically if median offset drifts past 40 ms across a session.

Expect 60–150 ms on Android. This is normal and correctable.

### 5.4 Hit windows

| Rating | Window | Score |
|---|---|---|
| Perfect | ±30 ms | 100 |
| Good | ±60 ms | 70 |
| Late / Early | ±110 ms | 35 |
| Miss | beyond | 0, breaks streak |

Multiplier: ×1 at 0 streak, ×2 at 10, ×3 at 25, ×4 at 50. Cap at ×4 so a bad bar isn't unrecoverable.

---

## 6. Game modes

Each mode is a self-contained React component consuming the shared audio engine. All eight map onto a specific curriculum exit check.

### 6.1 Tuner (utility)
Big needle, cents readout, auto-detects nearest string. Green at ±5 cents, holds green for 1.5 s before marking that string done. Six dots across the top fill in as each string passes. "All six in under 2:00" is the Stage 0 exit check — time it and record the result.

### 6.2 String Sniper — Stage 0
App names a string ("play string 4"), user picks it, YIN verifies. 20 rounds, scored on accuracy and reaction time. Alternates between naming by number and by letter. Failure state is picking the wrong string, which is exactly the confusion this drill kills.

### 6.3 Fret Trainer — Stage 1
Shows a single position on a fretboard diagram. User plays it. Verifies pitch and — this is the point — verifies *cleanness*: a buzzed note produces low YIN confidence and a fast RMS decay. Flag it as buzz, not as wrong.

Includes the chromatic 1-2-3-4 run as a timed exercise. Metronome at 60 bpm, all 24 notes, every note must clear a confidence floor. That is the Stage 1 exit check, measured rather than self-reported.

### 6.4 Chord Check — Stage 2 (the flagship)
The arpeggio verifier from §4.1. Six-row diagnosis strip with a specific physical fix per dead string. Run any chord in the library. Log results so the app knows which chords are consistently failing and can weight practice toward them.

### 6.5 Change Race — Stage 2
The one-minute change drill, counted automatically. Two chords, 60 seconds. Detects each change via chroma transition and only counts it if the new chord clears the confidence floor — so a fumbled change genuinely doesn't count, which tapping a button can't enforce. Records personal best per pair. Target 20/min.

### 6.6 Ghost Strum — Stage 3 (the strumming fix)
Eight lamps for `1 & 2 & 3 & 4 &`. A swinging arm indicator shows continuous down-up motion. Hits are scored by onset timing against the grid.

**Honest limitation:** a ghost strum makes no sound, so it cannot be detected directly. What the app *can* detect is the consequence — when the hand stops, the strums following a chord change land late. So the scoring watches for exactly that: if mean timing error on the two hits following a chord change exceeds the error on the rest of the bar by more than 40 ms, the app reports "your hand stopped during the change" and shows the timing drift on a graph. Diagnosing the symptom is honest and is still the correct coaching.

Progressive patterns from `curriculum.md` Stage 3. Two-minute unbroken hold is the exit check.

### 6.7 Power Blitz — Stage 4
Call and response. App names a power chord (G5, C5, F#5), user has 2 seconds. Verifies root pitch via YIN on the lowest sounding note plus chroma confirmation of the fifth. Speed-scored, escalating. Builds note-name knowledge along strings 6 and 5 as a side effect.

### 6.8 Riff Runner — Stages 4–6
Scrolling note highway. Six lanes for six strings, fret numbers as blocks travelling toward a strike line. Monophonic riffs only — verified note by note with YIN, which is exactly the reliable case.

Speed trainer: any riff can be looped at 60–100% tempo with automatic 5 bpm increments once a pass clears 90% accuracy.

---

## 7. Progression

### 7.1 Structure

Mirror `curriculum.md`. Seven stages, each with concrete exit checks. **Exit checks are measured by the app, not self-reported.** A stage unlocks when every check passes.

```ts
type ExitCheck = {
  id: string;
  label: string;
  test: (stats: Stats) => boolean;
  progress: (stats: Stats) => number;   // 0..1 for the progress ring
};

const STAGE_2_CHECKS: ExitCheck[] = [
  {
    id: 'em-am-20',
    label: '20 clean Em↔Am changes in a minute',
    test: s => (s.bestChanges['Am/Em'] ?? 0) >= 20,
    progress: s => Math.min(1, (s.bestChanges['Am/Em'] ?? 0) / 20)
  },
  {
    id: 'six-chords-clean',
    label: 'All six chords, every string ringing',
    test: s => STAGE2_CHORDS.every(c => s.chordCleanRuns[c] >= 3),
    progress: s => STAGE2_CHORDS.filter(c => s.chordCleanRuns[c] >= 3).length / 6
  }
];
```

The user can force-unlock a stage, but the app records that they did and keeps showing the unmet check. Skipping is their call; hiding it isn't.

### 7.2 Rewards that aren't hollow

XP for practice minutes and clean reps. Levels every 1000 XP. But the meaningful rewards are unlocks: new chords, new riffs, new patterns, new game modes. Cosmetic-only rewards get stale within a week.

Daily streak with **one freeze token earned per seven-day streak** — an unforgiving streak makes people quit after a missed day, which is the opposite of the goal.

### 7.3 Benchmark history

Persist and chart over time:
- Em↔Am changes per minute
- Chromatic 1-2-3-4 top clean tempo
- Longest unbroken strum pattern
- Per-chord clean rate (%)
- Mean timing error (ms)

This is the plateau antidote. When it doesn't *feel* like improvement, the graph shows it.

---

## 8. Content and copyright

**This will bite you if ignored.** Tab for commercial songs is licensed material; so are recordings. Do not scrape Ultimate Guitar and do not bundle backing tracks.

Ship instead:
1. **Public domain melodies** — folk, traditional, pre-1930 material. Plenty of two-chord songs for Stage 2.
2. **Original exercise riffs** — write them into the app. They can be idiomatic to a genre without copying a specific song.
3. **Chord progressions** — a progression is not copyrightable. "I–V–vi–IV at 120 bpm" is fine to ship; it just can't be labelled with the song's name.
4. **Generated backing tracks** — synthesise drums and bass in Web Audio from progression data. No licensing, tiny bundle, tempo-adjustable for free.
5. **A tab importer** — accept pasted plain-text tab or a simple JSON format, parse it into the Riff Runner. Users supply their own material, legally, and the library becomes unbounded without you shipping anything.

The importer is the single highest-leverage content feature. Build it in Phase 6.

**Song data format:**

```ts
type Song = {
  id: string;
  title: string;
  source: 'public-domain' | 'original' | 'user-import';
  bpm: number;
  timeSignature: [number, number];
  sections: Array<{
    name: string;
    bars: Array<{
      chord?: string;
      pattern?: string;             // strum pattern id
      notes?: Array<{ string: number; fret: number; beat: number }>;
    }>;
  }>;
  stage: number;
};
```

---

## 9. Design direction

The app should feel like gear, not like a SaaS dashboard. The reference is an amplifier faceplate: dark chassis, cream silkscreen, one pilot lamp.

**Tokens**

```css
--chassis:  #1B211F;   /* dark slate-green, not black */
--panel:    #262E2B;
--panel-hi: #313B37;
--silk:     #EAE4D2;   /* cream silkscreen */
--dim:      #8E9891;
--lamp:     #F0A868;   /* amber pilot lamp — active state, accents */
--hit:      #7FD1B9;   /* seafoam — correct */
--miss:     #C4614A;   /* burnt coral — wrong */
--edge:     #3A4441;
```

**Type**
- Panel labels: Barlow Condensed, uppercase, wide tracking. Amp panels are silkscreened in caps — this is the subject's own vernacular, not decoration.
- Readouts and numerals: IBM Plex Mono, tabular figures. Every BPM, cents value, and score.
- Body: system sans.

**Signature element:** the fretboard renderer. One component drives chord diagrams, the fret trainer, and the Riff Runner highway — strings drawn at true relative gauge, fret markers as inlay dots, and the note highway rendered in perspective as those same six strings receding toward the horizon. The instrument *is* the interface. Everything else stays quiet.

**Motion:** feedback flashes only. A hit pulses the lamp, a miss shakes 4 px. No page transitions, no decorative animation. Respect `prefers-reduced-motion` — reduce to colour-only feedback.

**Layout for a phone in a guitar stand:** primary controls in the bottom third, minimum 56 px touch targets, high contrast, and never require two hands. Request a screen wake lock during any active game:

```ts
const lock = await navigator.wakeLock?.request('screen');
```
Release on unmount, re-acquire on `visibilitychange`.

---

## 10. Testing without ears

Neither you nor Claude Code can hear the output. The DSP must therefore be verifiable by assertion.

### 10.1 Synthesised fixtures

Generate test signals in code — a plucked string is well modelled as a harmonic series with exponential decay:

```ts
function synthString(f0: number, sr = 48000, dur = 0.5, harmonics = 8) {
  const n = Math.floor(sr * dur);
  const out = new Float32Array(n);
  for (let h = 1; h <= harmonics; h++) {
    const amp = 1 / (h * h);              // realistic rolloff
    for (let i = 0; i < n; i++) {
      out[i] += amp * Math.sin(2 * Math.PI * f0 * h * i / sr) * Math.exp(-3 * i / sr);
    }
  }
  return out;
}
```

**Required assertions:**
- YIN returns each open-string frequency within ±2 cents.
- YIN does **not** octave-error when harmonic 2 is boosted above harmonic 1 — build that fixture explicitly and assert against it. This is the single most important test in the suite.
- Detection holds with white noise added at 20 dB SNR.
- Chroma correctly ranks Em above Am, C, and G for a synthesised Em spectrum.
- Onset detector finds exactly 8 onsets in a signal with 8 known attacks, within ±10 ms each.
- Scheduler drift is under 1 ms over 300 scheduled beats.

### 10.2 Recorded fixtures

Record short WAVs on the actual phone — one per open string, one per chord, one muted-string case. Commit them to `tests/fixtures/`. These catch real-world problems synthesised signals never will: room noise, phone mic rolloff below 100 Hz, and Android's processing if it sneaks back on.

### 10.3 Live debug overlay

A hidden panel (long-press the version number) showing live f0, confidence, RMS, chroma bars, and detected onsets. This is how you diagnose "why won't it hear my low E" without guessing.

---

## 11. Build phases

Each phase ends deployable and usable. Do not proceed until the previous one works on the actual phone.

| Phase | Deliverable | Proves |
|---|---|---|
| **0** | Vite + TS + Tailwind + PWA scaffold, deployed, installable | The pipeline works end to end |
| **1** | Mic permission, AudioWorklet capture, level meter, **Tuner** | The whole audio chain works on Android |
| **2** | YIN + note maths + full test suite. String Sniper, Fret Trainer | Monophonic detection is trustworthy |
| **3** | **Chord Check** arpeggio verifier + chord library + diagnosis text | The flagship feature |
| **4** | Scheduler, onset detection, latency calibration, Ghost Strum | Rhythm engine |
| **5** | Chroma matching, Change Race, Power Blitz | Chord-level recognition |
| **6** | Riff Runner, tab importer, song data, generated backing tracks | Content pipeline |
| **7** | Progression, XP, streaks, benchmark charts, curriculum gating | Retention |
| **8** | Offline, accessibility, error states, polish | Ship quality |

**If you build only Phases 0–3, you already have the thing that matters** — a working tuner and a chord checker that tells you which string is dead. That is roughly a weekend of Claude Code sessions and it beats everything in the practice kit. Phases 4–8 are the game.

---

## 12. Known limitations — state these in the app, don't hide them

1. **No polyphonic transcription.** Full strums are matched by profile, not transcribed. Arpeggio mode is the accurate one, and the UI should say so where it matters.
2. **Ghost strums are inaudible.** Scored by their effect on subsequent timing, not directly.
3. **No tone judgement.** The app cannot tell you your bends are ugly or your vibrato is stiff. That stays with the photo loop and with your own ears.
4. **Noisy rooms degrade detection.** Show the input level meter prominently; if the noise floor is above the gate, say so instead of failing silently.
5. **Latency varies by device and session.** Recalibration is one tap and should be easy to find.
6. **A clip-on tuner is still better than the app tuner** in a loud room, because it reads vibration rather than air. Say so in the tuner screen. Trust is worth more than a feature claim.

---

## 13. Handing this to Claude Code

```bash
mkdir fretwork && cd fretwork
git init
# put this file at docs/SPEC.md
claude
```

Then:

> Read docs/SPEC.md in full. Build Phase 0 only, exactly as specified. Stop when it deploys and I confirm it installs on my phone.

Work one phase per session. At the start of each, tell it to re-read the spec and the previous phase's output. Commit at every phase boundary so a bad session is one `git reset` away.

For Phase 2 onward, insist tests are written **before** implementation — the DSP assertions in §10.1 are the specification of correct behaviour, and they are the only thing standing between you and audio code that silently reports the wrong octave.

---

## Appendix — open decisions for the builder

- **Song library seed.** Section 8 needs a starting genre. Pick the bands you actually want to play and choose public-domain and original material that sits in the same idiom — the exercise riffs in Phase 6 should sound like the music you're aiming at, or the payoff never lands.
- **Left/right handed:** spec assumes right-handed. Mirroring the fretboard renderer is a single transform; add it as a setting in Phase 0 if needed.
- **Tuning support:** standard E only in v1. Drop D and half-step down are just different reference frequency tables — add in Phase 5.
