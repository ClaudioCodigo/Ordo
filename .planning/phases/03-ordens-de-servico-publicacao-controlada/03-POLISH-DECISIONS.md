# Fase 3 — Decisões de polimento do fluxo de Ordens de Serviço

**Reunido em:** 01/09/2026  
**Status:** decisões de polimento aprovadas; definição do sistema de equipamentos adiada  
**Escopo:** alterações funcionais e de experiência posteriores à implementação inicial da Fase 3

## Objetivo

Este documento consolida as mudanças discutidas para corrigir e simplificar o fluxo guiado de criação, atendimento, atualização e publicação de Ordens de Serviço no Nexo.

Ele registra o comportamento desejado, mas não afirma que as mudanças já estejam implementadas. Quando houver divergência, estas decisões substituem as propostas correspondentes de `implementation_plan.md` e refinam o contexto original da Fase 3.

## 1. Fluxos operacionais separados

Ao trabalhar em uma OS, o técnico escolhe explicitamente um dos fluxos:

1. **Resolução**
2. **Solicitação**
3. **Atualização**

`Atualização` é um fluxo independente e enxuto. Ele não reutiliza os campos de Resolução ou Solicitação.

### 1.1 Nova OS

Uma OS criada pelo Nexo segue esta ordem:

1. Resumo e agendamento.
2. Escolha entre `Resolução` e `Solicitação`.
3. Preenchimento guiado.
4. Prévia completa, somente leitura.
5. Confirmação e publicação.

O fluxo `Atualização` não aparece na criação porque ainda não existe histórico anterior para atualizar.

### 1.2 OS existente no CalDAV

Uma OS recebida pelo calendário segue esta ordem:

1. Abertura do evento remoto em modo somente leitura.
2. Ação explícita para iniciar ou continuar o atendimento.
3. Conferência do `SUMMARY` e de eventuais alterações remotas.
4. Escolha entre `Resolução`, `Solicitação` ou `Atualização`.
5. Preenchimento guiado do fluxo escolhido.
6. Prévia completa, somente leitura.
7. Confirmação e publicação.

O editor deve abrir sempre a mesma cópia local vinculada à ocorrência remota, sem criar rascunhos duplicados.

## 2. Campos dos formulários

### 2.1 Resolução

- Problema relatado
- Solução executada
- Pendências
- Observações
- Parecer técnico: `Concluído` ou `Não concluído`

### 2.2 Solicitação

- Solicitação
- Ação executada
- Pendências
- Observações
- Parecer técnico: `Concluído` ou `Não concluído`

### 2.3 Atualização

- Texto da atualização
- Parecer técnico: `Concluído` ou `Não concluído`

### 2.4 Campos e estados removidos

Não fazem parte destes fluxos:

- Diagnóstico
- Validação realizada
- Estado `Concluído com pendências`

O campo `Pendências` continua presente em Resolução e Solicitação. O app não deve tentar julgar contradições entre esse campo e o parecer `Concluído`, nem exibir alerta automático sobre essa combinação nesta entrega.

## 3. Parecer técnico e validação externa

O parecer do técnico e a decisão de quem valida a OS são estados diferentes.

- **Parecer técnico:** `Concluído` ou `Não concluído`.
- **Validação externa inicial:** `Aguardando responsável`.
- A cor e as ações posteriores realizadas no calendário representam a decisão externa.
- O responsável pode manter ou tornar o evento vermelho e replicá-lo ou reagendá-lo para outro dia.
- A decisão externa prevalece sobre o parecer técnico para o estado operacional apresentado pelo app.

O Nexo não deve aplicar automaticamente as cores de validação do calendário.

## 4. Formato do fluxo Atualização

Uma atualização nova é colocada acima do conteúdo anterior. O `DESCRIPTION` anterior é preservado integralmente abaixo de um divisor.

Formato de referência:

```text
Nº da OS: 15479
Empresa: PIER
Local: Armazém 5
Técnico: Claudio / Rodrigo

Parecer técnico: Concluído
Validação: Aguardando responsável
Data: 01/09/2026

Atualização:
Texto informado pelo técnico.

--- Histórico anterior ---

<DESCRIPTION anterior preservado integralmente>
```

Regras:

- A atualização mais recente fica acima do histórico anterior.
- O texto anterior não é reformatado, resumido ou descartado.
- O horário não é repetido no `DESCRIPTION`; permanece em `DTSTART` e `DTEND`.
- A data continua no corpo como registro operacional.
- `Concluído` e `Não concluído` são pareceres técnicos e ambos aguardam validação externa.

## 5. Tratamento do SUMMARY

### 5.1 OS existente

O `SUMMARY` remoto de uma OS existente deve ser preservado exatamente como veio do CalDAV.

- A conferência permite corrigir os campos extraídos localmente.
- As correções alimentam o cabeçalho organizado do `DESCRIPTION`.
- As correções não reescrevem o `SUMMARY` remoto.
- A tela deve mostrar o `SUMMARY` original e os campos extraídos de maneira editável.
- Segmentos ambíguos devem permanecer visíveis para conferência, sem serem forçados silenciosamente para um campo incorreto.

O parser não pode:

- usar a string completa do `SUMMARY` como título do atendimento;
- colocar nome do técnico em `Local`;
- colocar número da OS em `Local`;
- inventar `Empresa/Unidade` quando o padrão utiliza somente `Empresa`.

### 5.2 Nova OS

Na criação, a primeira etapa é dedicada ao resumo e ao agendamento. O Nexo gera o `SUMMARY` a partir de campos estruturados confirmados pelo técnico.

Padrão aprovado:

```text
EMPRESA - ???? - TÉCNICO - CATEGORIA - TÍTULO CURTO - LOCAL
```

Regras:

- O primeiro campo é somente `Empresa`.
- `????` representa uma OS que ainda não recebeu número oficial.
- Não usar `SEM OS`, pois isso pode sugerir que o trabalho foi executado sem o processo oficial.
- `Técnico` é texto livre e aceita mais de um nome, por exemplo `Claudio / Rodrigo`.
- O nome configurado ou usado recentemente pode ser sugerido, mas continua editável.
- Como o Nexo possui os campos estruturados da criação, o cabeçalho do `DESCRIPTION` deve usar diretamente os valores confirmados, sem analisar novamente o texto que o próprio app gerou.

### 5.3 Atribuição posterior do número oficial

Quando o responsável substituir `????` por um número válido no calendário:

- se o número local estiver ausente ou provisório, o Nexo atualiza automaticamente o campo local;
- o app informa, por exemplo, `OS oficial atribuída: 15479`;
- se já houver localmente outro número válido, a diferença vira conflito e exige conferência;
- se outros campos do `SUMMARY` também mudarem, somente os campos afetados são apresentados para revisão.

## 6. Remoção do assistente isolado

O botão chamado **Assistente de extração de resumo** deve ser removido.

A lógica útil de extração não deve permanecer como ferramenta opcional e isolada. Ela passa a integrar a etapa obrigatória de conferência do `SUMMARY` no fluxo normal.

## 7. Agendamento de novas OS

Toda OS criada pelo Nexo deve ter período definido.

- Eventos de dia inteiro não são permitidos.
- Data e hora de início são obrigatórias.
- Data e hora de término são obrigatórias.
- Início e término são totalmente ajustáveis.
- Término anterior ao início é um erro bloqueante.
- A faixa operacional normal é das **06:00 às 19:00**.
- Um período parcialmente ou totalmente fora dessa faixa gera aviso, mas não bloqueia a publicação.

### Período inicial aprovado

O período deve ser preenchido inicialmente de `agora` até `uma hora depois`. Os dois valores continuam totalmente ajustáveis pelo técnico antes da publicação.

## 8. Alterações remotas e conflitos

O evento remoto permanece somente leitura até o técnico iniciar ou continuar o atendimento.

Se o servidor mudar enquanto existe uma cópia local:

- o app compara a base remota conhecida, a versão remota atual e os campos locais;
- apresenta somente campos novos ou diferentes para conferência;
- oferece, por campo, `Manter meu valor` ou `Usar valor do calendário`;
- após a escolha, o técnico ainda pode editar livremente a cópia local;
- a publicação usa o `ETag` remoto renovado;
- uma nova alteração concorrente bloqueia novamente o envio e exige nova conferência;
- nenhuma mudança local ou remota é descartada silenciosamente.

Quando somente o número oficial mudar de `????` para um número válido, aplica-se a atualização automática descrita na seção 5.3.

## 9. Publicação, fila e navegação

As ações remotas continuam semanticamente distintas:

- `Publicar no calendário` para criar o evento remoto de uma OS nova;
- `Enviar atualização` para acrescentar uma atualização a uma OS existente;
- `Finalizar OS` para publicar Resolução ou Solicitação concluída.

Todas utilizam a mesma infraestrutura segura de prévia, outbox e escrita condicional, mas representam intenções diferentes para o usuário.

Após confirmar a publicação:

1. O app abre a Central de sincronizações para mostrar o resultado ou o estado da fila.
2. Ao sair ou voltar dessa central, retorna diretamente à origem do atendimento: `Hoje` ou `Agenda`.
3. Editor e prévia são removidos da pilha de navegação.
4. Se a origem não puder ser determinada, o fallback é `Hoje`.

## 10. Prévia

- A prévia permanece completa e somente leitura.
- Deve mostrar exatamente o texto e o período confirmados para publicação.
- Correções são feitas retornando ao formulário estruturado.
- Não existe editor livre do texto final.
- A boa legibilidade observada na implementação atual deve ser preservada.

## 11. Formulário de equipamentos

Já existe decisão anterior de manter um formulário básico e opcional para registros de equipamentos/dispositivos, sem especialização por tipo nesta fase.

### Decisão adiada

O sistema de equipamentos ainda não foi definido. Portanto, a decisão sobre a presença dessa seção nos fluxos de Resolução, Solicitação e Atualização fica adiada até que esse sistema seja especificado.

Até essa definição, a implementação não deve presumir a presença do formulário de equipamentos em nenhum dos três fluxos.

## 12. Impactos esperados na implementação atual

O planejamento técnico deverá considerar, no mínimo:

- substituir o editor genérico atual pelos três fluxos operacionais aprovados;
- revisar o modelo de estado para separar modo do fluxo, parecer técnico e validação externa;
- remover `CONCLUIDO_COM_PENDENCIAS` da experiência do usuário;
- preservar `Pendências` como campo textual em Resolução e Solicitação;
- corrigir extração e mapeamento do `SUMMARY`;
- integrar a conferência do resumo ao fluxo e remover o botão isolado;
- adicionar início e término ajustáveis à criação de OS;
- preservar o `SUMMARY` de eventos existentes durante atualizações do `DESCRIPTION`;
- implementar o formato acumulativo do fluxo Atualização;
- preservar origem de navegação ao passar pela Central de sincronizações;
- atualizar renderer, extractor, persistência Room, prévia, conflitos, testes e migrações conforme necessário.

## 13. Critérios de aceite funcionais

1. Uma nova OS não pode ser publicada sem início e término válidos.
2. Uma nova OS gera `SUMMARY` no padrão aprovado e usa `????` enquanto não houver número oficial.
3. Uma OS existente preserva o `SUMMARY` remoto mesmo após correções no cabeçalho do `DESCRIPTION`.
4. Técnico e número da OS nunca são preenchidos no campo Local pelo parser.
5. O botão `Assistente de extração de resumo` não aparece mais como fluxo isolado.
6. Resolução, Solicitação e Atualização exibem somente os campos aprovados para cada fluxo.
7. `Pendências` permanece disponível em Resolução e Solicitação.
8. O app não oferece `Concluído com pendências`.
9. Uma Atualização preserva integralmente o `DESCRIPTION` anterior abaixo do divisor.
10. O parecer técnico não é confundido com a validação externa.
11. Alterações remotas nunca sobrescrevem silenciosamente o rascunho local.
12. Depois da Central de sincronizações, voltar leva à tela `Hoje` ou `Agenda` que originou o atendimento.

---

*Documento de decisões de polimento da Fase 3. A definição do sistema de equipamentos permanece adiada.*
