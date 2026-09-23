# UX Structure

## Design rule

Use the functional information architecture users expect from an IPTV player while keeping PINK IPTV visually and technically original. Do not copy third-party source code, proprietary icons, artwork, logos or pixel-identical screen compositions.

## Android Shell 002 — implemented screen map

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

### Shell routes
TV ao Vivo, Filmes, Séries, EPG and Favoritos are visual shells only in Order 002. They do not load invented or real provider catalog data and state that functionality arrives in a later phase.

Definições exposes only the Order 002 local-session logout/clear action.

## Android TV / D-pad principles

- Every essential action is focusable and operable without touch.
- Focus uses a strong PINK Primary / Pink Soft visual state suitable for distance viewing.
- Home cards define deterministic left/right/up/down focus neighbors.
- OK/Enter activates focused actions and Back returns through the navigation stack.
- Login works with D-pad plus the system keyboard.
- Touchscreen is not required by the manifest.

## Future UX

Search, real Live/VOD/Series catalogs, player, advanced Catch Up behavior, detailed Account, VPN, Support and About are not implemented by Order 002. They require separately authorized future work.

Phone/tablet and TV may adapt layout density and grid width, but must not diverge into separate product logic.
