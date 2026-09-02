# Fluxo Guiado de Preenchimento e Finalização de OS

## Contexto

O fluxo atual de atendimento a uma OS exige que o técnico navegue por uma tela monolítica (`ServiceOrderEditorScreen`) com 4 seções colapsáveis. Não há distinção visual clara entre OS abertas/concluídas, a ordenação temporal é newest-first (invertida), e o formato do DESCRIPTION renderizado não corresponde ao padrão do ICS de referência.

O usuário solicita:
1. Listagem unificada das OS do dia na tela Hoje, ordenadas **mais antigas primeiro** (oldest-first), com distinção visual de status e validação de dois fatores (client-side + server-side)
2. Ao clicar num card de OS, auto-extração do SUMMARY e pré-preenchimento parcial do DESCRIPTION
3. Preenchimento guiado em etapas, uma tela por componente principal
4. Formato de DESCRIPTION alinhado com o padrão do ICS de referência (`45830EE1-...ics`)
5. Estado de conclusão com opções pré-formatadas e validação bidirecional

---

## User Review Required

> [!IMPORTANT]
> **Inversão de ordenação**: A tela Hoje atualmente exibe newest-first. Este plano inverte para **oldest-first** (mais antigas primeiro, concluídas ao final). Isso afeta tanto `OperationalOrderProjection.project()` quanto a UI.

> [!IMPORTANT]
> **Formato do DESCRIPTION**: O renderer atual coloca `Estado:` e `Data de Conclusão:` no **final** do texto. O ICS de referência os coloca **entre o cabeçalho e a Demanda**. Este plano alinha com o formato do ICS de referência:
> ```
> OS: 15479
> Cliente: PIER
> Local: Armazém 5
> Técnico: Claudio
> 
> Estado: Concluído
> Data de Conclusão: 31/08/2026
> 
> Demanda:
> REVISAR TODAS AS CÂMERAS DO ARM 5
> 
> Causa:
> N/A
> 
> Solução:
> Foi realizado a revisão...
> 
> Pendências:
> Nenhuma
> ```
> Note que `Cliente` e `Local` ficam em **linhas separadas** (não mais `Cliente: PIER - Armazém 5`).

> [!WARNING]
> **Breaking change no renderer**: Qualquer OS já publicada com o formato antigo terá inconsistência se for republicada. O `ServiceOrderDiff` vai detectar divergência no texto bruto. Isso é aceitável?

---

## Open Questions

1. **Nome do Técnico padrão**: O técnico é sempre "Claudio" ou devemos manter o campo livre? Podemos pré-fixar o nome salvo nas preferências recentes (`RecentServiceOrderPreferences`)?

2. **Categorias fixas**: Você usa categorias como CFTV, REDE, ELÉTRICA, etc.? Se sim, podemos oferecer um `DropdownMenu` com categorias pré-definidas em vez de campo livre. Quais seriam?

3. **Estado "Não concluído"**: Quando uma OS é marcada como "Não concluído" localmente (checkbox), ela volta para o status `EM_ANDAMENTO` ou fica num estado especial tipo `NAO_CONCLUIDO`?

4. **SUMMARY format** — confirmar: `CLIENT - OS# - CATEGORY - TITLE - LOCATION` (ex: `PIER - 15479 - CFTV - REVISAR CÂMERAS - ARM5`). O renderer deve montar o SUMMARY neste formato ao publicar?

---

## Proposed Changes

### Onda A — Correção do Renderer (formato ICS de referência)

#### [MODIFY] [ServiceOrderRenderer.kt](file:///c:/Users/claudio.lima/AndroidStudioProjects/Nexo/app/src/main/java/dev/claudiocodigo/nexo/domain/serviceorder/ServiceOrderRenderer.kt)

- **`appendHeader()`**: Separar `Cliente:` e `Local:` em linhas distintas (antes era `Cliente: X - Y`).
- **`renderCompletion()`**: Mover `Estado: Concluído` e `Data de Conclusão:` para **entre o header e a Demanda** (não mais no final).
- **`renderUpdate()`**: Manter `Estado:` ausente em updates (adicionamos apenas em completion).
- **Novo método `renderSummary(order)`**: Gera o SUMMARY no formato `CLIENT - OS# - CATEGORY - TITLE - LOCATION`.

---

### Onda B — Inversão de ordenação e UI unificada dos cards

#### [MODIFY] [OperationalOrderProjection.kt](file:///c:/Users/claudio.lima/AndroidStudioProjects/Nexo/app/src/main/java/dev/claudiocodigo/nexo/domain/serviceorder/OperationalOrderProjection.kt)

- Inverter sort para **oldest-first** (`compareBy` em vez de `compareByDescending`).
- Adicionar sort secundário: OS concluídas (validadas ou aguardando validação) vão para o **final** da lista.

#### [MODIFY] [HojeScreen.kt](file:///c:/Users/claudio.lima/AndroidStudioProjects/Nexo/app/src/main/java/dev/claudiocodigo/nexo/ui/screens/hoje/HojeScreen.kt)

- Substituir as seções separadas (legacy `ServiceOrderCard` + `RemoteEventCard`) por uma **única lista de `OperationalOrderCard`** já disponível em `HojeUiState.Success.cards`.
- Novo `@Composable UnifiedOsCard(card: OperationalOrderCard, onClick: ...)`:
  - Badge de status colorido (verde=validado, amarelo=aguardando validação, azul=em andamento, cinza=pendente, vermelho=atenção)
  - Chip de `externalId` (nº da OS)
  - Título, cliente, local
  - Horário extraído de `startMillis`
- Agrupar visualmente: **Abertas/Em Andamento** primeiro, depois **Concluídas/Validadas**.

#### [MODIFY] [HojeViewModel.kt](file:///c:/Users/claudio.lima/AndroidStudioProjects/Nexo/app/src/main/java/dev/claudiocodigo/nexo/ui/screens/hoje/HojeViewModel.kt)

- Recalcular `hojeCards` usando a nova ordenação oldest-first da projeção.
- Adicionar `hojeOpenCards` e `hojeConcludedCards` como partições separadas para a UI.

---

### Onda C — Fluxo Guiado de Preenchimento (step-by-step)

A ideia é **não criar telas novas de Navigation**, mas sim introduzir um **stepper interno** dentro do `ServiceOrderEditorScreen` existente. Cada "passo" exibe apenas os campos relevantes daquela fase, com botão "Próximo" e "Voltar".

#### [MODIFY] [ServiceOrderEditorState.kt](file:///c:/Users/claudio.lima/AndroidStudioProjects/Nexo/app/src/main/java/dev/claudiocodigo/nexo/ui/screens/oseditor/ServiceOrderEditorState.kt)

- Adicionar `currentStep: EditorStep = EditorStep.IDENTIFICACAO` ao state.
- Novo enum:
  ```kotlin
  enum class EditorStep {
      IDENTIFICACAO,  // OS#, Cliente, Local, Técnico, Categoria, Título
      DEMANDA,        // Demanda/Solicitação original
      EXECUCAO,       // Atualizações de campo (opcional, skip se não houver)
      CONCLUSAO       // Causa, Solução, Pendências, Estado
  }
  ```

#### [MODIFY] [ServiceOrderEditorViewModel.kt](file:///c:/Users/claudio.lima/AndroidStudioProjects/Nexo/app/src/main/java/dev/claudiocodigo/nexo/ui/screens/oseditor/ServiceOrderEditorViewModel.kt)

- `fun nextStep()`: avança o step, faz validação local do step atual.
- `fun previousStep()`: volta ao step anterior.
- `fun onConclusionStateChange(state: ConclusionState)`: define o estado de conclusão (Concluído / Com Pendências / Não Concluído).

#### [MODIFY] [ServiceOrderEditorScreen.kt](file:///c:/Users/claudio.lima/AndroidStudioProjects/Nexo/app/src/main/java/dev/claudiocodigo/nexo/ui/screens/oseditor/ServiceOrderEditorScreen.kt)

- Substituir o layout monolítico scrollable por um **stepper visual** (indicador de progresso no topo + conteúdo do step atual).
- Cada step mostra apenas as seções composables relevantes (`IdentificationSection`, `DemandSection`, etc.).
- Bottom bar muda de "Publicar" para "Próximo →" nos steps intermediários, e "Publicar" no step final.
- No step `CONCLUSAO`, mostrar seleção de estado:
  - `FilterChip`: Concluído | Com Pendências | Não Concluído
  - Quando "Não Concluído": exibir checkbox "Marcar como não concluído (aguardar validação externa)"

#### [NEW] [StepperIndicator.kt](file:///c:/Users/claudio.lima/AndroidStudioProjects/Nexo/app/src/main/java/dev/claudiocodigo/nexo/ui/components/StepperIndicator.kt)

- Componente reutilizável de indicador de progresso horizontal (4 dots + labels).

---

### Onda D — Modelo de Conclusão com validação bidirecional

#### [MODIFY] [ServiceOrderModels.kt](file:///c:/Users/claudio.lima/AndroidStudioProjects/Nexo/app/src/main/java/dev/claudiocodigo/nexo/domain/serviceorder/ServiceOrderModels.kt)

- Adicionar ao `StructuredServiceOrder`:
  ```kotlin
  val conclusionState: ConclusionState = ConclusionState.NAO_DEFINIDO
  ```
- Novo enum:
  ```kotlin
  enum class ConclusionState {
      NAO_DEFINIDO,
      CONCLUIDO,
      CONCLUIDO_COM_PENDENCIAS,
      NAO_CONCLUIDO
  }
  ```

#### [MODIFY] [ServiceOrderRenderer.kt](file:///c:/Users/claudio.lima/AndroidStudioProjects/Nexo/app/src/main/java/dev/claudiocodigo/nexo/domain/serviceorder/ServiceOrderRenderer.kt)

- `renderCompletion()` usa `conclusionState` para definir o texto de `Estado:`:
  - `CONCLUIDO` → `"Concluído"`
  - `CONCLUIDO_COM_PENDENCIAS` → `"Concluído com pendências"`
  - `NAO_CONCLUIDO` → `"Não concluído"`

#### [MODIFY] [ServiceOrderExtractor.kt](file:///c:/Users/claudio.lima/AndroidStudioProjects/Nexo/app/src/main/java/dev/claudiocodigo/nexo/domain/serviceorder/ServiceOrderExtractor.kt)

- `extractDescription()`: parsear `Estado:` para mapear para `ConclusionState`.

---

### Onda E — Auto-extração ao clicar no card (RemoteEventDetail → Editor)

#### [MODIFY] [RemoteEventDetailViewModel.kt](file:///c:/Users/claudio.lima/AndroidStudioProjects/Nexo/app/src/main/java/dev/claudiocodigo/nexo/ui/screens/remoto/RemoteEventDetailViewModel.kt)

- Sem mudanças significativas — o `startAttendance()` já faz extração e pré-preenche. A principal mudança é que agora o `ServiceOrderEditorScreen` abre no step `IDENTIFICACAO` com campos já pré-preenchidos, e o usuário apenas revisa e avança.

#### [MODIFY] [ServiceOrderEditorViewModel.kt](file:///c:/Users/claudio.lima/AndroidStudioProjects/Nexo/app/src/main/java/dev/claudiocodigo/nexo/ui/screens/oseditor/ServiceOrderEditorViewModel.kt)

- Em `loadOrder()`, detectar se os campos de identificação já estão preenchidos (linked OS). Se sim, sugerir avanço automático para o step `DEMANDA` ou `CONCLUSAO` dependendo do progresso.

---

## Verification Plan

### Automated Tests

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Novos testes:
- `ServiceOrderRendererTest` — validar que `renderCompletion()` gera texto no formato ICS de referência exato
- `ServiceOrderRendererTest` — validar que `renderSummary()` gera `CLIENT - OS# - CATEGORY - TITLE - LOCATION`
- `OperationalOrderProjectionTest` — validar oldest-first sort com concluídas ao final
- `ServiceOrderExtractorTest` — validar parsing de `ConclusionState` do campo `Estado:`
- `ServiceOrderEditorViewModelTest` — validar navegação de steps

### Manual Verification

1. Abrir tela Hoje → verificar que OS aparecem ordenadas mais antigas primeiro, concluídas ao final
2. Clicar num card de evento CalDAV → verificar que o editor abre com campos pré-preenchidos no step IDENTIFICAÇÃO
3. Navegar pelos steps (Próximo/Voltar) → verificar que cada step mostra apenas os campos relevantes
4. Marcar como "Concluído" → publicar → verificar que o DESCRIPTION segue o formato ICS de referência
5. Marcar como "Não concluído" → verificar que aparece checkbox local e status visual diferente
