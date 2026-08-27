# Fase 2 — Registro de dependências e licenças (Lote 0)

Objetivo deste arquivo: fixar as dependências da camada CalDAV, registrar licenças
e registrar as decisões de não-cópia de código de terceiros, conforme o Lote 0.

## Dependências aprovadas

### HTTP (WebDAV/CalDAV)

- **OkHttp `4.12.0`** — `com.squareup.okhttp3:okhttp`
  - Licença: Apache License 2.0.
  - Motivo: é necessário emitir `PROPFIND` e `REPORT`, que a
    `HttpURLConnection` do Android não permite. OkHttp ainda controla
    redirecionamentos e remove o cabeçalho `Authorization` em redirect entre
    hosts (propriedade usada pela regra de não vazar credencial).
- **OkHttp MockWebServer `4.12.0`** — `com.squareup.okhttp3:mockwebserver`
  - Licença: Apache License 2.0.
  - Escopo: **somente testes** (`testImplementation`). Usado como servidor
    simulado para provar zero escrita e a allowlist de métodos.

### iCalendar

- **Parser próprio** (nenhuma dependência externa de iCalendar).
  - Decisão: o candidato do `02-RESEARCH.md`, `ical4j` (BSD-3-Clause), não foi
    acoplado ao domínio. A porta de reversibilidade do Lote 0 (tamanho do APK /
    compatibilidade com `minSdk 26` / superfície de dependências) foi acionada:
    para a Fase 2 somente-leitura, um leitor autocontido de RFC 5545 cobre
    `VEVENT`, `SUMMARY`, `DESCRIPTION`, `LOCATION`, `DTSTART`/`DTEND`,
    `UID`, `SEQUENCE`, `COLOR`, `RRULE`/`EXDATE`, all-day e fusos, preservando
    sempre o ICS bruto.
  - Nota de licença: o código do parser é original do projeto Nexo
    (Apache License 2.0) e não copia o cliente open source Nextcloud.
  - Alternativa registrada: `ical4j` (BSD-3-Clause) permanece disponível caso
    uma fase futura precise de semântica completa de recorrência.

### Camera / QR (adicionada somente na tela do scanner — Lote 2)

- Decisão registrada antecipadamente para evitar desvio de escopo no Lote 0:
  - `androidx.camera:camera-core`, `camera-camera2`, `camera-lifecycle`,
    `camera-view` — Apache License 2.0.
  - `com.google.zxing:core` para decodificação QR embarcada — Apache License
    2.0.
  - Escolha evita dependência obrigatória de Google Play Services e funciona
    sem rede depois da instalação.
  - A permissão `CAMERA` só é adicionada quando o scanner existir. Neste
    snapshot, apenas `INTERNET` foi adicionada ao manifesto.

## Regras de licença / não-cópia

- Nenhum código do cliente open source Nextcloud foi incorporado sem revisão de
  licença. O protocolo CalDAV/WebDAV é implementado a partir das RFCs 4791,
  5545, 6578, 6764 e 7986.
- Tratamento de credenciais: nenhuma senha, token ou URL particular entra em
  `git`, logs, relatórios, telas de confirmação ou exports.

## Verificação do Lote 0

- [x] O cliente rejeita `PUT`/`DELETE`/`POST`/`PATCH` (allowlist de métodos).
- [x] Fixture ICS passa no parser (folding, escapes, acentos, `COLOR`,
      recorrência, all-day, fuso, UTC).
- [x] `testDebugUnitTest`, `assembleDebug`, `assembleDebugAndroidTest` e
      `lintDebug` passam.
