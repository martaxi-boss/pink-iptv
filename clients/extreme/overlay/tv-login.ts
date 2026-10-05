import type { TvView } from '@/scripts/tv/router'
import { navigate } from 'astro:transitions/client'
import { mountPinkLogin } from '@/scripts/lib/pink-login.js'
const view: TvView = {
  mount(root) { return mountPinkLogin(root, () => navigate('/tv', { history: 'replace' })) },
}
export default view
