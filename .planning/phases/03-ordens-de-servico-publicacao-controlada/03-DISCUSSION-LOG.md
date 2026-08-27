# Phase 3: Ordens de Serviço e publicação controlada - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-08-27
**Phase:** 03-ordens-de-servico-publicacao-controlada
**Areas discussed:** início do atendimento e vínculo, editor estruturado, publicação e fila offline, conflitos e estados operacionais

---

## Início do atendimento e vínculo

| Option | Description | Selected |
|--------|-------------|----------|
| Criar ao iniciar atendimento | Evento permanece somente leitura até ação explícita; cria uma única cópia local vinculada | ✓ |
| Criar ao abrir | Visualizar o evento já cria uma OS local | |
| Manter cartões separados | Evento e OS aparecem separadamente | |

**User's choice:** abrir evento remoto somente leitura e criar/continuar a mesma cópia ao tocar no botão inferior; menu de três pontos pode repetir a ação.
**Notes:** `SUMMARY` e `DESCRIPTION` alimentam o editor. Se o remoto mudou desde o snapshot, comparar somente campos divergentes e perguntar `Manter meu valor` ou `Usar valor do calendário`, sem sobrescrever texto local.

| Option | Description | Selected |
|--------|-------------|----------|
| Editor preenchido diretamente | Extrair campos reconhecíveis e destacar ambiguidades | ✓ |
| Tela de revisão intermediária | Confirmar tudo antes de criar cópia | |
| Apenas texto bruto | Preenchimento manual dos campos | |

**User's choice:** editor preenchido, com número da OS extraído e restante conferível/editável.
**Notes:** descrição remota não formatada entra inteira no campo de origem do preset; extração é determinística.

---

## Editor estruturado

| Option | Description | Selected |
|--------|-------------|----------|
| Tela única com seções recolhíveis | Um fluxo, componentes internos separados | ✓ |
| Etapas guiadas | Navegação sequencial entre páginas | |
| Abas internas | Um grupo de campos por aba | |

**User's choice:** tela única com seções recolhíveis.
**Notes:** tela única não significa arquivo monolítico; usar componentes testáveis e estado compartilhado.

| Option | Description | Selected |
|--------|-------------|----------|
| Preset explícito | Técnico escolhe a semântica dos campos | ✓ |
| Rótulos individuais | Escolha manual campo a campo | |
| Detecção automática | App aplica preset a partir do SUMMARY | |

**User's choice:** testar preset explícito e ajustar depois se a experiência não funcionar.
**Notes:** iniciar com `Diagnóstico/correção` e `Serviço solicitado`.

| Option | Description | Selected |
|--------|-------------|----------|
| Formulário genérico repetível | Itens/equipamentos básicos e formatação | ✓ |
| Somente estrutura de dados | Texto livre nesta fase | |
| Formulários especializados | Bateria, nobreak, rede etc. agora | |

**User's choice:** somente formulário básico genérico nesta fase.
**Notes:** especializações futuras reutilizam a mesma estrutura.

| Option | Description | Selected |
|--------|-------------|----------|
| Prévia somente leitura | Corrigir nos campos estruturados | ✓ |
| Texto final editável | Permitir divergência entre estrutura e publicação | |
| Sobrescrita avançada | Escape explícito para edição manual | |

**User's choice:** prévia somente leitura.
**Notes:** a projeção publicada é sempre regenerada dos dados salvos.

---

## Publicação e fila offline

| Option | Description | Selected |
|--------|-------------|----------|
| Barra fixa inferior | Ação remota contextual visível | ✓ |
| Menu no topo | Ações somente no overflow | |
| Seção Publicação | Botões no fim do formulário | |

**User's choice:** barra fixa inferior.
**Notes:** autosave elimina botões duplicados; `Publicar`, `Enviar atualização` e `Finalizar` usam o mesmo pipeline com intenções e projeções diferentes.

| Option | Description | Selected |
|--------|-------------|----------|
| Prévia completa sempre | Conferir texto, agenda, data e horário antes de enviar | ✓ |
| Confirmação compacta | Diálogo resumido | |
| Envio imediato | Um toque | |

**User's choice:** prévia completa antes de cada envio.
**Notes:** cancelar a prévia não cria operação nem mutação.

| Option | Description | Selected |
|--------|-------------|----------|
| Fila imutável | Guarda exatamente a versão confirmada | ✓ |
| Fila acompanha rascunho | Edições alteram o envio pendente | |
| Bloquear edição | OS somente leitura até enviar/cancelar | |

**User's choice:** fila imutável.
**Notes:** antes do envio, revalidar mudança/conflito; não publicar se a base remota mudou.

| Option | Description | Selected |
|--------|-------------|----------|
| Estado no cartão + central | Indicador local e lista consolidada | ✓ |
| Somente cartão | Sem visão geral | |
| Somente central | Cartão com indicador mínimo | |

**User's choice:** estado no cartão e tela específica de Central de sincronizações.

---

## Conflitos e estados operacionais

| Option | Description | Selected |
|--------|-------------|----------|
| Comparação por campo | Versão remota e local por campo, texto completo expansível | ✓ |
| Textos integrais | Comparar descrições inteiras | |
| Reaplicar bloco | Acrescentar atualização à nova base como atalho | |

**User's choice:** versão remota e versão local campo a campo.
**Notes:** descrições recebidas sem padrão entram como um único campo de origem; comparação é semântica e não linha a linha.

| Option | Description | Selected |
|--------|-------------|----------|
| Aguardando validação externa | Conclusão interna separada do verde | ✓ |
| Concluída normalmente | Verde apenas informativo | |
| Concluída com alerta | Ausência de verde como pendência crítica | |

**User's choice:** `Aguardando validação` ou `Aguardando fechamento do sistema`; adotado `Aguardando validação externa` com explicação.

| Option | Description | Selected |
|--------|-------------|----------|
| Requer atenção com reabertura explícita | Preserva conclusão e adiciona nova etapa | ✓ |
| Reabrir automaticamente | Volta direto ao editor | |
| Apenas alerta | Mantém concluída sem ação operacional | |

**User's choice:** `Requer atenção` com `Reabrir atendimento` explícito.

| Option | Description | Selected |
|--------|-------------|----------|
| Mapeamento configurável de cores | Defaults observados e associação ajustável | ✓ |
| Lista fixa | Somente cores codificadas | |
| Detecção por tonalidade | Qualquer verde/vermelho ganha significado | |

**User's choice:** registrar irregularidade das cores e não inferir significado do roxo sem evidência.
**Notes:** verdes observados variam; azuis são neutros; ICS convidado pode nem carregar `COLOR`.

| Option | Description | Selected |
|--------|-------------|----------|
| Verificar permissão | Editar localmente e publicar somente se autorizado | ✓ |
| Bloquear organizador externo | Todo convite somente leitura | |
| Tentar sem preflight | Descobrir apenas pelo erro | |

**User's choice:** verificar permissão e preservar o rascunho quando o servidor negar.
**Notes:** evento compartilhado real contém `ORGANIZER` e `ATTENDEE`; essas propriedades e demais campos desconhecidos devem sobreviver ao round-trip.

## the agent's Discretion

- Microcopy e composição interna dos componentes, dentro dos contratos registrados no CONTEXT.
- Retenção visual do histórico recente da Central de sincronizações.

## Deferred Ideas

- Mesclagem automática avançada e notificações para a Fase 4.
- Formulários técnicos especializados para a Fase 5.
- Classificar roxo como `Autosserviço` somente após evidência futura.
