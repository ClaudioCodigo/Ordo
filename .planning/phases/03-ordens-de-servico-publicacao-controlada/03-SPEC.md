# Fase 3: Ordens de Serviço e publicação controlada — Especificação

**Criada:** 27/08/2026
**Ambiguidade:** 0,09 (gate: ≤ 0,20)
**Requisitos:** 8 bloqueados

## Objetivo

Transformar eventos CalDAV e atendimentos provisórios em Ordens de Serviço estruturadas, editáveis offline e publicáveis com prévia e confirmação, sem perder contexto, campos nativos, versões locais ou alterações concorrentes.

## Contexto

A Fase 2 já mantém um espelho Room somente leitura dos eventos CalDAV, preservando `href`, `UID`, `ETag`, `SEQUENCE`, ICS bruto, `SUMMARY`, `DESCRIPTION`, datas, localização e cor. A Fase 1 já oferece OS local com UUID, número externo opcional, autosave e edição offline, mas o modelo ainda é genérico e não possui vínculo com evento, atualizações estruturadas, histórico de versões ou publicação remota.

O ICS real analisado usa `TZID=America/Sao_Paulo`, `VTIMEZONE`, folding RFC 5545, escapes de texto, `STATUS:CONFIRMED`, `SEQUENCE` e `????` como número provisório. `ETag` pertence à camada WebDAV e não ao arquivo exportado. O exemplo também demonstrou que a data escrita manualmente no `DESCRIPTION` pode divergir de `DTSTART`; o Nexo deve alertar, nunca corrigir silenciosamente.

## Requisitos

1. **Vínculo seguro entre evento e OS**: iniciar o atendimento de um evento cria ou recupera uma única OS local vinculada pela identidade remota composta por conta, calendário e `href`.
   - **Atual:** evento remoto e `ServiceOrder` são registros separados, sem vínculo ou conversão.
   - **Alvo:** preservar `SUMMARY`, `DESCRIPTION` e ICS brutos; extrair número, empresa/unidade, técnico, categoria e título apenas quando reconhecíveis, apresentando campos ambíguos para confirmação e aceitando `????`/`SEM OS` como ausência de número oficial.
   - **Aceite:** abrir duas vezes o mesmo `href` recupera a mesma OS; `SUMMARY` vazio, com acentos ou com mais de seis segmentos não causa perda do texto bruto nem cria campos inventados.

2. **Atendimento provisório com publicação explícita**: uma nova OS nasce como rascunho estritamente local e só se torna evento remoto após confirmação do técnico.
   - **Atual:** a nova OS possui UUID e autosave local, mas não pode ser publicada.
   - **Alvo:** oferecer os estados `Rascunho local`, `Aguardando envio`, `Publicado` e `Falha/Conflito`; “Publicar no calendário” cria imediatamente um `VEVENT` na data e horário escolhidos ou mantém uma operação confirmada na fila quando estiver offline. O número oficial continua opcional.
   - **Aceite:** salvar ou editar rascunho nunca cria evento; confirmar online cria exatamente um recurso; confirmar offline cria exatamente um item de fila que posteriormente resulta em um único recurso, mesmo após reinício ou repetição do worker.

3. **Formulário estruturado e data confiável**: a OS distingue identificação, demanda, atualizações, resultado e pendências, preservando convenções do calendário.
   - **Atual:** título, cliente, unidade e descrição são campos livres; técnico, categoria e horário não são estruturados.
   - **Alvo:** reter técnico e empresa/unidade usados recentemente; permitir data, início e fim ajustáveis; inserir data de execução obrigatória no texto e manter horário apenas em `DTSTART`/`DTEND`; preservar Unicode e escapes RFC 5545.
   - **Aceite:** campos obrigatórios vazios bloqueiam publicação, mas não autosave; divergência entre data textual e data do evento produz aviso antes da publicação e nenhuma das datas é alterada automaticamente.

4. **Atualização com contexto sem duplicação**: cada atualização é um registro local independente e a publicação renderiza uma descrição consolidada.
   - **Atual:** não existe modelo de atualização; o projeto legado prependia a descrição completa anterior e gerava repetição crescente.
   - **Alvo:** o `DESCRIPTION` de atualização contém identificação, demanda original, blocos de atualização em ordem cronológica e pendências atuais; cada atualização aparece uma única vez e todas as versões publicadas ficam no histórico local.
   - **Aceite:** duas atualizações distintas permanecem distintas mesmo com texto igual; renderizar ou reenviar o mesmo conjunto não duplica blocos; empates de data mantêm ordem estável de criação; atualização vazia não pode ser publicada.

5. **Finalização consolidada**: finalizar representa a conclusão interna do técnico e produz um texto limpo, sem apagar evidências locais.
   - **Atual:** `CONCLUIDA` altera apenas o status local e a descrição continua livre.
   - **Alvo:** a descrição final contém identificação, demanda/problema original, `Estado: Concluído`, causa, solução/resultado e pendências; a cronologia de atualizações sai da descrição final, mas permanece consultável no histórico local.
   - **Aceite:** finalizar exige causa e solução/resultado não vazios, aceita pendências explicitamente como `Nenhuma`, cria uma versão histórica e não remove demanda nem atualizações armazenadas localmente.

6. **Prévia e preservação do VEVENT**: nenhuma publicação ocorre sem prévia do conteúdo remoto resultante.
   - **Atual:** o detalhe remoto é somente leitura e não existe gerador ou prévia de ICS.
   - **Alvo:** para evento existente, modificar apenas `DESCRIPTION`, `SEQUENCE`, `DTSTAMP` e `LAST-MODIFIED`; preservar exatamente `SUMMARY`, `DTSTART`, `DTEND`, `LOCATION`, `COLOR`, `UID`, recorrência e propriedades desconhecidas, inclusive quando campos opcionais estão ausentes. Na criação provisória, gerar os campos nativos a partir do formulário e um UID estável.
   - **Aceite:** teste com fixture baseada no ICS real comprova folding/unfolding, acentos, vírgulas escapadas e `VTIMEZONE`; publicar atualização não acrescenta `LOCATION`/`COLOR` ausentes nem altera os campos preservados; abrir ou cancelar a prévia não muta estado remoto.

7. **Escrita condicional e recuperação manual mínima**: toda escrita remota é protegida por precondição e nunca sobrescreve silenciosamente uma versão mais nova.
   - **Atual:** o cliente HTTP bloqueia todos os métodos mutantes e não existe fila de publicação.
   - **Alvo:** criação usa precondição de não existência; atualização usa `If-Match` com o `ETag` da base. Em `412`, interromper o envio, conservar rascunho e base, baixar a versão remota e mostrar remoto versus prévia local; “Reaplicar minha atualização” só usa o novo `ETag` após nova revisão e confirmação.
   - **Aceite:** `412` nunca causa retry automático nem perda local; uma segunda mudança concorrente causa novo bloqueio; repetição após sucesso não cria recurso ou atualização duplicada; falha de rede mantém a operação confirmada pendente.

8. **Histórico e estados operacionais distintos**: conclusão do técnico, validação externa e retorno para correção são sinais independentes.
   - **Atual:** a cor remota é classificada, mas não está vinculada a uma OS estruturada nem a versões publicadas.
   - **Alvo:** `Estado: Concluído` representa encerramento interno; verde representa validação externa do responsável; vermelho representa `Requer atenção`; o Nexo nunca aplica verde. Bases remotas, rascunhos e versões publicadas permanecem separados e ordenados cronologicamente.
   - **Aceite:** concluída sem verde aparece como `Aguardando validação`; verde aparece como `Validada`; vermelho tem precedência visual como `Requer atenção`; sincronizações repetidas não criam versões históricas idênticas.

## Limites

**Dentro do escopo:**

- Vincular evento remoto a uma OS local estruturada sem alterar o evento ao abri-lo.
- Criar rascunho provisório local e publicá-lo explicitamente, com fila offline.
- Extrair campos confiáveis do `SUMMARY` mantendo o original e confirmando ambiguidades.
- Formulários separados para demanda, atualizações, finalização e pendências.
- Gerar e revisar prévias de atualização e finalização.
- Criar `VEVENT` provisório e atualizar `DESCRIPTION` de evento existente.
- Escrita condicional, detecção de `412` e reaplicação manual mínima sobre a versão mais recente.
- Histórico local imutável de bases, rascunhos e versões publicadas.
- Estados `Concluída`, `Aguardando validação`, `Validada` e `Requer atenção`.

**Fora do escopo:**

- Exclusão remota de eventos — não é necessária para o fluxo operacional desta fase.
- Aplicar verde ou alterar qualquer cor automaticamente — a validação pertence ao responsável externo.
- Mesclagem automática campo a campo e editor avançado de conflitos — permanecem na Fase 4.
- Notificações locais e monitoramento proativo de retorno vermelho — permanecem na Fase 4.
- Cadastro completo de ativos, nobreaks, baterias e resultados técnicos — permanecem na Fase 5.
- Deduplicação automática de eventos ou chamados semelhantes — sem identidade segura, apenas sugestões futuras são permitidas.
- Reescrita por LLM das atualizações em um resultado final — a consolidação exige texto confirmado pelo técnico.

## Restrições

- Android offline-first: todo texto digitado deve chegar ao Room antes de depender da rede.
- Uma conta e uma agenda de trabalho ativas por instalação nesta versão.
- Identidade remota por conta + calendário + `href`; `UID` auxilia correlação, mas não é chave única.
- Número oficial é opcional e mutável; UUID interno não muda.
- Horários usam o fuso do evento, inclusive `America/Sao_Paulo`; a descrição contém data, não horário.
- Texto e ICS devem preservar Unicode, folding, escapes, recorrência e propriedades desconhecidas.
- Operações confirmadas podem ser retomadas automaticamente quando a rede voltar, mas uma operação nova nunca é criada sem confirmação.
- `ETag`/`If-Match` é obrigatório; não existe caminho de força bruta ou retry cego de `412`.
- Credenciais não entram em ICS, prévias, histórico, logs ou fixtures. Segurança canônica adicional será auditada por `gsd-secure-phase`.

## Critérios de aceite

- [ ] Abrir repetidamente o mesmo evento recupera uma única OS local e preserva ICS, `SUMMARY` e `DESCRIPTION` brutos.
- [ ] Parsing tolera `????`, segmentos extras, campos vazios, Unicode, folding e valores contendo `:` sem truncar ou inventar dados.
- [ ] Rascunho local nunca publica sem a ação explícita “Publicar no calendário”.
- [ ] Publicação confirmada online ou retomada offline cria exatamente um `VEVENT` com UID estável.
- [ ] Data textual divergente de `DTSTART` gera aviso e exige decisão, sem correção automática.
- [ ] Atualizações aparecem uma única vez, em ordem cronológica estável, mantendo demanda e pendências atuais.
- [ ] Finalização exige causa e solução/resultado e gera descrição consolidada com `Estado: Concluído`.
- [ ] Histórico local conserva demanda, atualizações, bases remotas e versões publicadas mesmo após finalização.
- [ ] Prévia baseada na fixture ICS preserva campos nativos, propriedades desconhecidas, fuso, escapes e ausência de opcionais.
- [ ] Atualização existente usa `If-Match`; criação usa precondição de não existência.
- [ ] Resposta `412` preserva o rascunho, mostra remoto versus local e nunca dispara retry automático.
- [ ] Reaplicação após conflito exige nova prévia e confirmação; outro conflito volta a bloquear com os dados intactos.
- [ ] Conclusão interna sem verde aparece como `Aguardando validação`; verde como `Validada`; vermelho como `Requer atenção`.
- [ ] O aplicativo não possui caminho que aplique verde automaticamente ou exclua evento remoto nesta fase.
- [ ] Build, testes unitários, testes instrumentados relevantes e lint passam; migração Room preserva rascunhos das versões anteriores.

## Cobertura de bordas

**Cobertura:** 31/31 bordas aplicáveis resolvidas · 0 não resolvidas

| Categoria | Requisito | Status | Resolução |
|---|---|---|---|
| vazio, encoding, idempotência, concorrência | R1 | ✅ cobertas | Texto bruto aceita vazio/Unicode; vínculo composto recupera uma OS; autosave mais novo não é substituído por leitura antiga. |
| idempotência, concorrência | R2 | ✅ cobertas | UID/operação estáveis e fila única; worker repetido ou interrompido não duplica recurso. |
| vazio, encoding, idempotência, concorrência | R3 | ✅ cobertas | Vazio bloqueia publicação, não autosave; Unicode é preservado; salvamentos repetidos convergem para a revisão local mais nova. |
| adjacência, vazio, encoding, ordenação, idempotência, concorrência | R4 | ✅ cobertas | Atualizações têm UUID próprio, vazio é rejeitado, ordem possui desempate estável e renderização é determinística sem duplicação. |
| vazio, encoding, idempotência, concorrência | R5 | ✅ cobertas | Campos finais obrigatórios são validados; repetição não duplica versão; histórico local não é perdido por publicação concorrente. |
| vazio, encoding, idempotência, concorrência | R6 | ✅ cobertas | Opcionais ausentes continuam ausentes; round-trip textual é preservado; prévias repetidas/canceladas não escrevem. |
| idempotência, concorrência | R7 | ✅ cobertas | Precondições tornam retry seguro; `412` sempre bloqueia e exige nova revisão com a base mais recente. |
| adjacência, vazio, ordenação, idempotência, concorrência | R8 | ✅ cobertas | Estados possuem precedência definida; ausência de cor é válida; versões têm ordem estável e sync repetido não duplica histórico. |

## Proibições (must-NOT)

**Cobertura:** 4/4 proibições próprias resolvidas · 0 não resolvidas

| Proibição | Requisito | Status | Verificação |
|---|---|---|---|
| O Nexo NÃO DEVE publicar evento ou atualização nova sem confirmação explícita do técnico. | R2, R6 | resolvida | teste: salvar, autosave, abrir ou cancelar prévia produz zero requisições mutantes. |
| O Nexo NÃO DEVE apagar, substituir silenciosamente ou ocultar demanda, rascunho ou versão histórica para simplificar a descrição remota. | R4, R5, R8 | resolvida | teste: finalização altera a projeção remota, mas todas as entidades históricas permanecem consultáveis. |
| O Nexo NÃO DEVE tratar conclusão interna como validação externa nem aplicar verde automaticamente. | R8 | resolvida | teste: concluir gera estado aguardando validação e nenhuma mutação de `COLOR`. |
| O Nexo NÃO DEVE reenviar cegamente após `412` nem apresentar uma versão desatualizada como publicada. | R7 | resolvida | teste: `412` encerra a tentativa, preserva três versões e exige nova confirmação. |

Credenciais, injeção, armazenamento seguro e demais controles canônicos de segurança são responsabilidade adicional de `gsd-secure-phase`; não foram duplicados como proibições específicas desta fase.

## Relatório de ambiguidade

| Dimensão | Pontuação | Mínimo | Status | Observações |
|---|---:|---:|---|---|
| Clareza do objetivo | 0,95 | 0,75 | ✓ | Três fluxos definidos: atender, atualizar/finalizar e criar provisório. |
| Clareza dos limites | 0,90 | 0,70 | ✓ | Escritas permitidas e itens adiados estão explícitos. |
| Clareza das restrições | 0,88 | 0,65 | ✓ | Offline-first, ICS real, ETag e preservação definidos. |
| Critérios de aceite | 0,86 | 0,70 | ✓ | Critérios positivos, negativos, concorrentes e offline são verificáveis. |
| **Ambiguidade** | **0,09** | **≤ 0,20** | **✓** | Gate aprovado. |

## Registro da entrevista

| Rodada | Perspectiva | Pergunta resumida | Decisão bloqueada |
|---|---|---|---|
| 1 | Pesquisador | Quais fluxos e campos remotos entram? | Atender existente, atualizar/finalizar e criar provisório; existente altera descrição e preserva campos nativos. |
| 1 | Pesquisador | Atualização substitui ou acumula descrição? | Atualização preserva contexto; finalização é consolidada e histórico integral fica local. |
| 2 | Simplificador | Quando o provisório chega ao calendário? | Rascunho é local; confirmação publica imediatamente ou enfileira se offline. |
| 2 | Simplificador | Qual o mínimo de conflito? | Escrita condicional obrigatória; recursos avançados permanecem fora. |
| 3 | Guardião de limites | Como finalizar e recuperar conflito? | Final sem cronologia remota; reaplicação manual mínima após revisão lado a lado. |
| 3 | Guardião de limites | Conclusão e verde são equivalentes? | Não: conclusão é interna; verde é validação externa; vermelho requer atenção. |

---

*Fase: 03-ordens-de-servico-publicacao-controlada*
*Spec criada: 27/08/2026*
*Próximo passo: `$gsd-discuss-phase 3` — decisões de implementação para construir o que foi especificado acima.*
