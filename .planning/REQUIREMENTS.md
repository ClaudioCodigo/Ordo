# Requisitos do Nexo

## Fundacao e integridade

- [x] **FND-01** Preservar a UI e as cinco abas aprovadas durante a estabilizacao.
- [x] **FND-02** Build, testes unitarios, APK de teste e lint devem passar sem erros.
- [x] **FND-03** Remover dependencias, recursos e permissoes sem uso.
- [x] **FND-04** Usar apenas versoes estaveis e coerentes das dependencias.
- [x] **FND-05** Exportar schema Room e exigir migracoes explicitas, sem migracao destrutiva.
- [x] **FND-06** Ligar a UI a um repositorio Room real; fakes ficam restritos a demo/testes.
- [x] **FND-07** Estado editavel pertence ao ViewModel e e salvo automaticamente no banco.
- [x] **FND-08** Nova OS, salvar rascunho e finalizar nao podem ser botoes inertes.
- [x] **FND-09** Indicadores de sincronizacao mostram somente estado real.

## Conta e descoberta

- [ ] **AUT-01** Conectar pelo QR do Nextcloud como fluxo principal.
- [ ] **AUT-02** Permitir configuracao manual de servidor, login e senha de aplicativo.
- [ ] **AUT-03** Validar esquema, campos e HTTPS antes de salvar.
- [ ] **AUT-04** Armazenar senha com protecao baseada no Android Keystore.
- [ ] **AUT-05** Descobrir e listar calendarios; selecionar um calendario de trabalho.
- [ ] **AUT-06** Desconectar apaga credenciais locais; exportacoes nunca as incluem.

## Leitura CalDAV e agenda

- [ ] **CAL-01** Importar eventos por `UID`/`href`, preservando ICS bruto, `ETag`, `SEQUENCE` e cor.
- [ ] **CAL-02** Preservar `SUMMARY` e `DESCRIPTION` brutos mesmo quando a extracao for parcial.
- [ ] **CAL-03** Mostrar Hoje, Agenda, pesquisa, atrasos e estado de sincronizacao a partir do banco local.
- [ ] **CAL-04** Reconhecer verde como validado, vermelho como requer atencao e outras cores como nao classificadas.
- [ ] **CAL-05** Tratar nova `UID` como novo evento e apenas sugerir relacao com eventos semelhantes.

## Ordens de Servico

- [ ] **OS-01** Extrair campos confiaveis do `SUMMARY` sem assumir quantidade fixa de segmentos.
- [ ] **OS-02** Numero oficial da OS e opcional; atendimento provisório usa UUID interno e `SEM OS` visual.
- [ ] **OS-03** Criar atendimento provisório no calendario imediatamente quando houver internet e manter fila offline quando nao houver.
- [ ] **OS-04** Formulario retem tecnico, empresa/unidade e selecoes repetidas.
- [ ] **OS-05** Data de execucao e automatica, editavel e obrigatoria na descricao; horario usa `DTSTART`/`DTEND`.
- [ ] **OS-06** Atualizacao preserva demanda e contexto sem formar uma montanha de texto.
- [ ] **OS-07** Finalizacao gera descricao consolidada com demanda, resultado, concluidos e pendencias.
- [ ] **OS-08** Versoes anteriores ficam no historico local.

## Sincronizacao e conflitos

- [ ] **SYN-01** Autosave local nunca depende da rede.
- [ ] **SYN-02** Guardar versao-base e `ETag` ao abrir o editor.
- [ ] **SYN-03** Verificar mudanca remota com a OS aberta e antes de publicar.
- [ ] **SYN-04** PUT e DELETE existentes usam `If-Match`; criacao usa precondicao adequada.
- [ ] **SYN-05** Em `412`, interromper escrita, baixar remoto e preservar rascunho.
- [ ] **SYN-06** Mesclar automaticamente campos nao concorrentes e pedir decisao nos concorrentes.
- [ ] **SYN-07** Permitir revisar lado a lado, combinar, descartar rascunho ou substituir com confirmacao.
- [ ] **SYN-08** Sincronizar ao abrir, manualmente, apos publicacao e periodicamente com restricao de rede.

## Notificacoes

- [ ] **NOT-01** Usar NotificationManager local, canal de notificacao e permissao do Android 13+.
- [ ] **NOT-02** Mudanca de finalizada/validada para vermelho gera `Requer atencao` e notificacao.
- [ ] **NOT-03** Nova UID semelhante e apresentada como possivel continuacao, nao como reabertura automatica.

## Ativos e procedimentos

- [ ] **INV-01** Cadastrar clientes, unidades, locais, equipamentos, nobreaks e baterias por UUID.
- [ ] **INV-02** Serie e obrigatoria ou marcada como ausente/ilegivel com codigo interno.
- [ ] **INV-03** Registrar bateria instalada, retirada, substituida e transferida sem perder historico.
- [ ] **INV-04** Bateria A/B e rotulo de procedimento, nunca identidade permanente.
- [ ] **BAT-01** Migrar dominio e roteiro aprovado do VoltIQ com seus testes.
- [ ] **BAT-02** Vincular teste a OS, bateria e opcionalmente nobreak/local.
- [ ] **BAT-03** Teste avulso pode ser vinculado posteriormente a uma OS.
- [ ] **BAT-04** Incluir resultados vinculados automaticamente na descricao, com opcao de omissao explicita.

## Historico e portabilidade

- [ ] **REP-01** Historico por OS, bateria, nobreak, equipamento e local.
- [ ] **REP-02** Tendencia compara testes por data sem media que esconda degradacao.
- [ ] **REP-03** Gerar TXT por OS, bateria, nobreak e periodo.
- [ ] **REP-04** Exportar/importar JSON versionado sem credenciais e sem dados parciais.
- [ ] **REP-05** Conflitos de importacao nunca sobrescrevem silenciosamente.
- [ ] **OSS-01** Documentar instalacao, configuracao e politica de segredos para uso open source.
- [ ] **OSS-02** Nenhum dado, URL ou credencial particular permanece no repositorio.

## Rastreabilidade

| Fase | Requisitos |
|---|---|
| 1 | FND-01..09 |
| 2 | AUT-01..06, CAL-01..05 |
| 3 | OS-01..08 |
| 4 | SYN-01..08, NOT-01..03 |
| 5 | INV-01..04, BAT-01..04 |
| 6 | REP-01..03 |
| 7 | REP-04..05, OSS-01..02 |
