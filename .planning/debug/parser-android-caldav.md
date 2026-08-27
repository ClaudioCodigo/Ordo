---
status: resolved
trigger: "Ao conectar por QR ou dados manuais, a descoberta CalDAV falha no Android com This parser does not support specification Unknown version 0.0, após uma correção anterior envolver setFeature em runCatching."
created: 2026-08-26
updated: 2026-08-26
---

# Parser Android na descoberta CalDAV

## Symptoms

- Expected: depois de validar QR/manual, descobrir e listar as agendas Nextcloud.
- Actual: o servidor é aceito como conectado, mas a descoberta termina com erro vermelho.
- Error: `This parser does not support specification "Unknown" version "0.0"`.
- Timeline: começou após a implementação da Fase 2; o erro anterior era a URI `http://apache.org/xml/features/disallow-doctype-decl` e foi parcialmente contornado com `runCatching` nos `setFeature`.
- Reproduction: conectar pelo QR do Nextcloud ou pelos mesmos dados manualmente e aguardar `Descobrindo agendas...`.
- Device available: Samsung SM-A556E conectado por ADB.

## Current Focus

- hypothesis: `DocumentBuilderFactory` do Android rejeita outra configuração JAXP ainda aplicada diretamente, provavelmente `isXIncludeAware = false`; tornar tudo best-effort sem outra defesa enfraqueceria a proteção XXE.
- test: executar parser namespace-aware e rejeição de DOCTYPE como testes instrumentados no Android real.
- expecting: XML CalDAV simples deve ser parseado; XML com DOCTYPE deve falhar antes de qualquer resolução externa.
- next_action: confirmar no código, aplicar defesa independente do provider (rejeição de DOCTYPE + EntityResolver), compilar e executar testes no SM-A556E.

## Evidence

- timestamp: 2026-08-26
  observation: o código atual protege apenas quatro chamadas `setFeature` com `runCatching`; `isXIncludeAware` e `isExpandEntityReferences` continuam diretas.
- timestamp: 2026-08-26
  observation: o aparelho SM-A556E está visível pelo ADB e pode executar testes instrumentados.
- timestamp: 2026-08-26
  observation: a mensagem `This parser does not support specification "Unknown" version "0.0"` é a exceção lançada pela implementação JAXP base do Android para APIs não suportadas, incluindo `setXIncludeAware`/`isXIncludeAware`; o parser local ainda chamava essas APIs (e `setExpandEntityReferences`) dentro da configuração da factory.
- timestamp: 2026-08-26
  observation: após reiniciar o ADB, o Samsung SM-A556E ficou online; o Gradle falhou antes da compilação/teste com `java.io.IOException: Unable to establish loopback connection` tanto no sandbox quanto fora dele.
- timestamp: 2026-08-26
  observation: uma sonda executada pelo `app_process` no SM-A556E reproduziu exatamente `UnsupportedOperationException: This parser does not support specification "Unknown" version "0.0"` ao chamar `setXIncludeAware(false)`; a configuração corrigida leu `multistatus` e o bloqueio prévio rejeitou DOCTYPE.
- timestamp: 2026-08-26
  observation: os arquivos Kotlin reais `CalDavModels.kt` e `CalDavXmlParser.kt` foram compilados diretamente com o compilador Kotlin 2.2.20 e executaram a prova de parse/rejeição com `PRODUCTION_PARSER_OK`.
- timestamp: 2026-08-26
  observation: o relatório independente `relatorio_investigacao_parser.md` chegou à mesma causa e confirmou que o APK instalado ainda contém o parser anterior.
- timestamp: 2026-08-26
  observation: não há processo `java.exe`/daemon Gradle ativo para encerrar; apenas `studio64.exe` está aberto. Um daemon novo chega a aceitar TCP em 127.0.0.1, mas falha ao criar o pipe interno do seletor Java por socket Unix (`Invalid argument: connect`).
- timestamp: 2026-08-26
  observation: após reiniciar o computador e instalar pelo Android Studio, o usuário concluiu a UAT com a conta Nextcloud real: a descoberta listou as agendas, a agenda de trabalho foi selecionada e os eventos foram lidos pelo aplicativo.
- timestamp: 2026-08-26
  observation: o retorno automático e sem confirmação ao selecionar uma agenda foi identificado como uma falha de feedback visual separada; não é falha de descoberta nem de persistência.

## Eliminated

- hypothesis: QR inválido ou credenciais incorretas.
  reason: QR e entrada manual chegam ao mesmo estado conectado e falham somente quando começa o parser da descoberta.

## Resolution

- root_cause:
  `DocumentBuilderFactory` Android não implementa integralmente JAXP 1.5. A chamada direta a `isXIncludeAware = false` (e potencialmente a configuração de expansão de entidades) propaga `UnsupportedOperationException` com a mensagem `Unknown version 0.0` antes de o XML CalDAV ser lido.
- fix:
  `CalDavXmlParser` agora configura apenas `namespaceAware` diretamente; flags Xerces/accessExternal opcionais são tentadas por helpers que capturam somente falhas de configuração. As chamadas XInclude/expansão não são invocadas. A rejeição independente de provider para qualquer `DOCTYPE` ocorre antes da factory, e `EntityResolver` sempre retorna uma fonte vazia para bloquear resolução externa.
- verification:
  causa e estratégia confirmadas no Android real por sonda isolada; parser Kotlin de produção compilado e executado diretamente com sucesso. `CalDavXmlParserTest` cobre XML namespace-aware e rejeição de DOCTYPE, e `CalDavXmlParserInstrumentedTest` cobre os mesmos casos no Android. A validação funcional definitiva também passou no Samsung SM-A556E com a conta Nextcloud real: descoberta, seleção e leitura de eventos concluídas. A execução Gradle via terminal Codex continua afetada por um problema ambiental de loopback, mas a instalação/build pelo Android Studio funciona.
- files_changed:
  `app/src/main/java/dev/claudiocodigo/nexo/data/caldav/CalDavXmlParser.kt`, `app/src/androidTest/java/dev/claudiocodigo/nexo/data/caldav/CalDavXmlParserInstrumentedTest.kt`, `.planning/debug/parser-android-caldav.md`
