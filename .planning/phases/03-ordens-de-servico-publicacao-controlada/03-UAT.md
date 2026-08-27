# Checklist de Validação UAT Nextcloud (Fase 3)

Este roteiro deve ser executado exclusivamente em ambiente de teste ou horário controlado (ex: sábado/domingo), utilizando uma senha de aplicativo temporária configurada apenas no dispositivo. **Nenhuma senha, URL real, conta ou corpo de arquivo ICS deve ser colado no repositório ou chat.**

---

## 🔒 Pré-requisitos de Segurança
- [ ] Gerada senha de aplicativo temporária no Nextcloud.
- [ ] Aplicativo Nexo conectado na agenda de trabalho controlada.
- [ ] Horário ou slot de testes sem risco de interferir em ordens de serviço reais.

---

## 🧪 Casos de Teste Controlados

### 1. Criação Provisória com Publicação Controlada (`[TESTE NEXO]`)
- **Procedimento:**
  1. No Nexo, crie uma nova OS provisória com o título `[TESTE NEXO] - Diagnóstico`.
  2. Preencha Cliente, Unidade e Demanda.
  3. Prossiga para a tela de Conferência (Prévia de Publicação).
  4. Confirme a publicação para o Nextcloud.
- **Resultado Esperado:** Exatamente 1 evento criado no Nextcloud com a descrição formatada e resumo preservado.
- **Status:** `[ ] PENDENTE / [ ] PASS / [ ] FAIL`

### 2. Cancelamento na Tela de Prévia (Zero Mutação Remota)
- **Procedimento:**
  1. Inicie a edição de uma nova OS ou evento.
  2. Na tela de Prévia de Publicação, clique em "Voltar" ou "Cancelar".
- **Resultado Esperado:** Nenhum evento ou modificação é transmitido ao Nextcloud. Rascunho local permanece intacto no Nexo.
- **Status:** `[ ] PENDENTE / [ ] PASS / [ ] FAIL`

### 3. Atualização Condicional Lossless (`If-Match`)
- **Procedimento:**
  1. Abra o evento `[TESTE NEXO]` criado no Passo 1 no Nexo.
  2. Adicione uma nova nota de campo / atualização.
  3. Avance para a prévia e confirme o envio.
- **Resultado Esperado:** Apenas o campo `DESCRIPTION` e metadados (`SEQUENCE`, `LAST-MODIFIED`, `DTSTAMP`) são atualizados no servidor. `SUMMARY`, `DTSTART`, `DTEND`, `LOCATION`, `COLOR` e propriedades nativas permanecem inalterados.
- **Status:** `[ ] PENDENTE / [ ] PASS / [ ] FAIL`

### 4. Simulação de Conflito Concorrente (HTTP 412)
- **Procedimento:**
  1. No Nexo, prepare uma atualização no editor até a tela de prévia (sem confirmar ainda).
  2. Pela interface web do Nextcloud, edite a descrição do mesmo evento e salve.
  3. No Nexo, clique em confirmar a publicação.
- **Resultado Esperado:** O Nexo detecta o HTTP 412, marca o estado como `CONFLITO` na Central de Sincronizações, não faz retries cegos, preserva o rascunho local e permite resolver campo a campo na tela de Revisão de Conflitos.
- **Status:** `[ ] PENDENTE / [ ] PASS / [ ] FAIL`

### 5. Finalização de Atendimento
- **Procedimento:**
  1. Conclua o atendimento preenchendo Causa, Solução e Itens.
  2. Confirme o envio da conclusão.
- **Resultado Esperado:** No Nextcloud, o corpo do evento recebe a tag `Estado: Concluído`. No Nexo, o cartão exibe `Aguardando validação externa` (ou `Validado externamente` se a cor for alterada para verde no servidor).
- **Status:** `[ ] PENDENTE / [ ] PASS / [ ] FAIL`

---

## 🧹 Pós-Teste e Limpeza
- [ ] Revogar imediatamente a senha de aplicativo temporária nas configurações de segurança do Nextcloud.
- [ ] Confirmar que o Nexo interrompe as comunicações e sinaliza necessidade de reconexão de forma segura.
