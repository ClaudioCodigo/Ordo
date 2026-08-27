# Fase 2 — Plano: conta Nextcloud e agenda somente leitura

## Objetivo

Conectar o Nexo a uma conta Nextcloud por QR ou configuracao manual, descobrir uma agenda compartilhada e exibir seus eventos reais em Hoje/Agenda a partir do cache Room, sem disponibilizar qualquer escrita remota.

## Entrega demonstravel

Em um aparelho Android, o tecnico escaneia o QR do Nextcloud, confirma os dados nao secretos, seleciona a agenda de trabalho, sincroniza e visualiza eventos com titulo, descricao, data, horario, local, cor e identidade remota. O app continua mostrando os dados ja importados sem rede e nenhum recurso do servidor muda de `ETag` por acao do Nexo.

## Escopo e cercas

- Cobre `AUT-01..06` e `CAL-01..05`.
- Uma conta e uma agenda de trabalho ativas por instalacao.
- Importacao e cache somente leitura; nenhuma criacao, edicao, finalizacao ou exclusao remota.
- Eventos remotos e rascunhos locais usam armazenamentos separados.
- Tela de evento remoto e somente leitura nesta fase.
- Sem notificacoes, resolucao de conflitos ou formatacao/publicacao de OS; pertencem as Fases 3 e 4.
- Nenhuma credencial real entra em chat, Git, teste automatizado, log ou screenshot.

## Referencias obrigatorias

- Pesquisa da fase: `02-RESEARCH.md`.
- Documento operacional legado: projeto doador `TEDFieldServices`, arquivo `CALDAV-DATA-FORMAT.md`.
- RFC 4791 (CalDAV), RFC 5545 (iCalendar), RFC 6578 (sync-collection), RFC 6764 (descoberta) e RFC 7986 (`COLOR`).
- Documentacao oficial de service discovery do Nextcloud e criptografia/Keystore do Android, listadas em `02-RESEARCH.md`.

## Estrategia de execucao

Cada lote deve ser pequeno, revisado antes do seguinte e terminar com testes. A implementacao futura sera delegada ao subagente `gpt-5.6-luna`, conforme decisao do projeto; o agente principal revisara os diffs e executara as verificacoes.

O primeiro caminho vertical sera: configuracao manual de uma conta de teste -> descoberta de agenda em servidor simulado -> importacao de um evento -> persistencia Room -> exibicao somente leitura em Hoje. QR, sincronizacao incremental e expansoes de UI entram depois que esse caminho estiver protegido por testes.

## Lote 0 — Contrato de seguranca e spikes de dependencias

1. Definir interfaces `CredentialStore`, `CalDavDiscoveryClient`, `CalDavReadClient`, `CalendarRepository` e `CalendarSyncCoordinator` sem metodo mutante.
2. Criar uma allowlist de metodos HTTP: `OPTIONS`, `PROPFIND`, `REPORT`, `GET` e `HEAD`; qualquer outro metodo deve falhar antes de sair do aparelho.
3. Avaliar e fixar dependencias estaveis para HTTP, servidor simulado, CameraX/QR e iCalendar.
4. Fazer spike de parsing com ICS reais anonimizados: folding, escapes, acentos, `COLOR`, recorrencia, evento de dia inteiro e fuso horario.
5. Registrar licencas e notices das dependencias; nao copiar codigo do cliente open source Nextcloud sem revisao de licenca.
6. Adicionar `INTERNET` e, somente quando o scanner existir, `CAMERA`; nenhuma outra permissao.

**Porta de reversibilidade:** se o parser iCalendar candidato nao funcionar no minSdk do Nexo ou aumentar o APK de forma desproporcional, parar antes de acopla-lo ao dominio e comparar uma alternativa compativel.

**Verificacao:** teste do cliente rejeita `PUT`/`DELETE`; fixture ICS passa no parser; build e relatorio de dependencias/licencas passam.

## Lote 1 — Modelo remoto e migracao Room 1 -> 2

1. Criar entidades para conta sem segredo, calendario descoberto, evento remoto e estado de sincronizacao.
2. Identificar evento por conta + calendario + `href`; indexar `UID` sem torna-lo chave unica global.
3. Guardar ICS bruto, `href`, `UID`, `ETag`, `SEQUENCE`, `SUMMARY`, `DESCRIPTION`, `LOCATION`, inicio/fim, all-day, fuso, recorrencia, cor do evento, cor da agenda e timestamps de sincronizacao.
4. Manter `ServiceOrderEntity` separado; nenhuma chave remota pode substituir ou apagar rascunhos existentes.
5. Implementar DAOs transacionais e consultas para Hoje, Agenda, pesquisa, atrasados e detalhe remoto.
6. Criar migracao explicita 1 -> 2, exportar schema 2 e testar que OS/rascunhos da Fase 1 sobrevivem inalterados.

**Verificacao:** teste de migracao usa schema v1 real; reinicio preserva eventos e rascunhos; colisao de UID em hrefs distintos conserva ambos.

## Lote 2 — Conta, QR/manual e armazenamento seguro

1. Criar em Mais a tela `Conta Nextcloud`, com estados desconectado, validando, conectado, erro de autenticacao e erro TLS/rede.
2. Implementar entrada manual de servidor, usuario e senha de aplicativo.
3. Implementar scanner local do QR no formato observado, com CameraX e decodificacao embarcada; oferecer colar/digitar como alternativa.
4. Exibir uma tela de confirmacao contendo apenas servidor e usuario; a senha nunca volta a ser exibida.
5. Normalizar URL, exigir HTTPS, rejeitar campos ausentes e impedir envio de Authorization a host diferente durante redirect.
6. Cifrar a senha com AES-GCM/Android Keystore; excluir material cifrado de backup e tratar chave ausente/corrompida sem crash.
7. Implementar desconexao que elimina segredo, metadados da conta e cache remoto em transacao, sem apagar rascunhos locais independentes.

**Verificacao:** testes do parser QR cobrem ordem, percent-encoding, delimitadores ausentes, URL HTTP e payload malformado; teste instrumentado prova cifrar/decifrar/apagar; busca no repositorio e logs nao encontra senha ou Authorization.

## Lote 3 — Descoberta CalDAV e selecao da agenda

1. Resolver `/.well-known/caldav`, `current-user-principal` e `calendar-home-set` usando XML estruturado por namespace.
2. Listar apenas colecoes que suportem `VEVENT`, preservando `href`, nome, descricao, cor, privilegios e `sync-token` quando disponiveis.
3. Mostrar agendas descobertas e permitir selecionar exatamente uma agenda de trabalho.
4. Persistir a selecao e permitir troca consciente de agenda; trocar limpa somente o cache remoto da selecao anterior.
5. Tratar 401, 403, 404, redirect inseguro, timeout, TLS invalido e XML parcial com mensagens orientadas ao usuario.
6. Nunca oferecer opcao para ignorar certificado invalido.

**Verificacao:** servidor simulado responde com diferentes prefixos XML e redirecionamentos; descoberta continua correta. Testes confirmam que Authorization nao cruza host e que nenhuma requisicao mutante ocorre.

## Lote 4 — Tracer vertical de importacao somente leitura

1. Implementar carga inicial em lotes: listar `href`/`getetag` e obter os recursos por `calendar-multiget` ou fallback seguro.
2. Interpretar iCalendar sem regex, preservando o texto bruto antes de qualquer extracao.
3. Gravar o lote em transacao e publicar estado de sincronizacao somente depois do commit.
4. Mapear `COLOR` verde `#008000` para `Validado`, vermelho `#B22222` para `Requer atencao` e demais/ausente para `Nao classificado`, sem confundir cor do evento com cor da agenda.
5. Exibir ao menos um evento importado em Hoje e no detalhe remoto somente leitura.
6. Tratar uma nova `UID` como novo evento; semelhanca de titulo/descricao nunca causa mesclagem automatica.

**Verificacao:** teste end-to-end com servidor simulado percorre conexao -> descoberta -> selecao -> REPORT/multiget -> Room -> Hoje; o gravador HTTP comprova zero escrita.

## Lote 5 — Sincronizacao incremental e robustez offline

1. Usar `sync-token`/`sync-collection` quando suportado; manter fallback por comparacao de `href` + `ETag`.
2. Atualizar somente recursos alterados e remover do espelho apenas recursos que o servidor informou como excluidos.
3. Em 401/403, manter cache e marcar conta como requer nova autenticacao; nunca apagar dados automaticamente.
4. Em falha de rede, parser ou lote parcial, conservar a ultima visao consistente e mostrar erro com opcao de tentar novamente.
5. Sincronizar ao conectar, abrir o app e atualizar manualmente; preparar WorkManager de leitura com restricao de rede, sem prometer periodicidade exata.
6. Impedir duas sincronizacoes concorrentes para a mesma agenda e registrar somente telemetria local sem conteudo de evento/credenciais.

**Verificacao:** testes cobrem token valido/invalido, fallback ETag, alteracao, exclusao, 401, timeout, lote corrompido e duas chamadas concorrentes.

## Lote 6 — Hoje, Agenda, pesquisa e estados reais

1. Unificar na apresentacao os cards locais e os eventos remotos sem fundir seus registros no banco.
2. Mostrar origem/estado de maneira discreta e compreensivel; evento remoto abre detalhe somente leitura.
3. Implementar agrupamento por dia, pesquisa em titulo/descricao/local, atrasados e filtros sem depender da rede.
4. Mostrar ultima sincronizacao real, progresso, modo offline e erro; eliminar qualquer horario ou sucesso ficticio.
5. Preservar o comportamento aprovado de criar, editar e excluir rascunhos locais.
6. Garantir acessibilidade, textos em portugues e adaptacao celular/tablet da UI existente.

**Verificacao:** testes de ViewModel e UI cobrem dados mistos, pesquisa, cores, offline, cache vazio, erro e navegacao; UAT confirma que os rascunhos da Fase 1 continuam funcionando.

## Lote 7 — Validacao integrada e teste real controlado

1. Executar `testDebugUnitTest`, testes de migracao/Room, `assembleDebug`, `assembleDebugAndroidTest`, testes instrumentados e `lintDebug`.
2. Executar suite de seguranca que falha diante de metodo HTTP mutante, log de Authorization ou exportacao de segredo.
3. Instalar no aparelho conectado e testar QR com senha de aplicativo temporaria gerada pelo usuario diretamente no Nextcloud; a credencial nao sera compartilhada no chat.
4. Selecionar a agenda real, sincronizar e comparar quantidade/amostra de eventos com o cliente web.
5. Registrar `href`/`ETag` de uma amostra antes e depois; confirmar que nenhum recurso remoto foi criado, modificado, recolorido ou excluido.
6. Revogar a senha temporaria ao fim do teste e confirmar que o app preserva o cache, informa que precisa reconectar e nao entra em loop.
7. Atualizar README, requisitos, estado e resumo da fase somente apos aceite do usuario.

## Matriz de cobertura

| Requisito | Lotes |
|---|---|
| AUT-01 | 0, 2, 7 |
| AUT-02 | 2, 7 |
| AUT-03 | 2, 3 |
| AUT-04 | 2, 7 |
| AUT-05 | 3, 4, 7 |
| AUT-06 | 1, 2, 7 |
| CAL-01 | 1, 4, 5 |
| CAL-02 | 0, 1, 4 |
| CAL-03 | 1, 4, 6 |
| CAL-04 | 1, 4, 6 |
| CAL-05 | 1, 4, 5 |

## Criterios de conclusao

- QR e configuracao manual funcionam sem expor a senha.
- Certificado HTTPS e validado normalmente; nao existe bypass TLS.
- Uma agenda pode ser descoberta, selecionada, importada e consultada offline.
- UID, href, ETag, SEQUENCE, ICS bruto, SUMMARY, DESCRIPTION e cores sao preservados.
- Rascunhos locais sobrevivem a migracao, sincronizacao, troca de agenda e falhas de rede.
- Hoje, Agenda, pesquisa e detalhe usam somente o banco local.
- Nenhuma funcao de escrita CalDAV existe ou e acionada na Fase 2.
- Build, testes, migracao, instrumentacao, lint e UAT em aparelho passam.
- O usuario confirma que os eventos exibidos correspondem ao calendario e que nenhum evento remoto mudou.
