// Temporary observer at one pinned HTTP command, without a core API alias.
import { fileURLToPath } from "node:url"

export function observeHttpBodySource(code, id) {
  if (!/[\\/]@tauri-apps[\\/]plugin-http[\\/]dist-js[\\/]index\.js(?:\?.*)?$/.test(id)) return null
  const before = "data = await invoke('plugin:http|fetch_read_body', {"
  if (code.split(before).length !== 2) throw new Error("Pinned HTTP body IPC observer drift")
  const helper = fileURLToPath(new URL("../scripts/lib/pink-body-ipc.js", import.meta.url))
  return {
    code: "import { observePinkBodyInvoke } from " + JSON.stringify(helper) + ";\n" +
      code.replace(before, "data = await observePinkBodyInvoke(invoke, 'plugin:http|fetch_read_body', {"),
    map: null
  }
}
export function pinkHttpBodyObserver() {
  return { name: "pink-http-body-observer", enforce: "pre", transform: observeHttpBodySource }
}
