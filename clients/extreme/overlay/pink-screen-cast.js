import { installPinkBridge } from "./pink-bridge.js"
import { toast } from "./toast.js"

// Android screen mirroring keeps the protected IPTV provider URL on this phone.
export async function openPinkCastSettings() {
  installPinkBridge()
  const open = typeof window !== "undefined" && window.PinkScreenCast?.open
  try {
    if (typeof open === "function" && await open()) return true
  } catch { /* Android may not expose a Cast target. */ }
  toast({ title: "Abre Transmitir ecrã nas definições do Android.", duration: 4000 })
  return false
}
