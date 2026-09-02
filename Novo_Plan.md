# Implementação do polimento da Fase 3 — Nexo

## Resumo

Reestruturar o fluxo de OS sobre a implementação atual, preservando Room, CalDAV, outbox e alterações não consolidadas. As decisões mais recentes substituem os formatos conflitantes do documento, `SPEC.md`, `REQUIREMENTS.md` e `implementation_plan.md`.

## Mudanças de implementação

### 1. Domínio e migração Room v5

- Introduzir `ServiceOrderFlow`: `RESOLUTION`, `REQUEST` e `UPDATE`.
- Introduzir `TechnicalOpinion`: `CONCLUDED` e `NOT_CONCLUDED`; ambos encerram localmente a OS e aguardam validação externa.
- Persistir fluxo ativo, parecer, observações opcionais, rascunho de atualização, revisão do rascunho e sinalização de número oficial recém-atribuído.
- Migrar `DIAGNOSTICO_CORRECAO` para Resolução e `SERVICO_SOLICITADO` para Solicitação.
- Migrar `CONCLUIDO_COM_PENDENCIAS` para `NOT_CONCLUDED`, preservando Pendências.
- Preservar tabelas e dados de equipamentos, mas ocultá-los e não renderizá-los.
- Vincular outbox/versão à revisão confirmada para que uma publicação atrasada nunca apague edições posteriores.
- Exportar schema v5 e manter migrações não destrutivas 1→5, 2→5, 3→5 e 4→5.

### 2. Renderização, SUMMARY e conflitos

- Nova OS exige Empresa, Técnico, Categoria, Título curto e Local; gera:
  `EMPRESA - ???? - TÉCNICO - CATEGORIA - TÍTULO CURTO - LOCAL`.
- Eventos existentes preservam o `SUMMARY` remoto exatamente; a extração passa a retornar campos confiáveis e segmentos ambíguos visíveis, sem atribuições inventadas.
- Resolução gera exatamente: `OS`, `Cliente`, `Local`, `Técnico`, `Estado`, `Data de Conclusão`, `Demanda`, `Causa`, `Solução`, `Pendências` e, somente quando preenchido, `Observações` após Pendências.
- Solicitação usa o mesmo cabeçalho, com `Solicitação`, `Ação Realizada`, `Pendências` e Observações opcionais.
- Atualização gera `OS`, `Cliente`, `Local`, `Técnico`, `Atualização`, Observações opcionais e depois `--- Histórico anterior ---` com o `DESCRIPTION` remoto anterior integral. Não inclui Estado, data ou validação.
- Pendências vazias renderizam `Nenhuma`; Observações vazias não criam seção.
- Expandir a comparação de três vias para empresa, local, técnico, categoria, título, número oficial e conteúdo não estruturado.
- Quando `????` virar um número válido e não houver outro número local, atualizar automaticamente e marcar o card com `Número oficial atribuído`; o sinal desaparece ao abrir a OS. Números válidos divergentes continuam exigindo revisão.

### 3. Fluxos e interface

- Nova OS: resumo/agendamento → escolha Resolução ou Solicitação → formulário → prévia.
- OS existente: detalhe remoto somente leitura → conferência integrada do SUMMARY → escolha entre os três fluxos → formulário → prévia.
- Remover rota, botão, tela e ViewModel do “Assistente de extração de resumo”.
- Resolução contém Demanda, Causa, Solução, Pendências, Observações e Parecer técnico.
- Solicitação contém Solicitação, Ação Realizada, Pendências, Observações e Parecer técnico.
- Atualização contém texto da atualização, Observações e Parecer técnico.
- Adicionar seletores obrigatórios de início e término para novas OS, inicialmente `agora` e `+1 hora`.
- Bloquear término anterior/igual ao início e eventos de dia inteiro; horários fora de 06:00–19:00 apenas geram aviso.
- Manter equipamentos fora dos três formulários até definição futura.

### 4. Prévia, publicação e navegação

- A prévia mostra, em modo somente leitura, o `SUMMARY`, período e `DESCRIPTION` exatos.
- Usar `CREATE`/“Publicar no calendário” para nova OS, `UPDATE`/“Enviar atualização” para Atualização e `FINALIZE`/“Finalizar OS” para Resolução ou Solicitação existentes.
- Impedir uma segunda publicação da mesma OS enquanto houver operação pendente, enviando ou em conflito.
- Continuar usando `If-Match`, ETag renovado, snapshot imutável e tratamento de `412`.
- Após sucesso, arquivar a versão, atualizar a base remota e limpar apenas o rascunho correspondente à revisão enviada.
- Verde e vermelho continuam controlando a validação externa do card; o Nexo não grava cor nem inclui “Validação” no `DESCRIPTION`.
- Depois da confirmação, abrir a Central de sincronizações removendo editor e prévia da pilha. Voltar leva diretamente à origem `Hoje` ou `Agenda`, com fallback em `Hoje`.

## Interfaces internas afetadas

- `StructuredServiceOrder`: fluxo, parecer técnico, observações, rascunho/revisão e aviso de número oficial.
- `ConfirmedPreviewSnapshot` e outbox: fluxo e revisão imutável confirmada.
- `ExtractedSummary`: confiança/origem dos campos e segmentos ambíguos.
- `OperationalOrderCard`: indicador de número oficial atribuído.
- Repositórios: confirmação transacional, aplicação segura do resultado e reconhecimento do aviso no card.

## Testes e aceite

- Testes golden dos três formatos, incluindo Observações, `Não concluído` e histórico integral.
- Parser com SUMMARY aprovado, formatos legados, segmentos ambíguos e prevenção de técnico/número em Local.
- Migrações Room preservando rascunhos, Pendências, histórico e equipamentos.
- Validação de período, faixa operacional e proibição de dia inteiro.
- Reconciliação automática de `????`, conflito entre números válidos e sinalização no card.
- Preservação de SUMMARY, datas, cor, UID, recorrência e propriedades ICS desconhecidas.
- ViewModels e Compose cobrindo os três fluxos, prévia somente leitura e remoção do assistente isolado.
- Navegação Hoje/Agenda → editor → prévia → central → origem.
- Gates finais: `testDebugUnitTest`, `assembleDebug`, `assembleDebugAndroidTest`, `lintDebug` e UAT controlada no Nextcloud.

## Premissas fixadas

- O working tree atual do Nexo é a linha de base e não será resetado.
- Parecer `Não concluído` também encerra localmente e aguarda decisão externa.
- Observações são opcionais nos três fluxos.
- Equipamentos existentes são preservados, ocultados e excluídos dos novos textos.
- O formato fornecido pelo usuário prevalece sobre os documentos anteriores.
- A linha de base de testes deverá ser revalidada no início da execução; nesta sessão o Gradle não iniciou por falha de conexão loopback do ambiente.
