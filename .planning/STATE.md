# Estado do projeto

- Data da ultima atualizacao: 27/08/2026.
- Estado: Fase 2 — implementação técnica verificada; UAT real de conexão, descoberta, seleção de agenda e leitura de eventos aprovada no aparelho.
- Proxima acao: concluir os cenarios residuais da UAT (revogacao da senha e confirmacao externa de zero escrita) e iniciar o planejamento da Fase 3.
- Fase 1 concluida e preservada: cinco abas, Room como fonte local, rascunhos nao perdidos.
- Fase 2: QR/manual, Keystore AES-GCM (sem backup), descoberta e selecao de agenda, importacao somente leitura, sincronizacao WorkManager com rede, sync-collection RFC 6578 com fallback href+ETag e eventos remotos em Hoje/Agenda com detalhe somente leitura.
- Migracao Room 1→2 explicita; schema 2 exportado; rascunhos da Fase 1 sobrevivem.
- Allowlist HTTP read-only; nenhuma escrita remota existe ou e acionada.
- Validação final: `testDebugUnitTest` passou com 85 testes e zero falhas; `assembleDebug`, `assembleDebugAndroidTest` e `lintDebug` também passaram.
- Testes instrumentados (migracao 1→2, Keystore) compilados; executar em aparelho/emulador.
- RRULE/EXDATE/RECURRENCE-ID e ICS bruto sao preservados, mas recorrencias ainda nao sao expandidas.
- A credencial real permaneceu fora do chat e do repositorio; a UAT de leitura foi concluida no aparelho.

## Riscos remanescentes

- Restam na UAT a revogacao da senha temporaria e a confirmacao externa de que nenhum recurso remoto foi modificado.
- Conflitos, publicacao remota, notificacoes, inventario completo e relatorios ainda nao foram implementados (Fases 3-7).
- O diagnostico de bateria continua demonstrativo e nao deve aprovar ou condenar baterias nesta fase.

## Decisoes de colaboracao

- Planejamento da Fase 2 feito pelas skills/research da fase.
- Implementacao autonoma lote por lote (0→6) com verificacao de build/testes/lint a cada lote.
- Lote 7: documentacao/estado atualizados; UAT em aparelho e teste com credencial real ficam para o autor.
- Nenhuma credencial deve ser enviada por chat; QR sera escaneado no app durante teste real.
