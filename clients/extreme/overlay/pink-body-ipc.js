// Temporary fixed-state observation of the existing protected HTTP body IPC.
// It neither changes transport nor examines arguments, bytes or error messages.
function mark(stage) {
  try {
    window.PinkNative?.postMessage(JSON.stringify({
      id: "0", operation: "bodyIpc", payload: {stage}
    }))
  } catch {}
}
export function observePinkBodyInvoke(invoke, command, args, options) {
  if (typeof window === "undefined" || window.location?.pathname !== "/livetv") {
    return invoke(command, args, options)
  }
  mark("calling")
  let pending
  try { pending = invoke(command, args, options) }
  catch (failure) { mark("threw"); throw failure }
  mark("returned")
  // Preserve the original promise and its exact value/error for the plugin.
  pending.then(() => mark("resolved"), () => mark("rejected"))
  return pending
}
