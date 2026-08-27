# Phase 3: Ordens de Serviço e publicação controlada - Context

**Gathered:** 2026-08-27
**Status:** Ready for planning

<domain>
## Phase Boundary

Esta fase transforma um evento CalDAV em uma única cópia de trabalho local estruturada, permite criar OS provisória local, registrar atualizações/finalização e publicar explicitamente uma versão previamente conferida. Inclui a escrita condicional e a recuperação manual mínima necessárias para nunca sobrescrever uma mudança remota; mesclagem automática, notificações e módulos técnicos especializados continuam em fases posteriores.

</domain>

<spec_lock>
## Requirements (locked via SPEC.md)

**8 requirements are locked.** See `03-SPEC.md` for full requirements, boundaries, and acceptance criteria.

Downstream agents MUST read `03-SPEC.md` before planning or implementing. Requirements are not duplicated here.

**In scope (from SPEC.md):** vincular evento remoto a uma OS local estruturada; criar e publicar rascunho provisório explicitamente; extrair campos confiáveis do `SUMMARY`; formular demanda, atualizações, finalização e pendências; gerar prévias; criar `VEVENT` e atualizar `DESCRIPTION`; usar escrita condicional e recuperação manual mínima após `412`; manter histórico local e estados operacionais distintos.

**Out of scope (from SPEC.md):** exclusão remota; aplicar ou alterar cor automaticamente; mesclagem automática avançada; notificações; cadastro completo de ativos/baterias/nobreaks; deduplicação automática; reescrita por LLM.

</spec_lock>

<decisions>
## Implementation Decisions

### Início do atendimento e vínculo
- **D-01:** Tocar no cartão abre primeiro o evento remoto em modo somente leitura. A cópia de trabalho nasce apenas após a ação explícita `Iniciar atendimento`.
- **D-02:** Após existir vínculo, o detalhe remoto oferece `Continuar atendimento` e abre sempre a mesma OS local; não cria rascunhos duplicados. Hoje e Agenda representam o vínculo como um único fluxo operacional, embora os registros remoto e local permaneçam separados no banco.
- **D-03:** A identidade do vínculo usa conta + calendário + `href`; `UID + RECURRENCE-ID` identifica uma ocorrência de série sem alterar a série inteira. `UID` isolado não é chave única.
- **D-04:** Ao iniciar atendimento, o editor abre já preenchido. `SUMMARY` é a fonte principal de número da OS, técnico, setor/categoria e título curto; `DESCRIPTION` é a fonte principal de conteúdo da solicitação e demais campos reconhecidos. Extração é determinística, nunca por LLM, e campos ambíguos exigem conferência.
- **D-05:** A cópia local guarda snapshot remoto e `ETag`. Se a versão remota mudar antes de continuar o atendimento, o rascunho não é alterado: o app refaz a extração e apresenta somente campos novos/diferentes, com `Manter meu valor` ou `Usar valor do calendário`; após a decisão, atualiza a base e permite edição local normal.
- **D-06:** A descrição originalmente recebida, mesmo como texto corrido e sem padrão, entra inteira no campo de origem definido pelo preset (`Demanda`, `Solicitação` ou `Problema relatado`). O app desfaz folding/escapes do ICS para exibição, mas preserva o texto e o ICS brutos.

### Editor estruturado e projeção textual
- **D-07:** O editor será uma tela única com seções recolhíveis: Identificação, Demanda/Solicitação, Atualizações, Pendências, Itens/Equipamentos, Finalização e Prévia. A tela compartilha um único estado/ViewModel, mas cada seção deve ser um componente separado para manutenção.
- **D-08:** Um preset explícito no topo define a semântica e os rótulos. A primeira entrega contém ao menos `Diagnóstico/correção` (`Demanda`, `Problema identificado`, `Causa`, `Solução executada`) e `Serviço solicitado` (`Solicitação`, sem problema/causa obrigatórios, `Ação executada`). Detecção pelo `SUMMARY` pode sugerir, mas não selecionar silenciosamente.
- **D-09:** A OS pode conter uma lista opcional e repetível de registros genéricos de item/equipamento, com ação, tipo, marca, modelo, número de série, equipamento relacionado, localização e observação. A fase implementa somente o formulário básico e sua formatação; bateria, nobreak e rede especializados ficam para fases futuras.
- **D-10:** A prévia é somente leitura e sempre regenerada a partir dos campos estruturados. Correções são feitas no formulário; não existe editor livre do texto final nesta fase.
- **D-11:** Data de execução aparece no `DESCRIPTION`; horário permanece somente em `DTSTART`/`DTEND`. Divergências são alertadas e nunca corrigidas silenciosamente.

### Publicação e fila offline
- **D-12:** Autosave local não tem botão concorrente. Uma barra fixa inferior apresenta exatamente a ação remota aplicável ao estado: `Publicar no calendário` cria o recurso da OS provisória, `Enviar atualização` atualiza evento existente e `Finalizar OS` publica a consolidação e conclui internamente.
- **D-13:** Toda criação, atualização ou finalização exige prévia completa de texto, data, horário e calendário de destino, seguida de confirmação explícita.
- **D-14:** Uma publicação confirmada offline cria uma operação imutável contendo exatamente a versão conferida. Edições posteriores permanecem no rascunho e não alteram a operação; substituir uma operação pendente exige cancelamento ou nova prévia/confirmação explícita.
- **D-15:** Antes de enviar item da fila, o app revalida a base remota. Mudança de `ETag` bloqueia o envio como conflito; `412` nunca recebe retry automático. Falha de rede continua pendente.
- **D-16:** Cada cartão exibe `Aguardando conexão`, `Enviando`, `Falhou` ou `Conflito`. Em `Mais`, uma Central de sincronizações lista pendentes, andamento, falhas, conflitos e histórico recente, permitindo tentar novamente ou cancelar o que ainda não foi enviado.

### Conflitos, compartilhamento e estados operacionais
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

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Contrato do produto e da fase
- `.planning/phases/03-ordens-de-servico-publicacao-controlada/03-SPEC.md` — requisitos bloqueados, limites, aceites, bordas e proibições da Fase 3.
- `.planning/PROJECT.md` — princípios offline-first, identidade, escrita condicional, formato operacional e projetos doadores.
- `.planning/REQUIREMENTS.md` — requisitos `OS-01..OS-08` e separação dos recursos avançados `SYN-*`/`NOT-*`.
- `.planning/ROADMAP.md` — objetivo e entrega demonstrável da Fase 3; a SPEC e este CONTEXT refinam a fronteira do conflito mínimo.
- `.planning/STATE.md` — estado verificado da Fase 2 e riscos remanescentes.

### Referência CalDAV legada
- `../TEDFieldServices/CALDAV-DATA-FORMAT.md` — documento doador sobre o formato CalDAV; usar como referência, validando tudo contra o código atual e a RFC, sem copiar credenciais ou retry inseguro.

### Amostras reais
- Os ICS reais analisados não são artefatos canônicos e não devem ser copiados ao repositório. Criar fixtures anônimas mínimas que cubram folding, escapes, `VTIMEZONE`, ocorrência recorrente (`UID + RECURRENCE-ID`), `ORGANIZER`/`ATTENDEE`, propriedades desconhecidas e ausência de `COLOR`.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `RemoteEventDetailScreen`: base do detalhe remoto somente leitura e do botão `Iniciar/Continuar atendimento`.
- `NovaOSScreen` / `NovaOSViewModel`: autosave local e formulário atual a evoluir para seções estruturadas.
- `DetalhesScreen` / `DetalhesViewModel`: edição e conclusão locais atuais, a integrar ao novo estado operacional.
- `CalendarSyncCoordinator`, `SyncScheduler` e WorkManager existente: padrões de sincronização, restrição de rede e persistência; não reutilizar a allowlist read-only para escrita.
- Parser iCalendar e armazenamento do ICS bruto: base para round-trip preservando propriedades.

### Established Patterns
- Room é a fonte local de verdade e migrações são explícitas, sem fallback destrutivo.
- Estado editável pertence ao ViewModel e chega ao Room antes de qualquer rede.
- Cliente CalDAV de leitura possui allowlist mutante bloqueada; a escrita deve usar um componente separado, estreito e testável, sem enfraquecer o cliente read-only.
- Eventos remotos e OS locais são entidades distintas; o vínculo deve ser explícito e idempotente.

### Integration Points
- Detalhe remoto inicia/continua o vínculo com a OS local.
- Hoje e Agenda combinam evento, estado local e estado da fila sem duplicar o fluxo operacional.
- Room recebe entidades de vínculo, campos estruturados, atualizações, itens, snapshots/versões e operações de publicação.
- WorkManager executa operações confirmadas e revalida `ETag`/permissões antes de mutar CalDAV.
- `Mais` recebe a Central de sincronizações.

</code_context>

<specifics>
## Specific Ideas

- O output precisa transformar descrições recebidas como texto corrido em uma OS legível, mantendo a solicitação original e separando o que o técnico escreveu depois.
- Exemplo de preset `Serviço solicitado`: cabeçalho com número/data/técnico/local, bloco `Solicitação` e bloco `Ação executada`.
- Eventos recorrentes podem compartilhar `UID`, mas cada ocorrência pode ter número, descrição, cor e anexos próprios.
- Eventos convidados podem aparecer sem `COLOR` no ICS; a cor percebida no Nextcloud pode vir da agenda, portanto ausência de cor é válida.
- Teste remoto real deve criar recurso marcado `[TESTE NEXO]` somente em sábado/domingo e nunca usar fixtures com dados pessoais ou credenciais.

</specifics>

<deferred>
## Deferred Ideas

- Mesclagem automática de campos não concorrentes, editor avançado de três vias, descarte/substituição ampliados, monitoramento periódico do editor e notificações locais: Fase 4.
- Formulários especializados e integração de diagnóstico para baterias, nobreaks, rede e ativos: Fase 5.
- Relatórios e consultas históricas amplas: Fase 6.
- Inferir `#9370DB` como `Autosserviço`: adiado até existir evidência operacional; por enquanto a cor permanece não mapeada.

</deferred>

---

*Phase: 03-ordens-de-servico-publicacao-controlada*
*Context gathered: 2026-08-27*
