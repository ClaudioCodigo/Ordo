# Fase 3: Ordens de Serviço e publicação controlada - Research

**Pesquisado em:** 2026-08-27  
**Domínio:** Android offline-first, escrita CalDAV condicional, edição lossless de iCalendar e outbox persistente  
**Confiança:** ALTA para a arquitetura interna; MÉDIA para interoperabilidade do servidor real até a UAT

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

#### Início do atendimento e vínculo
- **D-01:** Tocar no cartão abre primeiro o evento remoto em modo somente leitura. A cópia de trabalho nasce apenas após a ação explícita `Iniciar atendimento`.
- **D-02:** Após existir vínculo, o detalhe remoto oferece `Continuar atendimento` e abre sempre a mesma OS local; não cria rascunhos duplicados. Hoje e Agenda representam o vínculo como um único fluxo operacional, embora os registros remoto e local permaneçam separados no banco.
- **D-03:** A identidade do vínculo usa conta + calendário + `href`; `UID + RECURRENCE-ID` identifica uma ocorrência de série sem alterar a série inteira. `UID` isolado não é chave única.
- **D-04:** Ao iniciar atendimento, o editor abre já preenchido. `SUMMARY` é a fonte principal de número da OS, técnico, setor/categoria e título curto; `DESCRIPTION` é a fonte principal de conteúdo da solicitação e demais campos reconhecidos. Extração é determinística, nunca por LLM, e campos ambíguos exigem conferência.
- **D-05:** A cópia local guarda snapshot remoto e `ETag`. Se a versão remota mudar antes de continuar o atendimento, o rascunho não é alterado: o app refaz a extração e apresenta somente campos novos/diferentes, com `Manter meu valor` ou `Usar valor do calendário`; após a decisão, atualiza a base e permite edição local normal.
- **D-06:** A descrição originalmente recebida, mesmo como texto corrido e sem padrão, entra inteira no campo de origem definido pelo preset (`Demanda`, `Solicitação` ou `Problema relatado`). O app desfaz folding/escapes do ICS para exibição, mas preserva o texto e o ICS brutos.

#### Editor estruturado e projeção textual
- **D-07:** O editor será uma tela única com seções recolhíveis: Identificação, Demanda/Solicitação, Atualizações, Pendências, Itens/Equipamentos, Finalização e Prévia. A tela compartilha um único estado/ViewModel, mas cada seção deve ser um componente separado para manutenção.
- **D-08:** Um preset explícito no topo define a semântica e os rótulos. A primeira entrega contém ao menos `Diagnóstico/correção` (`Demanda`, `Problema identificado`, `Causa`, `Solução executada`) e `Serviço solicitado` (`Solicitação`, sem problema/causa obrigatórios, `Ação executada`). Detecção pelo `SUMMARY` pode sugerir, mas não selecionar silenciosamente.
- **D-09:** A OS pode conter uma lista opcional e repetível de registros genéricos de item/equipamento, com ação, tipo, marca, modelo, número de série, equipamento relacionado, localização e observação. A fase implementa somente o formulário básico e sua formatação; bateria, nobreak e rede especializados ficam para fases futuras.
- **D-10:** A prévia é somente leitura e sempre regenerada a partir dos campos estruturados. Correções são feitas no formulário; não existe editor livre do texto final nesta fase.
- **D-11:** Data de execução aparece no `DESCRIPTION`; horário permanece somente em `DTSTART`/`DTEND`. Divergências são alertadas e nunca corrigidas silenciosamente.

#### Publicação e fila offline
- **D-12:** Autosave local não tem botão concorrente. Uma barra fixa inferior apresenta exatamente a ação remota aplicável ao estado: `Publicar no calendário` cria o recurso da OS provisória, `Enviar atualização` atualiza evento existente e `Finalizar OS` publica a consolidação e conclui internamente.
- **D-13:** Toda criação, atualização ou finalização exige prévia completa de texto, data, horário e calendário de destino, seguida de confirmação explícita.
- **D-14:** Uma publicação confirmada offline cria uma operação imutável contendo exatamente a versão conferida. Edições posteriores permanecem no rascunho e não alteram a operação; substituir uma operação pendente exige cancelamento ou nova prévia/confirmação explícita.
- **D-15:** Antes de enviar item da fila, o app revalida a base remota. Mudança de `ETag` bloqueia o envio como conflito; `412` nunca recebe retry automático. Falha de rede continua pendente.
- **D-16:** Cada cartão exibe `Aguardando conexão`, `Enviando`, `Falhou` ou `Conflito`. Em `Mais`, uma Central de sincronizações lista pendentes, andamento, falhas, conflitos e histórico recente, permitindo tentar novamente ou cancelar o que ainda não foi enviado.

#### Conflitos, compartilhamento e estados operacionais
- **D-17:** A resolução manual mostra `Versão remota` e `Versão local` por campo estruturado; a descrição completa pode ser expandida. Texto remoto não padronizado é comparado semanticamente como o campo de origem, não por igualdade de linhas. Não há mesclagem automática de três vias nesta fase.
- **D-18:** Após escolher valores no conflito, o técnico pode continuar editando e publicar sua versão. A publicação usa o `ETag` renovado e, se houver outra mudança concorrente, bloqueia novamente e exige nova revisão.
- **D-19:** Finalização local produz `Aguardando validação externa`, explicado como serviço concluído no Nexo e aguardando fechamento no sistema/calendário. Verde muda para `Validada externamente`; o Nexo nunca aplica verde.
- **D-20:** Se uma OS concluída/validada ficar vermelha, o estado passa para `Requer atenção`, preservando conclusão e histórico. `Reabrir atendimento` cria uma nova etapa de atualização na mesma OS; não reabre automaticamente.
- **D-21:** Cores são sinais configuráveis, não inferências rígidas. Os verdes observados (`#008000`, `#228B22`, `#32CD32`, `green`, `darkolivegreen`) iniciam mapeados como validação; `#B22222` como requer atenção; azuis (`#4682B4`, `#00679E`) são neutros/em aberto; `#9370DB` permanece sem estado mapeado. Cores originais são preservadas e nunca escritas pelo Nexo nesta fase.
- **D-22:** Eventos compartilhados/convidados preservam `ORGANIZER`, `ATTENDEE`, `PARTSTAT`, `RSVP` e propriedades desconhecidas. Atendimento local é permitido; antes da publicação, o app verifica capacidade/permissão do recurso e, se o servidor negar, mantém o rascunho e mostra `Sem permissão para atualizar este evento`.

### the agent's Discretion
- Microcopy secundária, ícones, animações e ordem exata dos campos dentro de cada seção, desde que não escondam ações remotas nem criem uma segunda fonte de texto.
- Forma técnica de modularizar os composables e o estado do editor, preservando uma tela única e componentes testáveis.
- Quantidade e retenção exatas do histórico recente exibido na Central de sincronizações, sem apagar o histórico canônico das versões da OS.

### Deferred Ideas (OUT OF SCOPE)
- Mesclagem automática de campos não concorrentes, editor avançado de três vias, descarte/substituição ampliados, monitoramento periódico do editor e notificações locais: Fase 4.
- Formulários especializados e integração de diagnóstico para baterias, nobreaks, rede e ativos: Fase 5.
- Relatórios e consultas históricas amplas: Fase 6.
- Inferir `#9370DB` como `Autosserviço`: adiado até existir evidência operacional; por enquanto a cor permanece não mapeada.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|---|---|---|
| OS-01 | Extrair campos confiáveis do `SUMMARY` sem assumir quantidade fixa de segmentos. | Parser determinístico com confiança por campo, preservação do bruto e fixtures de separadores extras. |
| OS-02 | Número oficial da OS é opcional; atendimento provisório usa UUID interno e `SEM OS` visual. | Identidade local independente e UID/href estáveis na publicação. |
| OS-03 | Criar atendimento provisório no calendário imediatamente quando houver internet e manter fila offline quando não houver. | Outbox imutável em Room, worker único e reconciliação idempotente após resposta perdida. |
| OS-04 | Formulário retém técnico, empresa/unidade e seleções repetidas. | Estado estruturado no Room; DataStore apenas para preferências recentes não canônicas. |
| OS-05 | Data de execução é automática, editável e obrigatória na descrição; horário usa `DTSTART`/`DTEND`. | Renderer puro e validador de divergência antes da confirmação. |
| OS-06 | Atualização preserva demanda e contexto sem formar uma montanha de texto. | Atualizações normalizadas por UUID e projeção textual determinística. |
| OS-07 | Finalização gera descrição consolidada com demanda, resultado, concluídos e pendências. | Renderer de finalização separado, sem cronologia remota, mantendo entidades locais. |
| OS-08 | Versões anteriores ficam no histórico local. | Snapshots e versões imutáveis, separados do rascunho mutável e do cache remoto. |
</phase_requirements>

## Summary

A fase deve ser planejada como quatro slices conectados: (1) modelo local estruturado e vínculo idempotente; (2) parser/renderizador e editor iCalendar lossless; (3) escritor CalDAV estreito com precondições; (4) outbox persistente, revisão de conflito e UI. Essa ordem permite provar preservação e idempotência antes de habilitar qualquer `PUT`. [VERIFIED: `.planning/phases/03-ordens-de-servico-publicacao-controlada/03-SPEC.md:19-57`, critérios e limites lidos nesta sessão]

O maior gap do código atual é recorrência: `remote_events` tem chave primária `"accountId", "calendarHref", "href"`, enquanto o mapper escolhe o mestre com `"firstOrNull { it.recurrenceId == null }"`. Um recurso CalDAV pode conter o mestre e exceções com o mesmo UID; portanto, a fase precisa manter o recurso bruto separado das ocorrências derivadas e selecionar o `VEVENT` alvo por UID + RECURRENCE-ID. [VERIFIED: `app/src/main/java/dev/claudiocodigo/nexo/data/local/entity/RemoteEventEntity.kt:17-30`; `app/src/main/java/dev/claudiocodigo/nexo/data/caldav/RemoteEventMapper.kt:21-25`] [CITED: https://www.rfc-editor.org/rfc/rfc4791.html]

O segundo gap é round-trip: o parser atual preserva `rawIcs`, mas seu modelo tipado não retém a lista ordenada de propriedades desconhecidas, nem `ORGANIZER`/`ATTENDEE`. Reconstruir o evento apenas a partir de `IcsEvent` apagaria conteúdo. O plano deve introduzir uma representação tokenizada lossless e editar cirurgicamente somente as quatro propriedades autorizadas. [VERIFIED: `app/src/main/java/dev/claudiocodigo/nexo/data/ical/IcsModel.kt:10-37`, quote: `"uid", "summary", "description", "location", "dtStart", "dtEnd", "allDay", "color", "sequence", "status", "dtStamp", "lastModified", "recurrenceRule", "recurrenceExceptionDates", "recurrenceId", "duration", "classValue", "transparency"`] [CITED: https://www.rfc-editor.org/rfc/rfc5545.html]

**Primary recommendation:** não habilitar `PUT` até existirem testes de round-trip lossless, migração 2→3, criação/atualização idempotentes e bloqueio comprovado de todo envio sem precondição.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|---|---|---|---|
| Formulário, prévia, revisão por campo | UI Compose | ViewModel | A UI apenas emite eventos e renderiza estado imutável. [CITED: https://developer.android.com/develop/ui/compose/state-hoisting] |
| Regras de extração/renderização/estado | Domínio | — | Devem ser funções Kotlin puras, independentes de Android e rede, para testes rápidos. [VERIFIED: padrão atual de modelos/repositórios em `app/src/main/java/dev/claudiocodigo/nexo/domain/`] |
| Rascunho, vínculo, atualizações, versões, outbox | Room / storage | Domínio | Room continua como fonte local e precisa tornar unicidade/idempotência atômicas. [VERIFIED: `app/src/main/java/dev/claudiocodigo/nexo/data/repository/RoomServiceOrderRepository.kt:12-28`] |
| Leitura e revalidação remota | Cliente CalDAV read-only existente | Coordenador | O contrato atual não expõe métodos mutantes. Quote: `"the client cannot publish, edit, colorize or delete remote resources"`. [VERIFIED: `app/src/main/java/dev/claudiocodigo/nexo/domain/caldav/CalDavReadClient.kt:3-9`] |
| PUT condicional | Novo cliente escritor estreito | OkHttp | Deve expor somente create/update e exigir precondição no tipo/chamada. [CITED: https://www.rfc-editor.org/rfc/rfc9110.html] |
| Drenagem offline | WorkManager | Outbox Room | WorkManager agenda; Room decide exatamente qual operação existe e seu estado. [CITED: https://developer.android.com/topic/architecture/data-layer/offline-first] |
| Permissão e scheduling compartilhado | Servidor CalDAV/WebDAV | Cliente | O app consulta privilégios, preserva scheduling e trata a resposta do servidor como autoridade final. [CITED: https://www.rfc-editor.org/rfc/rfc3744.html] [CITED: https://www.rfc-editor.org/rfc/rfc6638.html] |

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---|---:|---|---|
| Kotlin | 2.2.10 | Domínio, coroutines e UI | Já é a linguagem do app; não fazer upgrade durante esta fase. [VERIFIED: `gradle/libs.versions.toml:9`, quote: `kotlin = "2.2.10"`] |
| Room | 2.7.0 | Fonte local, histórico e outbox | Já exporta schema e possui infraestrutura de migração/teste. [VERIFIED: `gradle/libs.versions.toml:19-22`, quote: `roomRuntime = "2.7.0"`, `roomKtx = "2.7.0"`, `roomCompiler = "2.7.0"`, `roomTesting = "2.7.0"`] |
| WorkManager | 2.11.2 | Drenar fila quando conectado | Já está integrado e suporta unique work, constraints e retry. [VERIFIED: `gradle/libs.versions.toml:32`, quote: `workRuntimeKtx = "2.11.2"`] [CITED: https://developer.android.com/reference/androidx/work/WorkManager.html] |
| OkHttp + MockWebServer | 4.12.0 | HTTP CalDAV e testes de contrato | Já implementa origin checks, redirects manuais e testes sem servidor real. [VERIFIED: `gradle/libs.versions.toml:36`, quote: `okhttp = "4.12.0"`] |
| Compose Material 3 + Navigation 3 | BOM 2024.09.00 / 1.0.1 | Editor, prévia, conflito e central | Mantém a arquitetura visual aprovada. [VERIFIED: `gradle/libs.versions.toml:10,13-14`, quote: `composeBom = "2024.09.00"`, `navigation3Ui = "1.0.1"`, `navigation3Runtime = "1.0.1"`] |

### Supporting

| Library | Version | Purpose | When to Use |
|---|---:|---|---|
| Coroutines test | 1.10.2 | Testar autosave, concorrência e workers | Unit tests de coordenadores/ViewModels. [VERIFIED: `gradle/libs.versions.toml:23`, quote: `kotlinxCoroutinesTest = "1.10.2"`] |
| Room testing | 2.7.0 | Validar 1→3 e 2→3 | Testes instrumentados de migração e DAOs. [CITED: https://developer.android.com/reference/androidx/room/testing/MigrationTestHelper] |
| JUnit 4 | 4.13.2 | Suíte existente | Preservar o runner atual. [VERIFIED: `gradle/libs.versions.toml:4`, quote: `junit = "4.13.2"`] |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|---|---|---|
| Editor lossless sobre o parser existente | Biblioteca iCalendar externa | Uma biblioteca pode validar mais RFC, mas uma reserialização não garante preservação textual/propriedades privadas exigida pela SPEC sem um spike específico. Não adicionar dependência nesta fase. [ASSUMED] |
| Uma fila Room drenada por worker único | Um WorkRequest por publicação | O segundo acopla identidade ao banco interno do WorkManager e dificulta reconciliação, cancelamento e histórico; Room deve ser a autoridade. [CITED: https://developer.android.com/topic/architecture/data-layer/offline-first] |
| Estado de formulário estruturado | Texto final editável | Contraria a prévia somente leitura e cria duas fontes de verdade. [VERIFIED: `.planning/phases/03-ordens-de-servico-publicacao-controlada/03-CONTEXT.md`, D-10] |

**Installation:** nenhuma dependência nova é necessária. Preserve o catálogo atual; não faça upgrade oportunista junto da escrita CalDAV.

## Package Legitimacy Audit

Esta fase não instala pacote externo novo. O gate de legitimidade não se aplica; todas as recomendações usam dependências já presentes no catálogo. [VERIFIED: `app/build.gradle.kts:49-103`; `gradle/libs.versions.toml:40-98`]

## Architecture Patterns

### System Architecture Diagram

```text
Hoje / Agenda ──abre──> Detalhe remoto somente leitura
                             │
                   Iniciar/Continuar atendimento
                             ▼
              vínculo único + snapshot + ETag (Room)
                             │
                             ▼
           Editor estruturado ──autosave──> Room
                │                          │
                └──render puro──> Prévia  │
                                  │ confirmar
                                  ▼
                         versão + outbox imutável
                                  │
                   WorkManager com rede disponível
                                  │
                 revalidar GET/ETag/permissão
                     │ igual              │ mudou/412
                     ▼                    ▼
              CalDavWriteClient     conflito persistido
              PUT condicional       remoto × local
                     │                    │ nova revisão
                     ▼                    └──> nova prévia/outbox
              sync read-only atual
                     ▼
             cache remoto + cartões + estado externo
```

### Recommended Project Structure

```text
domain/
├── serviceorder/       # modelos, presets, validação, extração, renderer, diff
├── publication/        # contratos de preview/outbox/conflito
└── caldav/             # contrato estreito CalDavWriteClient
data/
├── local/entity/       # vínculos, updates, items, versions, outbox, occurrences
├── ical/               # tokenização lossless, selector e editor de VEVENT
├── caldav/             # writer OkHttp condicional e mapper de erros
└── worker/             # PublicationWorker + scheduler único
ui/screens/
├── oseditor/           # tela e seções stateless
├── preview/            # prévia completa somente leitura
├── conflito/           # revisão manual por campo
└── sincronizacoes/     # central em Mais
```

### Pattern 1: recurso remoto separado de ocorrência

Mantenha uma linha canônica por recurso WebDAV (`account + calendar + href`, com ETag e ICS bruto) e derive componentes/ocorrências em tabela separada. A chave da ocorrência inclui o recurso e o RECURRENCE-ID normalizado; o master usa ausência explícita de RECURRENCE-ID. O vínculo da OS aponta para ambos. Isso evita que uma exceção substitua o master ou que uma edição seja aplicada à série inteira. [CITED: https://www.rfc-editor.org/rfc/rfc4791.html] [CITED: https://www.rfc-editor.org/rfc/rfc5545.html]

O código atual não suporta essa cardinalidade: quote `primaryKeys = ["accountId", "calendarHref", "href"]` e quote `firstOrNull { it.recurrenceId == null }`. [VERIFIED: `app/src/main/java/dev/claudiocodigo/nexo/data/local/entity/RemoteEventEntity.kt:17-20`; `app/src/main/java/dev/claudiocodigo/nexo/data/caldav/RemoteEventMapper.kt:21-25`]

Não expandir toda RRULE nesta fase. Indexar o master e os componentes explicitamente presentes no recurso cobre exceções exportadas sem introduzir um motor de recorrência; expansão completa continua uma capacidade distinta. [ASSUMED]

### Pattern 2: editor iCalendar lossless e cirúrgico

O editor deve tokenizar limites de componentes e content lines preservando a forma física original. Para atualizar uma ocorrência, localize exatamente o VEVENT por UID + RECURRENCE-ID e substitua apenas DESCRIPTION, SEQUENCE, DTSTAMP e LAST-MODIFIED. Todo o restante — inclusive VTIMEZONE, ORGANIZER, ATTENDEE, VALARM, ATTACH, X-properties, parâmetros, ordem e ausência de COLOR/LOCATION — atravessa intacto. [VERIFIED: `.planning/phases/03-ordens-de-servico-publicacao-controlada/03-SPEC.md:44-47`] [CITED: https://www.rfc-editor.org/rfc/rfc5545.html]

Ao emitir uma linha nova, escape TEXT na ordem lógica (barra, newline, ponto-e-vírgula, vírgula) e faça folding por octetos UTF-8, nunca por quantidade de caracteres; RFC 5545 recomenda linhas até 75 octetos e continuação com CRLF + espaço. [CITED: https://www.rfc-editor.org/rfc/rfc5545.html]

Para eventos provisórios novos, prefira DTSTART/DTEND UTC derivados do fuso escolhido e um UID estável gerado uma única vez; isso evita fabricar VTIMEZONE. Para eventos existentes, preserve exatamente a representação de horário recebida. [ASSUMED]

### Pattern 3: parser determinístico com confiança por campo

`SUMMARY` deve sempre manter o bruto. Separe pelo delimitador observado, reconheça apenas posições/âncoras confiáveis e associe um estado de confiança a cada extração. `????` e `SEM OS` representam ausência do número; segmentos extras não podem ser descartados nem automaticamente atribuídos quando o título/local for ambíguo. [VERIFIED: `.planning/phases/03-ordens-de-servico-publicacao-controlada/03-SPEC.md:19-22,97-99`]

`DESCRIPTION` já formatado pode ser lido por labels conhecidos usando o primeiro `:` como separador. Se não houver estrutura confiável, o texto inteiro vira o campo de origem do preset. Nunca use `split(':').last`, normalização destrutiva de acentos ou uma LLM. [VERIFIED: `../TEDFieldServices/CALDAV-DATA-FORMAT.md`, problema conhecido 7.2, referência doadora lida nesta sessão] [VERIFIED: `.planning/phases/03-ordens-de-servico-publicacao-controlada/03-CONTEXT.md`, D-04 e D-06]

### Pattern 4: renderer puro e versões imutáveis

Renderize a prévia como função pura do snapshot estruturado. Atualizações têm UUID próprio e ordem estável por data do atendimento, criação e UUID; textos iguais continuam registros distintos, mas cada ID aparece uma vez. A finalização usa outro renderer que omite a cronologia remota sem apagar atualizações locais. [VERIFIED: `.planning/phases/03-ordens-de-servico-publicacao-controlada/03-SPEC.md:34-42,102-104`]

Confirmar uma prévia deve, numa única transação Room, gravar a versão publicada pretendida e uma operação imutável que referencia essa versão. Alterações posteriores só mudam o rascunho. [VERIFIED: `.planning/phases/03-ordens-de-servico-publicacao-controlada/03-CONTEXT.md`, D-13 e D-14]

### Pattern 5: outbox idempotente com reconciliação

Use uma tabela de outbox como verdade canônica e um único unique work para drená-la. O worker reivindica uma operação em transação, lê o payload confirmado imutável, revalida base/permissão e executa no máximo um PUT por tentativa. WorkManager não deve carregar o ICS completo; use apenas o ID da operação. [CITED: https://developer.android.com/topic/architecture/data-layer/offline-first] [CITED: https://developer.android.com/reference/androidx/work/WorkManager.html]

Criação usa URI determinística baseada no UID estável e `If-None-Match: *`; atualização usa `If-Match` com o ETag confirmado. [CITED: https://www.rfc-editor.org/rfc/rfc9110.html] [CITED: https://www.rfc-editor.org/rfc/rfc4791.html]

Há um caso obrigatório de idempotência: o PUT pode ter sido aceito e a resposta perdida. No retry, um 412 não prova conflito externo. Faça GET do alvo; se o UID e a projeção mutável/digest corresponderem exatamente ao payload confirmado, marque sucesso reconciliado. Caso contrário, persista conflito. Nunca repita automaticamente o PUT após 412. [ASSUMED, derivado dos critérios de idempotência e da semântica de 412]

Falha de rede/timeout mantém pendente e permite `Result.retry()`. 401 exige reconexão; 403/permissão é falha permanente visível; 412 vira estado de conflito persistido e o worker retorna sucesso para não criar retry automático. Recupere operações abandonadas em `Enviando` por lease/timestamp após processo morto. [CITED: https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work] [VERIFIED: `.planning/phases/03-ordens-de-servico-publicacao-controlada/03-CONTEXT.md`, D-15 e D-16]

### Pattern 6: escritor separado, estreito e condicionado

Não altere `HttpMethodAllowlist.ALLOWED`, cujo valor atual é quote `setOf("OPTIONS", "PROPFIND", "REPORT", "GET", "HEAD")`. Crie outro cliente sem API genérica, expondo somente criação e atualização condicionais; não exponha DELETE, mudança de cor ou PUT sem precondição. [VERIFIED: `app/src/main/java/dev/claudiocodigo/nexo/data/caldav/HttpMethodAllowlist.kt:9-24`]

Reaproveite as proteções do cliente atual: HTTPS/origem esperada, redirecionamento manual same-origin, timeout e credencial apenas após checagem de origem. Não reutilize o interceptor read-only. [VERIFIED: `app/src/main/java/dev/claudiocodigo/nexo/data/caldav/CalDavHttpClient.kt:20-23,68-95,108-115`]

O escritor deve interpretar 200/201/204 como sucesso e capturar o ETag de resposta; se ausente, faça leitura do recurso antes de atualizar o cache/versão publicada. Mapeie corpos DAV de 403/409 para mensagem útil sem incluir conteúdo sensível. [CITED: https://www.rfc-editor.org/rfc/rfc4791.html] [ASSUMED para o fallback de ETag]

### Pattern 7: privilégio como dica, resposta como autoridade

O parser atual reduz privilégios a `entry.privileges.any { it.contains("write") }`, insuficiente para distinguir update (`write-content`) de create (`bind` no pai). Preserve o conjunto real de privilégios descobertos e verifique a operação específica. Ainda assim, a resposta do PUT é a autoridade porque servidores podem impor controles adicionais. [VERIFIED: `app/src/main/java/dev/claudiocodigo/nexo/data/caldav/CalDavXmlParser.kt:85-98,133-139`] [CITED: https://www.rfc-editor.org/rfc/rfc3744.html]

Eventos com ORGANIZER/ATTENDEE são scheduling objects: um PUT pode causar mensagens e regras próprias do servidor. Preserve todas essas propriedades; não edite PARTSTAT/RSVP e não implemente iTIP. RFC 6638 permite `If-Schedule-Tag-Match`, mas a decisão da fase exige ETag para detectar qualquer mudança; capture schedule-tag apenas se vier disponível, sem usá-lo para contornar If-Match. [CITED: https://www.rfc-editor.org/rfc/rfc6638.html]

### Pattern 8: conflito como novo ciclo, não mutação do envio antigo

Ao detectar mudança antes de editar ou um 412, persista três referências: base usada, versão local confirmada e remoto atual. Mostre diferenças por campo; a descrição integral é apenas apoio. Uma escolha manual atualiza o rascunho/base, mas não altera o payload histórico da operação bloqueada. Nova publicação exige nova prévia, confirmação e nova operação com ETag renovado. [VERIFIED: `.planning/phases/03-ordens-de-servico-publicacao-controlada/03-CONTEXT.md`, D-05, D-17 e D-18]

### Anti-Patterns to Avoid

- **Liberar PUT no cliente read-only:** derruba a garantia de zero escrita da Fase 2 e cria caminhos não auditados.
- **Reconstruir o ICS pelo modelo tipado atual:** apaga propriedades desconhecidas, participantes, alarmes e componentes irmãos.
- **Tratar href como ocorrência:** um href pode armazenar mestre e exceções recorrentes.
- **Usar WorkManager como banco da fila:** perde domínio, histórico e reconciliação transacional.
- **Atualizar a linha pendente com o rascunho atual:** viola a prévia confirmada e imutável.
- **Retornar retry em 412/403:** transforma conflito/permissão em loop automático.
- **Marcar publicado antes da confirmação remota/reconciliação:** apresenta uma versão desatualizada como aceita.
- **Inferir permissão só por cor, ORGANIZER ou nome da agenda:** somente privilégios e resposta HTTP são relevantes.
- **Fazer merge automático:** está explicitamente adiado para a Fase 4.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---|---|---|---|
| Persistência/concorrência da fila | Arquivo JSON, singleton ou flags em memória | Room + transações + índices únicos | Precisa sobreviver processo/reboot e garantir unicidade atômica. [CITED: https://developer.android.com/topic/architecture/data-layer/offline-first] |
| Agendamento de rede | BroadcastReceiver próprio/polling | WorkManager com NetworkType.CONNECTED | Respeita restrições e trabalho persistente. [CITED: https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work] |
| Condicionais HTTP | Comparação de timestamp/SEQUENCE no cliente | If-Match / If-None-Match e ETag do servidor | A precondição precisa ser atômica no servidor. [CITED: https://www.rfc-editor.org/rfc/rfc9110.html] |
| Scheduling de convidados | Envio iTIP manual ou alteração de participantes | Preservar propriedades e deixar o servidor CalDAV processar | RFC 6638 atribui operações de scheduling ao servidor. [CITED: https://www.rfc-editor.org/rfc/rfc6638.html] |
| Motor completo de RRULE | Expansor recorrente improvisado | Somente componentes explícitos nesta fase; spike futuro com biblioteca se expansão virar requisito | Regras, exceções, DST e RANGE são um domínio separado. [ASSUMED] |
| Reescrita do texto recebido | Regex destrutiva ou LLM | Extração conservadora + bruto preservado + confirmação | O usuário definiu preservação e determinismo. [VERIFIED: `.planning/phases/03-ordens-de-servico-publicacao-controlada/03-CONTEXT.md`, D-04 e D-06] |

**Key insight:** o código customizado aceitável aqui é um editor mínimo lossless sobre o ICS já preservado, não um novo servidor CalDAV, motor de recorrência ou protocolo de scheduling.

## Common Pitfalls

### Pitfall 1: resposta perdida após PUT bem-sucedido
**What goes wrong:** o retry recebe 412 e parece conflito, ou cria duplicata com outro href.  
**How to avoid:** UID/href estáveis e GET de reconciliação antes de classificar 412.  
**Warning signs:** duas operações para a mesma versão ou novo UID a cada tentativa. [ASSUMED]

### Pitfall 2: folding por caracteres
**What goes wrong:** acentos UTF-8 são partidos no meio e o servidor rejeita/corrompe texto.  
**How to avoid:** contar octetos e nunca dividir code point; preservar linhas não modificadas. [CITED: https://www.rfc-editor.org/rfc/rfc5545.html]

### Pitfall 3: exceção recorrente editando o master
**What goes wrong:** todas as ocorrências mudam ou a OS errada recebe DESCRIPTION.  
**How to avoid:** componente alvo por UID + RECURRENCE-ID e teste com dois VEVENTs no mesmo VCALENDAR. [CITED: https://www.rfc-editor.org/rfc/rfc4791.html]

### Pitfall 4: opcional ausente sendo fabricado
**What goes wrong:** atualização adiciona COLOR/LOCATION vazio, alterando semântica e UI.  
**How to avoid:** editor cirúrgico; asserts de ausência, não só igualdade de valor. [VERIFIED: `.planning/phases/03-ordens-de-servico-publicacao-controlada/03-SPEC.md:44-47`]

### Pitfall 5: conflito apagando o rascunho
**What goes wrong:** carregar remoto no mesmo objeto editável substitui texto local.  
**How to avoid:** tabelas/objetos separados para base, draft, remote-current e published-version; decisão manual cria nova revisão. [VERIFIED: `.planning/PROJECT.md`, princípio de três versões]

### Pitfall 6: privilégio genérico falso-positivo
**What goes wrong:** `write-properties` é interpretado como permissão para PUT de conteúdo ou create.  
**How to avoid:** preservar nomes exatos; update exige write-content, create exige bind no pai; sempre tratar 403. [CITED: https://www.rfc-editor.org/rfc/rfc3744.html]

### Pitfall 7: operação presa em Enviando
**What goes wrong:** processo morre depois do claim e a fila nunca retoma.  
**How to avoid:** lease com timestamp, claim transacional e recuperação de lease expirado. [ASSUMED]

### Pitfall 8: histórico duplicado por sync
**What goes wrong:** cada leitura do mesmo ETag cria uma versão histórica.  
**How to avoid:** versão remota única por recurso+ETag/digest e versão publicada única por operationId. [VERIFIED: `.planning/phases/03-ordens-de-servico-publicacao-controlada/03-SPEC.md:54-57`]

## Code Examples

### Contrato estreito para escrita

```kotlin
// Pattern derived from RFC 9110/RFC 4791; names are proposed, not existing API.
interface CalDavWriteClient {
    suspend fun create(request: ConditionalCreate): WriteOutcome
    suspend fun update(request: ConditionalUpdate): WriteOutcome
}

data class ConditionalCreate(
    val targetHref: String,
    val rawIcs: String,
    val requireAbsent: Boolean = true,
)

data class ConditionalUpdate(
    val targetHref: String,
    val rawIcs: String,
    val baseEtag: String,
)
```

Não ofereça overload sem `baseEtag` para update; para criação o adapter sempre converte `requireAbsent` em `If-None-Match: *`. [CITED: https://www.rfc-editor.org/rfc/rfc9110.html]

### Worker como dreno, não como fonte de verdade

```kotlin
override suspend fun doWork(): Result = when (outbox.drainNext()) {
    DrainOutcome.Empty -> Result.success()
    DrainOutcome.Sent -> Result.success()
    DrainOutcome.TransientFailure -> Result.retry()
    DrainOutcome.ConflictPersisted -> Result.success()
    DrainOutcome.PermanentFailurePersisted -> Result.success()
}
```

Conflito e falha permanente são estados de domínio já persistidos; retornar retry nesses casos violaria D-15. [CITED: https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work] [VERIFIED: `.planning/phases/03-ordens-de-servico-publicacao-controlada/03-CONTEXT.md`, D-15]

## State of the Art

| Old Approach | Current Approach | Impact |
|---|---|---|
| Prepend da descrição anterior inteira | Entidades de atualização + projeção consolidada | Evita crescimento quadrático e duplicação. [VERIFIED: SPEC R4] |
| UID como identidade suficiente | Recurso por href e ocorrência por UID+RECURRENCE-ID | Preserva séries e exceções. [CITED: https://www.rfc-editor.org/rfc/rfc4791.html] |
| Retry genérico de sync | Classificação de falhas + precondições + reconciliação | Rede retenta; conflito exige pessoa. [CITED: https://www.rfc-editor.org/rfc/rfc9110.html] |
| Um modelo ICS apenas extrativo | Documento lossless + visão tipada | Permite alterar uma propriedade sem apagar as demais. [ASSUMED] |
| Estado em composables grandes | Screen state no ViewModel + seções stateless | Facilita testes e manutenção da tela única. [CITED: https://developer.android.com/develop/ui/compose/state-hoisting] |

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|---|---|---|
| A1 | Componentes recorrentes explicitamente presentes bastam para a Fase 3; expansão integral de RRULE pode ficar fora. | Architecture Pattern 1 | Uma OS implícita recorrente pode não aparecer como ocorrência atendível. Validar contra agenda real antes de fechar o plano. |
| A2 | Eventos provisórios novos podem usar DTSTART/DTEND em UTC sem VTIMEZONE e ainda cumprir a UX/fuso escolhido. | Architecture Pattern 2 | O servidor/UI pode normalizar de forma inesperada; cobrir na UAT de fim de semana. |
| A3 | Comparação do payload remoto permite reconciliar resposta perdida de PUT sem falso conflito. | Architecture Pattern 5 | O servidor pode reserializar o ICS; comparar campos mutáveis semânticos, não bytes integrais. |
| A4 | O servidor real disponibiliza ETag consistente e aceita os condicionais normativos. | Writer | Sem isso, a publicação deve permanecer bloqueada; validar em agenda de teste. |
| A5 | Um lease persistente é suficiente para recuperar worker interrompido. | Pitfall 7 | Lease curto demais causa concorrência; longo demais atrasa recuperação. Definir e testar com clock injetado. |

## Open Questions

1. **Ocorrências implícitas de RRULE precisam aparecer já na Fase 3?**
   - What we know: a fase exige não alterar a série e os exemplos têm RECURRENCE-ID; a Fase 2 não expande recorrências. [VERIFIED: `.planning/STATE.md`]
   - What's unclear: se a agenda operacional contém instâncias futuras ainda não materializadas pelo servidor.
   - Recommendation: planejar suporte completo a componentes explícitos agora e inserir um checkpoint de UAT; se houver instância implícita necessária, criar spike de expansão antes do lote de UI.

2. **O Nextcloud real retorna ETag no PUT e preserva reserialização suficiente para reconciliação?**
   - What we know: RFC 4791 trata entity tags de recursos de calendário. [CITED: https://www.rfc-editor.org/rfc/rfc4791.html]
   - What's unclear: comportamento da versão/configuração particular.
   - Recommendation: teste controlado `[TESTE NEXO]` em sábado/domingo, primeiro create, depois update, depois conflito deliberado.

3. **Qual conjunto de privilégios aparece em eventos convidados?**
   - What we know: o parser já consulta current-user-privilege-set, mas colapsa os valores. [VERIFIED: `app/src/main/java/dev/claudiocodigo/nexo/data/caldav/CalDavXmlParser.kt:133-139`]
   - What's unclear: se a coleção compartilhada anuncia write-content/bind e se o recurso individual difere.
   - Recommendation: preservar privilégios exatos e fazer a checagem final por operação/HTTP.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|---|---|---:|---|---|
| Java | Gradle/Android build | ✓ | host 26.0.1; daemon configurado para Java 21 | Android Studio JDK |
| Gradle wrapper | build/test | ✓ | 9.4.1 | Android Studio |
| Android SDK/ADB | instrumentados/UAT | ✓ | ADB 1.0.31 | emulador/aparelho do usuário |
| Nextcloud real | UAT CalDAV | condicionado | credencial somente no aparelho | MockWebServer para automação |
| Context7 CLI | docs | ✗ | — | fontes oficiais Android/RFC via busca web |

O comando `gradlew testDebugUnitTest` não iniciou nesta sessão por `java.io.IOException: Unable to establish loopback connection`; isso é uma limitação ambiental do daemon/loopback, não uma falha observada nos testes do projeto. O último estado registrado informa 85 testes unitários verdes, mas a execução da fase deve repetir a suíte no Android Studio/host funcional. [VERIFIED: `.planning/STATE.md:14-16`] [VERIFIED: execução local desta sessão]

**Missing dependencies with no fallback:** nenhuma para planejar; acesso ao servidor real é necessário somente no gate manual de UAT.

## Validation Architecture

### Test Framework

| Property | Value |
|---|---|
| Framework | JUnit 4 + kotlinx-coroutines-test + MockWebServer; AndroidJUnit4 + Room MigrationTestHelper |
| Config | `app/build.gradle.kts`, schemas em `app/schemas/` |
| Quick run | `./gradlew testDebugUnitTest` |
| Full suite | `./gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug` e instrumentados no aparelho |

[VERIFIED: `app/build.gradle.kts:9-12,44-46,85-102`; `app/src/androidTest/java/dev/claudiocodigo/nexo/data/local/NexoDatabaseMigrationTest.kt:23-49`]

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|---|---|---|---|---|
| OS-01 | SUMMARY tolerante, bruto preservado, ambiguidades | unit | `./gradlew testDebugUnitTest --tests '*ServiceOrderExtractionTest'` | ❌ Wave 0 |
| OS-02 | vínculo único e número opcional | DAO/integration | `./gradlew connectedDebugAndroidTest` | ❌ Wave 0 |
| OS-03 | create online/offline exatamente uma vez | unit + MockWebServer + worker | `./gradlew testDebugUnitTest --tests '*Publication*Test'` | ❌ Wave 0 |
| OS-04 | retenção e autosave estruturado | ViewModel/unit | `./gradlew testDebugUnitTest --tests '*ServiceOrderEditorViewModelTest'` | ❌ Wave 0 |
| OS-05 | data textual, horário nativo, divergência | unit | `./gradlew testDebugUnitTest --tests '*ServiceOrderRendererTest'` | ❌ Wave 0 |
| OS-06 | updates ordenados, sem duplicação | unit | `./gradlew testDebugUnitTest --tests '*ServiceOrderRendererTest'` | ❌ Wave 0 |
| OS-07 | finalização consolidada e validações | unit/ViewModel | `./gradlew testDebugUnitTest --tests '*ServiceOrderCompletionTest'` | ❌ Wave 0 |
| OS-08 | snapshots/versions sobrevivem finalização e sync | DAO/migration | `./gradlew connectedDebugAndroidTest` | ❌ Wave 0 |
| R6/R7 | round-trip ICS e PUT condicionado | unit + MockWebServer | `./gradlew testDebugUnitTest --tests '*IcsDocumentEditorTest' --tests '*CalDavWriteClientTest'` | ❌ Wave 0 |
| D-17/D-18 | conflito remoto/local e nova confirmação | unit/ViewModel | `./gradlew testDebugUnitTest --tests '*ConflictReviewViewModelTest'` | ❌ Wave 0 |

### Required Fixture Matrix

Criar apenas fixtures anônimas mínimas em `app/src/test/resources/ical/`; os ICS de Downloads não entram no Git. [VERIFIED: CONTEXT canonical refs]

- evento simples com Unicode, vírgula, ponto-e-vírgula, barra e folding;
- VTIMEZONE + VEVENT com TZID America/Sao_Paulo;
- recurso com master e exceção RECURRENCE-ID no mesmo VCALENDAR;
- ORGANIZER/ATTENDEE/PARTSTAT/RSVP, VALARM, ATTACH e X-property desconhecida;
- LOCATION e COLOR ausentes;
- DESCRIPTION cru não formatado e DESCRIPTION já formatado com valor contendo `:`;
- 412 por concorrente e 412 após resposta perdida do próprio PUT;
- 401, 403, 409, 5xx e timeout.

### Sampling Rate

- **Per task commit:** testes unitários focalizados do módulo alterado.
- **Per wave merge:** `testDebugUnitTest` + `assembleDebug`.
- **Phase gate:** unitários, APK debug, APK androidTest, lint e instrumentados/migração verdes; depois UAT real controlada.

### Wave 0 Gaps

- [ ] Fixture factory anônima e helpers de invariantes ICS.
- [ ] Testes do documento/editor lossless antes da implementação.
- [ ] Testes do writer que provam ausência de PUT incondicional e de DELETE.
- [ ] Testes de migração 1→3 e 2→3 com rascunho/cache preservados.
- [ ] Testes do outbox para concorrência, processo interrompido, response-loss e retry.
- [ ] Testes de extração/renderização golden.
- [ ] Teste de navegação remoto → iniciar/continuar → editor → prévia → central.

### Manual UAT Gate

1. Usar senha temporária somente no aparelho e agenda controlada.
2. Criar `[TESTE NEXO]` em sábado/domingo; confirmar um único recurso e preservar horário/local.
3. Atualizar DESCRIPTION; comparar SUMMARY, DTSTART, DTEND, LOCATION, COLOR, UID e propriedades desconhecidas antes/depois.
4. Alterar remotamente antes do PUT; confirmar 412/revisão sem perda local.
5. Testar evento convidado gravável e evento sem permissão.
6. Revogar credencial após teste; nenhuma credencial/ICS real entra em logs, fixtures ou commit.

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---|---|---|
| V2 Authentication | yes | Reusar senha de aplicativo protegida pelo CredentialStore; nunca persistir em outbox/ICS. [VERIFIED: `app/src/main/java/dev/claudiocodigo/nexo/domain/caldav/CalDavModels.kt:3-23`] |
| V3 Session Management | limited | 401 invalida capacidade operacional e solicita reconexão sem apagar cache/rascunho. [VERIFIED: padrão da Fase 2 em `.planning/STATE.md`] |
| V4 Access Control | yes | current-user-privilege-set + 403 final; write-content/bind por operação. [CITED: https://www.rfc-editor.org/rfc/rfc3744.html] |
| V5 Input Validation | yes | URL same-origin/HTTPS, XML namespace-aware seguro, ICS limits e validação de campos antes da prévia. [VERIFIED: `app/src/main/java/dev/claudiocodigo/nexo/data/caldav/CalDavHttpClient.kt:68-95`; `CalDavXmlParser.kt:166-195`] |
| V6 Cryptography | yes, unchanged | Android Keystore existente; não criar criptografia nova. [VERIFIED: `.planning/STATE.md`] |
| V7 Error/Logging | yes | Sem payload, Authorization ou descrição em logs; ampliar SecurityPolicyTest ao writer/outbox. [VERIFIED: `app/src/test/java/dev/claudiocodigo/nexo/SecurityPolicyTest.kt:8-55`] |
| V13 API/Web Service | yes | Conditional PUT, media type text/calendar, origem fixa, redirects same-origin, resposta por status. [CITED: https://www.rfc-editor.org/rfc/rfc9110.html] |

### Known Threat Patterns

| Pattern | STRIDE | Standard Mitigation |
|---|---|---|
| Overwrite concorrente | Tampering | If-Match/If-None-Match + 412 persistido. |
| Redirect com Authorization | Information disclosure | Checar origem antes de anexar credencial; não seguir cross-origin. |
| ICS/XML malicioso ou gigante | DoS / injection | Limite de tamanho, parser sem entidades externas, falha segura mantendo cache anterior. |
| Operação duplicada | Tampering | operationId/UID estáveis, unique index, transação e reconciliação. |
| Vazamento em histórico/outbox | Information disclosure | Nunca armazenar Authorization/app password; testes de política. |
| Alteração indevida de convidados | Spoofing/Tampering | Preservar scheduling fields, não editar PARTSTAT alheio, respeitar ACL/403. |

## Sources

### Primary (HIGH/MEDIUM confidence)

- https://www.rfc-editor.org/rfc/rfc9110.html — If-Match, If-None-Match e 412.
- https://www.rfc-editor.org/rfc/rfc4918.html — WebDAV, condicionais e ETag.
- https://www.rfc-editor.org/rfc/rfc4791.html — recursos CalDAV, PUT, UID, recorrência e precondições.
- https://www.rfc-editor.org/rfc/rfc5545.html — iCalendar, folding, TEXT, UID e RECURRENCE-ID.
- https://www.rfc-editor.org/rfc/rfc6638.html — scheduling, organizer/attendee e schedule-tag.
- https://www.rfc-editor.org/rfc/rfc3744.html — privilégios WebDAV ACL.
- https://developer.android.com/topic/architecture/data-layer/offline-first — outbox Room + WorkManager.
- https://developer.android.com/reference/androidx/work/WorkManager.html — unique work.
- https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work — constraints e retry.
- https://developer.android.com/training/data-storage/room/migrating-db-versions — migrações explícitas.
- https://developer.android.com/reference/androidx/room/testing/MigrationTestHelper — validação de schema/migração.
- https://developer.android.com/develop/ui/compose/state-hoisting — ViewModel, state hoisting e UDF.

### In-repo primary evidence

- `03-SPEC.md`, `03-CONTEXT.md`, `PROJECT.md`, `REQUIREMENTS.md`, `STATE.md` e `ROADMAP.md`.
- `IcsParser.kt`, `IcsModel.kt`, `RemoteEventMapper.kt`, `RemoteEventEntity.kt`.
- `CalDavHttpClient.kt`, `HttpMethodAllowlist.kt`, `CalDavXmlParser.kt`.
- `NexoDatabase.kt`, `NexoDatabaseMigrations.kt`, DAOs, workers e testes atuais.

### External donor reference

- `../TEDFieldServices/CALDAV-DATA-FORMAT.md` — apenas formato legado e pitfalls; não copiar retry, regex ou credenciais.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — versões lidas no catálogo e nenhuma dependência nova.
- Architecture: HIGH — gaps confirmados no código e decisões bloqueadas na SPEC/CONTEXT.
- Protocol: MEDIUM-HIGH — RFCs normativas; servidor real ainda precisa UAT.
- Pitfalls: HIGH para perda/concorrência; MEDIUM para comportamento específico de scheduling do Nextcloud.

**Research date:** 2026-08-27  
**Valid until:** 2026-09-26 para stack/protocolo; revalidar comportamento do servidor após upgrade do Nextcloud.
