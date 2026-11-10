# PediuPatrão

Sistema de pizzaria em Java 21, Spring Boot, Spring Security, Thymeleaf e MongoDB.

## Apresentação

A apresentação do projeto está disponível na pasta [presentation](presentation/). Os documentos solicitados na atividade estão na pasta [docs](docs/).

## Executar

É necessário ter Java 21 e uma instância MongoDB com replica set (ou MongoDB Atlas) em execução. As operações e a auditoria usam transações conjuntas; MongoDB standalone não suporta essas escritas. O projeto inclui Maven Wrapper.

Preencha o arquivo `.env` na raiz com sua URI em `MONGODB_URI`, o banco em `MONGODB_DATABASE` e a senha inicial em `ADMIN_PASSWORD`, sem aspas. O Spring Boot lê esse arquivo automaticamente e ele está ignorado pelo Git. Em outro checkout, copie `.env.example` para `.env`.

Execute na raiz do projeto, no PowerShell:

```powershell
.\mvnw.cmd spring-boot:run
```

Abra http://localhost:8080/login. Em um banco sem usuários, a aplicação cria o usuário `admin` com perfil ADMIN e a senha definida em `ADMIN_PASSWORD`. A senha não é redefinida nas próximas execuções. Entre como ADMIN e cadastre os usuários GERENTE e ATENDENTE em **Configurações → Usuários**.

A conexão usa o banco definido em `MONGODB_DATABASE` (por padrão, `pizzaria`). Para usar outra instância, informe sua URI em `MONGODB_URI`.

Para reutilizar um banco antigo, atribua o perfil ADMIN ao administrador existente diretamente no MongoDB antes de entrar. Usuários antigos com perfil USER ou sem perfil são autenticados como ATENDENTE; o ADMIN pode atualizar seus perfis pelo sistema. Pedidos antigos continuam disponíveis para consulta; preços, horários e responsáveis que não foram registrados não são inventados. A aplicação inicializa o campo de versão dos pedidos antigos para permitir o controle de concorrência.

## Requisitos implementados

| Perfil | Permissões |
| --- | --- |
| ADMIN | Administrar usuários e perfis, consultar pedidos e auditoria. |
| GERENTE | Consultar clientes, registrar pedidos, alterar status, aplicar descontos, cancelar pedidos e consultar auditoria e dashboard. |
| ATENDENTE | Cadastrar, atualizar e excluir clientes, registrar pedidos e consultar seu andamento. |

A gestão de produtos existente foi mantida para os usuários autenticados. As permissões são verificadas no backend pelo Spring Security, incluindo chamadas diretas às APIs. ADMIN também recebe acesso negado ao tentar cancelar ou aplicar desconto.

- **RF01:** três perfis, autenticação com o perfil persistido, gestão de usuários restrita ao ADMIN e senhas criptografadas sem exposição nas respostas ou formulários.
- **RF02:** itens com quantidades e preços registrados, desconto percentual, valor total, responsável e entrada. Na criação, o servidor consulta os preços dos produtos e calcula o total, ignorando valores comerciais, status e responsáveis enviados pelo cliente.
- **RF03:** fluxo RECEBIDO → EM_PREPARACAO → PRONTO → SAIU_PARA_ENTREGA ou RETIRADO → FINALIZADO. Saída registra horário e responsável. Transições inválidas são rejeitadas.
- **RF04:** desconto entre 0% e 20% e cancelamento com justificativa, exclusivos do GERENTE e somente antes da saída. Pedidos cancelados preservam o histórico e não permitem novas alterações.
- **RF05:** auditoria persistida na coleção `auditoria`, com usuário, perfil, data/hora, operação, entidade, identificador e campos anteriores/novos. Inclui pedidos, status, descontos, cancelamentos e dados de clientes. A consulta é exclusiva de ADMIN e GERENTE, sem rotas para editar ou excluir eventos. Os eventos usam somente campos necessários e não incluem credenciais.

As regras ficam nos serviços; os controladores e formulários utilizam essas mesmas regras. Formulários POST incluem CSRF automaticamente pelo Thymeleaf. Chamadas de API que alteram dados também exigem sessão autenticada e token CSRF. Erros de regra retornam HTTP 400; operações sem permissão retornam 403. Pedidos possuem controle de versão para impedir atualizações concorrentes perdidas.

## APIs principais

| Método | Rota | Acesso |
| --- | --- | --- |
| GET | /api/pedidos | Três perfis |
| POST | /api/pedidos | ATENDENTE e GERENTE |
| GET | /api/dashboard/resumo?inicio=2026-11-01&fim=2026-11-07 | ADMIN e GERENTE |
| PUT | /api/pedidos/{id}/status?status=PRONTO | GERENTE |
| PUT | /api/pedidos/{id}/desconto?percentual=20 | GERENTE |
| PUT | /api/pedidos/{id}/cancelamento?justificativa=Motivo | GERENTE |
| GET | /api/clientes | GERENTE e ATENDENTE |
| POST, PUT, DELETE | /api/clientes e /api/clientes/{id} | ATENDENTE |
| GET, POST, PUT, DELETE | /api/usuarios e /api/usuarios/{id} | ADMIN |
| GET | /api/auditoria | ADMIN e GERENTE |

Exemplo de corpo para registrar um pedido:

```json
{
  "clienteId": "id-do-cliente",
  "itens": [
    {"produtoId": "id-do-produto", "quantidade": 2}
  ]
}
```

## Testes

O dashboard está em **/dashboard**, também disponível no menu para ADMIN e GERENTE. Mantém as cores atuais e usa CoreUI com gráficos, indicadores, ranking, filtros e atualização manual pela API.

A implementação está na branch `feature/dashboard-administrativo`. No fluxo Gitflow, revisar a feature em PR para `develop` e promover a versão validada para `main`; nenhum merge ou envio remoto foi realizado automaticamente.

A administração protege o próprio perfil/login, a exclusão do próprio usuário e a remoção/rebaixamento do último ADMIN. Pedidos e clientes são gravados na mesma transação da auditoria. Cancelamentos e descontos continuam exclusivos do GERENTE.

```powershell
.\mvnw.cmd test
```

Os testes verificam cálculo no servidor, transições e saída, limites de desconto, cancelamento, auditoria de telefone e endereço, perfis de autenticação, proteção contra atribuição indevida de perfis, respostas sem senhas, CSRF e bloqueio das APIs. Também verificam os formulários Thymeleaf. Usam repositórios simulados, sem acessar MongoDB externo.
