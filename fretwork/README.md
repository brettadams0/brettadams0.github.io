# Fretwork

A free, offline PWA that listens to an electric guitar and teaches a complete
beginner. No backend, no accounts, all DSP client-side.

The full build specification is [docs/SPEC.md](docs/SPEC.md). [CLAUDE.md](CLAUDE.md)
distils it into the rules that are expensive to get wrong, and is what each
working session reads.

```sh
npm install
npm run dev      # dev server
npm run build    # typecheck + production build into dist/
npm run preview  # serve the production build
npm run icons    # regenerate the launcher icons from the §9 palette
```

Phase 0 is scaffolding only: the shell installs to an Android home screen and
launches standalone, and the Start button is deliberately inert. No audio code
exists yet.
