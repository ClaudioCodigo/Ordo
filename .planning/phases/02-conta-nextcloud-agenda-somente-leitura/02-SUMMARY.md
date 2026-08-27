# Fase 2 — Resumo: conta Nextcloud e agenda somente leitura

## Entrega

A Fase 2 conecta o Nexo a uma conta Nextcloud por QR (`nc://login/...`) ou
configuração manual, descobre as agendas, permite selecionar uma agenda de
trabalho e importa os eventos para um espelho Room somente leitura, exibidos em
Hoje/Agenda com título, data, local, UID, ETag e classificação de cor. Nenhuma
função de escrita remota existe ou é acionada.

## O que foi implementado

### Lote 0 — Contrato de segurança e dependências

- Interfaces `CredentialStore`, `CalDavDiscoveryClient`, `CalDavReadClient`,
  `CalendarRepository` e `CalendarSyncCoordinator` sem método mutante.
- Allowlist de métodos HTTP: `OPTIONS`, `PROPFIND`, `REPORT`, `GET`, `HEAD`;
  qualquer outro método falha antes de sair do aparelho (guarda + interceptor).
- Parser iCalendar autocontido (RFC 5545), preservando o ICS bruto; teste em
  fixtures cobre folding, escapes, acentos, `COLOR`, recorrência, all-day, fuso
  e UTC.
- Dependências registradas com licenças em `03-DEPENDENCIES.md` (OkHttp/MockWebServer
  Apache-2.0; parser próprio; CameraX/ZXing Apache-2.0).
- Permissões: `INTERNET` e, com o scanner, `CAMERA` (com `uses-feature` opcional).

### Lote 1 — Modelo remoto e migração Room 1→2

- Entidades `calendar_accounts`, `calendars`, `remote_events`,
  `calendar_sync_state`, com `ServiceOrderEntity` mantida separada.
- Evento identificado por conta + calendário + `href`; `uid` indexado e não
  único — colisão de UID em `href` distintos preserva ambos.
- Migração explícita 1→2 (validação JVM de consistência com o schema v2 exportado)
  e teste instrumentado que confirma sobrevivência dos rascunhos.

### Lote 2 — Conta, QR/manual e armazenamento seguro

- Tela Conta Nextcloud (desconectado / validando / conectado), entrada manual e
  colar QR.
- Scanner QR com CameraX + ZXing (sem Google Play Services).
- Normalização/validação HTTPS (sem credencial embutida), parser QR
  ordem-independente e com percent-encoding.
- Senha cifrada AES-GCM/Android Keystore; material excluído de backup; chave
  ausente/corrompida tratada sem crash; desconexão apaga segredo, conta e cache
  remoto sem tocar nos rascunhos.

### Lote 3 — Descoberta CalDAV e seleção

- Parser XML namespace-aware (sem regex/prefixo textual) para `multistatus`,
  `current-user-principal`, `calendar-home-set` e listas de calendário.
- Descoberta por `/.well-known/caldav`, principal e home-set; lista apenas
  coleções com suporte a `VEVENT`, preservando nome, descrição, cor, privilégios
  e `sync-token`.
- Persistência local das agendas e seleção de exatamente uma agenda de trabalho
  (troca limpa o cache remoto da seleção anterior).

### Lote 4 — Tracer de importação somente leitura

- `CalDavReadClient` (PROPFIND `href`/`getetag`, REPORT calendar-multiget,
  `sync-token`), sem escrita remota.
- Mapeamento ICS → `RemoteEvent` preservando raw; classificação `#008000` →
  Validado, `#B22222` → Requer atenção, demais → Não classificado.
- `CalendarSyncCoordinator` escreve apenas no cache local, em lote transacional,
  e publica estado de sincronização somente após o commit.

### Lote 5 — Sincronização incremental e robustez offline

- Sincronização incremental RFC 6578 por `sync-token` (`REPORT sync-collection`)
  com changed/deleted explícitos e fallback seguro por comparação de
  `href`+`ETag` quando o token é inválido, expirado ou não suportado;
  remove do espelho apenas recursos informados como ausentes.
- `WorkManager` periódico e manual com restrição de rede; guarda de concorrência
  (uma sincronização por agenda); 401/403 mantém o cache e marca reconexão;
  falha de rede/lote conserva a última visão consistente.

### Lote 6 — Hoje e estados reais

- Hoje unifica cartões locais e eventos remotos sem fundir os registros;
  evento remoto abre detalhe somente leitura.
- Estado de sincronização real (horário, erro, reconexão) — sem horário ou
  sucesso fictício.
- Rascunhos locais continuam criando/editorando/excluindo como aprovado.

## Verificações

- Validação final aprovada: `testDebugUnitTest` passou com 85 testes e zero
  falhas; `assembleDebug`, `assembleDebugAndroidTest` e `lintDebug` também
  passaram.
- Testes unitários: allowlist HTTP, parser ICS, classificação de cor, parser QR,
  normalização HTTPS, modelo de credencial, parser XML, descoberta/leitura com
  MockWebServer (zero escrita), mapper, migração 1→2 e políticas de segurança.
- Testes instrumentados (compilados; executar em aparelho): migração 1→2
  preservando rascunhos, e cifra/de-cifra/apagar do Keystore.
- RRULE, EXDATE, RECURRENCE-ID e o ICS bruto são preservados; ocorrências
  recorrentes ainda não são expandidas.
- Nenhuma credencial real ou servidor Nextcloud real foi usado nos testes.

## Validação real em aparelho

- Aprovados em 27/08/2026: conexão por credencial Nextcloud, descoberta das
  agendas, seleção explícita da agenda de trabalho e leitura dos eventos reais.
- A tela Hoje e a Agenda foram ajustadas para ordenar do mais recente ao mais
  antigo; eventos históricos com 30 dias completos ou mais não poluem as
  seções Requer atenção/Atrasados de Hoje, mas permanecem consultáveis na Agenda.

## Cenários residuais da UAT

- Confirmar que nenhum recurso remoto foi criado, alterado, recolorido ou
  excluído (comparar `href`/`ETag` antes e depois).
- Revogar a senha temporária e confirmar que o app preserva o cache e informa
  reconexão sem loop.
