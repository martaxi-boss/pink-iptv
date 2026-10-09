# PINK IPTV TESTE 2 — teste seguro no Android

> Apenas para o proprietário, em ambiente de testes. **Não é uma atualização da aplicação PINK IPTV instalada, nem uma APK para clientes.**

## Porquê uma terceira aplicação independente?

A aplicação já instalada usa `com.pinkiptv.extreme`. Os certificados de algumas APKs de desenvolvimento anteriores não coincidem, pelo que uma atualização direta pode falhar e desinstalar apagaria a identidade privada WireGuard armazenada no Android.

A QA usa, exclusivamente na variante **debug**, `com.pinkiptv.extreme.qa2` e o nome de ícone **PINK IPTV TESTE 2**. Não modifica a assinatura, os dados, a identidade nem o pacote da aplicação original. O Java/Kotlin namespace e o código oficial WireGuard permanecem iguais.

## Onde obter a APK de testes

1. Abrir a execução GitHub Actions **PINK Android Owner QA2 087** da PR de testes. Confirmar **Success** na compilação, validação de assinatura, testes de VPN no emulador, lint e análise de segredos.
2. Na secção **Artifacts**, descarregar **PINK-IPTV-TESTE2-087**, descomprimir e encontrar `PINK-IPTV-TESTE2.apk`.
3. Confirmar em `provenance.json` que `application_id` é `com.pinkiptv.extreme.qa2`, que `production_release` é `false` e que a SHA-256 corresponde à APK e ao ficheiro `SHA256SUMS`.
4. Instalar essa APK **sem desinstalar a PINK IPTV existente**. Deverão permanecer três ícones distintos: **PINK IPTV**, **PINK IPTV TESTE** e **PINK IPTV TESTE 2**.

Se o Android disser que precisa de substituir a aplicação antiga ou desinstalá-la, **cancelar** imediatamente. Significa que o identificador da APK não é o de testes. O certificado de debug desta QA não é uma identidade de assinatura de produção.

## O que a nova QA2 realmente comprova antes da instalação

Os testes automáticos usam vídeos locais e um fornecedor de catálogo **simulado**, sem dados privados. Exigem abertura real do menu de três pontos no player Android, visibilidade de Picture-in-Picture e Play on TV, escolha de **Zoom** e regresso a **Fit**, imagem e áudio reais, faixas/legendas, catálogos Filmes/Séries completos na ligação nativa simulada, VPN/Keystore e análise de segurança. Não reproduzem o fornecedor real no telefone, nem garantem compatibilidade com uma televisão externa.

Esta APK `com.pinkiptv.extreme.qa2` é uma **terceira instalação**, não uma atualização da QA anterior (`com.pinkiptv.extreme.qa`). Como a assinatura de debug dos workflows não é persistente, não tentar atualizar esta QA2 com outra APK do CI do mesmo identificador. O sistema Android deve apresentar **instalar** e não **atualizar** a aplicação antiga. A original e a TESTE anterior não são apagadas nem modificadas.

### Critérios para aceitar o teste físico (não basta abrir a aplicação)

1. Em **Live TV**, confirmar visual PINK em repouso, ON/OFF e EPG; abrir um canal real com imagem/som e tocar nos três pontos. Confirmar, no mínimo, **Display mode**, **Picture-in-Picture** e **Play on TV**, além dos restantes controlos originais do menu Live.
2. Em **Filmes** e em **Séries**, confirmar **categorias e listagens efetivamente preenchidas** sem `CATEGORIES · STAGE` nem `Can't reach`; escolher um título de cada e reproduzir imagem e áudio.
3. Durante a reprodução de cada tipo, abrir o menu **⋯**. Trocar o formato de imagem para **Zoom** e novamente **Fit**, testar PiP e verificar que **Play on TV** permite aceder às definições de transmissão de ecrã do Android (o envio direto de URLs IPTV para a TV está intencionalmente bloqueado para proteger credenciais e VPN).
4. Confirmar depois a **PINK IPTV original** ainda instalada e operacional. O Android só permite uma VPN ativa de cada vez; a troca pode exigir voltar a ativá-la.
5. Se não houver TV compatível, registar a função de transmissão como **não testada fisicamente**, nunca como aprovada. Se as categorias falharem na conta real, registar só o código da fase, sem revelar endereços, palavras-passe ou chaves.

## Aceitação física no telemóvel

- Abrir **PINK IPTV TESTE 2**. Quando o Android apresentar consentimento VPN, confirmar que é pedido pela aplicação de testes. O Android só disponibiliza uma VPN ativa de cada vez: ligar a QA interrompe temporariamente o túnel da PINK IPTV antiga, mas **não** apaga a antiga aplicação.
- Entrar com uma conta PINK válida, verificar a autenticação, o túnel WireGuard protegido, a navegação do catálogo Live e a reprodução real de imagem e áudio.
- Abrir categorias e títulos de Filmes e Séries; confirmar listagem e reprodução. Registar falhas por códigos fixos, sem divulgar passwords, URLs de cliente, chaves ou tokens.
- Carregar Home, regressar à app, alternar Wi-Fi/dados móveis quando aplicável, e terminar/reabrir a QA. Verificar que mantém a mesma identidade de instalação e que não consome uma vaga nova por cada login.
- **Não criar intencionalmente 10 dispositivos** só para provocar o limite. O limite de 10 e a recuperação de instalações antigas têm testes do backend já aprovados. O ecrã de recuperação só deve aparecer em condições reais de quota esgotada; ao libertar instalações, escolher apenas dispositivos efetivamente abandonados.
- No fim, fechar a QA e reabrir a PINK IPTV original; confirmar novamente que a identidade e a reprodução da aplicação antiga permanecem operacionais. O Android poderá pedir para voltar a ativar a VPN original.

## Assinatura de desenvolvimento e terceira identidade

Esta é uma APK **isolada para um único teste físico**, com chave de depuração criada no CI. Uma reconstrução noutro runner poderá ter assinatura diferente e falhar a atualização da QA2. O próximo teste não deve ser preparado desinstalando a QA original; se for necessário testar versões futuras, terá de ser encontrada primeiro uma assinatura persistente segura ou outro método que não destrua identidades VPN.

A primeira autenticação da TESTE 2 pode ocupar mais uma vaga de dispositivo; o Android pode desligar temporariamente a VPN da app antiga. Nenhuma instalação pode ser automaticamente removida ou vaga de VPN libertada sem escolha explícita do proprietário.

## Notas importantes sobre as vagas

Cada instalação QA usa um par de chaves próprio e, após autenticação, pode ocupar **uma** das 10 vagas do utilizador. Fechar ou remover a APK não liberta imediatamente essa autorização. **Não apagar a QA repetidamente para voltar a instalá-la**. Planear uma libertação autenticada do dispositivo de testes quando já não for necessário.

## O que ainda não fica autorizado

Esta verificação **não** é uma assinatura persistente para APKs normais e **não** autoriza substituir a APK funcional instalada nem distribuir uma versão pública aos clientes. Para atualizar a aplicação original em cima da existente é necessária uma APK assinada com a mesma identidade de assinatura; na ausência da chave, a transição de produção é uma decisão separada e controlada.
