# Roadmap do Nexo

## Fase 1 — Estabilizacao da fundacao

**Status:** concluida em 25/08/2026.

**Objetivo:** tornar o scaffold realmente offline-first e seguro para receber dados reais, preservando a UI aprovada.

**Entrega demonstravel:** criar e editar uma OS local, fechar/reabrir o app e recuperar o rascunho; build, testes e lint passam; nenhuma migracao pode apagar o banco.

## Fase 2 — Conta Nextcloud e agenda somente leitura

**Status:** proxima fase planejada.

**Objetivo:** conectar por QR/manual, descobrir o calendario e importar eventos sem permitir escrita remota.

**Entrega demonstravel:** escanear QR, selecionar calendario e ver eventos reais em Hoje/Agenda com UID, ETag, descricao e cor, sem modificar o servidor.

**Seguranca de teste:** calendario separado; nenhum evento preexistente e alterado.

## Fase 3 — Ordens de Servico e publicacao controlada

**Objetivo:** estruturar formularios, atualizacoes, finalizacoes e atendimentos provisórios.

**Entrega demonstravel:** criar uma OS `[TESTE NEXO]` em sabado/domingo, editar descricao formatada e preservar titulo, data, horario, local e cor.

## Fase 4 — Conflitos, sincronizacao e notificacoes

**Objetivo:** implantar autosave, escrita condicional, mesclagem de tres vias, sincronizacao periodica e sinais vermelho/verde.

**Entrega demonstravel:** duas versoes concorrentes causam conflito visivel sem perder texto; mudanca para vermelho gera `Requer atencao` e notificacao local.

## Fase 5 — Inventario e diagnostico de baterias

**Objetivo:** cadastrar ativos, registrar instalacoes/retiradas e migrar o modulo aprovado do VoltIQ integrado a OS.

**Entrega demonstravel:** uma OS testa duas baterias, troca uma, preserva numeros de serie e gera o trecho tecnico na descricao.

## Fase 6 — Historico, tendencias e relatorios TXT

**Objetivo:** transformar registros acumulados em consulta operacional e relatorios.

**Entrega demonstravel:** localizar bateria/nobreak, comparar testes e gerar TXT por OS, ativo ou periodo.

## Fase 7 — Portabilidade e open source

**Objetivo:** exportar/importar JSON com conflitos seguros e preparar distribuicao publica.

**Entrega demonstravel:** transferir dados entre duas instalacoes sem duplicar historico e sem expor credenciais; documentacao permite configuracao por terceiro.

## Regra de execucao

- O subagente `gpt-5.6-luna` implementa lotes pequenos e delimitados quando a execucao for autorizada.
- O agente principal revisa cada diff, corrige riscos e executa verificacoes antes de aceitar o lote.
- Nenhuma fase avancara com lint/testes obrigatorios falhando ou com risco conhecido de perda silenciosa.
