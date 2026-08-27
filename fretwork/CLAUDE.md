# Fretwork

A free, offline PWA that listens to an electric guitar and teaches a complete
beginner. No backend, no paid services, all DSP client-side. Full spec:
docs/SPEC.md.

## Stack
Vite · React 18 · TypeScript · Tailwind · Zustand · Dexie · vite-plugin-pwa ·
Vitest. No audio libraries — the DSP is written by hand.

## Rules that are expensive to get wrong

- getUserMedia MUST set echoCancellation, noiseSuppression and autoGainControl
  to false. Android's voice processing eats sustained guitar notes. Verify with
  getSettings() after acquiring the track; warn in the UI if ignored.
- Audio capture uses AudioWorklet. Never ScriptProcessorNode.
- Musical timing schedules against AudioContext.currentTime with a lookahead
  loop. Never setInterval or setTimeout for note timing.
- Pitch detection is YIN with parabolic interpolation. Never FFT peak-picking —
  it octave-errors on the low E, where the 2nd harmonic exceeds the fundamental.
- Chord diagnosis uses arpeggio mode (monophonic, reliable). Strum mode uses
  chroma matching and is advisory only. Never present a chroma guess as a
  diagnosis.
- Never ship copyrighted tab, lyrics, or recordings. Public domain, original,
  or user-imported only.
- AudioContext must be created or resumed inside a user gesture.

## Working agreement
- One phase per session (§11). Never start the next phase unprompted.
- Audio code: tests from §10.1 before implementation.
- No new dependencies without asking.
- No refactoring outside the current phase's scope.
- The user tests on a Samsung S20 FE in Chrome. Neither of us can hear the
  output — behaviour must be verifiable by assertion or by a number on screen.
- Commit at every phase boundary.

## Design
Amplifier faceplate, not a dashboard. Tokens live in the Tailwind theme:
chassis #1B211F, panel #262E2B, silk #EAE4D2, dim #8E9891, lamp #F0A868,
hit #7FD1B9, miss #C4614A, edge #3A4441.
Barlow Condensed uppercase for panel labels, IBM Plex Mono for all numerals,
system sans for body. Touch targets 56px minimum, primary controls in the
bottom third. Feedback flashes only — no decorative motion.

## Tuned constants
Record any value discovered by testing on the real device here, with the reason.
- (empty)
