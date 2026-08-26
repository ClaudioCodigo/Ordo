# Nexo

O Nexo é um assistente offline-first para técnicos de campo. Ele organiza ordens de serviço, registros técnicos e, futuramente, os ativos revisados no atendimento.

## O que funciona nesta versão

- cinco áreas: Hoje, Agenda, Ferramentas, Cadastros e Mais;
- ordens de serviço locais persistidas em Room;
- criação de atendimento provisório com UUID interno e número oficial opcional;
- edição com autosave local e recuperação do rascunho após sair e voltar;
- finalização e reabertura somente local, com confirmação;
- filtros e agrupamento da agenda;
- fluxo visual demonstrativo de diagnóstico de bateria;
- esquema Room versionado em `app/schemas`.

O banco local é a fonte de verdade da aplicação nesta fase. O rascunho local nunca deve ser descartado ou sobrescrito silenciosamente.

## Demonstrativo e limitações atuais

O diagnóstico de bateria apresenta o roteiro inicial, mas ainda não calcula uma decisão definitiva de saúde. Não use esse fluxo para condenar ou aprovar uma bateria sem a revisão técnica prevista para a próxima etapa.

A sincronização ainda não está ativa. O estado mostrado pela aplicação é local e não significa que uma OS foi publicada no calendário.

## Próximas etapas

Estão planejados, mas ainda não implementados: conexão Nextcloud/CalDAV, QR Code e configuração manual; sincronização condicional, conflitos e notificações locais; inventário completo de clientes, locais, nobreaks e baterias; histórico, tendências, relatórios TXT e exportação/importação JSON; e a migração completa do diagnóstico de baterias do VoltIQ.

Não coloque senhas, tokens, URLs particulares ou dados reais de atendimento no repositório, em logs ou em relatórios de exemplo.

## Compilar e testar

Abra o projeto no Android Studio com JDK 17 ou superior. No terminal do projeto:

```text
gradlew.bat testDebugUnitTest
gradlew.bat assembleDebug
gradlew.bat assembleDebugAndroidTest
gradlew.bat lintDebug
```

Para testes instrumentados, conecte um aparelho ou inicie um emulador e execute `gradlew.bat connectedDebugAndroidTest`. A integração Nextcloud não deve ser testada com credenciais reais nesta fase.

## Licença e contribuições

O projeto está sendo construído como software open source. Contribuições devem preservar a operação offline-first, a identidade por UUID e o princípio de que nenhum rascunho é perdido sem confirmação explícita.
