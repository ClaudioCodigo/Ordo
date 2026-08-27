# Fase 2 — Pesquisa: conta Nextcloud e agenda somente leitura

## Resultado

A Fase 2 pode ser implementada como um cliente CalDAV estritamente de leitura, mantendo o Room como fonte da UI e separando o espelho remoto dos rascunhos locais. A separacao e indispensavel: uma atualizacao recebida do calendario nunca pode sobrescrever campos que o tecnico esteja editando localmente.

## Referencias

### Referencia operacional do projeto anterior

- Origem: projeto doador `TEDFieldServices`, arquivo `CALDAV-DATA-FORMAT.md`.
- Uso permitido nesta fase: exemplos de `VEVENT`, formato observado de `SUMMARY`/`DESCRIPTION`, escapes iCalendar, line folding, `UID`, `href`, `ETag`, `SEQUENCE` e problemas ja encontrados no Nextcloud.
- Nao reutilizar como implementacao: regex sobre XML/ICS, `split(':').last`, quantidade fixa de segmentos do `SUMMARY`, retry cego ou qualquer rotina de `PUT`/`DELETE`.

### Especificacoes e documentacao primaria

- CalDAV: https://datatracker.ietf.org/doc/html/rfc4791
- iCalendar: https://datatracker.ietf.org/doc/html/rfc5545
- Extensoes iCalendar, incluindo `COLOR`: https://datatracker.ietf.org/doc/html/rfc7986
- WebDAV Collection Synchronization: https://datatracker.ietf.org/doc/html/rfc6578
- Descoberta CalDAV: https://datatracker.ietf.org/doc/html/rfc6764
- Nextcloud — service discovery: https://docs.nextcloud.com/server/stable/admin_manual/issues/general_troubleshooting.html#service-discovery
- Android Keystore/criptografia: https://developer.android.com/privacy-and-security/cryptography

## Servidor real informado

- O endereco particular nao e registrado no repositorio; sera informado somente no aparelho durante a configuracao.
- Verificacao publica em 26/08/2026: TLS 1.3, certificado correspondente ao host e validacao da cadeia aprovada.
- Regra de implementacao: usar a validacao TLS normal do Android, aceitar apenas HTTPS e nunca oferecer bypass para certificado invalido.
- A verificacao nao autenticou, nao acessou agenda e nao utilizou credenciais.

## Decisoes tecnicas

### Isolamento de dados

O cache CalDAV sera armazenado em entidades remotas proprias. `ServiceOrderEntity` continua sendo o registro local editavel. Eventos importados aparecem em Hoje/Agenda, mas a tela remota permanece somente leitura nesta fase. A transformacao de um evento em OS editavel e a publicacao pertencem a Fase 3.

### Limite de escrita remota

A interface de rede da fase expoe somente operacoes de descoberta e leitura. Metodos mutantes (`PUT`, `POST`, `PATCH`, `DELETE`, `PROPPATCH`, `MKCALENDAR`, `MOVE` e `COPY`) nao terao API publica nem implementacao. Testes com servidor simulado falham se qualquer metodo fora da lista permitida for observado.

### Descoberta

1. Consultar `/.well-known/caldav` e seguir redirecionamento HTTPS seguro.
2. Descobrir `current-user-principal`.
3. Descobrir `calendar-home-set`.
4. Listar colecoes de calendario por propriedades e namespaces XML, nunca pelo prefixo textual (`D:`, `d:` etc.).
5. Permitir selecionar uma unica agenda de trabalho ativa.

Credenciais nao serao encaminhadas automaticamente para outro host durante redirecionamentos.

### Sincronizacao

- Preferir `sync-token`/`sync-collection` quando anunciado pelo servidor.
- Fazer carga inicial e fallback por `PROPFIND` de `href`/`getetag`, seguidos de `calendar-multiget` em lotes.
- Persistir um lote em transacao; falha parcial conserva o ultimo cache consistente.
- Exclusao remota remove apenas o espelho remoto correspondente, nunca um rascunho local independente.

### Parsing

- XML: parser estruturado e sensivel a namespace; regex sobre XML e proibida.
- iCalendar: avaliar uma biblioteca RFC 5545 compativel com Android e com a licenca Apache 2.0 do Nexo; `ical4j` (BSD-3-Clause) e o candidato inicial, sujeito a spike de compatibilidade, tamanho e notices.
- Preservar sempre o ICS bruto e os valores brutos de `SUMMARY` e `DESCRIPTION`.
- Tratar folding, escapes, UTF-8, campos com `:`, eventos de dia inteiro, fusos, recorrencia e excecoes.
- O parser operacional do `SUMMARY` sera apenas indicativo nesta fase; nenhuma informacao bruta sera descartada quando a extracao for ambigua.

### QR e segredo

- Formato observado: `nc://login/user:<login>&password:<appPassword>&server:<server>`.
- O conteudo do QR vive apenas em memoria durante a importacao e nunca entra em log, analytics, estado salvo, banco Room ou relatorio.
- Servidor e usuario podem ficar em preferencias locais; a senha de aplicativo sera cifrada com chave AES-GCM criada no Android Keystore.
- O material cifrado e seu IV ficam fora de backup; a chave nao e exportavel.
- Desconectar apaga chave, material cifrado, conta, agenda selecionada e cache remoto, preservando somente rascunhos locais independentes.

Para leitura local do QR, o candidato e CameraX com decodificador QR embarcado e compativel com Apache 2.0. A escolha final deve evitar dependencia obrigatoria de Google Play Services e funcionar sem rede depois da instalacao.

## Riscos a testar

- Servidor sem `sync-token` ou com redirecionamento de descoberta incompleto.
- Agendas compartilhadas com privilegio somente leitura ou nomes iguais.
- Uma resposta XML usando prefixos diferentes.
- Um recurso ICS contendo mais de um `VEVENT`, recorrencias e excecoes.
- Cor do evento (`COLOR`) confundida com cor da colecao da agenda.
- Mesmo `UID` em `href` diferente; nunca deduplicar automaticamente.
- Revogacao da senha de aplicativo e respostas 401/403 sem apagar o cache.
- Backup/restauracao deixando material cifrado sem a chave correspondente.
