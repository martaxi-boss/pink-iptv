# PINK IPTV — Auditoria e layout premium (15/10/2026)

**Fonte de verdade:** `martaxi-boss/pink-iptv`, cliente Android/Android TV atual em `clients/extreme/` (Tauri + Astro, upstream 1.9.0 pinned). O cliente anterior `android/` permanece histórico.

## Constatações verificadas
- **CI PIP/Cast, PR #68:** Android lint do run 38714909951 falhou com duas etiquetas sem tradução (MissingTranslation). A correção delimitada foi enviada no commit 97438411e7c70a8954b7d524b648f5c46b333ccd, PR #68; a nova CI precisa terminar antes de certificação.
- **Filmes/Séries:** o antigo overlay de imagem decorativa podia ocupar até 60dvh antes dos posters. Esta intervenção limita a faixa a 6,5–10rem e preserva grelhas, pesquisa e categorias.
- **Identidade visual:** o esquema do upstream ainda não tinha apresentação premium PINK coerente entre homepage, menu, Live e Android TV. A skin PINK Cinema aplica fundo escuro, marca rosa, masthead, foco legível, cartões e menus consistentes.
- **Hardware:** não há prova nesta alteração de reprodução Live, VOD/episódios, áudio/legendas, VPN real e D-pad em todos os equipamentos.

## Limites de alteração
O novo `premium_overlay.py` copia apenas CSS e insere marcação decorativa em layouts/home do upstream fixo. Não substitui nem edita o login, credenciais, DNS, URLs, WireGuard, backend, reprodutor, handlers nem os scripts de dados dinâmicos. O teste isolado é chamado no Android CI; o banner continua com teste próprio. Licenças/atribuições GPL preservadas.

## Verificações obrigatórias
1. Confirmar build/testes/lint Android e backend no commit exato do PR; não reutilizar checks anteriores.
2. Validar em telefone e Android TV reais a legibilidade, foco D-pad, carregamento de catálogos, reprodução e regresso ao menu.
3. Rever warnings de lint remanescentes (segurança WebView, política de rede, recursos privados e acessibilidade), sem inferir vulnerabilidades não demonstradas.

**Sem publicação, deploy, alteração da VPS ou autorização de lançamento comercial.**
