# Roadmap do Nexo

## Fase 1 — Estabilizacao da fundacao

**Status:** concluida em 25/08/2026.

**Objetivo:** tornar o scaffold realmente offline-first e seguro para receber dados reais, preservando a UI aprovada.

**Entrega demonstravel:** criar e editar uma OS local, fechar/reabrir o app e recuperar o rascunho; build, testes e lint passam; nenhuma migracao pode apagar o banco.

## Fase 2 — Conta Nextcloud e agenda somente leitura

**Status:** implementação técnica verificada; UAT real de conexão, descoberta, seleção e leitura aprovada em 27/08/2026. Restam os cenários de revogação e confirmação externa de zero escrita.

**Objetivo:** conectar por QR/manual, descobrir o calendario e importar eventos sem permitir escrita remota.

**Entrega demonstravel:** escanear QR, selecionar calendario e ver eventos reais em Hoje/Agenda com UID, ETag, descricao e cor, sem modificar o servidor.

**Seguranca de teste:** calendario separado; nenhum evento preexistente e alterado.

### Phase 3: Ordens de Servico e publicacao controlada

**Requirements:** OS-01, OS-02, OS-03, OS-04, OS-05, OS-06, OS-07, OS-08

**Goal:** estruturar formularios, atualizacoes, finalizacoes e atendimentos provisórios.

**Success Criteria:**

1. Criar uma OS `[TESTE NEXO]` em sabado/domingo.
2. Editar e publicar uma descricao formatada mediante previa e confirmacao.
3. Preservar titulo, data, horario, local, cor e propriedades desconhecidas do evento.

**Plans:** 9 plans in 9 sequential waves

**Wave 1**
- `03-01-PLAN.md` — modelo local estruturado, vínculo idempotente, histórico e migração Room v3.

**Wave 2** *(blocked on Wave 1 completion)*
- `03-02-PLAN.md` — extração/renderização determinística, edição ICS lossless e ocorrências explícitas.

**Wave 3** *(blocked on Waves 1–2 completion)*
- `03-03-PLAN.md` — writer CalDAV separado, permissões exatas e PUT estritamente condicional.

**Wave 4** *(blocked on Wave 3 completion)*
- `03-04-PLAN.md` — outbox imutável/idempotente, coordenador, worker e reconciliação.

**Wave 5** *(blocked on Wave 4 completion)*
- `03-05-PLAN.md` — editor estruturado em seções e prévia completa somente leitura.

**Wave 6** *(blocked on Wave 5 completion)*
- `03-06-PLAN.md` — início/continuação do atendimento e revisão manual de conflito por campo.

**Wave 7** *(blocked on Wave 6 completion)*
- `03-07-PLAN.md` — mapeamento configurável de cores e Central de sincronizações.

**Wave 8** *(blocked on Wave 7 completion)*
- `03-08-PLAN.md` — projeção operacional única nos cartões de Hoje e Agenda.

**Wave 9** *(blocked on Wave 8 completion)*
- `03-09-PLAN.md` — gates finais de segurança/migração/navegação e UAT controlada no Nextcloud.

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
