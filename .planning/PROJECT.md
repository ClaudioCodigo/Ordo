# Nexo

## Visao do produto

Nexo e um aplicativo Android offline-first para tecnicos de servico de campo. Ele transforma eventos de uma agenda compartilhada Nextcloud/CalDAV em Ordens de Servico estruturadas, preserva rascunhos, registra procedimentos e ativos e devolve ao calendario uma descricao clara para acompanhamento do controle tecnico.

O aplicativo nasce para ajudar seu autor no trabalho diario, mas sera open source e configuravel para outras pessoas. Cada instalacao usa sua propria conta Nextcloud e seu banco local.

## Fluxo principal

1. Ler eventos do calendario de trabalho e mostrar a agenda do dia.
2. Abrir uma OS existente ou criar atendimento provisório imediatamente visivel no calendario.
3. Registrar atualizacoes, pendencias, equipamentos, baterias e procedimentos.
4. Salvar rascunhos localmente sem perda, inclusive offline.
5. Publicar atualizacao ou finalizacao formatada no `DESCRIPTION` do evento.
6. Reconhecer validacao verde e retorno vermelho pela propriedade `COLOR`.
7. Preservar historico local, conflitos e relatorios.

## Principios fixados

- Offline-first: a UI le do banco local; rede nunca e pre-requisito para escrever um rascunho.
- Rascunhos e versoes nunca sao descartados ou sobrescritos silenciosamente.
- Identidade remota por `UID` e `href`; numero da OS e opcional e mutavel.
- Escrita CalDAV sempre condicional por `ETag`/`If-Match`.
- `412 Precondition Failed` interrompe o envio e abre resolucao de conflito; nunca faz retry cego.
- Versao-base, versao local e versao remota permanecem separadas para mesclagem de tres vias.
- Eventos parecidos nao sao deduplicados automaticamente.
- A cor e um sinal externo: verde valida, vermelho requer atencao; o app nao aplica verde sozinho.
- Data de execucao aparece na descricao; horario permanece nos campos proprios do evento.
- Resultados tecnicos sao dados estruturados locais e tambem geram texto humano na OS.
- Credenciais nunca entram em Git, logs, relatorios ou exportacoes.
- Dependencias e permissoes so entram quando uma funcionalidade real precisar delas.

## Integracao Nextcloud

- Entrada principal: QR `nc://login/user:<login>&password:<appPassword>&server:<server>`.
- Alternativa: servidor, login e senha de aplicativo informados manualmente.
- Primeira versao: uma conta e um calendario de trabalho ativos por instalacao.
- O app descobre calendarios apos autenticar; nao exige URL CalDAV completa.
- Sincronizacao ao abrir, atualizar manualmente, publicar e por WorkManager.
- Editor ativo verifica mudanca remota periodicamente; o `If-Match` continua sendo a garantia atomica.

## Formato operacional

`SUMMARY` preserva o texto bruto e pode extrair, com confirmacao quando ambiguo: unidade, numero da OS, tecnico, categoria e titulo curto.

`DESCRIPTION` recebe demanda, numero da OS, data de execucao, tecnico, local, atualizacoes, resultado, pendencias e registros tecnicos. Atualizacao preserva contexto; finalizacao produz uma versao consolidada e limpa.

Campos nativos CalDAV continuam sendo usados para `DTSTART`, `DTEND`, `LOCATION`, `UID`, `ETag`, `SEQUENCE` e `COLOR`.

## Modulos

- Hoje e Agenda.
- Ordens de Servico e rascunhos.
- Clientes, unidades, locais e equipamentos.
- Nobreaks, baterias, instalacoes e retiradas.
- Diagnostico guiado de baterias, migrado do VoltIQ apos revisao.
- Historico, tendencias, TXT e exportacao/importacao JSON.

## Projetos doadores

- `VoltIQ`: fonte do dominio e da UI de diagnostico de bateria ja aprovados; migrar por partes e preservar testes.
- `TEDFieldServices`: referencia de formato, CalDAV e exemplos de OS; nao copiar o retry de `412` nem credenciais/historico sensivel.
- `nextcloud-calendar-mobile`: somente referencia conceitual e protocolar; revisar compatibilidade de licenca antes de copiar codigo.

## Fora do primeiro marco

- Backend proprio, Firebase ou sincronizacao estruturada entre aparelhos.
- Fotos, assinatura, geolocalizacao e OCR.
- PDF e planilhas.
- Diagnostico laboratorial de capacidade ou percentuais absolutos de saude.
- Deduplicacao automatica de chamados semelhantes.

## Estado atual

A Fase 1 foi concluida. A UI aprovada e as cinco abas foram preservadas; Room e a fonte local real; nova OS, autosave, edicao, finalizacao e reabertura locais funcionam; o schema v1 e versionado e nao existe fallback de migracao destrutiva. Build, 15 testes unitarios, APK de testes e lint passam. CalDAV continua deliberadamente inativo ate a Fase 2.
