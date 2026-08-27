/**
 * Phase 0 is one screen: the name, and a Start button that does nothing.
 * It exists to prove the install, the standalone launch and the design tokens
 * on the real device before any audio code is written.
 */
export default function App() {
  return (
    <main className="flex min-h-dvh flex-col bg-chassis px-6 pb-[env(safe-area-inset-bottom)]">
      {/* Upper two thirds: identity. */}
      <div className="flex flex-1 flex-col items-center justify-center gap-3">
        <h1 className="panel-label text-5xl font-semibold text-silk">
          Fretwork
        </h1>
        <p className="panel-label text-sm text-dim">Electric guitar · offline</p>
      </div>

      {/* Bottom third: the only control, within thumb reach. */}
      <div className="flex flex-col items-center gap-4 pb-10">
        <button
          type="button"
          className="min-h-touch w-full max-w-sm rounded-lg border border-edge bg-panel px-8 text-lg font-semibold text-lamp active:bg-edge panel-label"
        >
          Start
        </button>
        <p className="numeral text-xs text-dim">v0.0.0 · phase 0</p>
      </div>
    </main>
  );
}
