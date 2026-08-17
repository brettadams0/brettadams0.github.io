// §3.2. WebLLM (MLC) via WebGPU, in the side panel.
//
// The library is **not vendored in this repository**, and that is a deliberate
// consequence of §3.1: WebLLM's normal install path is a CDN script tag, the
// extension's CSP forbids remote script, and a build step to bundle it would add
// a toolchain to a project whose dependency list is meant to stay "free, offline,
// and unlimited". So it is a manual, one-time drop — the same shape as the model
// file on Android (§3.3) — documented in the extension README.
//
// Until the file is there, `createEngine` returns null and everything degrades to
// the no-model path, which §13 already specifies:
//
// > WebGPU unavailable in browser → extension degrades to template-only path.

const VENDOR_PATH = '../../vendor/web-llm.js';

/** The 1B–2B class model that fits a browser tab's memory budget. */
const MODEL_ID = 'gemma-2-2b-it-q4f16_1-MLC';

export async function webGpuAvailable() {
  if (!('gpu' in navigator)) return false;
  try {
    const adapter = await navigator.gpu.requestAdapter();
    return adapter !== null;
  } catch {
    return false;
  }
}

/**
 * Returns `{ generate, modelId }`, or null with a reason on
 * `createEngine.lastReason` when inference is not available.
 */
export async function createEngine(onProgress) {
  createEngine.lastReason = null;

  if (!(await webGpuAvailable())) {
    createEngine.lastReason =
      'This browser has no WebGPU adapter, so there is nowhere to run a model.';
    return null;
  }

  let webllm;
  try {
    webllm = await import(VENDOR_PATH);
  } catch {
    createEngine.lastReason =
      'web-llm.js is not in vendor/. See extension/README.md — one download, then Cue is offline forever.';
    return null;
  }

  const engine = await webllm.CreateMLCEngine(MODEL_ID, {
    initProgressCallback: (report) => onProgress?.(report.text || ''),
  });

  return {
    modelId: MODEL_ID,
    /**
     * One completion. Seeded per variant so §6.1's "different seeds" is true
     * rather than three calls to the same sampler.
     */
    async generate(prompt, { seed, temperature, maxTokens }) {
      const reply = await engine.chat.completions.create({
        messages: [{ role: 'user', content: prompt }],
        temperature,
        seed,
        max_tokens: maxTokens,
        stream: false,
      });
      return reply.choices?.[0]?.message?.content?.trim() || '';
    },
  };
}
