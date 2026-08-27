# Summary 03-08: Cartões Unificados em Hoje e Agenda

**Execution Date:** 2026-08-27
**Wave:** 8
**Status:** Completed

---

## 🎯 What was delivered

1. **Projeção Operacional Pura (`OperationalOrderProjection`):**
   - Criados `OperationalOrderCard`, `OperationalStatus` e `CardNavigationTarget`.
   - Regra de unificação determinística:
     - Evento remoto vinculado a OS local através de `RemoteOccurrenceKey` unifica em **exatamente um cartão**.
     - Eventos remotos avulsos e OS locais provisórias permanecem cartões individuais separados.
     - Não há merge fuzzy por título ou UID.
   - Precedência de status rigorosa:
     1. `REQUER_ATENCAO` (Vermelho do Nextcloud)
     2. `CONFLITO_PUBLICACAO` (Status CONFLICT 412 na Outbox)
     3. `ENVIANDO_PUBLICACAO` / `AGUARDANDO_CONEXAO` / `FALHA_PUBLICACAO` (Outbox)
     4. `VALIDADO_EXTERNAMENTE` (Verde do Nextcloud)
     5. `AGUARDANDO_VALIDACAO_EXTERNA` (Conclusão interna sem confirmação remota)
     6. `EM_ANDAMENTO`
     7. `PENDENTE`

2. **Unificação e Reabertura Explícita na Tela Hoje (`HojeViewModel`):**
   - Particionamento por cartões unificados (`emAndamentoCards`, `requerAtencaoCards`, `hojeCards`).
   - Reabertura transacional de atendimento (`reopenOrder`) que insere nova etapa de atualização no histórico da OS sem apagar os dados anteriores.
   - Preservado o filtro de segurança que oculta eventos com mais de 30 dias na aba Hoje.

3. **Unificação e Busca na Tela Agenda (`AgendaViewModel`):**
   - Agrupamento cronológico ordenado (mais recente primeiro) por rótulo de data (`dd 'de' MMMM 'de' yyyy`).
   - Busca abrangente em títulos, clientes, unidades e números de OS sem acoplar entidades físicas.

---

## 🧪 Verification & Tests

- `OperationalOrderProjectionTest`: Matriz de testes de unificação, isolamento, precedência e ordenação.
- `HojeViewModelTest`: Testes de particionamento, filtro de 30 dias e ordenação.
- `AgendaViewModelTest` & `AgendaRemoteViewModelTest`: Testes de busca, agrupamento e delegação.
- `./gradlew testDebugUnitTest`: **BUILD SUCCESSFUL**.
