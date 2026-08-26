# Fase 1 — Plano de estabilizacao da fundacao

## Objetivo

Transformar o scaffold em uma base local confiavel sem redesenhar a UI aprovada e sem iniciar CalDAV real.

## Protocolo de delegacao

- Implementador: subagente `gpt-5.6-luna`.
- Revisao: agente principal apos cada lote.
- Luna nao deve copiar codigo do VoltIQ/TEDFieldServices nesta fase.
- Cada lote deve informar arquivos alterados, testes executados e pendencias.
- O agente principal rejeita retry cego, migracao destrutiva, estado ficticio ou supressao de lint.

## Lote 0 — Baseline e higiene Git

1. Revisar `.gitignore`, `.idea`, `.agent`, arquivos vazios e artefatos gerados.
2. Garantir ausencia de URLs/credenciais reais.
3. Criar baseline rastreavel do scaffold antes das correcoes, se identidade Git estiver configurada.

**Verificacao:** `git status` contem apenas arquivos intencionais.

## Lote 1 — Dependencias, permissoes e build

1. Remover CameraX, localizacao, Accompanist Permissions, Retrofit, Moshi, OkHttp, Coil e Material Views se realmente nao usados.
2. Remover `INTERNET` nesta fase.
3. Alinhar Kotlin/serialization, Lifecycle, Compose BOM, Navigation 3, Hilt, Room, DataStore e WorkManager em versoes estaveis compativeis.
4. Corrigir inicializacao Hilt/WorkManager removendo o initializer padrao conforme o manifest merger.
5. Corrigir os dois erros de lint sem criar baseline de supressao.
6. Remover recursos e arquivos placeholder sem uso.

**Verificacao:** `assembleDebug`, `testDebugUnitTest`, `assembleDebugAndroidTest` e `lintDebug` passam.

## Lote 2 — Persistencia local real

1. Exportar schema Room e configurar diretorio de schemas.
2. Remover `fallbackToDestructiveMigration()`.
3. Substituir armazenamento de status por conversor tipado seguro ou mapeamento tolerante.
4. Implementar `RoomServiceOrderRepository` e liga-lo em producao; fake somente em testes/previews.
5. Definir operacoes de insercao/atualizacao sem `REPLACE` destrutivo.
6. Adicionar campos minimos para rascunho e auditoria local sem antecipar todo o modelo CalDAV.
7. Criar testes DAO e de repositorio com banco em memoria.

**Verificacao:** OS salva no Room, sobrevive a reinicio e schema v1 e exportado.

## Lote 3 — Editor e autosave

1. Mover estado editavel de detalhes para ViewModel (`SavedStateHandle` quando apropriado).
2. Fazer ambos os botoes de salvar usarem o mesmo estado atual.
3. Implementar autosave local com debounce e indicador `Salvando/Salvo localmente/Erro`.
4. Salvar ao sair da tela e preservar campos em recriacao de processo.
5. Implementar rota e formulario minimo de Nova OS com UUID e numero opcional.
6. Implementar finalizacao somente local nesta fase, com confirmacao e possibilidade de reabrir.
7. Substituir horario falso de sincronizacao por estado honesto `Ainda nao sincronizado`.

**Verificacao:** editar, navegar para tras, encerrar/reabrir e recuperar texto sem perda.

## Lote 4 — Testes e aceite da fundacao

1. Fortalecer teste de navegacao com tags/conteudo exclusivo por destino.
2. Testar filtro da agenda, estados Hoje, nova OS, autosave e finalizacao/reabertura.
3. Compilar e executar testes instrumentados se houver aparelho/emulador.
4. Fazer UAT manual preservando aparencia e fluxo aprovados.
5. Atualizar README para distinguir implementado, demonstrativo e futuro.

## Criterios de conclusao

- UI principal preservada.
- Nenhum botao principal aparenta funcionar sem funcionar.
- Rascunho sobrevive a fechamento/reabertura.
- Room sem fallback destrutivo e com schema exportado.
- Nenhuma permissao ou dependencia prematura.
- Build, testes unitarios, APK de teste e lint aprovados.
- Diff revisado pelo agente principal.
- Projeto pronto para Fase 2, ainda sem acessar Nextcloud real.
