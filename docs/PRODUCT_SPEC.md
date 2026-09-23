# Product Specification

## Mission

Build PINK IPTV as a simple branded IPTV player for customers who receive a username and password from the operator.

The user must not need to know DNS, subdomains, ports, M3U URLs, API tokens or VPN configuration.

## v1 user journey

1. Install PINK IPTV.
2. Open app.
3. On first VPN use, approve the operating-system VPN permission/setup.
4. Enter Username and Password.
5. App resolves the customer's assigned service host through the PINK backend.
6. App establishes/validates the PINK WireGuard tunnel when VPN mode is enabled.
7. App authenticates against the assigned Xtream host.
8. App loads Home.
9. User browses Live TV, Movies, Series, EPG/Catch Up and Favorites.
10. Playback goes directly from the provider, through the VPN gateway when enabled, to the client.

## Core features

- Username/password login
- session persistence
- Live TV categories and channels
- current/next programme display where EPG data exists
- Movies/VOD
- Series, seasons and episodes
- global search
- Favorites
- recent/continue watching
- EPG/Catch Up where provider data supports it
- player controls
- account/expiration status
- language/settings
- integrated VPN status and automatic reconnect
- graceful offline/error states

## UX principles

- PINK branding: dark media surfaces with pink/magenta highlights.
- Big touch targets and TV-remote focus states.
- Same information architecture on Android and Windows.
- No technical service URLs exposed during normal login.
- Fast route to Live TV: two actions or fewer after Home.
- Never expose reseller/admin controls to end users.

## Out of scope for v1

- user-supplied M3U playlists
- MAG/Enigma portals
- reseller panel replacement
- transcoding or restreaming
- CDN
- iOS/tvOS
- Samsung/LG native apps
- payments inside the player
