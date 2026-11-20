// The shared size rule measures UTF-8, the same encoding used by the CLI.
const encoder = new TextEncoder();
function byteLength(text) {
  return encoder.encode(text).byteLength;
}
export { byteLength as "Buffer.byteLength" };
