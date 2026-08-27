# Fretwork

A free, offline PWA that listens to an electric guitar and teaches a complete
beginner. No backend, no paid services, all DSP client-side. Full spec:
docs/SPEC.md — read the section named here rather than re-reading the whole file.

## Stack
Vite · React 18 · TypeScript · Tailwind · Zustand · Dexie · vite-plugin-pwa ·
Vitest. No audio libraries — the DSP is written by hand (§1).

## Layout (§1 — do not reorganise)
```
src/audio/    engine · yin · chroma · onset · notes · worklet/capture.worklet
src/engine/   scheduler · scoring · progression
src/content/  chords · curriculum · riffs/ · songs/
src/games/    one component per mode (§6)
src/ui/       shared components + the fretboard renderer
src/store/    zustand slices        src/db/  Dexie schema
tests/fixtures/
```

## Rules that are expensive to get wrong

Microphone (§3.1)
- getUserMedia MUST set echoCancellation, noiseSuppression and autoGainControl
  to false. Android's voice processing eats sustained guitar notes.
- Verify with getSettings() after acquiring the track. If processing is still
  on, show a one-line banner — degraded but usable. Never fail silently, never
  silently proceed.

Capture (§2)
- AudioWorklet only. Never ScriptProcessorNode — it runs on the main thread, so
  a React re-render stalls capture and the rhythm game desyncs.
- 48 kHz mono, 2048-sample frames on a 512-sample hop (75% overlap).

Pitch (§3.2)
- YIN with parabolic interpolation. Never FFT peak-picking or raw
  autocorrelation — on a plucked string the 2nd harmonic often exceeds the
  fundamental, so both octave-error constantly on the low E.
- threshold 0.12. Reject confidence < 0.85, or f0 outside 70–1400 Hz.
- Before YIN: gate on RMS < 0.008, high-pass at 65 Hz, and do **not** window —
  a Hann window biases the difference function.
- Report "settled" only when 4 of the last 5 frames agree within 30 cents.
  In tune = ±5 cents; close = ±15.

Chords (§4)
- Arpeggio mode is monophonic and is the only source of truth for diagnosis.
- Strum mode uses chroma, accepts cosine similarity ≥ 0.80, and is advisory.
  Below that, say it did not recognise the chord. Never present a chroma guess
  as a diagnosis — a confidently wrong answer destroys trust faster than a miss.

Timing (§5)
- Schedule against AudioContext.currentTime with a 25 ms lookahead loop and a
  0.15 s horizon. Never setInterval or setTimeout for note timing.
- Visuals pop from a queue in requestAnimationFrame against the same clock.
  Never derive audio from video or video from audio.
- Onset: flux > median(last 24) × 1.6 + 0.01, 60 ms refractory.
- Latency calibration is mandatory before scoring, or every hit reads late and
  the user is told they have bad timing when they don't. Expect 60–150 ms on
  Android. Hit windows: ±30 ms perfect, ±60 good, ±110 late/early.

Content (§8)
- Never ship copyrighted tab, lyrics, or recordings. Public domain, original,
  or user-imported only. A chord progression is not copyrightable; a song title
  attached to it is a licensing problem.

Honesty (§12)
- State the known limitations in the app. No polyphonic transcription, ghost
  strums are inferred from their effect on later timing, no tone judgement, a
  clip-on tuner beats this in a loud room. Say so where it matters.

## Working agreement (§11, §13)
- One phase per session. Never start the next phase unprompted.
- Phase 2 onward: write the §10.1 assertions **before** the implementation.
  They are the specification of correct behaviour and the only thing standing
  between us and audio code that silently reports the wrong octave.
- No new dependencies without asking. No refactoring outside the phase.
- The user tests on a Samsung S20 FE in Chrome. Neither of us can hear the
  output — behaviour must be verifiable by assertion or by a number on screen.
- Commit at every phase boundary.

Phases: 0 scaffold · 1 mic + worklet + tuner · 2 YIN + notes + tests, Sniper,
Fret Trainer · 3 Chord Check · 4 scheduler + onset + latency + Ghost Strum ·
5 chroma, Change Race, Power Blitz · 6 Riff Runner + importer + songs ·
7 progression · 8 polish.

## Design (§9)
Amplifier faceplate, not a dashboard. Tokens live in src/styles/theme.css as
Tailwind theme tokens; a hex code in a component is a bug:
chassis #1B211F, panel #262E2B, panel-hi #313B37, silk #EAE4D2, dim #8E9891,
lamp #F0A868, hit #7FD1B9, miss #C4614A, edge #3A4441.
Barlow Condensed uppercase for panel labels, IBM Plex Mono for all numerals,
system sans for body. Touch targets 56px minimum, primary controls in the
bottom third, never require two hands.
The fretboard renderer is the signature element and drives chord diagrams, the
fret trainer and the Riff Runner highway. Everything else stays quiet.
Feedback flashes only — no page transitions, no decorative motion. Under
prefers-reduced-motion, reduce to colour-only. Hold a screen wake lock during
any active game; release on unmount, re-acquire on visibilitychange.

## Tuned constants
Record any value discovered by testing on the real device here, with the reason.
- (empty)
