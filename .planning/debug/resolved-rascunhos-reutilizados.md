# Rascunhos reutilizados e editor desatualizado — resolvido

## Sintomas reproduzidos

- tocar em Nova OS reabria o mesmo formulario;
- o card refletia o Room, mas Detalhes podia exibir uma copia antiga;
- nao havia exclusao explicita de rascunhos locais;
- a tela Detalhes mostrava dois comandos equivalentes para salvar.

## Causa raiz

O `NavDisplay` nao possuia os decoradores de estado e `ViewModelStore` por `NavEntry`.
Assim, `hiltViewModel()` usava um dono mais amplo e mantinha `SavedStateHandle` e estado editavel
depois que a entrada havia saído da pilha.

## Correcao

- adicionados `rememberSaveableStateHolderNavEntryDecorator()` e
  `rememberViewModelStoreNavEntryDecorator()`;
- `Route.NovaOS` passou a ter uma identidade unica por abertura;
- removida a chave ampla/reutilizavel do ViewModel de Detalhes;
- Agenda ganhou exclusao confirmada para rascunhos locais nao concluidos;
- removido o icone de disquete duplicado, mantendo o botao inferior;
- testes unitarios e instrumentados ampliados.

## Verificacao

- build, testes unitarios, APK de testes e lint aprovados;
- 6 testes instrumentados aprovados em Samsung SM-A556E;
- o APK corrigido foi reinstalado após o executor de testes remover o pacote de desenvolvimento.
