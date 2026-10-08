# PINK IPTV — recuperação de limite VPN 066

## Evidência física e causa-raiz

A APK Android de debug 062 abriu o login real no telemóvel do proprietário
mas devolveu `PINK: VPN_ENROLL`. A inspeção read-only na VPS mostrou, nos
dois períodos relevantes, autenticação com HTTP 200 e enrollment HTTP 429.
Os serviços PINK, PostgreSQL, Nginx e WireGuard estavam ativos.

- VPS Task063: [HTTP 200/429, diagnóstico anónimo](https://github.com/martaxi-boss/VPS/actions/runs/37822637078).
- VPS Task064: [cinco instalações VPN ativas, uma conta saturada, zero peers online](https://github.com/martaxi-boss/VPS/actions/runs/37825179081).
- VPS Task065: [vaga recuperada por expiração natural, quatro de cinco instalações válidas](https://github.com/martaxi-boss/VPS/actions/runs/37826376443).

**Não revogar peers:** esta recuperação não exigiu editar a base de dados ou
desligar aparelhos. A aplicação 062 que está instalada deve ser testada
novamente **sem desinstalação**, para preservar a identidade WireGuard
atual e usar a vaga disponível.

## Correção do código neste branch

O backend original aplicava o limite de cinco instalações ativas, mas
respondia com HTTP429 sem informar quanto tempo faltava. A implementação
066 mantém a quota e passa a enviar `Retry-After` limitado a 1–86400
segundos, calculado pela inscrição ativa que expira primeiro. Apenas
uma sessão PINK válida pode receber esta informação; o corpo permanece
genérico e sem dados sobre aparelhos de terceiros.

No Android, a resposta 429 na etapa autorizada `vpn_enroll` ganha o
código seguro `VPN_LIMIT`, em vez de `VPN_ENROLL`. Os erros de HTTPS,
consentimento Android, sessão e ativação continuam distintos e
**fail-closed**. A interface informa em português para esperar a
libertação de uma vaga e não desinstalar a aplicação; nunca apresenta
conteúdo do servidor, credenciais, peers, endereços ou chaves.

Esta melhoria **não** aumenta o limite, remove instalações, autoriza
streaming fora do WireGuard ou cria um novo serviço de backend. Não
inclui auto-substituição arbitrária de aparelhos, que exigiria
política e confirmação de dispositivos legítimos.

## Aceitação e isolamento

A tarefa está vinculada à implementação `builder/vpn-quota-recovery-066`
e ao controlo Project Leader canónico. CI exigido: `Backend CI` e
`PINK Extreme Android 042` sobre o SHA exato de implementação,
incluindo testes de sessão, quota, WebView, Keystore, Android VPN,
Media3, privacidade e APK debug.

O código só pode ser integrado após ambas as verificações passarem.
O novo cabeçalho HTTP backend não está em produção até um deploy
separadamente certificado e autorizado. A aplicação original 062
permanece candidata para o teste físico atual; não apresentar uma nova
APK como tendo passado no telemóvel sem prova do proprietário.

## Pendências de qualidade de longo prazo

Uma reinstalação Android pode destruir a identidade Keystore anterior
e criar uma nova instalação. A quota protege múltiplos clientes, mas
precisa de gestão segura de dispositivos substituídos a médio prazo.
Uma futura solução deverá autenticar explicitamente a conta, listar
apenas IDs sem segredos e exigir consentimento informado antes de
revogar qualquer instalação ainda válida. **Não contornar a quota nem
revogar unilateralmente dispositivos online.**
