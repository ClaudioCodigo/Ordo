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
- esquema Room versionado em `app/schemas` (schema 1 → 2, migração explícita);
- **Conta Nextcloud** por QR (`nc://login/...`) ou configuração manual, com senha de aplicativo cifrada (AES-GCM/Android Keystore) e excluída de backup;
- **descoberta de agendas** (CalDAV) e seleção de uma agenda de trabalho;
- **importação somente leitura** dos eventos do calendário para um espelho Room, exibidos em Hoje/Agenda com título, data, local, UID, ETag e classificação de cor (verde = validado, vermelho = requer atenção);
- **sincronização periódica e manual** com WorkManager, somente com rede, sem nenhuma escrita remota.
- **sincronização incremental CalDAV RFC 6578** por `sync-token`, com fallback seguro `href`+`ETag` quando o servidor não suporta o delta ou o token expira; somente o cache local é alterado.

O banco local é a fonte de verdade: os rascunhos locais nunca são descartados ou sobrescritos silenciosamente. Eventos remotos e rascunhos locais vivem em tabelas separadas.

## Demonstrativo e limitações atuais

O diagnóstico de bateria apresenta o roteiro inicial, mas ainda não calcula uma decisão definitiva de saúde. Não use esse fluxo para condenar ou aprovar uma bateria sem a revisão técnica prevista para a próxima etapa.

A Fase 2 não escreve no servidor: os eventos são importados somente para leitura. A publicação de OS formatada no `DESCRIPTION`, a resolução de conflitos e as notificações pertencem às Fases 3 e 4.
RRULE, EXDATE, RECURRENCE-ID e o ICS bruto são preservados no espelho, mas a expansão de ocorrências recorrentes ainda não está implementada.
Embora a implementação técnica esteja verificada, a Fase 2 permanece aguardando UAT em aparelho com Nextcloud real. Nenhuma credencial real foi usada nos testes automatizados.

## Próximas etapas

Ainda não implementados: publicação/edição remota e resolução de conflitos (Fases 3–4); inventário completo de clientes, locais, nobreaks e baterias; histórico, tendências, relatórios TXT e exportação/importação JSON; migração completa do diagnóstico de baterias do VoltIQ.

Não coloque senhas, tokens, URLs particulares ou dados reais de atendimento no repositório, em logs ou em relatórios de exemplo.

## Compilar e testar

Abra o projeto no Android Studio com JDK 17 ou superior. No terminal do projeto:

```text
gradlew.bat testDebugUnitTest
gradlew.bat assembleDebug
gradlew.bat assembleDebugAndroidTest
gradlew.bat lintDebug
```

Para testes instrumentados (migração Room, cifra/de-cifra do Keystore), conecte um aparelho ou inicie um emulador e execute `gradlew.bat connectedDebugAndroidTest`. A integração Nextcloud só deve ser testada com um calendário de teste e uma senha de aplicativo temporária; a credencial não deve ser compartilhada.

## Licença e contribuições

O projeto está sendo construído como software open source. Contribuições devem preservar a operação offline-first, a identidade por UUID, o princípio de que nenhum rascunho é perdido sem confirmação explícita e a regra de que nenhuma escrita remota acontece fora do escopo da fase.
