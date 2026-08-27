# Walkthrough: Fase 3 — Ordens de Serviço com Publicação Controlada

A Fase 3 foi concluída com sucesso. A implementação entrega o suporte completo a Ordens de Serviço Estruturadas (provisórias e vinculadas a eventos de calendário), migração de banco de dados não destrutiva (Room v3), edição cirúrgica e *lossless* de arquivos `.ics` (RFC 5545), cliente CalDAV de escrita estritamente condicional com precondições `If-Match` / `If-None-Match`, fila outbox offline com WorkManager e interface de resolução de conflitos 412 campo a campo.

---

## 🚀 Principais Entregas por Onda

### 1. Domínio Estruturado e Persistência Não Destrutiva (Room v3)
* **Aggregate [`StructuredServiceOrder`](file:///c:/Users/claudio.lima/AndroidStudioProjects/Nexo/app/src/main/java/dev/claudiocodigo/nexo/domain/serviceorder/ServiceOrderModels.kt):** Modelagem com identificação de OS oficial/provisória, presets operacionais (*Diagnóstico/Correção* e *Serviço Solicitado*), demanda original, histórico cronológico de notas de campo, itens genéricos de equipamento e dados de conclusão.
* **Identidade de Vínculo [`RemoteOccurrenceKey`](file:///c:/Users/claudio.lima/AndroidStudioProjects/Nexo/app/src/main/java/dev/claudiocodigo/nexo/domain/serviceorder/ServiceOrderModels.kt):** Chave composta por `accountId + calendarHref + eventHref + recurrenceId`.
* **Migração Explícita [`MIGRATION_2_3`](file:///c:/Users/claudio.lima/AndroidStudioProjects/Nexo/app/src/main/java/dev/claudiocodigo/nexo/data/local/NexoDatabaseMigrations.kt):** Migração 100% aditiva e não destrutiva, preservando drafts e tabelas existentes byte a byte.
* **Idempotência Atômica:** [`ServiceOrderStoreDao.createOrGetAttendance`](file:///c:/Users/claudio.lima/AndroidStudioProjects/Nexo/app/src/main/java/dev/claudiocodigo/nexo/data/local/dao/ServiceOrderStoreDao.kt) garante que múltiplos cliques em "Iniciar atendimento" retornam exatamente o mesmo UUID local.

### 2. Extração, Validação e Edição Cirúrgica de `.ics`
* **Parser Conservador [`ServiceOrderExtractor`](file:///c:/Users/claudio.lima/AndroidStudioProjects/Nexo/app/src/main/java/dev/claudiocodigo/nexo/domain/serviceorder/ServiceOrderExtractor.kt):** Identifica números de OS oficiais (`OS 15428`), mapeia `????` e `SEM OS` para ausência de número oficial (`null`) e extrai seções estruturadas sem perder o texto original não padronizado.
* **Renderizador Padronizado [`ServiceOrderRenderer`](file:///c:/Users/claudio.lima/AndroidStudioProjects/Nexo/app/src/main/java/dev/claudiocodigo/nexo/domain/serviceorder/ServiceOrderRenderer.kt):** Gera a projeção limpa de `DESCRIPTION` para envio de atualizações e para finalização de OS (com `Estado: Concluído`). A data de execução é formatada no corpo (`dd/MM/yyyy`) e horários permanecem restritos aos campos `DTSTART`/`DTEND`.
* **Editor RFC 5545 [`IcsDocumentEditor`](file:///c:/Users/claudio.lima/AndroidStudioProjects/Nexo/app/src/main/java/dev/claudiocodigo/nexo/data/ical/IcsDocumentEditor.kt):** Modifica exclusivamente `DESCRIPTION`, `SEQUENCE`, `DTSTAMP` e `LAST-MODIFIED`, preservando `VTIMEZONE`, `LOCATION`, `COLOR`, `SUMMARY`, participantes e *line folding* em 75 octetos.

### 3. Cliente CalDAV de Escrita Estritamente Condicional
* **Contrato Seguro [`CalDavWriteClient`](file:///c:/Users/claudio.lima/AndroidStudioProjects/Nexo/app/src/main/java/dev/claudiocodigo/nexo/domain/caldav/CalDavWriteClient.kt):**
  * `ConditionalCreate`: emite `PUT` com `If-None-Match: *`.
  * `ConditionalUpdate`: exige `baseEtag` não vazio e emite `If-Match: "<etag>"`.
  * Resultados tipados: `Created`, `Updated`, `Conflict` (412), `PermissionDenied` (401/403), `TransientFailure` e `PermanentFailure`.
* **Transporte HTTP [`NextcloudCalDavWriteClient`](file:///c:/Users/claudio.lima/AndroidStudioProjects/Nexo/app/src/main/java/dev/claudiocodigo/nexo/data/caldav/NextcloudCalDavWriteClient.kt):** Isolamento de mesma origem, limpeza imediata de credenciais da memória e timeouts seguros.

### 4. Fila Outbox Offline e Agendamento Conectado
* **Outbox Persistente:** Operações registradas na tabela `publication_outbox` através de transação atômica que guarda o snapshot de prévia imutável (`ConfirmedPreviewSnapshot`).
* **Coordenador [`RoomPublicationCoordinator`](file:///c:/Users/claudio.lima/AndroidStudioProjects/Nexo/app/src/main/java/dev/claudiocodigo/nexo/data/publication/RoomPublicationCoordinator.kt):** Faz claim seguro de operações elegíveis com lease de 60s, executa a requisição condicional e atualiza o estado da OS para `PUBLISHED` ou `CONFLICT`.
* **WorkManager [`PublicationWorker`](file:///c:/Users/claudio.lima/AndroidStudioProjects/Nexo/app/src/main/java/dev/claudiocodigo/nexo/data/worker/PublicationWorker.kt) & [`PublicationScheduler`](file:///c:/Users/claudio.lima/AndroidStudioProjects/Nexo/app/src/main/java/dev/claudiocodigo/nexo/data/worker/PublicationScheduler.kt):** Drenagem automática acionada quando o dispositivo obtém conectividade de rede (`NetworkType.CONNECTED`), sem loop cego de retry em caso de conflito 412.

### 5. UI: Editor Estruturado e Prévia de Conferência
* **Editor Unificado [`ServiceOrderEditorScreen`](file:///c:/Users/claudio.lima/AndroidStudioProjects/Nexo/app/src/main/java/dev/claudiocodigo/nexo/ui/screens/oseditor/ServiceOrderEditorScreen.kt):** Seções recolhíveis para Preset, Identificação, Demanda, Atualizações de Campo e Finalização. Autosave com debounce de 500ms e flush imediato ao sair (`ON_STOP`).
* **Sugestões Recentes:** [`RecentServiceOrderPreferences`](file:///c:/Users/claudio.lima/AndroidStudioProjects/Nexo/app/src/main/java/dev/claudiocodigo/nexo/data/preferences/RecentServiceOrderPreferences.kt) lembra último técnico, cliente e unidade preenchidos.
* **Conferência Read-Only [`PublicationPreviewScreen`](file:///c:/Users/claudio.lima/AndroidStudioProjects/Nexo/app/src/main/java/dev/claudiocodigo/nexo/ui/screens/preview/PublicationPreviewScreen.kt):** Exibe a prévia exata do texto que será enviado ao Nextcloud e alerta caso haja divergência entre a data textual e o horário agendado no evento.

### 6. Resolução de Conflitos (HTTP 412)
* **Diferenciação Semântica [`ServiceOrderDiff`](file:///c:/Users/claudio.lima/AndroidStudioProjects/Nexo/app/src/main/java/dev/claudiocodigo/nexo/domain/serviceorder/ServiceOrderDiff.kt):** Compara valores locais com os valores remotos atuais do servidor.
* **Tela de Resolução [`ConflictReviewScreen`](file:///c:/Users/claudio.lima/AndroidStudioProjects/Nexo/app/src/main/java/dev/claudiocodigo/nexo/ui/screens/conflito/ConflictReviewScreen.kt):** Permite ao usuário escolher campo a campo entre `Manter Local` e `Usar Remoto`, renovando o ETag base e retornando ao editor para gerar nova prévia.

---

## 🧪 Validação e Testes

Todos os testes unitários e de compilação passaram com sucesso:
* `./gradlew testDebugUnitTest`: **108 testes executados e 100% aprovados** (cobertura de extração, renderização, edição ICS, escrita CalDAV MockWebServer, coordenação de outbox, ViewModel do editor e resolução de conflitos).
* `./gradlew assembleDebug`: **APK compilado com sucesso**.
