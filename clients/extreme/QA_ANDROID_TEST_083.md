# PINK IPTV TESTE — teste seguro no Android

> Apenas para o proprietário, em ambiente de testes. **Não é uma atualização da aplicação PINK IPTV instalada, nem uma APK para clientes.**

## Porquê duas aplicações?

A aplicação já instalada usa `com.pinkiptv.extreme`. Os certificados de algumas APKs de desenvolvimento anteriores não coincidem, pelo que uma atualização direta pode falhar e desinstalar apagaria a identidade privada WireGuard armazenada no Android.

A QA usa, exclusivamente na variante **debug**, `com.pinkiptv.extreme.qa` e o nome de ícone **PINK IPTV TESTE**. Não modifica a assinatura, os dados, a identidade nem o pacote da aplicação original. O Java/Kotlin namespace e o código oficial WireGuard permanecem iguais.

## Onde obter a APK de testes

1. Abrir a execução GitHub Actions **PINK Android Parallel QA 083** da PR de testes. Confirmar **Success** na compilação, validação de assinatura, testes de VPN no emulador, lint e análise de segredos.
2. Na secção **Artifacts**, descarregar **PINK-IPTV-TESTE-083**, descomprimir e encontrar `PINK-IPTV-TESTE.apk`.
3. Confirmar em `provenance.json` que `application_id` é `com.pinkiptv.extreme.qa`, que `production_release` é `false` e que a SHA-256 corresponde à APK e ao ficheiro `SHA256SUMS`.
4. Instalar essa APK **sem desinstalar a PINK IPTV existente**. Deverão aparecer dois ícones: **PINK IPTV** e **PINK IPTV TESTE**.

Se o Android disser que precisa de substituir a aplicação antiga ou desinstalá-la, **cancelar** imediatamente. Significa que o identificador da APK não é o de testes. O certificado de debug desta QA não é uma identidade de assinatura de produção.

## Aceitação física no telemóvel

- Abrir **PINK IPTV TESTE**. Quando o Android apresentar consentimento VPN, confirmar que é pedido pela aplicação de testes. O Android só disponibiliza uma VPN ativa de cada vez: ligar a QA interrompe temporariamente o túnel da PINK IPTV antiga, mas **não** apaga a antiga aplicação.
- Entrar com uma conta PINK válida, verificar a autenticação, o túnel WireGuard protegido, a navegação do catálogo Live e a reprodução real de imagem e áudio.
- Abrir categorias e títulos de Filmes e Séries; confirmar listagem e reprodução. Registar falhas por códigos fixos, sem divulgar passwords, URLs de cliente, chaves ou tokens.
- Carregar Home, regressar à app, alternar Wi-Fi/dados móveis quando aplicável, e terminar/reabrir a QA. Verificar que mantém a mesma identidade de instalação e que não consome uma vaga nova por cada login.
- **Não criar intencionalmente 10 dispositivos** só para provocar o limite. O limite de 10 e a recuperação de instalações antigas têm testes do backend já aprovados. O ecrã de recuperação só deve aparecer em condições reais de quota esgotada; ao libertar instalações, escolher apenas dispositivos efetivamente abandonados.
- No fim, fechar a QA e reabrir a PINK IPTV original; confirmar novamente que a identidade e a reprodução da aplicação antiga permanecem operacionais. O Android poderá pedir para voltar a ativar a VPN original.

## Notas importantes sobre as vagas

Cada instalação QA usa um par de chaves próprio e, após autenticação, pode ocupar **uma** das 10 vagas do utilizador. Fechar ou remover a APK não liberta imediatamente essa autorização. **Não apagar a QA repetidamente para voltar a instalá-la**. Planear uma libertação autenticada do dispositivo de testes quando já não for necessário.

## O que ainda não fica autorizado

Esta verificação **não** é uma assinatura persistente para APKs normais e **não** autoriza substituir a APK funcional instalada nem distribuir uma versão pública aos clientes. Para atualizar a aplicação original em cima da existente é necessária uma APK assinada com a mesma identidade de assinatura; na ausência da chave, a transição de produção é uma decisão separada e controlada.
