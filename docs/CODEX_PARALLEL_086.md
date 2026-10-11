# Project Leader + dois Codex em paralelo — PINK IPTV

Este projeto usa **uma conta Codex** e, quando há duas tarefas
independentes, **dois subagentes** da mesma sessão. Não duplica a conta.

A configuração já fica guardada em:
- `.codex/config.toml` — máximo de **2** subagentes;
- `.codex/agents/pink_android.toml` — Android, ecrãs e players;
- `.codex/agents/pink_backend.toml` — backend, autenticação e VPN;
- `AGENTS.md` — coordenação do Project Leader e gestão de esforço.

## Arranque (depois da autenticação existente do Codex)

Abrir o projeto PINK IPTV no Codex CLI, extensão IDE ou app Codex
compatível, com a conta já autenticada. Dar a ordem normal, incluindo
`@Project Leader` se esse plugin estiver disponível na interface.

Exemplo de ordem dita por voz ou escrita:

> Continua o PINK IPTV em modo automático. Usa apenas um Codex
> quando a tarefa for pequena ou dependente. Se houver tarefas
> independentes, divide Android e backend entre `pink_android` e
> `pink_backend`, em paralelo. Começa com raciocínio baixo,
> sobe para médio/alto só perante complexidade demonstrada e
> reduz de novo quando estiver resolvida. Não dupliques análise
> nem testes e não alteres VPS de produção nem a APK instalada.
> No fim apresenta os ficheiros e testes verificados.

Para execução sequencial, dizer **"modo série: primeiro Android,
depois backend"**. Para exigir paralelismo em trabalhos independentes,
dizer **"modo paralelo: Android e backend"**. Se os caminhos ou
dependências coincidirem, o Project Leader serializa mesmo em modo
paralelo, para evitar conflitos.

## Factos e limites

- A configuração apenas **habilita** delegação Codex. Não cria
  automaticamente dois processos ativos e não comprova que estão
  a executar neste momento.
- O Codex precisa de estar disponível/autenticado no ambiente
  onde for iniciado. O ChatGPT com conector GitHub pode
  preparar e validar o repositório mas não herda automaticamente
  uma sessão Codex autenticada nem altera a quota da conta.
- O limite de dois corresponde a **subagentes** além do
  coordenador, e depende da disponibilidade/limites da conta.
- `low/medium/high` são níveis de esforço por agente/etapa;
  não é possível garantir uma alteração instantânea no interior
  de uma chamada já em execução. Não há garantia de poupança
  quantificada de tokens sem métricas reais do Codex.
- Cada subagente pode consumir tokens adicionais. Utilizar os
  dois só se houver ganho de trabalho independente; caso
  contrário, usar um.
- Nenhum subagente faz merge, publicação, alterações VPS,
  operações com credenciais, alteração de assinatura APK nem
  manipulação de leases de clientes sem o controlo apropriado.

Validação estática rápida (no repositório):

```sh
python3 -m unittest discover -s tests -p 'test_codex_parallel_086.py' -v
```

A aprovação destes testes confirma configuração e regras,
**não confirma uma sessão Codex ativa**.
