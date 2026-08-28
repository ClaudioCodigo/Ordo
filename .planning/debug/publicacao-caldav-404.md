---
status: awaiting_verification
trigger: "Falha na publicação CalDAV: HTTP 404 em todas as OS criadas; uma OS remota editada não chegou à fila de sincronização"
created: 2026-08-28
updated: 2026-08-28T14:02:01-03:00
---

## Symptoms

- expected: Ao confirmar a prévia, uma OS provisória deve entrar na fila e criar o evento no calendário; uma OS remota editada deve entrar na fila para atualização condicional.
- actual: CREATE deixou de retornar 404 e sincroniza. UPDATE/FINALIZE de uma OS remota recebe sucesso e é marcado como publicado, mas o evento do Nextcloud permanece visualmente inalterado.
- errors: "Falha na publicação CalDAV: HTTP 404"
- timeline: Primeiro teste da implementação da Fase 3; o fluxo de escrita ainda não havia funcionado em ambiente real.
- reproduction: Criar uma OS, preencher e confirmar a publicação; ou abrir uma OS remota, iniciar atendimento, preencher, conferir a prévia e publicar.

## Current Focus

- bug_class: bohrbug
- hypothesis: Confirmada e corrigida: dois predicados incompatíveis substituídos por RemoteChangeAnalysis de três vias, com bloqueio seguro para texto bruto não representado.
- test: Compilar pelo Android Studio/Gradle os testes RemoteEventDetailViewModelTest, ConflictReviewViewModelTest e ServiceOrderDiffTest; repetir UAT de Continuar Atendimento com ETag apenas de metadados e com DESCRIPTION corrido alterado.
- expecting: zero navegação quando a lista acionável é vazia; base/ETag renovados sem perder campos locais; Manter Local inicialmente selecionado; Usar Calendário aplica somente a escolha explícita; texto não representável bloqueia sem avançar a base.
- next_action: Quando o Gradle/Android Studio puder abrir loopback, executar os três testes focados e a UAT de continuação; não reconstruir antes disso por exigência deste checkpoint.
- candidate_causes:
  - code: confirmado; navegação e UI usavam comparações distintas em vez da interseção base→remoto E local→remoto.
  - data: confirmado como condição; snapshot antigo/local já conciliado produz a lista acionável vazia, e texto remoto não representado exige bloqueio seguro.
- and_gate: sim; o conflito fantasma exige o predicado divergente e o estado base/local/remoto específico simultaneamente.
- reasoning_checkpoint:
    hypothesis: RemoteEventDetailViewModel abre a revisão com um booleano base→remoto, enquanto ConflictReviewViewModel exibe local→remoto; a tela vazia ocorre porque nenhum cálculo exige simultaneamente que o remoto tenha mudado desde a base e ainda divirja do rascunho local.
    confirming_evidence:
      - Com base `Título antigo`, local `Título já conciliado` e remoto `Título já conciliado`, remoteFieldsChanged é true e o detalhe navega, mas ServiceOrderDiff devolve zero diferenças.
      - D-05/D-17 define explicitamente a comparação base/local/remoto e a UI inicializa escolhas somente a partir da lista de FieldDifference.
    falsification_test: A hipótese estaria errada se o caso reproduzido gerasse pelo menos um FieldDifference ou se startAttendance retornasse requiresReview=false sem alterar o código; a leitura integral mostra o oposto deterministicamente.
    fix_rationale: Um único RemoteChangeAnalysis de três vias, reutilizado para decidir navegação e montar a revisão, elimina a divergência de predicados; zero itens renova apenas snapshot/ETag, e texto bruto alterado que não mapeia para campo suportado bloqueia a renovação em vez de ser aceito silenciosamente.
    blind_spots: Gradle permanece bloqueado por loopback; o caminho de entrada pela Central após 412 não é a reprodução atual e precisará dos testes adjacentes quando o ambiente compilar.
    candidate_causes:
      - code: dois predicados incompatíveis governam navegação e cartões; o diff existente não filtra pela base.
      - data: o estado gatilho contém snapshot antigo com rascunho local já igual ao remoto, ou texto SUMMARY/DESCRIPTION alterado fora dos campos representados.
      - environment: ETag pode mudar por metadados CalDAV sem mudança semântica em SUMMARY/DESCRIPTION, expondo o predicado incorreto.
    and_gate: sim; a revisão vazia exige simultaneamente o predicado de código base→remoto e um estado de dados em que local→remoto é vazio. A aceitação silenciosa exige ETag novo e texto remoto não representado pelo extrator.
- tdd_checkpoint:

## Evidence

- timestamp: 2026-08-28T10:00:00-03:00
  observed: RoomPublicationCoordinator construía CREATE em `/remote.php/dav/calendars/{user}/trabalho/{uuid}.ics`, ignorando o href da agenda selecionada.
  implication: servidores cuja coleção não se chama literalmente `trabalho` respondem HTTP 404 em toda criação provisória.
- timestamp: 2026-08-28T10:05:00-03:00
  observed: PublicationPreviewViewModel não bloqueava toque duplicado e não capturava falha de persistência; RoomPublicationRepository gravava versão e outbox fora de uma transação.
  implication: uma exceção local podia deixar gravação parcial e nenhum feedback na prévia, parecendo que a OS não entrou na fila.
- timestamp: 2026-08-28T10:10:00-03:00
  observed: PublicationWorker consumia somente uma operação e PublicationScheduler usava ExistingWorkPolicy.KEEP.
  implication: uma confirmação feita enquanto o worker estava ativo podia ter seu agendamento descartado e permanecer pendente.
- timestamp: 2026-08-28T10:15:00-03:00
  observed: após CREATE o aggregate não recebia occurrenceKey/baseSnapshot; após UPDATE o ETag e ICS-base não eram renovados.
  implication: a publicação seguinte podia criar duplicata ou usar ETag obsoleto e cair em conflito 412 artificial.
- timestamp: 2026-08-28T10:25:00-03:00
  observed: git diff --check passou; três tentativas de Gradle, inclusive com JBR do Android Studio e IPv4 forçado, falharam antes da compilação com `Unable to establish loopback connection`.
  implication: o bloqueio de verificação é ambiental do Windows/Gradle, anterior à execução dos testes, e não fornece sinal sobre a correção.
- timestamp: 2026-08-28T10:35:00-03:00
  observed: revisão estática confirmou compatibilidade das assinaturas Hilt/Room/WorkManager; resolução de coleção passou a reutilizar CalDavXmlParser.resolveHref, já usado e testado pelo fluxo de leitura.
  implication: não restou erro de API evidente no diff; compilação e UAT nativa são os gates restantes.
- timestamp: 2026-08-28T10:45:00-03:00
  observed: aparelho `RXCX402YK1V` apareceu autorizado no ADB, mas nova execução de testes direcionados + assembleDebug com o JBR do Android Studio falhou novamente antes da configuração do projeto por `Unable to establish loopback connection`.
  implication: conexão ADB não remove o bloqueio ambiental do Gradle; instalar o APK antigo não validaria a correção. Compilação pelo Android Studio/reinicialização e UAT humana continuam indispensáveis.
- timestamp: 2026-08-28T11:00:00-03:00
  observed: UAT real confirmou que CREATE agora sincroniza sem HTTP 404.
  implication: a correção de destino baseada na agenda selecionada resolveu o defeito original de criação.
- timestamp: 2026-08-28T11:05:00-03:00
  observed: FINALIZE de evento remoto entrou na fila, recebeu sucesso e foi marcado Published, mas não alterou o evento visível. IcsDocumentEditor procurava UID igual ao nome do arquivo e, quando não encontrava, retornava o ICS original sem erro.
  implication: href/ETag suficientemente válidos para receber 2xx; o payload aceito podia ser byte a byte o snapshot antigo, explicando sucesso sem mudança.
- timestamp: 2026-08-28T11:10:00-03:00
  observed: botão de publicação disparava flushNow assíncrono e navegava imediatamente; a prévia podia reler o estado anterior do Room. Após confirmação, a navegação removia apenas a prévia e retornava ao editor.
  implication: além do UID, havia corrida de persistência e feedback operacional ambíguo.
- timestamp: 2026-08-28T11:20:00-03:00
  observed: destino CREATE é deterministicamente a única agenda marcada como selecionada no Room; a prévia exibia apenas o rótulo fixo `Agenda de Trabalho`.
  implication: não há seleção por OS nesta fase; o relato de agenda de teste versus real pode ser ambiguidade de interface. A prévia agora mostra o displayName real antes da confirmação.
- timestamp: 2026-08-28T11:30:00-03:00
  observed: SPEC 3 R1 e CONTEXT D-04/D-05 exigem extrair número oficial do SUMMARY e, se o remoto mudar após existir cópia local, apresentar somente campos novos/diferentes para escolha. Notificações em segundo plano estão explicitamente fora da Fase 3.
  implication: preencher o número reconhecido ao iniciar e revisar a mudança ao continuar são escopo obrigatório; ausência de notificação push/local nesta fase não é defeito.
- timestamp: 2026-08-28T11:35:00-03:00
  observed: RemoteEventDetailViewModel descartava ExtractedSummary.externalId, e createOrGetAttendance devolvia vínculo existente sem comparar ETag, SUMMARY, DESCRIPTION ou snapshot. ConflictField também não representava número oficial.
  implication: OS já numerada abria com campo vazio; número acrescentado depois nunca era detectado nem revisado.
- timestamp: 2026-08-28T11:45:00-03:00
  observed: extração e revisão agora usam precedência SUMMARY e fallback de cabeçalho reconhecido no DESCRIPTION; cópia nova recebe o número, enquanto divergência em cópia existente abre Revisão do Calendário com padrão Manter Local.
  implication: demanda original e valor local nunca são substituídos silenciosamente; aceitar remoto atualiza valor e snapshot, recusar atualiza somente a base após decisão.
- timestamp: 2026-08-28T12:00:00-03:00
  observed: RemoteEventDetailViewModel decide abrir revisão comparando os campos extraídos do snapshot-base com o remoto, mas ConflictReviewViewModel monta os cartões comparando os campos locais com o remoto.
  implication: base→remoto pode divergir enquanto local→remoto é vazio; a diferença de predicados explica deterministicamente a tela de revisão sem campos.
- timestamp: 2026-08-28T12:02:00-03:00
  observed: para ETag diferente sem remoteFieldsChanged, o fluxo já renova etag/rawIcs/rawSummary/rawDescription e preserva todos os campos locais sem navegar.
  implication: o comportamento exigido para mudança apenas de metadados existe, mas seu guard usa um predicado diferente do diff realmente exibido.
- timestamp: 2026-08-28T12:08:00-03:00
  observed: CONTEXT D-05/D-17 exige apresentar somente campos locais↔remotos novos/diferentes e tratar DESCRIPTION corrido como campo de origem; D-06 exige preservar texto bruto.
  implication: a lista retornada por ServiceOrderDiff é o oráculo de navegação; um ETag sem itens nessa lista deve apenas renovar a base, enquanto DESCRIPTION corrido diferente deve gerar ORIGINAL_DEMAND.
- timestamp: 2026-08-28T12:15:00-03:00
  observed: a regressão de diff vazio foi codificada com base antiga, rascunho local já conciliado e remoto igual ao local; o fluxo atual avalia remoteFieldsChanged=true e chama onStarted(..., true), embora ConflictReviewViewModel calcule zero cartões.
  implication: a reprodução é determinística e falsifica a suficiência do predicado booleano base→remoto.
- timestamp: 2026-08-28T12:17:00-03:00
  observed: a execução focada de RemoteEventDetailViewModelTest foi bloqueada antes da configuração do projeto por java.io.IOException Unable to establish loopback connection.
  implication: o teste RED não pôde executar neste shell; a falha ambiental conhecida não contradiz a reprodução estática e deverá ser registrada como verificação não executada.
- timestamp: 2026-08-28T13:45:00-03:00
  observed: ServiceOrderDiff agora calcula base→remoto por campo e só produz FieldDifference quando local→remoto também diverge; RemoteEventDetailViewModel navega somente se essa lista é não vazia.
  implication: snapshots antigos já conciliados e ETags alterados apenas por metadados convergem por refresh de base, sem abrir Revisão do Calendário.
- timestamp: 2026-08-28T13:50:00-03:00
  observed: RemoteChangeAnalysis compara SUMMARY/DESCRIPTION brutos canonizados e detecta alterações não representadas, inclusive updates remotos fora do modelo atual de ConflictField; detalhe e revisão entram em Error antes de salvar nova base.
  implication: texto remoto significativo não é mais confundido com mudança de metadados nem aceito silenciosamente.
- timestamp: 2026-08-28T13:55:00-03:00
  observed: compilação isolada de ServiceOrderModels/Extractor/Diff com Kotlin compiler 2.2.21 passou; ServiceOrderDiffTest executou via JUnit 4.13.2 com 5 testes e zero falhas.
  implication: a lógica pura e as fronteiras local-only/remote-já-conciliado compilam e passam independentemente do daemon Gradle.
- timestamp: 2026-08-28T13:58:00-03:00
  observed: nova tentativa Gradle focada nos três testes, com JBR e JAVA_OPTS alinhados, continuou falhando antes da configuração por Unable to establish loopback connection; git diff --check passou com apenas avisos LF/CRLF.
  implication: ViewModels e integração Android ainda aguardam compilação, sem erro textual/de whitespace no diff.

## Eliminated

- Credencial/autenticação como causa primária do CREATE: o retorno observado é 404, enquanto o cliente tipa 401/403 separadamente; a leitura CalDAV já funciona.
- Falha de formatação da prévia como causa do 404: o destino HTTP era montado independentemente do ICS e continha a coleção fixa incorreta.
- eventHref/ETag inválidos como causa principal do UPDATE inócuo: a operação recebeu sucesso remoto; o caminho do editor permite produzir payload original mesmo com prévia textual correta.
- Um guard simples de ETag ou de local→remoto isoladamente: ETag sozinho cria falso conflito por metadados, e local→remoto sozinho transforma edição local normal em conflito; D-05 exige a interseção base→remoto E local→remoto.

## Resolution

- root_cause: O CREATE ignorava a agenda selecionada. No UPDATE, a identidade do VEVENT era inferida do nome do arquivo em vez do UID interno; alvo ausente devolvia silenciosamente o ICS original. A navegação para a prévia também podia vencer o save assíncrono, e o worker/KEEP podia deixar operações paradas. Na continuação, o detalhe decidia revisão por base→remoto e a tela gerava cartões por local→remoto; a combinação com snapshot antigo/local já conciliado criava conflito vazio, e texto bruto não representado podia ser aceito como mudança de metadados.
- fix: Usar agenda selecionada no CREATE; extrair UID do ICS e falhar fechado se o VEVENT não existir; aguardar persistência antes da prévia; exibir o nome real da agenda; abrir a Central após confirmar; tornar versão+outbox transacionais; drenar toda a fila; renovar vínculo/snapshot/ETag; extrair número oficial com precedência SUMMARY→DESCRIPTION. Para a revisão fantasma, centralizar comparação base/local/remoto em RemoteChangeAnalysis, navegar somente com diferenças acionáveis, renovar base em diff vazio e bloquear sem avançar a base quando texto bruto alterado não é representável.
- oracle_type: specified (CONTEXT D-05/D-06/D-17)
- verification:
    target_test: parcial; ServiceOrderDiffTest passou 5/5 via compilador/JUnit isolados, testes de ViewModel bloqueados pelo loopback do Gradle.
    mutation_check: skipped; Stryker não se aplica ao projeto Kotlin e o daemon Gradle não inicia.
    no_op_deletion: pass; o diff adiciona análise de três vias/guard fail-closed e não remove nem curto-circuita a escolha remota acionável.
    adjacent_tests: bloqueado; RemoteEventDetailViewModelTest e ConflictReviewViewModelTest não executaram por Unable to establish loopback connection.
    revert_and_reconfirm: não executado; a correção está dentro de um batch compartilhado não commitado que não pode ser stashed/revertido com segurança nesta continuação.
    guardrail_verdict: pending Android/Gradle verification.
- files_changed: app/src/main/java/dev/claudiocodigo/nexo/MainActivity.kt; app/src/main/java/dev/claudiocodigo/nexo/data/ical/IcsDocumentEditor.kt; app/src/main/java/dev/claudiocodigo/nexo/data/publication/RoomPublicationCoordinator.kt; app/src/main/java/dev/claudiocodigo/nexo/data/repository/RoomPublicationRepository.kt; app/src/main/java/dev/claudiocodigo/nexo/data/worker/PublicationScheduler.kt; app/src/main/java/dev/claudiocodigo/nexo/data/worker/PublicationWorker.kt; app/src/main/java/dev/claudiocodigo/nexo/domain/serviceorder/ServiceOrderDiff.kt; app/src/main/java/dev/claudiocodigo/nexo/domain/serviceorder/ServiceOrderExtractor.kt; app/src/main/java/dev/claudiocodigo/nexo/ui/screens/conflito/ConflictReviewScreen.kt; app/src/main/java/dev/claudiocodigo/nexo/ui/screens/conflito/ConflictReviewViewModel.kt; app/src/main/java/dev/claudiocodigo/nexo/ui/screens/oseditor/ServiceOrderEditorScreen.kt; app/src/main/java/dev/claudiocodigo/nexo/ui/screens/oseditor/ServiceOrderEditorViewModel.kt; app/src/main/java/dev/claudiocodigo/nexo/ui/screens/preview/PublicationPreviewViewModel.kt; app/src/main/java/dev/claudiocodigo/nexo/ui/screens/preview/PublicationPreviewScreen.kt; app/src/main/java/dev/claudiocodigo/nexo/ui/screens/remoto/RemoteEventDetailScreen.kt; app/src/main/java/dev/claudiocodigo/nexo/ui/screens/remoto/RemoteEventDetailViewModel.kt; regression tests under app/src/test
