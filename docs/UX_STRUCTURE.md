# UX Structure

## Design rule

Use the functional information architecture users expect from an IPTV player while keeping PINK IPTV visually and technically original. Do not copy third-party source code, proprietary icons, artwork, logos or pixel-identical screen compositions.

## Current Android screen map

### Splash
- PINK IPTV branding and startup state.
- No artificial delay; secure local state is checked immediately.

### Login
- USERNAME.
- PASSWORD, masked.
- ENTRAR.
- Concise resource-based error/loading states.
- No Remember me; the older Remember me concept is superseded by the Owner rule.
- No DNS, portal, URL, M3U, port, token, API, MAC, MAG, Enigma or Mega subscription field.

### Home
Large routes:
- TV ao Vivo
- Filmes
- Séries
- EPG
- Favoritos
- Definições

Home is adaptive for phone/tablet width and remains the same logical hierarchy on Android TV / TV Box.

### Implemented routes
Phase 3 replaces the historical Order 002 shells with authenticated Live TV, Movies/VOD and Series catalogs; Series detail/seasons/episodes; Media3 playback; EPG/Catch Up; Favorites, Recent history and Continue Watching; and Global Search.

Settings retains local-session logout. WireGuard identity/permission/preparation settings are development work in open PR #13 and are not yet merged. No real VPN tunnel is implemented.

## Android TV / D-pad principles

- Every essential action is focusable and operable without touch.
- Focus uses a strong PINK Primary / Pink Soft visual state suitable for distance viewing.
- Home cards define deterministic left/right/up/down focus neighbors.
- OK/Enter activates focused actions and Back returns through the navigation stack.
- Login works with D-pad plus the system keyboard.
- Touchscreen is not required by the manifest.

## Future UX

Detailed Account, Support, About, real VPN activation and Windows remain future work. Implemented Phase 3 surfaces still require the outstanding real-provider and post-Shell physical-TV evidence documented in PROJECT_STATE.md; implementation does not imply production readiness.

Phone/tablet and TV may adapt layout density and grid width, but must not diverge into separate product logic.
