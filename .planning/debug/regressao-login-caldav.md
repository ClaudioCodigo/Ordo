---
status: awaiting_human_verification
trigger: "Depois dos commits 1bd7f08 e 0450477, o login CalDAV que funcionava voltou a apresentar erro."
created: 2026-08-31
updated: 2026-08-31
---

# Regressão no login CalDAV após auto-sync

## Symptoms

- expected: Credenciais anteriormente válidas devem conectar, descobrir as agendas e iniciar a sincronização sem transformar falhas posteriores em erro de login.
- actual: Após as alterações recentes, o aplicativo voltou a apresentar erro no login CalDAV.
- errors: Mensagem exata ainda não registrada; relato do usuário é "voltou a dar o erro no login do CalDav".
- timeline: Regressão observada depois dos commits `1bd7f08` e `0450477`; a base `ad99b1d` havia funcionado no aparelho.
- reproduction: Tentar conectar novamente usando o fluxo CalDAV que funcionava antes das alterações de resumo, auto-seleção, auto-discovery e auto-sync.

## Current Focus

- hypothesis: Confirmada: `0450477` acoplou persistência da conta a discovery/sync automáticos e introduziu seleção heurística de agenda; telas Hoje/Agenda também passaram a disparar sync no init, criando chamadas concorrentes durante configuração.
- test: Remover orquestração remota do login e toda seleção implícita; verificar que sem seleção explícita não se lê a senha nem se chama o cliente remoto.
- expecting: Login apenas persiste credenciais; discovery popula lista; somente confirmação explícita seleciona e agenda sync.
- next_action: Capturar a mensagem exata no aparelho e executar build/testes pelo Android Studio; se o erro persistir, continuar a partir da mensagem e do ponto exato do fluxo.
- reasoning_checkpoint:
- tdd_checkpoint:

## Evidence

- timestamp: 2026-08-31T00:00:00-03:00
  checked: diff `ad99b1d..0450477`
  found: `ContaNextcloudViewModel.store()` passou a executar discovery e agendar sync dentro do callback de sucesso da persistência; `RoomCalendarSyncCoordinator` passou a executar discovery fallback e escolher uma agenda por nome/permissão.
  implication: autenticação, discovery, seleção e sync deixaram de ser etapas isoladas e podem concorrer.
- timestamp: 2026-08-31T00:01:00-03:00
  checked: testes automatizados existentes
  found: os 139 testes reportados eram unitários; o teste instrumentado `partialFetchPreservesCache` contradiz a nova tolerância a multiget parcial e falharia, pois as guardas de resposta completa foram removidas.
  implication: o gate não executou comportamento Room/Android e deixou passar risco de remoção incorreta do cache.
- timestamp: 2026-08-31T00:02:00-03:00
  checked: requisito de seleção de agenda
  found: nomes `pessoal`, `personal`, `ordens`, `trabalho` e primeiro calendário gravável eram usados para selecionar destino sem confirmação.
  implication: sync/publicação poderiam usar uma agenda inferida incorretamente.
- timestamp: 2026-08-31T00:03:00-03:00
  checked: correção local e `git diff --check`
  found: auto-discovery no login e seleção implícita removidos; sync sem seleção termina antes de ler senha/rede; guardas de multiget parcial restauradas; diff sem erros de whitespace.
  implication: mudanças inseguras estão corrigidas, pendentes de compilação e teste no aparelho.
- timestamp: 2026-08-31T00:04:00-03:00
  checked: Gradle unitário focado, com daemon, sem daemon e fora do sandbox
  found: todas as tentativas falharam antes da compilação com `java.io.IOException: Unable to establish loopback connection`.
  implication: o host não oferece um sinal de compilação nesta sessão; Android Studio/aparelho deve executar a verificação.

## Eliminated

## Resolution

- root_cause: O commit `0450477` misturou persistência da conta, discovery, escolha heurística de agenda e sync automático, permitindo concorrência no setup e sincronização contra uma agenda nunca escolhida pelo usuário.
- fix: Separar novamente o salvamento da conta do discovery/sync; exigir seleção explícita; preservar somente seleção anterior ainda válida; impedir qualquer chamada remota sem seleção; restaurar fail-safe para respostas multiget parciais.
- verification: `git diff --check` passou; testes de regressão adicionados, mas a execução Gradle está bloqueada pelo erro de loopback do host. Falta mensagem runtime exata e UAT no aparelho.
- files_changed: `RoomCalendarSetupRepository.kt`, `RoomCalendarSyncCoordinator.kt`, `ContaNextcloudViewModel.kt`, `ContaNextcloudScreen.kt`, `RoomCalendarSyncCoordinatorTest.kt`, `RoomCalendarSyncCoordinatorSelectionTest.kt`.
