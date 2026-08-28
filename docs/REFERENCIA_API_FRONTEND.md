# API IGRP Process Management — Referência para o Client SDK

> **Objetivo deste documento.** Descrever o contrato HTTP completo da API de *Process Management* da plataforma IGRP, para que a equipa de frontend possa **atualizar o client SDK** com tipos, caminhos e contratos exatos. Para cada endpoint indica-se o método, o caminho completo, os parâmetros de caminho/query, o corpo do pedido e a forma exata da resposta (incluindo o nome de cada campo tal como serializa em JSON).

| | |
|---|---|
| **Serviço** | `igrp-platform-process-management` |
| **Estilo** | REST sobre HTTP, JSON (`application/json`) |
| **Base path da aplicação** | `/` (sem *context-path* — os caminhos abaixo são relativos à raiz do serviço) |
| **Autenticação** | OAuth2 *Bearer Token* (JWT) |
| **Formato de erros** | RFC 7807 — *Problem Details* (`application/problem+json`) |
| **Total de endpoints** | 55, em 5 grupos de recursos |
| **Data do documento** | 2026-06-30 |

> ⚠️ **Nota sobre a URL base.** O serviço não define `context-path`, pelo que os caminhos começam na raiz (ex.: `GET /areas`). Em ambientes reais o serviço costuma estar atrás de um *gateway*/*ingress* que adiciona um prefixo (ex.: `/process-management`). **A `baseURL` do SDK deve ser configurável** e apontar para a origem efetiva exposta a cada ambiente.

---

## Índice

1. [Visão geral](#1-visão-geral)
2. [Autenticação e autorização](#2-autenticação-e-autorização)
3. [Cabeçalhos e CORS](#3-cabeçalhos-e-cors)
4. [Paginação](#4-paginação)
5. [Formato de erros (RFC 7807)](#5-formato-de-erros-rfc-7807)
6. [Tipos de dados e mapeamento para TypeScript](#6-tipos-de-dados-e-mapeamento-para-typescript)
7. [Enumerações](#7-enumerações)
8. [Índice rápido de endpoints](#8-índice-rápido-de-endpoints)
9. [Referência de endpoints](#9-referência-de-endpoints)
   - [Áreas e Definições de Processo (Areas)](#áreas-e-definições-de-processo-areas)
   - [Definições de Processo (Process Definitions)](#definições-de-processo-process-definitions)
   - [Instâncias de Processo (Process Instances)](#instâncias-de-processo-process-instances)
   - [Tarefas (Task Instances)](#tarefas-task-instances)
   - [Atividades (Activities)](#atividades-activities)
10. [Orientações para atualizar o Client SDK](#10-orientações-para-atualizar-o-client-sdk)

---

## 1. Visão geral

A API expõe **5 grupos de recursos**, organizados por domínio:

| Grupo | Rota base | Responsabilidade |
|---|---|---|
| **Áreas** | `/areas` | Áreas organizacionais e as definições de processo associadas a cada área. |
| **Definições de Processo** | `/process-definitions` | Deploy de processos BPMN, artefactos, sequências, prioridades, exportação/importação e arquivo. |
| **Instâncias de Processo** | `/process-instances` | Arranque, consulta, pesquisa, estatísticas e eventos de instâncias de processo em execução. |
| **Tarefas** | `/tasks-instances` | Ciclo de vida de tarefas (claim/unclaim/assign/complete/save), variáveis, regras de atribuição e estatísticas. |
| **Atividades** | `/activities` | Consulta de atividades e progresso dentro de uma instância de processo. |

> ⚠️ **Atenção a um detalhe de nomenclatura:** a rota base das tarefas é **`/tasks-instances`** (com **s** em `tasks`), enquanto a das instâncias é `/process-instances`. Confirmar este caminho exato no SDK — é uma fonte comum de erro 404.

Convenções gerais a ter em conta ao gerar o SDK:

- **Identificadores** são `UUID` em formato *string* na esmagadora maioria dos recursos.
- **Datas/horas** são *strings* ISO-8601 (sem *timezone* explícito em vários DTOs — ver §6).
- Vários endpoints de **listagem usam `POST .../search`** (e `POST .../me`) com um corpo de filtro **e** parâmetros de query de paginação em simultâneo (ver §4). Não são `GET` simples.
- As respostas de listagem vêm sempre **embrulhadas num envelope paginado** (`content` + metadados de página).

---

## 2. Autenticação e autorização

A API é um **OAuth2 Resource Server**. Todos os pedidos (exceto os públicos abaixo) exigem um **JWT** válido no cabeçalho `Authorization`:

```http
Authorization: Bearer <access_token>
```

- **Esquema:** `bearer`, formato `JWT` (no OpenAPI o esquema chama-se `bearerAuth`).
- **Sessão IRN (desde a release 24.4):** com o adapter de autorização IRN ativo (produção), além do
  Bearer o cliente tem de enviar o **cookie de sessão IRN** em todos os pedidos de negócio:
  `Cookie: session_id=<sessão>`. Sem o cookie, a resolução de permissões falha e o pedido leva
  **403** mesmo com token válido. A API em si continua *stateless* (não cria sessões próprias).
- **Chamadores de máquina (release 24.6):** sistemas externos sem sessão IRN autenticam com uma
  **API key M2M** — `Authorization: Bearer igrpm2m_…`, sem Keycloak nem cookie. Emissão e gestão são
  de super-admin (`/m2m-keys`); detalhe em `docs/SPEC_M2M_AUTHORIZATION.md`. Irrelevante para o SDK
  de frontend de utilizador.
- **Claim de principal:** configurável no servidor (por omissão `sub`; nos deployments IRN, `email`).
- **Autorizações:** o servidor enriquece o token com *roles*/grupos (prefixos `ROLE_` e `GROUP_`) e permissões obtidas do serviço de IAM. Um *super admin* recebe também os papéis Activiti `ROLE_ACTIVITI_ADMIN`/`ROLE_ACTIVITI_USER`. **Para o frontend, o relevante é: sem token válido → `401`; token válido mas sem permissão para a operação → `403`.**

**Endpoints públicos (sem token):**

- `GET /actuator/health`, `GET /actuator/health/**`
- Documentação OpenAPI/Swagger: `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html`, `/swagger-resources/**`, `/webjars/**`

> 💡 A especificação OpenAPI viva está em **`/v3/api-docs`** e o Swagger UI em **`/swagger-ui.html`**. Podem ser usados para **geração automática de tipos** no SDK (ver §10).

**Resposta a falta de autenticação (`401`):** o servidor devolve `401 Unauthorized` e o cabeçalho `WWW-Authenticate: Basic realm="Restricted Content"`.

---

## 3. Cabeçalhos e CORS

| Cabeçalho | Valor | Notas |
|---|---|---|
| `Authorization` | `Bearer <jwt>` | Obrigatório em todos os endpoints não públicos. |
| `Cookie` | `session_id=<sessão IRN>` | Obrigatório nos pedidos de negócio com o adapter IRN ativo (ver §2). |
| `Content-Type` | `application/json` | Em pedidos com corpo (`POST`/`PUT`). |
| `Accept` | `application/json` | Recomendado. Respostas de erro usam `application/problem+json`. |

**CORS (desde o endurecimento pós-24.4):** as origens permitidas vêm de `IGRP_CORS_ALLOWED_ORIGINS`
(lista por vírgulas), com `allowCredentials: true` e os métodos `GET, POST, PUT, PATCH, DELETE, HEAD,
OPTIONS`. **Vazio = nenhum acesso cross-origin** — o wildcard foi eliminado. Um frontend noutro domínio
tem de estar listado, senão as chamadas falham silenciosamente no browser.

---

## 4. Paginação

Os endpoints de listagem aceitam dois parâmetros de query:

| Parâmetro | Tipo | Por omissão | Descrição |
|---|---|---|---|
| `page` | `number` (inteiro) | `0` em alguns endpoints; nos restantes não há valor por omissão | Índice da página, começando em **0**. |
| `size` | `number` (inteiro) | `20` em alguns endpoints | Número de elementos por página. |

> Nem todos os endpoints definem valores por omissão (alguns têm `page`/`size` simplesmente opcionais). O SDK deve **enviar sempre `page` e `size` explicitamente** para um comportamento previsível.

Todas as respostas paginadas seguem o **mesmo envelope** (`...ListPageDTO`): um array `content` com os itens, mais os metadados de página:

```jsonc
{
  "content": [ /* itens do tipo do recurso */ ],
  "pageNumber": 0,        // página atual (0-based)
  "pageSize": 20,         // dimensão pedida
  "totalElements": 137,   // total de registos (number / long)
  "totalPages": 7,        // total de páginas
  "last": false,          // é a última página?
  "first": true           // é a primeira página?
}
```

**Tipo TypeScript sugerido para o envelope:**

```ts
export interface Page<T> {
  content: T[];
  pageNumber: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
  first: boolean;
}
```

> ⚠️ Note-se que os campos de página são `pageNumber`/`pageSize` (e não `page`/`size`, que são os nomes dos **parâmetros de entrada**).

---

## 5. Formato de erros (RFC 7807)

Os erros são devolvidos como **Problem Details** (RFC 7807), com `Content-Type: application/problem+json`. Estrutura base:

```jsonc
{
  "type": "about:blank",
  "title": "Mensagem de erro",
  "status": 400,
  "detail": "Descrição adicional (opcional)",
  "instance": "/areas"
}
```

Casos especiais úteis para o frontend:

**Erros de validação (`400`)** — falha de `@NotNull`/`@NotBlank`/`@Size`/etc. O corpo inclui uma propriedade extra `errors` (mapa `campo → mensagem`):

```jsonc
{
  "title": "Validation Errors",      // ou "Constraint Violation Errors"
  "status": 400,
  "errors": {
    "name": "must not be blank",
    "code": "must not be blank"
  }
}
```

**Valor de enum inválido (`400`)** — ao enviar um valor fora dos permitidos para um campo enum:

```jsonc
{
  "title": "Invalid value for enum type: Status",
  "status": 400,
  "CurrentValue": "FOO",
  "AllowedValues": ["ACTIVE", "INACTIVE"]
}
```

**JSON malformado (`400`):** `title: "Malformed JSON request"` com `detail`.
**Violação de integridade / chave estrangeira (`400`):** `title: "Foreign Key Constraint Violation"` / `"Data Integrity Violation"` com `detail`.

**Tabela de estados HTTP:**

| Código | Quando ocorre |
|---|---|
| `200 OK` | Leitura/atualização bem-sucedida. |
| `201 Created` | Criação bem-sucedida (ex.: `POST /areas`). |
| `204 No Content` | Eliminação bem-sucedida (corpo vazio). |
| `400 Bad Request` | Validação, enum inválido, JSON malformado, violação de integridade, argumento ilegal. |
| `401 Unauthorized` | Token em falta ou inválido. |
| `403 Forbidden` | Autenticado mas sem permissão. |
| `404 Not Found` | Recurso inexistente ou caminho incorreto. |
| `500 Internal Server Error` | Erro do motor de processos (Activiti), *deployment*, ou erro inesperado. |

> Vários endpoints devolvem `String` como tipo de retorno em operações de comando (ex.: `assign`, `archive`, `delete`). Tratar a resposta como texto/`void` — não assumir JSON estruturado nesses casos. As eliminações (`DELETE`) respondem `204` sem corpo.

---

## 6. Tipos de dados e mapeamento para TypeScript

| Tipo Java (servidor) | Tipo no JSON | Tipo TypeScript sugerido |
|---|---|---|
| `String` | string | `string` |
| `UUID` | string (formato UUID) | `string` |
| `Integer` / `int` / `Long` / `long` | número | `number` |
| `BigDecimal` / `Double` | número | `number` |
| `Boolean` / `boolean` | booleano | `boolean` |
| `Instant` / `LocalDateTime` / `OffsetDateTime` | string ISO-8601 | `string` |
| `List<T>` | array | `T[]` |
| `Map<String,Object>` | objeto | `Record<string, unknown>` |
| `enum` | string (nome da constante) | *union* de literais (ver §7) |
| `Object` (variáveis dinâmicas) | qualquer | `unknown` |

> **Datas:** vários DTOs usam tipos sem *timezone* explícito; tratar como `string` ISO-8601 no SDK e converter para `Date` apenas na camada de apresentação, com cuidado quanto a *timezones*.
> **Variáveis de processo/tarefa:** os valores de variáveis são dinâmicos (`Object`/`Map`); modelar como `unknown` e refinar por `type` (ver enum `VariableType`).

---

## 7. Enumerações

Os valores enviados/recebidos no JSON são os **nomes das constantes** (em maiúsculas). A coluna "Rótulo" é apenas a descrição legível em português usada internamente.

**`Status`** — estado de áreas e definições de processo:

| Valor | Rótulo |
|---|---|
| `ACTIVE` | Ativo |
| `INACTIVE` | Inactive |

**`ProcessInstanceStatus`** — estado de instâncias de processo:

| Valor | Rótulo |
|---|---|
| `RUNNING` | Em Execução |
| `CREATED` | Criado |
| `SUSPENDED` | Suspenso |
| `CANCELED` | Cancelado |
| `COMPLETED` | Completado |

**`TaskInstanceStatus`** — estado de tarefas:

| Valor | Rótulo |
|---|---|
| `CREATED` | Criado |
| `ASSIGNED` | Atribuído |
| `SUSPENDED` | Suspenso |
| `COMPLETED` | Completo |
| `CANCELED` | Cancelado |

**`TaskEventType`** — tipo de evento de tarefa:

| Valor | Rótulo |
|---|---|
| `CREATE` | Criar |
| `CLAIM` | Assumir |
| `ASSIGN` | Atribuir |
| `UNCLAIM` | Libertar |
| `COMPLETE` | Terminar |

**`TaskAssignmentMode`** — modo de atribuição (regras de atribuição de tarefas):

| Valor |
|---|
| `ALWAYS` |
| `ONE_TIME` |

**`VariableType`** — tipo de uma variável de processo/tarefa:

| Valor |
|---|
| `STRING` |
| `INTEGER` |
| `LONG` |
| `DOUBLE` |
| `BOOLEAN` |
| `JSON` |

**`VariableTag`** — categoria de variável:

| Valor | Código interno |
|---|---|
| `FORMS` | `forms` |
| `VARIABLES` | `variables` |

**`VaribalesOperator`** — operador usado nos filtros de pesquisa (`VariablesFilterDTO`):

| Valor | Significado |
|---|---|
| `EQUALS` | igual |
| `EQUALS_IGNORE_CASE` | igual (ignora maiúsculas) |
| `NOT_EQUALS` | diferente |
| `NOT_EQUALS_IGNORE_CASE` | diferente (ignora maiúsculas) |
| `GREATER_THAN` | maior que |
| `GREATER_THAN_OR_EQUAL` | maior ou igual |
| `LESS_THAN` | menor que |
| `LESS_THAN_OR_EQUAL` | menor ou igual |
| `LIKE` | contém |
| `LIKE_IGNORE_CASE` | contém (ignora maiúsculas) |

**Sugestão TypeScript:**

```ts
export type Status = 'ACTIVE' | 'INACTIVE';
export type ProcessInstanceStatus = 'RUNNING' | 'CREATED' | 'SUSPENDED' | 'CANCELED' | 'COMPLETED';
export type TaskInstanceStatus = 'CREATED' | 'ASSIGNED' | 'SUSPENDED' | 'COMPLETED' | 'CANCELED';
export type TaskEventType = 'CREATE' | 'CLAIM' | 'ASSIGN' | 'UNCLAIM' | 'COMPLETE';
export type TaskAssignmentMode = 'ALWAYS' | 'ONE_TIME';
export type VariableType = 'STRING' | 'INTEGER' | 'LONG' | 'DOUBLE' | 'BOOLEAN' | 'JSON';
export type VariableTag = 'FORMS' | 'VARIABLES';
export type VariablesOperator =
  | 'EQUALS' | 'EQUALS_IGNORE_CASE' | 'NOT_EQUALS' | 'NOT_EQUALS_IGNORE_CASE'
  | 'GREATER_THAN' | 'GREATER_THAN_OR_EQUAL' | 'LESS_THAN' | 'LESS_THAN_OR_EQUAL'
  | 'LIKE' | 'LIKE_IGNORE_CASE';
```

---

## 8. Índice rápido de endpoints

55 endpoints. A descrição detalhada de cada um (parâmetros, corpo e resposta) está em [§9](#9-referência-de-endpoints).

### Áreas — `/areas`

| Método | Caminho | Descrição |
|---|---|---|
| `GET` | `/areas` | Listar áreas (filtros + paginação). |
| `POST` | `/areas` | Criar área. |
| `GET` | `/areas/{id}` | Obter área por ID. |
| `PUT` | `/areas/{id}` | Atualizar área. |
| `DELETE` | `/areas/{id}` | Eliminar área. |
| `GET` | `/areas/{areaId}/process-definitions` | Listar definições de processo da área. |
| `POST` | `/areas/{areaId}/process-definitions` | Associar/criar definição de processo na área. |
| `DELETE` | `/areas/{areaId}/process-definitions/{processDefinitionId}` | Remover definição de processo da área. |
| `GET` | `/areas/status` | Listar estados possíveis de área. |

### Definições de Processo — `/process-definitions`

| Método | Caminho | Descrição |
|---|---|---|
| `POST` | `/process-definitions/deploy` | Fazer *deploy* de um processo (BPMN). |
| `GET` | `/process-definitions` | Listar *deployments* (filtros + paginação). |
| `PUT` | `/process-definitions/{id}/artifacts/{taskKey}` | Configurar artefacto de uma tarefa. |
| `DELETE` | `/process-definitions/artifacts/{id}` | Eliminar artefacto. |
| `GET` | `/process-definitions/{id}/artifacts` | Listar artefactos da definição. |
| `GET` | `/process-definitions/{id}/deployed-artifacts` | Listar artefactos publicados. |
| `GET` | `/process-definitions/{processDefinitionKey}/sequence` | Obter a sequência do processo. |
| `POST` | `/process-definitions/{processDefinitionKey}/sequence` | Criar/definir a sequência do processo. |
| `POST` | `/process-definitions/{id}/assign` | Atribuir definição de processo. |
| `GET` | `/process-definitions/{id}/export` | Exportar definição (pacote). |
| `POST` | `/process-definitions/import` | Importar definição (pacote). |
| `DELETE` | `/process-definitions/{id}/archive` | Arquivar definição. |
| `POST` | `/process-definitions/{id}/unarchive` | Desarquivar definição. |
| `POST` | `/process-definitions/{id}/unassign` | Desatribuir definição. |
| `PUT` | `/process-definitions/{processKey}/priorities` | Configurar prioridades de tarefas. |
| `DELETE` | `/process-definitions/priorities/{id}` | Eliminar prioridade. |
| `GET` | `/process-definitions/{processKey}/priorities` | Listar prioridades. |

### Instâncias de Processo — `/process-instances`

| Método | Caminho | Descrição |
|---|---|---|
| `POST` | `/process-instances/search` | Pesquisar instâncias (filtro no corpo + paginação na query). |
| `GET` | `/process-instances/{id}` | Obter instância por ID. |
| `GET` | `/process-instances/status` | Listar estados de instância. |
| `GET` | `/process-instances/{id}/task-status` | Estado das tarefas de uma instância. |
| `GET` | `/process-instances/stats` | Estatísticas de instâncias. |
| `POST` | `/process-instances/event` | Disparar um evento de processo. |
| `POST` | `/process-instances/create` | Criar instância de processo. |
| `POST` | `/process-instances` | Criar e arrancar instância de processo. |
| `POST` | `/process-instances/{id}/start` | Arrancar uma instância existente por ID. |
| `POST` | `/process-instances/{id}/timer/reschedule` | Reagendar um *timer*. |

### Tarefas — `/tasks-instances`

| Método | Caminho | Descrição |
|---|---|---|
| `POST` | `/tasks-instances/search` | Pesquisar tarefas (filtro no corpo + paginação na query). |
| `GET` | `/tasks-instances/{id}` | Obter tarefa por ID. |
| `POST` | `/tasks-instances/{id}/claim` | Assumir (*claim*) a tarefa. |
| `POST` | `/tasks-instances/{id}/unclaim` | Libertar (*unclaim*) a tarefa. |
| `POST` | `/tasks-instances/{id}/assign` | Atribuir a tarefa a um utilizador. |
| `POST` | `/tasks-instances/{id}/complete` | Concluir a tarefa. |
| `POST` | `/tasks-instances/me` | Listar as minhas tarefas (filtro + paginação). |
| `GET` | `/tasks-instances/status` | Listar estados de tarefa. |
| `GET` | `/tasks-instances/event_type` | Listar tipos de evento de tarefa. |
| `GET` | `/tasks-instances/{id}/variables` | Obter variáveis/formulários da tarefa. |
| `GET` | `/tasks-instances/stats` | Estatísticas de tarefas. |
| `GET` | `/tasks-instances/stats/me` | Estatísticas das minhas tarefas. |
| `GET` | `/tasks-instances/assignment-rules` | Listar regras de atribuição (filtros + paginação). |
| `PUT` | `/tasks-instances/assignment-rules/{id}` | Atualizar regra de atribuição. |
| `DELETE` | `/tasks-instances/assignment-rules/{id}` | Eliminar regra de atribuição. |
| `POST` | `/tasks-instances/{id}/save` | Guardar dados da tarefa (rascunho). |

### Atividades — `/activities`

| Método | Caminho | Descrição |
|---|---|---|
| `GET` | `/activities/{id}` | Obter atividade por ID. |
| `GET` | `/activities/instances` | Listar instâncias de atividade (por `processIdentifier`). |
| `GET` | `/activities/progress` | Progresso das atividades (por `processIdentifier`). |

### Chaves M2M — `/m2m-keys` (só super-admin)

| Método | Caminho | Descrição |
|---|---|---|
| `POST` | `/m2m-keys` | Criar API key M2M (devolve o plaintext **uma vez**). |
| `GET` | `/m2m-keys` | Listar keys (sem segredos). |
| `DELETE` | `/m2m-keys/{id}` | Revogar — efeito imediato. |
| `POST` | `/m2m-keys/{id}/rotate` | Rodar (nova key; a antiga expira após o grace). |

> Consola de administração, **fora do SDK de utilizador**: exige JWT de super-admin (uma key M2M nunca
> acede). Contratos completos e regras de UX: `docs/M2M_FRONTEND_HANDOFF.md`.

---

## 9. Referência de endpoints

Descrição detalhada de cada endpoint, agrupada por recurso. Para cada um: método, caminho completo, parâmetros, corpo do pedido e forma da resposta. No fim de cada grupo, a subsecção **Modelos de dados (DTOs)** lista os campos exatos de cada DTO.

### Áreas e Definições de Processo (Areas)

Módulo responsável pela gestão de áreas organizacionais e das respetivas definições de processo (process definitions). A rota base é `/areas`. Permite listar, criar, consultar, atualizar e eliminar áreas, gerir as definições de processo associadas a cada área e obter a lista de estados (status) disponíveis para áreas. Os endpoints de listagem suportam paginação através dos parâmetros de query `page` e `size`.

#### `GET /areas`

Lista áreas, com filtros opcionais e paginação.

**Parâmetros de query**

| Nome | Tipo | Obrig. | Descrição |
|---|---|---|---|
| `code` | string | Não | Filtra pelo código da área. |
| `name` | string | Não | Filtra pelo nome da área. |
| `applicationBase` | string | Não | Filtra pela aplicação base associada. |
| `status` | string | Não | Filtra pelo estado (ver enum `Status`: `ACTIVE`, `INACTIVE`). |
| `parentId` | string | Não | Filtra pelo ID da área-pai. |
| `page` | number (integer) | Não | Número da página (paginação). |
| `size` | number (integer) | Não | Dimensão da página (paginação). |

**Resposta**

`200` — `AreaListPageDTO` (envelope paginado com `content` de `AreaDTO`).

```json
{
  "content": [
    {
      "id": "3f1c9a2e-1b2c-4d5e-8f90-1a2b3c4d5e6f",
      "code": "RH",
      "name": "Recursos Humanos",
      "applicationBase": "igrp-rh",
      "areaId": "00000000-0000-0000-0000-000000000000",
      "status": "ACTIVE",
      "statusDesc": "Ativo",
      "process": [],
      "createdAt": "2026-06-30T10:15:30",
      "updatedAt": "2026-06-30T10:15:30",
      "createdBy": "admin",
      "updatedBy": "admin",
      "description": "Área de gestão de recursos humanos",
      "color": "#1E90FF"
    }
  ],
  "pageNumber": 0,
  "pageSize": 20,
  "totalElements": 1,
  "totalPages": 1,
  "last": true,
  "first": true
}
```

#### `POST /areas`

Cria uma nova área.

**Corpo do pedido** — `AreaRequestDTO`

```json
{
  "code": "RH",
  "name": "Recursos Humanos",
  "description": "Área de gestão de recursos humanos",
  "applicationBase": "igrp-rh",
  "parentId": "3f1c9a2e-1b2c-4d5e-8f90-1a2b3c4d5e6f",
  "color": "#1E90FF"
}
```

| Campo | Tipo | Obrig. | Validação | Descrição |
|---|---|---|---|---|
| `code` | string | Sim | `@NotBlank` | Código da área. |
| `name` | string | Sim | `@NotBlank` | Nome da área. |
| `description` | string | Não | — | Descrição da área. |
| `applicationBase` | string | Sim | `@NotBlank` | Aplicação base associada. |
| `parentId` | string (UUID) | Não | — | ID da área-pai. |
| `color` | string | Não | — | Cor associada à área. |

**Resposta**

`201` — `AreaDTO` (ver Modelos de dados).

#### `GET /areas/{id}`

Obtém os detalhes de uma área pelo seu ID.

**Parâmetros de caminho**

| Nome | Tipo | Descrição |
|---|---|---|
| `id` | string | ID da área. |

**Resposta**

`200` — `AreaDTO`.

```json
{
  "id": "3f1c9a2e-1b2c-4d5e-8f90-1a2b3c4d5e6f",
  "code": "RH",
  "name": "Recursos Humanos",
  "applicationBase": "igrp-rh",
  "areaId": "00000000-0000-0000-0000-000000000000",
  "status": "ACTIVE",
  "statusDesc": "Ativo",
  "process": [
    {
      "id": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
      "processKey": "onboarding",
      "releaseId": "rel-2026-01",
      "areaId": "3f1c9a2e-1b2c-4d5e-8f90-1a2b3c4d5e6f",
      "status": "ACTIVE",
      "statusDesc": "Ativo",
      "version": "1.0.0",
      "createdAt": "2026-06-30T10:15:30",
      "createdBy": "admin",
      "removedAt": null,
      "removedBy": null,
      "name": "Onboarding de Colaboradores"
    }
  ],
  "createdAt": "2026-06-30T10:15:30",
  "updatedAt": "2026-06-30T10:15:30",
  "createdBy": "admin",
  "updatedBy": "admin",
  "description": "Área de gestão de recursos humanos",
  "color": "#1E90FF"
}
```

#### `PUT /areas/{id}`

Atualiza uma área existente.

**Parâmetros de caminho**

| Nome | Tipo | Descrição |
|---|---|---|
| `id` | string | ID da área a atualizar. |

**Corpo do pedido** — `AreaRequestDTO` (mesma forma e validações do `POST /areas`)

```json
{
  "code": "RH",
  "name": "Recursos Humanos (atualizado)",
  "description": "Descrição revista",
  "applicationBase": "igrp-rh",
  "parentId": "3f1c9a2e-1b2c-4d5e-8f90-1a2b3c4d5e6f",
  "color": "#FF8C00"
}
```

**Resposta**

`200` — `AreaDTO`.

#### `DELETE /areas/{id}`

Elimina uma área pelo seu ID.

**Parâmetros de caminho**

| Nome | Tipo | Descrição |
|---|---|---|
| `id` | string | ID da área a eliminar. |

**Resposta**

`204` — sem corpo (o tipo de retorno é `String`, mas a resposta documentada é No Content).

#### `GET /areas/{areaId}/process-definitions`

Lista as definições de processo de uma área, com filtros opcionais e paginação.

**Parâmetros de caminho**

| Nome | Tipo | Descrição |
|---|---|---|
| `areaId` | string | ID da área. |

**Parâmetros de query**

| Nome | Tipo | Obrig. | Descrição |
|---|---|---|---|
| `processKey` | string | Não | Filtra pela chave do processo. |
| `status` | string | Não | Filtra pelo estado (ver enum `Status`: `ACTIVE`, `INACTIVE`). |
| `releaseId` | string | Não | Filtra pelo ID de release. |
| `page` | number (integer) | Não | Número da página (paginação). |
| `size` | number (integer) | Não | Dimensão da página (paginação). |

**Resposta**

`200` — `ProcessDefinitionListPageDTO` (envelope paginado com `content` de `ProcessDefinitionDTO`).

```json
{
  "content": [
    {
      "id": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
      "processKey": "onboarding",
      "releaseId": "rel-2026-01",
      "areaId": "3f1c9a2e-1b2c-4d5e-8f90-1a2b3c4d5e6f",
      "status": "ACTIVE",
      "statusDesc": "Ativo",
      "version": "1.0.0",
      "createdAt": "2026-06-30T10:15:30",
      "createdBy": "admin",
      "removedAt": null,
      "removedBy": null,
      "name": "Onboarding de Colaboradores"
    }
  ],
  "pageNumber": 0,
  "pageSize": 20,
  "totalElements": 1,
  "totalPages": 1,
  "last": true,
  "first": true
}
```

#### `POST /areas/{areaId}/process-definitions`

Cria uma nova definição de processo associada a uma área.

**Parâmetros de caminho**

| Nome | Tipo | Descrição |
|---|---|---|
| `areaId` | string | ID da área à qual a definição de processo fica associada. |

**Corpo do pedido** — `ProcessDefinitionRequestDTO`

```json
{
  "processKey": "onboarding",
  "releaseId": "rel-2026-01",
  "version": "1.0.0",
  "name": "Onboarding de Colaboradores"
}
```

| Campo | Tipo | Obrig. | Validação | Descrição |
|---|---|---|---|---|
| `processKey` | string | Sim | `@NotBlank` | Chave do processo. |
| `releaseId` | string | Sim | `@NotBlank` | ID de release. |
| `version` | string | Não | — | Versão da definição de processo. |
| `name` | string | Sim | `@NotBlank` | Nome da definição de processo. |

**Resposta**

`201` — `ProcessDefinitionDTO` (ver Modelos de dados).

#### `DELETE /areas/{areaId}/process-definitions/{processDefinitionId}`

Remove uma definição de processo de uma área.

**Parâmetros de caminho**

| Nome | Tipo | Descrição |
|---|---|---|
| `areaId` | string | ID da área. |
| `processDefinitionId` | string | ID da definição de processo a remover. |

**Resposta**

`204` — sem corpo (o tipo de retorno é `String`, mas a resposta documentada é No Content).

#### `GET /areas/status`

Lista os estados (status) disponíveis para áreas, no formato label/value (adequado a dropdowns).

**Resposta**

`200` — `List<ConfigParameterDTO>`.

```json
[
  { "label": "Ativo", "value": "ACTIVE" },
  { "label": "Inactive", "value": "INACTIVE" }
]
```

#### Modelos de dados (DTOs)

#### `AreaRequestDTO` (corpo de criação/atualização de área)

| Campo (JSON) | Tipo | Obrigatório | Validação | Descrição |
|---|---|---|---|---|
| `code` | string | Sim | `@NotBlank` | Código da área. |
| `name` | string | Sim | `@NotBlank` | Nome da área. |
| `description` | string | Não | — | Descrição da área. |
| `applicationBase` | string | Sim | `@NotBlank` | Aplicação base associada. |
| `parentId` | string (UUID) | Não | — | ID da área-pai. |
| `color` | string | Não | — | Cor associada à área. |

#### `AreaDTO` (representação de área devolvida pelo API)

| Campo (JSON) | Tipo | Obrigatório | Validação | Descrição |
|---|---|---|---|---|
| `id` | string (UUID) | Sim | `@NotNull` | Identificador da área. |
| `code` | string | Sim | `@NotBlank` | Código da área. |
| `name` | string | Sim | `@NotBlank` | Nome da área. |
| `applicationBase` | string | Sim | `@NotBlank` | Aplicação base associada. |
| `areaId` | string (UUID) | Sim | `@NotNull` | ID da área-pai/referência hierárquica. |
| `status` | enum (string) | Sim | `@NotNull` | Estado da área. Valores: `ACTIVE`, `INACTIVE` (ver enum `Status`). |
| `statusDesc` | string | Não | — | Descrição legível do estado (ex.: "Ativo"). |
| `process` | `ProcessDefinitionDTO[]` | Não | `@Valid` | Lista de definições de processo da área (default: `[]`). |
| `createdAt` | string (ISO-8601, data-hora local) | Não | — | Data/hora de criação. |
| `updatedAt` | string (ISO-8601, data-hora local) | Não | — | Data/hora da última atualização. |
| `createdBy` | string | Não | — | Utilizador que criou. |
| `updatedBy` | string | Não | — | Utilizador que atualizou. |
| `description` | string | Não | — | Descrição da área. |
| `color` | string | Não | — | Cor associada à área. |

#### `ProcessDefinitionRequestDTO` (corpo de criação de definição de processo)

| Campo (JSON) | Tipo | Obrigatório | Validação | Descrição |
|---|---|---|---|---|
| `processKey` | string | Sim | `@NotBlank` | Chave do processo. |
| `releaseId` | string | Sim | `@NotBlank` | ID de release. |
| `version` | string | Não | — | Versão da definição. |
| `name` | string | Sim | `@NotBlank` | Nome da definição de processo. |

#### `ProcessDefinitionDTO` (representação de definição de processo devolvida pelo API)

| Campo (JSON) | Tipo | Obrigatório | Validação | Descrição |
|---|---|---|---|---|
| `id` | string (UUID) | Sim | `@NotNull` | Identificador da definição de processo. |
| `processKey` | string | Sim | `@NotBlank` | Chave do processo. |
| `releaseId` | string | Sim | `@NotBlank` | ID de release. |
| `areaId` | string (UUID) | Sim | `@NotNull` | ID da área associada. |
| `status` | enum (string) | Não | — | Estado da definição. Valores: `ACTIVE`, `INACTIVE` (ver enum `Status`). |
| `statusDesc` | string | Não | — | Descrição legível do estado. |
| `version` | string | Sim | `@NotBlank` | Versão da definição. |
| `createdAt` | string (ISO-8601, data-hora local) | Não | — | Data/hora de criação. |
| `createdBy` | string | Não | — | Utilizador que criou. |
| `removedAt` | string (ISO-8601, data-hora local) | Não | — | Data/hora de remoção (se removida). |
| `removedBy` | string | Não | — | Utilizador que removeu. |
| `name` | string | Não | — | Nome da definição de processo. |

#### `PageDTO` (campos de paginação herdados por `AreaListPageDTO` e `ProcessDefinitionListPageDTO`)

| Campo (JSON) | Tipo | Obrigatório | Validação | Descrição |
|---|---|---|---|---|
| `pageNumber` | number (integer) | Não | — | Número da página atual. |
| `pageSize` | number (integer) | Não | — | Dimensão da página. |
| `totalElements` | number (long) | Não | — | Total de elementos. |
| `totalPages` | number (integer) | Não | — | Total de páginas. |
| `last` | boolean | Sim | — | Indica se é a última página. |
| `first` | boolean | Sim | — | Indica se é a primeira página. |

#### `AreaListPageDTO` (envelope paginado de áreas, estende `PageDTO`)

| Campo (JSON) | Tipo | Obrigatório | Validação | Descrição |
|---|---|---|---|---|
| `content` | `AreaDTO[]` | Não | `@Valid` | Lista de áreas da página (default: `[]`). |
| *(+ campos de `PageDTO`)* | — | — | — | `pageNumber`, `pageSize`, `totalElements`, `totalPages`, `last`, `first`. |

#### `ProcessDefinitionListPageDTO` (envelope paginado de definições de processo, estende `PageDTO`)

| Campo (JSON) | Tipo | Obrigatório | Validação | Descrição |
|---|---|---|---|---|
| `content` | `ProcessDefinitionDTO[]` | Não | `@Valid` | Lista de definições de processo da página (default: `[]`). |
| *(+ campos de `PageDTO`)* | — | — | — | `pageNumber`, `pageSize`, `totalElements`, `totalPages`, `last`, `first`. |

#### `ConfigParameterDTO` (item label/value, usado em `GET /areas/status`)

| Campo (JSON) | Tipo | Obrigatório | Validação | Descrição |
|---|---|---|---|---|
| `label` | string | Sim | `@NotBlank` | Texto legível (ex.: "Ativo"). |
| `value` | string | Sim | `@NotBlank` | Valor do parâmetro (ex.: "ACTIVE"). |

#### Enum `Status`

Valores possíveis (serializados como o código em string):

| Valor (code) | Descrição |
|---|---|
| `ACTIVE` | Ativo |
| `INACTIVE` | Inactive |

### Definições de Processo (Process Definitions)

Módulo responsável pela gestão de definições de processo: deploy de modelos BPMN, listagem de deployments, configuração de artefactos (tarefas/formulários), sequências de numeração, prioridades de tarefas, atribuição (assign/unassign) de grupos, arquivamento e exportação/importação de pacotes de processo. Todos os endpoints partilham a rota base `/process-definitions` (a base path da aplicação é `/`, sem context-path).

#### `POST /process-definitions/deploy`

Faz o deploy de um processo BPMN, criando uma nova definição de processo.

**Corpo do pedido**: `ProcessDeploymentRequestDTO`

```json
{
  "name": "Pedido de Licença",
  "description": "Processo de aprovação de pedidos de licença",
  "key": "pedido-licenca",
  "resourceName": "pedido-licenca.bpmn",
  "bpmnXml": "<?xml version=\"1.0\"?><definitions>...</definitions>",
  "applicationBase": "rh-app"
}
```

| Campo | Tipo | Obrig. | Validação | Descrição |
|---|---|---|---|---|
| name | string | Não | — | Nome do processo |
| description | string | Não | — | Descrição do processo |
| key | string | Sim | @NotBlank | Chave única do processo |
| resourceName | string | Sim | @NotBlank | Nome do recurso BPMN |
| bpmnXml | string | Sim | @NotBlank | Conteúdo XML do diagrama BPMN |
| applicationBase | string | Sim | @NotBlank | Aplicação base associada |

**Resposta**: `201 Created` — `ProcessDeploymentDTO`

```json
{
  "key": "pedido-licenca",
  "name": "Pedido de Licença",
  "description": "Processo de aprovação de pedidos de licença",
  "version": "1",
  "bpmnXml": "<?xml version=\"1.0\"?>...",
  "bpmnUrl": "https://.../pedido-licenca.bpmn",
  "bpmnSourceType": "INLINE",
  "resourceName": "pedido-licenca.bpmn",
  "deployed": true,
  "deploymentId": "a1b2c3d4",
  "deployedAt": "2026-06-30T10:15:00",
  "applicationBase": "rh-app"
}
```

#### `GET /process-definitions`

Lista os deployments de definições de processo, com paginação e filtros opcionais.

**Parâmetros de query**

| Nome | Tipo | Obrig. | Descrição |
|---|---|---|---|
| applicationBase | string | Não | Filtra pela aplicação base |
| processName | string | Não | Filtra pelo nome do processo |
| page | number | Não | Número da página (paginação). Default `0` |
| size | number | Não | Tamanho da página (paginação). Default `20` |
| filterByCurrentUser | boolean | Não | Se `true`, filtra pelos processos do utilizador atual. Default `false` |
| candidateGroups | string | Não | Filtra por grupos candidatos |

**Resposta**: `200 OK` — `ProcessDeploymentListPageDTO` (envelope paginado)

```json
{
  "pageNumber": 0,
  "pageSize": 20,
  "totalElements": 1,
  "totalPages": 1,
  "last": true,
  "first": true,
  "content": [
    {
      "id": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
      "processKey": "pedido-licenca",
      "name": "Pedido de Licença",
      "description": "Processo de aprovação de pedidos de licença",
      "version": "1",
      "deploymentId": "a1b2c3d4",
      "applicationBase": "rh-app",
      "candidateGroups": "rh,gestores"
    }
  ]
}
```

#### `PUT /process-definitions/{id}/artifacts/{taskKey}`

Configura (cria/atualiza) um artefacto (tarefa) de uma definição de processo, identificado pela chave da tarefa.

**Parâmetros de caminho**

| Nome | Tipo | Descrição |
|---|---|---|
| id | string | Identificador da definição de processo |
| taskKey | string | Chave da tarefa/artefacto |

**Corpo do pedido**: `ProcessArtifactRequestDTO`

```json
{
  "name": "Aprovar Pedido",
  "formKey": "form-aprovacao",
  "candidateGroups": "gestores",
  "dueDate": "P5D",
  "priority": 50
}
```

| Campo | Tipo | Obrig. | Validação | Descrição |
|---|---|---|---|---|
| name | string | Sim | @NotBlank | Nome do artefacto |
| formKey | string | Sim | @NotBlank | Chave do formulário associado |
| candidateGroups | string | Não | — | Grupos candidatos à tarefa |
| dueDate | string | Não | — | Prazo da tarefa |
| priority | number (`Integer`) | Não | — | Prioridade da tarefa |

**Resposta**: `200 OK` — `ProcessArtifactDTO`

```json
{
  "id": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
  "name": "Aprovar Pedido",
  "key": "aprovar-pedido",
  "processDefinitionId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "formKey": "form-aprovacao",
  "candidateGroups": "gestores",
  "dueDate": "P5D",
  "priority": 50
}
```

#### `DELETE /process-definitions/artifacts/{id}`

Elimina um artefacto pelo seu identificador.

**Parâmetros de caminho**

| Nome | Tipo | Descrição |
|---|---|---|
| id | string | Identificador do artefacto |

**Resposta**: `204 No Content` (corpo vazio; tipo subjacente `String`)

#### `GET /process-definitions/{id}/artifacts`

Obtém a lista de artefactos de uma definição de processo.

**Parâmetros de caminho**

| Nome | Tipo | Descrição |
|---|---|---|
| id | string | Identificador da definição de processo |

**Resposta**: `200 OK` — `ProcessArtifactDTO[]`

```json
[
  {
    "id": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
    "name": "Aprovar Pedido",
    "key": "aprovar-pedido",
    "processDefinitionId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
    "formKey": "form-aprovacao",
    "candidateGroups": "gestores",
    "dueDate": "P5D",
    "priority": 50
  }
]
```

#### `GET /process-definitions/{id}/deployed-artifacts`

Obtém a lista de artefactos efetivamente publicados (deployed) de uma definição de processo.

**Parâmetros de caminho**

| Nome | Tipo | Descrição |
|---|---|---|
| id | string | Identificador da definição de processo |

**Resposta**: `200 OK` — `ProcessArtifactDTO[]` (mesma forma do endpoint anterior)

#### `GET /process-definitions/{processDefinitionKey}/sequence`

Obtém a configuração de sequência (numeração) de uma definição de processo.

**Parâmetros de caminho**

| Nome | Tipo | Descrição |
|---|---|---|
| processDefinitionKey | string | Chave da definição de processo |

**Resposta**: `200 OK` — `ProcessSequenceDTO`

```json
{
  "id": "c0a80123-7ac0-11ed-a1eb-0242ac120002",
  "name": "Sequência Licenças",
  "prefix": "LIC",
  "checkDigitSize": 1,
  "padding": 6,
  "dateFormat": "yyyy",
  "nextNumber": 42,
  "numberIncrement": 1,
  "processDefinitionKey": "pedido-licenca",
  "separator": "-"
}
```

#### `POST /process-definitions/{processDefinitionKey}/sequence`

Cria a configuração de sequência (numeração) de uma definição de processo.

**Parâmetros de caminho**

| Nome | Tipo | Descrição |
|---|---|---|
| processDefinitionKey | string | Chave da definição de processo |

**Corpo do pedido**: `SequenceRequestDTO`

```json
{
  "name": "Sequência Licenças",
  "prefix": "LIC",
  "dateFormat": "yyyy",
  "checkDigitSize": 1,
  "padding": 6,
  "numberIncrement": 1,
  "separator": "-"
}
```

| Campo | Tipo | Obrig. | Validação | Descrição |
|---|---|---|---|---|
| name | string | Sim | @NotBlank | Nome da sequência |
| prefix | string | Sim | @NotBlank | Prefixo do número gerado |
| dateFormat | string | Sim | @NotBlank | Formato de data incluído no número |
| checkDigitSize | number (`short`) | Sim | @NotNull | Dimensão do dígito de controlo |
| padding | number (`short`) | Sim | @NotNull | Preenchimento (zeros à esquerda) |
| numberIncrement | number (`short`) | Não | — | Incremento entre números |
| separator | string | Não | — | Separador entre componentes |

**Resposta**: `200 OK` — `ProcessSequenceDTO` (ver exemplo acima)

#### `POST /process-definitions/{id}/assign`

Atribui grupos candidatos a uma definição de processo.

**Parâmetros de caminho**

| Nome | Tipo | Descrição |
|---|---|---|
| id | string | Identificador da definição de processo |

**Corpo do pedido**: `AssignProcessDTO`

```json
{
  "candidateGroups": "rh,gestores"
}
```

| Campo | Tipo | Obrig. | Validação | Descrição |
|---|---|---|---|---|
| candidateGroups | string | Sim | @NotBlank | Grupos candidatos a atribuir |

**Resposta**: `204 No Content` (corpo vazio; tipo subjacente `String`)

#### `GET /process-definitions/{id}/export`

Exporta uma definição de processo como pacote (incluindo BPMN, artefactos e sequência).

**Parâmetros de caminho**

| Nome | Tipo | Descrição |
|---|---|---|
| id | string | Identificador da definição de processo |

**Resposta**: `200 OK` — `ProcessPackageDTO`

```json
{
  "processKey": "pedido-licenca",
  "processName": "Pedido de Licença",
  "processVersion": "1",
  "processDescription": "Processo de aprovação de pedidos de licença",
  "bpmnXml": "<?xml version=\"1.0\"?>...",
  "applicationBase": "rh-app",
  "artifacts": [
    {
      "id": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
      "name": "Aprovar Pedido",
      "key": "aprovar-pedido",
      "processDefinitionId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
      "formKey": "form-aprovacao",
      "candidateGroups": "gestores",
      "dueDate": "P5D",
      "priority": 50
    }
  ],
  "sequence": {
    "id": "c0a80123-7ac0-11ed-a1eb-0242ac120002",
    "name": "Sequência Licenças",
    "prefix": "LIC",
    "checkDigitSize": 1,
    "padding": 6,
    "dateFormat": "yyyy",
    "nextNumber": 42,
    "numberIncrement": 1,
    "processDefinitionKey": "pedido-licenca",
    "separator": "-"
  },
  "candidateGroups": "rh,gestores"
}
```

#### `POST /process-definitions/import`

Importa uma definição de processo a partir de um pacote.

**Corpo do pedido**: `ProcessPackageDTO` (ver tabela de campos em "Modelos de dados"; mesma forma do exemplo de export)

```json
{
  "processKey": "pedido-licenca",
  "processName": "Pedido de Licença",
  "processVersion": "1",
  "processDescription": "Processo de aprovação de pedidos de licença",
  "bpmnXml": "<?xml version=\"1.0\"?>...",
  "applicationBase": "rh-app",
  "artifacts": [],
  "sequence": null,
  "candidateGroups": "rh,gestores"
}
```

**Resposta**: `204 No Content` (corpo vazio; tipo subjacente `String`)

#### `DELETE /process-definitions/{id}/archive`

Arquiva uma definição de processo.

**Parâmetros de caminho**

| Nome | Tipo | Descrição |
|---|---|---|
| id | string | Identificador da definição de processo |

**Resposta**: `204 No Content` (corpo vazio; tipo subjacente `String`)

#### `POST /process-definitions/{id}/unarchive`

Desarquiva uma definição de processo previamente arquivada.

**Parâmetros de caminho**

| Nome | Tipo | Descrição |
|---|---|---|
| id | string | Identificador da definição de processo |

**Resposta**: `204 No Content` (corpo vazio; tipo subjacente `String`)

#### `POST /process-definitions/{id}/unassign`

Remove a atribuição de grupos candidatos de uma definição de processo.

**Parâmetros de caminho**

| Nome | Tipo | Descrição |
|---|---|---|
| id | string | Identificador da definição de processo |

**Corpo do pedido**: `AssignProcessDTO`

```json
{
  "candidateGroups": "rh,gestores"
}
```

| Campo | Tipo | Obrig. | Validação | Descrição |
|---|---|---|---|---|
| candidateGroups | string | Sim | @NotBlank | Grupos candidatos a remover |

**Resposta**: `204 No Content` (corpo vazio; tipo subjacente `String`)

#### `PUT /process-definitions/{processKey}/priorities`

Configura (cria/atualiza) as prioridades de tarefas de uma definição de processo. O corpo é uma **lista** de prioridades.

**Parâmetros de caminho**

| Nome | Tipo | Descrição |
|---|---|---|
| processKey | string | Chave da definição de processo |

**Corpo do pedido**: `TaskPriorityRequestDTO[]` (array)

```json
[
  {
    "code": "ALTA",
    "label": "Alta",
    "weight": 100,
    "id": "2f1c8d90-aaaa-bbbb-cccc-1234567890ab",
    "color": "#FF0000"
  },
  {
    "code": "NORMAL",
    "label": "Normal",
    "weight": 50,
    "color": "#00AA00"
  }
]
```

| Campo | Tipo | Obrig. | Validação | Descrição |
|---|---|---|---|---|
| code | string | Sim | @NotBlank | Código da prioridade |
| label | string | Sim | @NotBlank | Etiqueta apresentável |
| weight | number (`Integer`) | Sim | @NotNull | Peso/ordem da prioridade |
| id | string (UUID) | Não | — | Identificador (para atualização) |
| color | string | Não | — | Cor associada (ex.: hex) |

**Resposta**: `200 OK` — `TaskPriorityDTO` (objeto único)

```json
{
  "id": "2f1c8d90-aaaa-bbbb-cccc-1234567890ab",
  "code": "ALTA",
  "label": "Alta",
  "weight": 100,
  "processDefinitionKey": "pedido-licenca",
  "color": "#FF0000"
}
```

#### `DELETE /process-definitions/priorities/{id}`

Elimina uma prioridade de tarefa pelo seu identificador.

**Parâmetros de caminho**

| Nome | Tipo | Descrição |
|---|---|---|
| id | string | Identificador da prioridade |

**Resposta**: `204 No Content` (corpo vazio; tipo subjacente `String`)

#### `GET /process-definitions/{processKey}/priorities`

Obtém a lista de prioridades de tarefas configuradas para uma definição de processo.

**Parâmetros de caminho**

| Nome | Tipo | Descrição |
|---|---|---|
| processKey | string | Chave da definição de processo |

**Resposta**: `200 OK` — `TaskPriorityDTO[]`

```json
[
  {
    "id": "2f1c8d90-aaaa-bbbb-cccc-1234567890ab",
    "code": "ALTA",
    "label": "Alta",
    "weight": 100,
    "processDefinitionKey": "pedido-licenca",
    "color": "#FF0000"
  }
]
```

#### Modelos de dados (DTOs)

#### `ProcessDeploymentRequestDTO` (corpo de `POST /deploy`)

| Campo (JSON) | Tipo | Obrigatório | Validação | Descrição |
|---|---|---|---|---|
| name | string | Não | — | Nome do processo |
| description | string | Não | — | Descrição do processo |
| key | string | Sim | @NotBlank | Chave única do processo |
| resourceName | string | Sim | @NotBlank | Nome do recurso BPMN |
| bpmnXml | string | Sim | @NotBlank | Conteúdo XML do BPMN |
| applicationBase | string | Sim | @NotBlank | Aplicação base |

#### `ProcessDeploymentDTO` (resposta de `POST /deploy`)

| Campo (JSON) | Tipo | Obrigatório | Validação | Descrição |
|---|---|---|---|---|
| key | string | — | — | Chave do processo |
| name | string | — | — | Nome do processo |
| description | string | — | — | Descrição |
| version | string | — | — | Versão |
| bpmnXml | string | — | — | XML do BPMN |
| bpmnUrl | string | — | — | URL do recurso BPMN |
| bpmnSourceType | string | — | — | Tipo de origem do BPMN |
| resourceName | string | — | — | Nome do recurso |
| deployed | boolean | — | — | Indica se foi publicado |
| deploymentId | string | — | — | Identificador do deployment |
| deployedAt | string (ISO-8601, `LocalDateTime`) | — | — | Data/hora do deployment |
| applicationBase | string | — | — | Aplicação base |

#### `ProcessDeploymentListPageDTO` (resposta de `GET /`) — estende `PageDTO`

| Campo (JSON) | Tipo | Obrigatório | Validação | Descrição |
|---|---|---|---|---|
| content | `ProcessDeploymentListDTO[]` | — | @Valid | Lista de deployments da página |
| pageNumber | number | — | — | Número da página atual (herdado de `PageDTO`) |
| pageSize | number | — | — | Tamanho da página (herdado) |
| totalElements | number (`Long`) | — | — | Total de elementos (herdado) |
| totalPages | number | — | — | Total de páginas (herdado) |
| last | boolean | — | — | Se é a última página (herdado) |
| first | boolean | — | — | Se é a primeira página (herdado) |

#### `ProcessDeploymentListDTO` (item de `content`)

| Campo (JSON) | Tipo | Obrigatório | Validação | Descrição |
|---|---|---|---|---|
| id | string | — | — | Identificador |
| processKey | string | — | — | Chave do processo |
| name | string | — | — | Nome |
| description | string | — | — | Descrição |
| version | string | — | — | Versão |
| deploymentId | string | — | — | Identificador do deployment |
| applicationBase | string | — | — | Aplicação base |
| candidateGroups | string | — | — | Grupos candidatos |

#### `ProcessArtifactRequestDTO` (corpo de `PUT /{id}/artifacts/{taskKey}`)

| Campo (JSON) | Tipo | Obrigatório | Validação | Descrição |
|---|---|---|---|---|
| name | string | Sim | @NotBlank | Nome do artefacto |
| formKey | string | Sim | @NotBlank | Chave do formulário |
| candidateGroups | string | Não | — | Grupos candidatos |
| dueDate | string | Não | — | Prazo |
| priority | number (`Integer`) | Não | — | Prioridade |

#### `ProcessArtifactDTO` (resposta de artefactos)

| Campo (JSON) | Tipo | Obrigatório | Validação | Descrição |
|---|---|---|---|---|
| id | string (UUID) | — | — | Identificador |
| name | string | — | — | Nome |
| key | string | — | — | Chave do artefacto/tarefa |
| processDefinitionId | string | — | — | Id da definição de processo |
| formKey | string | — | — | Chave do formulário |
| candidateGroups | string | — | — | Grupos candidatos |
| dueDate | string | — | — | Prazo |
| priority | number (`Integer`) | — | — | Prioridade |

#### `ProcessSequenceDTO` (resposta de sequência)

| Campo (JSON) | Tipo | Obrigatório | Validação | Descrição |
|---|---|---|---|---|
| id | string (UUID) | — | — | Identificador |
| name | string | — | — | Nome da sequência |
| prefix | string | — | — | Prefixo |
| checkDigitSize | number (`short`) | — | — | Dimensão do dígito de controlo |
| padding | number (`short`) | — | — | Preenchimento |
| dateFormat | string | — | — | Formato de data |
| nextNumber | number (`Long`) | — | — | Próximo número |
| numberIncrement | number (`short`) | — | — | Incremento |
| processDefinitionKey | string | — | — | Chave da definição de processo |
| separator | string | — | — | Separador |

#### `SequenceRequestDTO` (corpo de `POST /{processDefinitionKey}/sequence`)

| Campo (JSON) | Tipo | Obrigatório | Validação | Descrição |
|---|---|---|---|---|
| name | string | Sim | @NotBlank | Nome da sequência |
| prefix | string | Sim | @NotBlank | Prefixo |
| dateFormat | string | Sim | @NotBlank | Formato de data |
| checkDigitSize | number (`short`) | Sim | @NotNull | Dimensão do dígito de controlo |
| padding | number (`short`) | Sim | @NotNull | Preenchimento |
| numberIncrement | number (`short`) | Não | — | Incremento |
| separator | string | Não | — | Separador |

#### `AssignProcessDTO` (corpo de `assign`/`unassign`)

| Campo (JSON) | Tipo | Obrigatório | Validação | Descrição |
|---|---|---|---|---|
| candidateGroups | string | Sim | @NotBlank | Grupos candidatos |

#### `ProcessPackageDTO` (corpo de `import` / resposta de `export`)

| Campo (JSON) | Tipo | Obrigatório | Validação | Descrição |
|---|---|---|---|---|
| processKey | string | Sim | @NotBlank | Chave do processo |
| processName | string | Sim | @NotBlank | Nome do processo |
| processVersion | string | Não | — | Versão |
| processDescription | string | Não | — | Descrição |
| bpmnXml | string | Sim | @NotBlank | XML do BPMN |
| applicationBase | string | Sim | @NotBlank | Aplicação base |
| artifacts | `ProcessArtifactDTO[]` | Não | @Valid | Lista de artefactos (default `[]`) |
| sequence | `ProcessSequenceDTO` | Não | @Valid | Configuração de sequência |
| candidateGroups | string | Não | — | Grupos candidatos |

#### `TaskPriorityRequestDTO` (item do corpo de `PUT /{processKey}/priorities`)

| Campo (JSON) | Tipo | Obrigatório | Validação | Descrição |
|---|---|---|---|---|
| code | string | Sim | @NotBlank | Código da prioridade |
| label | string | Sim | @NotBlank | Etiqueta |
| weight | number (`Integer`) | Sim | @NotNull | Peso |
| id | string (UUID) | Não | — | Identificador (atualização) |
| color | string | Não | — | Cor |

#### `TaskPriorityDTO` (resposta de prioridades)

| Campo (JSON) | Tipo | Obrigatório | Validação | Descrição |
|---|---|---|---|---|
| id | string (UUID) | — | — | Identificador |
| code | string | — | — | Código |
| label | string | — | — | Etiqueta |
| weight | number (`Integer`) | — | — | Peso |
| processDefinitionKey | string | — | — | Chave da definição de processo |
| color | string | — | — | Cor |

#### `PageDTO` (superclasse de envelopes paginados)

| Campo (JSON) | Tipo | Obrigatório | Validação | Descrição |
|---|---|---|---|---|
| pageNumber | number | — | — | Número da página atual |
| pageSize | number | — | — | Tamanho da página |
| totalElements | number (`Long`) | — | — | Total de elementos |
| totalPages | number | — | — | Total de páginas |
| last | boolean | — | — | Se é a última página |
| first | boolean | — | — | Se é a primeira página |

> Nota: os endpoints que devolvem `204 No Content` (`DELETE /artifacts/{id}`, `POST /{id}/assign`, `POST /import`, `DELETE /{id}/archive`, `POST /{id}/unarchive`, `POST /{id}/unassign`, `DELETE /priorities/{id}`) têm tipo de retorno Java `ResponseEntity<String>`, mas não produzem corpo útil; o SDK deve tratá-los como respostas sem conteúdo.

### Instâncias de Processo (Process Instances)

Módulo responsável pela gestão de instâncias de processo no runtime do motor BPM: pesquisa/listagem com filtros e paginação, consulta por identificador, estatísticas, estados de tarefas, disparo de eventos de processo, criação e arranque de instâncias, e reagendamento de timers. O `@RequestMapping` da classe é `process-instances` (sem barra inicial); como a aplicação não tem context-path, a rota base resolve para `/process-instances` e os caminhos são absolutos a partir de `/`.

Nota sobre validação de corpo: todos os endpoints com `@RequestBody` usam `@Valid`, pelo que as restrições documentadas nas tabelas de DTO (ex.: `@NotBlank`, `@NotNull`) são aplicadas no servidor e devolvem `400 Bad Request` quando violadas.

#### `POST /process-instances/search`

Lista/pesquisa instâncias de processo aplicando um filtro por variáveis (corpo) combinado com filtros escalares (query) e paginação.

**Parâmetros de query**

| Nome | Tipo | Obrig. | Descrição |
|------|------|--------|-----------|
| `number` | string | Não | Filtra pelo número da instância. |
| `name` | string | Não | Filtra pelo nome da instância. |
| `procReleaseKey` | string | Não | Filtra pela chave do release do processo. |
| `procReleaseId` | string | Não | Filtra pelo ID do release do processo. |
| `status` | string | Não | Filtra pelo estado (ver enum `ProcessInstanceStatus`). |
| `applicationBase` | string | Não | Filtra pela aplicação base. |
| `dateFrom` | string | Não | Data inicial do intervalo (filtro temporal). |
| `dateTo` | string | Não | Data final do intervalo (filtro temporal). |
| `page` | number (Integer) | Não | Índice da página (paginação). |
| `size` | number (Integer) | Não | Dimensão da página (paginação). |

**Corpo do pedido** — `VariablesFilterDTO`

```json
{
  "variables": [
    { "name": "valorPedido", "operator": "GREATER_THAN_OR_EQUAL", "value": 1000 },
    { "name": "regiao", "operator": "EQUALS_IGNORE_CASE", "value": "Praia" }
  ]
}
```

| Campo | Tipo | Obrigatório | Validação | Descrição |
|-------|------|-------------|-----------|-----------|
| `variables` | `VariablesExpressionDTO[]` | Não | `@Valid` (valida cada elemento) | Lista de expressões de filtro por variáveis. Default: lista vazia. |

**Resposta** — `200 OK`, `ProcessInstanceListPageDTO` (envelope paginado)

```json
{
  "pageNumber": 0,
  "pageSize": 20,
  "totalElements": 42,
  "totalPages": 3,
  "last": false,
  "first": true,
  "content": [
    {
      "id": "3f1c2e4a-9b6d-4f2a-8c1e-1a2b3c4d5e6f",
      "procReleaseKey": "pedido-licenca",
      "procReleaseId": "pedido-licenca:1:abc123",
      "number": "PI-2026-000123",
      "status": "RUNNING",
      "statusDesc": "Em Execução",
      "businessKey": "LIC-998",
      "version": "1",
      "startedAt": "2026-06-30T09:15:00",
      "startedBy": "joao.silva",
      "endedAt": null,
      "endedBy": null,
      "canceledAt": null,
      "cancelledBy": null,
      "obsCancel": null,
      "applicationBase": "licenciamento",
      "name": "Pedido de Licença",
      "progress": "40%",
      "priority": 50,
      "variables": [{ "name": "valorPedido", "value": 1500 }],
      "userProfileStartedBy": {
        "id": "a1b2c3d4-0000-1111-2222-333344445555",
        "username": "joao.silva",
        "email": "joao.silva@example.cv",
        "firstName": "João",
        "lastName": "Silva",
        "fullName": "João Silva",
        "sub": "auth0|joaosilva"
      },
      "userProfileEndedBy": null,
      "userProfileCancelledBy": null
    }
  ]
}
```

#### `GET /process-instances/{id}`

Obtém uma instância de processo pelo seu identificador.

**Parâmetros de caminho**

| Nome | Tipo | Descrição |
|------|------|-----------|
| `id` | string | Identificador da instância de processo. |

**Resposta** — `200 OK`, `ProcessInstanceDTO` (ver Modelos de dados).

#### `GET /process-instances/status`

Lista os estados possíveis de uma instância de processo, no formato label/value (para preencher seletores).

**Resposta** — `200 OK`, `ConfigParameterDTO[]`

```json
[
  { "label": "Em Execução", "value": "RUNNING" },
  { "label": "Criado", "value": "CREATED" },
  { "label": "Supenso", "value": "SUSPENDED" },
  { "label": "Cancelado", "value": "CANCELED" },
  { "label": "Completado", "value": "COMPLETED" }
]
```

#### `GET /process-instances/{id}/task-status`

Lista o estado de cada tarefa associada à instância de processo indicada.

**Parâmetros de caminho**

| Nome | Tipo | Descrição |
|------|------|-----------|
| `id` | string | Identificador da instância de processo. |

**Resposta** — `200 OK`, `ProcessInstanceTaskStatusDTO[]`

```json
[
  { "taskKey": "analise-tecnica", "name": "Análise Técnica", "status": "COMPLETED" },
  { "taskKey": "aprovacao", "name": "Aprovação", "status": "ASSIGNED" }
]
```

#### `GET /process-instances/stats`

Devolve estatísticas agregadas das instâncias de processo (totais por estado).

**Resposta** — `200 OK`, `ProcessInstanceStatsDTO`

```json
{
  "totalProcessInstances": 1200,
  "totalCreatedProcess": 80,
  "totalRunningProcess": 540,
  "totalCompletedProcess": 520,
  "totalSuspendedProcess": 30,
  "totalCanceledProcess": 30
}
```

#### `POST /process-instances/event`

Dispara um evento de processo (ex.: mensagem) opcionalmente direcionado a uma tarefa ou business key, com variáveis adicionais.

**Corpo do pedido** — `ProcessEventDTO`

```json
{
  "messageName": "pagamentoConfirmado",
  "taskId": "tarefa-123",
  "businessKey": "LIC-998",
  "variables": [{ "name": "valorPago", "value": 1500 }]
}
```

| Campo | Tipo | Obrigatório | Validação | Descrição |
|-------|------|-------------|-----------|-----------|
| `messageName` | string | Não | — | Nome da mensagem/evento a disparar. |
| `taskId` | string | Não | — | ID da tarefa alvo do evento. |
| `businessKey` | string | Não | — | Business key da instância alvo. |
| `variables` | `ProcessVariableDTO[]` (de `shared`) | Não | `@Valid` | Variáveis a passar com o evento. Default: lista vazia. |

**Resposta** — `200 OK`, `string` (corpo de texto simples).

#### `POST /process-instances/create`

Cria uma instância de processo (sem a iniciar).

**Corpo do pedido** — `CreateProcessRequestDTO`

```json
{
  "processDefinitionId": "pedido-licenca:1:abc123",
  "processKey": "pedido-licenca",
  "applicationBase": "licenciamento",
  "businessKey": "LIC-998",
  "priority": 50
}
```

| Campo | Tipo | Obrigatório | Validação | Descrição |
|-------|------|-------------|-----------|-----------|
| `processDefinitionId` | string | Não | — | ID da definição do processo. |
| `processKey` | string | Sim | `@NotBlank` | Chave do processo. |
| `applicationBase` | string | Sim | `@NotBlank` | Aplicação base. |
| `businessKey` | string | Não | — | Business key a associar. |
| `priority` | number (Integer) | Não | — | Prioridade da instância. |

**Resposta** — `201 Created`, `ProcessInstanceDTO` (ver Modelos de dados).

#### `POST /process-instances`

Cria e arranca uma instância de processo numa única operação, com variáveis iniciais e regras de atribuição de tarefas.

**Corpo do pedido** — `StartProcessRequestDTO`

```json
{
  "processDefinitionId": "pedido-licenca:1:abc123",
  "processKey": "pedido-licenca",
  "businessKey": "LIC-998",
  "applicationBase": "licenciamento",
  "priority": 50,
  "variables": [{ "name": "valorPedido", "value": 1500 }],
  "assignmentRules": [
    {
      "taskKey": "analise-tecnica",
      "assignee": "joao.silva",
      "candidateUsers": null,
      "candidateGroups": "tecnicos",
      "assignmentMode": "ALWAYS",
      "priority": 10
    }
  ]
}
```

| Campo | Tipo | Obrigatório | Validação | Descrição |
|-------|------|-------------|-----------|-----------|
| `processDefinitionId` | string | Não | — | ID da definição do processo. |
| `processKey` | string | Sim | `@NotBlank` | Chave do processo. |
| `businessKey` | string | Não | — | Business key a associar. |
| `applicationBase` | string | Sim | `@NotBlank` | Aplicação base. |
| `priority` | number (Integer) | Não | — | Prioridade da instância. |
| `variables` | `ProcessVariableDTO[]` | Não | `@Valid` | Variáveis iniciais. Default: lista vazia. |
| `assignmentRules` | `ProcessTaskAssignmentRuleDTO[]` | Não | `@Valid` | Regras de atribuição de tarefas. Default: lista vazia. |

**Resposta** — `200 OK`, `ProcessInstanceDTO` (ver Modelos de dados).

#### `POST /process-instances/{id}/start`

Arranca uma instância de processo previamente criada, identificada por `id`, fornecendo variáveis e regras de atribuição.

**Parâmetros de caminho**

| Nome | Tipo | Descrição |
|------|------|-----------|
| `id` | string | Identificador da instância de processo a arrancar. |

**Corpo do pedido** — `ProcessVariablesRequestDTO`

```json
{
  "variables": [{ "name": "valorPedido", "value": 1500 }],
  "assignmentRules": [
    {
      "taskKey": "analise-tecnica",
      "assignee": "joao.silva",
      "candidateUsers": null,
      "candidateGroups": "tecnicos",
      "assignmentMode": "ONE_TIME",
      "priority": 10
    }
  ]
}
```

| Campo | Tipo | Obrigatório | Validação | Descrição |
|-------|------|-------------|-----------|-----------|
| `variables` | `ProcessVariableDTO[]` | Não | `@Valid` | Variáveis a injetar no arranque. Default: lista vazia. |
| `assignmentRules` | `ProcessTaskAssignmentRuleDTO[]` | Não | `@Valid` | Regras de atribuição de tarefas. Default: lista vazia. |

**Resposta** — `200 OK`, `ProcessInstanceDTO` (ver Modelos de dados).

#### `POST /process-instances/{id}/timer/reschedule`

Reagenda um timer de uma instância de processo, definindo o número de segundos até disparar.

**Parâmetros de caminho**

| Nome | Tipo | Descrição |
|------|------|-----------|
| `id` | string | Identificador da instância de processo. |

**Corpo do pedido** — `TimerRescheduleDTO`

```json
{
  "elementId": "Timer_1",
  "seconds": 3600
}
```

| Campo | Tipo | Obrigatório | Validação | Descrição |
|-------|------|-------------|-----------|-----------|
| `elementId` | string | Não | — | ID do elemento de timer no modelo BPMN. |
| `seconds` | number (Long) | Sim | `@NotNull` | Segundos até ao disparo do timer. |

**Resposta** — `204 No Content` (declarado no `@ApiResponse`). O método devolve um `ResponseEntity<String>`, pelo que pode existir corpo de texto consoante o handler.

#### Modelos de dados (DTOs)

#### `VariablesFilterDTO`

| Campo (JSON) | Tipo | Obrigatório | Validação | Descrição |
|--------------|------|-------------|-----------|-----------|
| `variables` | `VariablesExpressionDTO[]` | Não | `@Valid` | Lista de expressões de filtro. Default: `[]`. |

#### `VariablesExpressionDTO`

| Campo (JSON) | Tipo | Obrigatório | Validação | Descrição |
|--------------|------|-------------|-----------|-----------|
| `name` | string | Sim | `@NotBlank` | Nome da variável a filtrar. |
| `operator` | enum `VaribalesOperator` | Sim | `@NotNull` | Operador de comparação. Valores: `EQUALS`, `EQUALS_IGNORE_CASE`, `NOT_EQUALS`, `NOT_EQUALS_IGNORE_CASE`, `GREATER_THAN`, `GREATER_THAN_OR_EQUAL`, `LESS_THAN`, `LESS_THAN_OR_EQUAL`, `LIKE`, `LIKE_IGNORE_CASE`. |
| `value` | any (`Object`) | Sim | `@NotNull` | Valor de comparação (tipo arbitrário: string/number/boolean…). |

#### `ProcessInstanceListPageDTO` (estende `PageDTO`)

| Campo (JSON) | Tipo | Obrigatório | Validação | Descrição |
|--------------|------|-------------|-----------|-----------|
| `content` | `ProcessInstanceDTO[]` | Não | `@Valid` | Página de instâncias. Default: `[]`. |
| `pageNumber` | number (Integer) | — | — | (herdado) Índice da página atual. |
| `pageSize` | number (Integer) | — | — | (herdado) Dimensão da página. |
| `totalElements` | number (Long) | — | — | (herdado) Total de elementos. |
| `totalPages` | number (Integer) | — | — | (herdado) Total de páginas. |
| `last` | boolean | — | — | (herdado) Indica se é a última página. |
| `first` | boolean | — | — | (herdado) Indica se é a primeira página. |

#### `PageDTO`

| Campo (JSON) | Tipo | Obrigatório | Validação | Descrição |
|--------------|------|-------------|-----------|-----------|
| `pageNumber` | number (Integer) | Não | — | Índice da página atual. |
| `pageSize` | number (Integer) | Não | — | Dimensão da página. |
| `totalElements` | number (Long) | Não | — | Total de elementos disponíveis. |
| `totalPages` | number (Integer) | Não | — | Total de páginas. |
| `last` | boolean | Não | — | É a última página. Serializa sempre (primitivo). |
| `first` | boolean | Não | — | É a primeira página. Serializa sempre (primitivo). |

#### `ProcessInstanceDTO`

| Campo (JSON) | Tipo | Obrigatório | Validação | Descrição |
|--------------|------|-------------|-----------|-----------|
| `id` | string (UUID) | Não | — | Identificador da instância. |
| `procReleaseKey` | string | Não | — | Chave do release do processo. |
| `procReleaseId` | string | Não | — | ID do release do processo. |
| `number` | string | Não | — | Número da instância. |
| `status` | enum `ProcessInstanceStatus` | Não | — | Estado. Valores: `RUNNING`, `CREATED`, `SUSPENDED`, `CANCELED`, `COMPLETED`. |
| `statusDesc` | string | Não | — | Descrição legível do estado. |
| `businessKey` | string | Não | — | Business key. |
| `version` | string | Não | — | Versão. |
| `startedAt` | string (ISO-8601, `LocalDateTime`) | Não | — | Data/hora de arranque. |
| `startedBy` | string | Não | — | Utilizador que iniciou. |
| `endedAt` | string (ISO-8601, `LocalDateTime`) | Não | — | Data/hora de fim. |
| `endedBy` | string | Não | — | Utilizador que terminou. |
| `canceledAt` | string (ISO-8601, `LocalDateTime`) | Não | — | Data/hora de cancelamento. |
| `cancelledBy` | string | Não | — | Utilizador que cancelou. |
| `obsCancel` | string | Não | — | Observação de cancelamento. |
| `applicationBase` | string | Não | — | Aplicação base. |
| `name` | string | Não | — | Nome da instância. |
| `progress` | string | Não | — | Progresso (ex.: percentagem). |
| `priority` | number (Integer) | Não | — | Prioridade. |
| `variables` | `ProcessVariableDTO[]` | Não | `@Valid` | Variáveis da instância. Default: `[]`. |
| `userProfileStartedBy` | `UserProfileDTO` | Não | `@Valid` | Perfil de quem iniciou. |
| `userProfileEndedBy` | `UserProfileDTO` | Não | `@Valid` | Perfil de quem terminou. |
| `userProfileCancelledBy` | `UserProfileDTO` | Não | `@Valid` | Perfil de quem cancelou. |

#### `ProcessVariableDTO`

(Existem duas classes idênticas, uma em `processruntime.application.dto` e outra em `shared.application.dto`; ambas serializam da mesma forma. Os endpoints `event` usam a versão de `shared`; os restantes usam a de `processruntime`.)

| Campo (JSON) | Tipo | Obrigatório | Validação | Descrição |
|--------------|------|-------------|-----------|-----------|
| `name` | string | Não | — | Nome da variável. |
| `value` | any (`Object`) | Não | — | Valor da variável (tipo arbitrário). |

#### `UserProfileDTO`

| Campo (JSON) | Tipo | Obrigatório | Validação | Descrição |
|--------------|------|-------------|-----------|-----------|
| `id` | string (UUID) | Não | — | Identificador do utilizador. |
| `username` | string | Não | — | Nome de utilizador. |
| `email` | string | Não | — | Email. |
| `firstName` | string | Não | — | Primeiro nome. |
| `lastName` | string | Não | — | Apelido. |
| `fullName` | string | Não | — | Nome completo. |
| `sub` | string | Não | — | Subject (identificador no IdP). |

#### `ProcessInstanceTaskStatusDTO`

| Campo (JSON) | Tipo | Obrigatório | Validação | Descrição |
|--------------|------|-------------|-----------|-----------|
| `taskKey` | string | Sim | `@NotBlank` | Chave da tarefa. |
| `name` | string | Não | — | Nome da tarefa. |
| `status` | enum `TaskInstanceStatus` | Não | — | Estado da tarefa. Valores: `CREATED`, `ASSIGNED`, `SUSPENDED`, `COMPLETED`, `CANCELED`. |

#### `ProcessInstanceStatsDTO`

| Campo (JSON) | Tipo | Obrigatório | Validação | Descrição |
|--------------|------|-------------|-----------|-----------|
| `totalProcessInstances` | number (Long) | Não | — | Total de instâncias. |
| `totalCreatedProcess` | number (Long) | Não | — | Total no estado `CREATED`. |
| `totalRunningProcess` | number (Long) | Não | — | Total no estado `RUNNING`. |
| `totalCompletedProcess` | number (Long) | Não | — | Total no estado `COMPLETED`. |
| `totalSuspendedProcess` | number (Long) | Não | — | Total no estado `SUSPENDED`. |
| `totalCanceledProcess` | number (Long) | Não | — | Total no estado `CANCELED`. |

#### `ConfigParameterDTO`

| Campo (JSON) | Tipo | Obrigatório | Validação | Descrição |
|--------------|------|-------------|-----------|-----------|
| `label` | string | Sim | `@NotBlank` | Rótulo legível. |
| `value` | string | Sim | `@NotBlank` | Valor/código. |

#### `ProcessEventDTO`

| Campo (JSON) | Tipo | Obrigatório | Validação | Descrição |
|--------------|------|-------------|-----------|-----------|
| `messageName` | string | Não | — | Nome da mensagem/evento. |
| `taskId` | string | Não | — | ID da tarefa alvo. |
| `businessKey` | string | Não | — | Business key alvo. |
| `variables` | `ProcessVariableDTO[]` (de `shared`) | Não | `@Valid` | Variáveis do evento. Default: `[]`. |

#### `CreateProcessRequestDTO`

| Campo (JSON) | Tipo | Obrigatório | Validação | Descrição |
|--------------|------|-------------|-----------|-----------|
| `processDefinitionId` | string | Não | — | ID da definição do processo. |
| `processKey` | string | Sim | `@NotBlank` | Chave do processo. |
| `applicationBase` | string | Sim | `@NotBlank` | Aplicação base. |
| `businessKey` | string | Não | — | Business key. |
| `priority` | number (Integer) | Não | — | Prioridade. |

#### `StartProcessRequestDTO`

| Campo (JSON) | Tipo | Obrigatório | Validação | Descrição |
|--------------|------|-------------|-----------|-----------|
| `processDefinitionId` | string | Não | — | ID da definição do processo. |
| `processKey` | string | Sim | `@NotBlank` | Chave do processo. |
| `businessKey` | string | Não | — | Business key. |
| `applicationBase` | string | Sim | `@NotBlank` | Aplicação base. |
| `priority` | number (Integer) | Não | — | Prioridade. |
| `variables` | `ProcessVariableDTO[]` | Não | `@Valid` | Variáveis iniciais. Default: `[]`. |
| `assignmentRules` | `ProcessTaskAssignmentRuleDTO[]` | Não | `@Valid` | Regras de atribuição. Default: `[]`. |

#### `ProcessVariablesRequestDTO`

| Campo (JSON) | Tipo | Obrigatório | Validação | Descrição |
|--------------|------|-------------|-----------|-----------|
| `variables` | `ProcessVariableDTO[]` | Não | `@Valid` | Variáveis. Default: `[]`. |
| `assignmentRules` | `ProcessTaskAssignmentRuleDTO[]` | Não | `@Valid` | Regras de atribuição. Default: `[]`. |

#### `ProcessTaskAssignmentRuleDTO`

| Campo (JSON) | Tipo | Obrigatório | Validação | Descrição |
|--------------|------|-------------|-----------|-----------|
| `taskKey` | string | Sim | `@NotBlank` | Chave da tarefa. |
| `assignee` | string | Não | — | Utilizador atribuído. |
| `candidateUsers` | string | Não | — | Utilizadores candidatos. |
| `candidateGroups` | string | Não | — | Grupos candidatos. |
| `assignmentMode` | enum `TaskAssignmentMode` | Não | — | Modo de atribuição. Valores: `ALWAYS`, `ONE_TIME` (desserialização via `@JsonCreator` case-insensitive, com `trim`; valor vazio/nulo → `null`). |
| `priority` | number (Integer) | Não | — | Prioridade da regra. |

#### `TimerRescheduleDTO`

| Campo (JSON) | Tipo | Obrigatório | Validação | Descrição |
|--------------|------|-------------|-----------|-----------|
| `elementId` | string | Não | — | ID do elemento de timer (BPMN). |
| `seconds` | number (Long) | Sim | `@NotNull` | Segundos até ao disparo. |

#### Enums de referência

- **`ProcessInstanceStatus`**: `RUNNING`, `CREATED`, `SUSPENDED`, `CANCELED`, `COMPLETED`.
- **`TaskInstanceStatus`**: `CREATED`, `ASSIGNED`, `SUSPENDED`, `COMPLETED`, `CANCELED`.
- **`VaribalesOperator`**: `EQUALS`, `EQUALS_IGNORE_CASE`, `NOT_EQUALS`, `NOT_EQUALS_IGNORE_CASE`, `GREATER_THAN`, `GREATER_THAN_OR_EQUAL`, `LESS_THAN`, `LESS_THAN_OR_EQUAL`, `LIKE`, `LIKE_IGNORE_CASE`.
- **`TaskAssignmentMode`**: `ALWAYS`, `ONE_TIME`.
- **`VariableType`** (não usado diretamente por estes endpoints): `STRING`, `INTEGER`, `LONG`, `DOUBLE`, `BOOLEAN`, `JSON`.
- **`VariableTag`** (não usado diretamente por estes endpoints): constantes `FORMS`, `VARIABLES`, com códigos serializados em minúsculas `forms`, `variables`.

### Tarefas (Task Instances)

Módulo de gestão de instâncias de tarefas do runtime de processos. Permite listar/pesquisar tarefas, obter detalhe, assumir (claim), libertar (unclaim), atribuir, guardar e concluir tarefas, consultar variáveis/formulários e estatísticas, bem como gerir as regras de atribuição (assignment rules). Rota base do controller: **`/tasks-instances`** (a app não tem context-path; o caminho completo de cada endpoint é `/tasks-instances` + o sufixo do método).

> Nota de rigor: a rota base real declarada no `@RequestMapping` é `tasks-instances` (e não `/task-instances`). Os caminhos abaixo refletem o código.

---

#### `POST /tasks-instances/search`

Pesquisa/lista paginada de instâncias de tarefas, aplicando filtros de query e um filtro por variáveis no corpo.

#### Parâmetros de query

| Nome | Tipo | Obrig. | Descrição |
|---|---|---|---|
| processInstanceId | string | Não | Filtra pelo ID da instância de processo. |
| processNumber | string | Não | Filtra pelo número do processo. |
| processReleaseKey | string | Não | Filtra pela release key do processo. |
| applicationBase | string | Não | Filtra pela aplicação base. |
| candidateGroups | string | Não | Filtra por grupos candidatos. |
| candidateUsers | string | Não | Filtra por utilizadores candidatos. |
| user | string | Não | Filtra pelo utilizador. |
| status | string | Não | Filtra pelo estado da tarefa (ver `TaskInstanceStatus`). |
| dateFrom | string | Não | Data inicial do intervalo (formato string conforme aceite pelo backend). |
| dateTo | string | Não | Data final do intervalo. |
| page | number | Não | Número da página (paginação). |
| size | number | Não | Dimensão da página (paginação). |
| name | string | Não | Filtra pelo nome da tarefa. |
| processName | string | Não | Filtra pelo nome do processo. |
| filterByCurrentUser | boolean | Não | Se `true`, restringe ao utilizador atual. Tipo primitivo `boolean` (default `false`). |
| priority | number | Não | Filtra pela prioridade. |

#### Corpo do pedido

`VariablesFilterDTO`

```json
{
  "variables": [
    { "name": "regiao", "operator": "EQUALS", "value": "Sotavento" },
    { "name": "montante", "operator": "GREATER_THAN_OR_EQUAL", "value": 1000 }
  ]
}
```

| Campo | Tipo | Obrigatório | Descrição |
|---|---|---|---|
| variables | `VariablesExpressionDTO[]` | Não (default `[]`) | Lista de expressões de filtro por variáveis (validada com `@Valid`). |

#### Resposta

**200** — `TaskInstanceListPageDTO` (envelope paginado, herda `PageDTO`).

```json
{
  "pageNumber": 0,
  "pageSize": 20,
  "totalElements": 42,
  "totalPages": 3,
  "last": false,
  "first": true,
  "content": [
    {
      "id": "6d3f6c2a-1f2b-4f1a-9a2e-0c1b2d3e4f5a",
      "taskKey": "approveRequest",
      "formKey": "approveForm",
      "name": "Aprovar pedido",
      "candidateGroups": "managers",
      "candidateUsers": "jdoe",
      "processInstanceId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
      "processNumber": "PN-2026-0042",
      "processName": "Pedido de Licença",
      "processKey": "licenseRequest",
      "businessKey": "BK-0042",
      "priority": 50,
      "assignedBy": "admin",
      "assignedAt": "2026-06-30T10:15:00",
      "startedBy": "system",
      "startedAt": "2026-06-30T09:00:00",
      "endedBy": null,
      "endedAt": null,
      "status": "ASSIGNED",
      "statusDesc": "Atribuido",
      "variables": [{ "name": "regiao", "value": "Sotavento" }],
      "applicationBase": "rgph",
      "forms": [],
      "processVariables": [],
      "dueDate": "2026-07-05T17:00:00",
      "userProfileEndedBy": null,
      "userProfileAssignedBy": { "id": "...", "username": "admin", "email": "admin@x.cv", "firstName": "Admin", "lastName": "User", "fullName": "Admin User", "sub": "..." },
      "userProfileStartedBy": null
    }
  ]
}
```

---

#### `GET /tasks-instances/{id}`

Obtém o detalhe de uma instância de tarefa pelo seu ID.

#### Parâmetros de caminho

| Nome | Tipo | Descrição |
|---|---|---|
| id | string | Identificador da instância de tarefa. |

#### Resposta

**200** — `TaskInstanceDTO`.

```json
{
  "id": "6d3f6c2a-1f2b-4f1a-9a2e-0c1b2d3e4f5a",
  "taskKey": "approveRequest",
  "formKey": "approveForm",
  "name": "Aprovar pedido",
  "externalId": "ext-123",
  "candidateGroups": "managers",
  "candidateUsers": "jdoe",
  "processInstanceId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "processKey": "licenseRequest",
  "processNumber": "PN-2026-0042",
  "businessKey": "BK-0042",
  "processName": "Pedido de Licença",
  "applicationBase": "rgph",
  "assignedAt": "2026-06-30T10:15:00",
  "assignedBy": "admin",
  "searchTerms": "licenca pedido",
  "priority": 50,
  "startedAt": "2026-06-30T09:00:00",
  "startedBy": "system",
  "status": "ASSIGNED",
  "statusDesc": "Atribuido",
  "endedAt": null,
  "endedBy": null,
  "taskInstanceEvents": [
    {
      "id": "f1e2d3c4-...",
      "taskInstanceId": "6d3f6c2a-...",
      "eventType": "CLAIM",
      "performedAt": "2026-06-30T10:15:00",
      "performedBy": "jdoe",
      "obs": null,
      "status": "ASSIGNED",
      "userProfilePerformedBy": null
    }
  ],
  "variables": [{ "name": "regiao", "value": "Sotavento" }],
  "forms": [],
  "processVariables": [],
  "dueDate": "2026-07-05T17:00:00",
  "userProfileAssignedBy": null,
  "userProfileEndedBy": null,
  "userProfileStartedBy": null
}
```

---

#### `POST /tasks-instances/{id}/claim`

Assume (claim) a tarefa indicada para o utilizador autenticado.

#### Parâmetros de caminho

| Nome | Tipo | Descrição |
|---|---|---|
| id | string | Identificador da instância de tarefa. |

#### Resposta

**204** — Sem corpo (No Content).

---

#### `POST /tasks-instances/{id}/unclaim`

Liberta (unclaim) a tarefa indicada, opcionalmente com uma nota.

#### Parâmetros de caminho

| Nome | Tipo | Descrição |
|---|---|---|
| id | string | Identificador da instância de tarefa. |

#### Corpo do pedido

`UnclaimTaskDTO`

```json
{ "note": "Não consigo concluir esta tarefa esta semana." }
```

| Campo | Tipo | Obrigatório | Descrição |
|---|---|---|---|
| note | string | Não | Nota/observação associada à libertação. |

#### Resposta

**204** — Sem corpo (No Content).

---

#### `POST /tasks-instances/{id}/assign`

Atribui a tarefa a um utilizador, podendo ajustar prioridade, nota e grupos/utilizadores candidatos.

#### Parâmetros de caminho

| Nome | Tipo | Descrição |
|---|---|---|
| id | string | Identificador da instância de tarefa. |

#### Corpo do pedido

`AssignTaskDTO`

```json
{
  "user": "jdoe",
  "priority": 70,
  "note": "Reatribuído ao gestor regional.",
  "candidateGroups": "managers",
  "candidateUsers": "jdoe,asilva"
}
```

| Campo | Tipo | Obrigatório | Validação | Descrição |
|---|---|---|---|---|
| user | string | Não | `@Size(min = 0)` | Utilizador a quem a tarefa é atribuída. |
| priority | number | Não | — | Nova prioridade da tarefa. |
| note | string | Não | — | Nota/observação. |
| candidateGroups | string | Não | — | Grupos candidatos. |
| candidateUsers | string | Não | — | Utilizadores candidatos. |

#### Resposta

**204** — Sem corpo (No Content).

---

#### `POST /tasks-instances/{id}/complete`

Conclui a tarefa, submetendo variáveis e dados de formulário.

#### Parâmetros de caminho

| Nome | Tipo | Descrição |
|---|---|---|
| id | string | Identificador da instância de tarefa. |

#### Corpo do pedido

`TaskDataDTO`

```json
{
  "variables": [{ "name": "decisao", "value": "APROVADO" }],
  "forms": [{ "name": "comentario", "value": "Tudo conforme." }]
}
```

| Campo | Tipo | Obrigatório | Descrição |
|---|---|---|---|
| variables | `ProcessVariableDTO[]` | Não (default `[]`) | Variáveis de processo a definir (validado com `@Valid`). |
| forms | `TaskVariableDTO[]` | Não (default `[]`) | Dados de formulário da tarefa (validado com `@Valid`). |

#### Resposta

**200** — `TaskInstanceDTO` (mesma forma do endpoint `GET /tasks-instances/{id}`).

---

#### `POST /tasks-instances/me`

Lista paginada das tarefas do utilizador autenticado, com filtros de query e filtro por variáveis no corpo.

#### Parâmetros de query

| Nome | Tipo | Obrig. | Descrição |
|---|---|---|---|
| processInstanceId | string | Não | Filtra pelo ID da instância de processo. |
| processNumber | string | Não | Filtra pelo número do processo. |
| applicationBase | string | Não | Filtra pela aplicação base. |
| processName | string | Não | Filtra pelo nome do processo. |
| status | string | Não | Filtra pelo estado (ver `TaskInstanceStatus`). |
| dateFrom | string | Não | Data inicial do intervalo. |
| dateTo | string | Não | Data final do intervalo. |
| page | number | Não | Número da página (paginação). |
| size | number | Não | Dimensão da página (paginação). |
| processReleaseKey | string | Não | Filtra pela release key do processo. |
| name | string | Não | Filtra pelo nome da tarefa. |
| priority | number | Não | Filtra pela prioridade. |

#### Corpo do pedido

`VariablesFilterDTO` (igual ao de `POST /tasks-instances/search`).

```json
{ "variables": [{ "name": "regiao", "operator": "EQUALS", "value": "Sotavento" }] }
```

#### Resposta

**200** — `TaskInstanceListPageDTO` (mesmo envelope paginado de `POST /tasks-instances/search`).

---

#### `GET /tasks-instances/status`

Lista os estados possíveis de uma instância de tarefa, como pares label/value.

#### Resposta

**200** — `ConfigParameterDTO[]`.

```json
[
  { "label": "Criado", "value": "CREATED" },
  { "label": "Atribuido", "value": "ASSIGNED" },
  { "label": "Suspenso", "value": "SUSPENDED" },
  { "label": "Completo", "value": "COMPLETED" },
  { "label": "Cancelado", "value": "CANCELED" }
]
```

---

#### `GET /tasks-instances/event_type`

Lista os tipos de evento possíveis de uma instância de tarefa, como pares label/value.

#### Resposta

**200** — `ConfigParameterDTO[]`.

```json
[
  { "label": "Criar", "value": "CREATE" },
  { "label": "Assumir", "value": "CLAIM" },
  { "label": "Atribuir", "value": "ASSIGN" },
  { "label": "Libertar", "value": "UNCLAIM" },
  { "label": "Terminar", "value": "COMPLETE" }
]
```

---

#### `GET /tasks-instances/{id}/variables`

Obtém as variáveis e os formulários associados a uma instância de tarefa.

#### Parâmetros de caminho

| Nome | Tipo | Descrição |
|---|---|---|
| id | string | Identificador da instância de tarefa. |

#### Resposta

**200** — `TaskVariablesFormsDTO`.

```json
{
  "variables": [{ "name": "regiao", "value": "Sotavento" }],
  "forms": [{ "name": "comentario", "value": "Tudo conforme." }]
}
```

---

#### `GET /tasks-instances/stats`

Obtém estatísticas globais de instâncias de tarefas.

#### Resposta

**200** — `TaskInstanceStatsDTO`.

```json
{
  "totalTaskInstances": 1200,
  "totalAvailableTasks": 300,
  "totalAssignedTasks": 450,
  "totalSuspendedTasks": 50,
  "totalCompletedTasks": 380,
  "totalCanceledTasks": 20
}
```

---

#### `GET /tasks-instances/stats/me`

Obtém as estatísticas de instâncias de tarefas do utilizador autenticado.

#### Resposta

**200** — `TaskInstanceStatsDTO` (mesma forma de `GET /tasks-instances/stats`).

---

#### `GET /tasks-instances/assignment-rules`

Lista paginada das regras de atribuição de tarefas, com diversos filtros de query.

#### Parâmetros de query

| Nome | Tipo | Obrig. | Descrição |
|---|---|---|---|
| processInstanceId | string | Não | Filtra pelo ID da instância de processo. |
| processDefinitionKey | string | Não | Filtra pela definição de processo. |
| taskDefinitionKey | string | Não | Filtra pela definição de tarefa. |
| assignee | string | Não | Filtra pelo responsável atribuído. |
| candidateUsers | string | Não | Filtra por utilizadores candidatos. |
| candidateGroups | string | Não | Filtra por grupos candidatos. |
| assignmentMode | string (enum) | Não | Modo de atribuição. Valores: `ALWAYS`, `ONE_TIME` (case-insensitive na desserialização). |
| consumed | boolean | Não | Filtra por regras consumidas. |
| active | boolean | Não | Filtra por regras ativas. |
| createdByTask | string | Não | Filtra pela tarefa que criou a regra. |
| page | number | Não | Número da página (paginação). |
| size | number | Não | Dimensão da página (paginação). |

#### Resposta

**200** — `TaskAssignmentRuleListPageDTO` (envelope paginado, herda `PageDTO`).

```json
{
  "pageNumber": 0,
  "pageSize": 20,
  "totalElements": 5,
  "totalPages": 1,
  "last": true,
  "first": true,
  "content": [
    {
      "id": "9a8b7c6d-5e4f-3a2b-1c0d-9e8f7a6b5c4d",
      "processDefinitionKey": "licenseRequest",
      "processInstanceId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
      "taskDefinitionKey": "approveRequest",
      "assignee": "jdoe",
      "candidateUsers": "jdoe,asilva",
      "candidateGroups": "managers",
      "assignmentMode": "ALWAYS",
      "priority": 50,
      "consumed": false,
      "active": true,
      "createdByTask": "6d3f6c2a-1f2b-4f1a-9a2e-0c1b2d3e4f5a"
    }
  ]
}
```

---

#### `PUT /tasks-instances/assignment-rules/{id}`

Atualiza uma regra de atribuição de tarefa (responsável e candidatos).

#### Parâmetros de caminho

| Nome | Tipo | Descrição |
|---|---|---|
| id | string | Identificador da regra de atribuição. |

#### Corpo do pedido

`TaskAssignmentRuleUpdateDTO`

```json
{
  "assignee": "asilva",
  "candidateUsers": "asilva,mlopes",
  "candidateGroups": "managers"
}
```

| Campo | Tipo | Obrigatório | Descrição |
|---|---|---|---|
| assignee | string | Não | Novo responsável atribuído. |
| candidateUsers | string | Não | Novos utilizadores candidatos. |
| candidateGroups | string | Não | Novos grupos candidatos. |

#### Resposta

**200** — `TaskAssignmentRuleListDTO` (mesma forma dos itens de `content` em `GET /tasks-instances/assignment-rules`).

---

#### `DELETE /tasks-instances/assignment-rules/{id}`

Elimina uma regra de atribuição de tarefa.

#### Parâmetros de caminho

| Nome | Tipo | Descrição |
|---|---|---|
| id | string | Identificador da regra de atribuição. |

#### Resposta

**204** — Sem corpo (No Content).

---

#### `POST /tasks-instances/{id}/save`

Guarda (sem concluir) as variáveis e dados de formulário de uma instância de tarefa.

#### Parâmetros de caminho

| Nome | Tipo | Descrição |
|---|---|---|
| id | string | Identificador da instância de tarefa. |

#### Corpo do pedido

`TaskDataDTO` (igual ao de `POST /tasks-instances/{id}/complete`).

```json
{
  "variables": [{ "name": "rascunho", "value": "em curso" }],
  "forms": [{ "name": "comentario", "value": "A rever." }]
}
```

#### Resposta

**200** — `TaskInstanceDTO` (mesma forma do endpoint `GET /tasks-instances/{id}`).

---

#### Modelos de dados (DTOs)

#### `VariablesFilterDTO`

| Campo (JSON) | Tipo | Obrigatório | Validação | Descrição |
|---|---|---|---|---|
| variables | `VariablesExpressionDTO[]` | Não | `@Valid` (cascata); default `[]` | Lista de expressões de filtro por variáveis. |

#### `VariablesExpressionDTO`

| Campo (JSON) | Tipo | Obrigatório | Validação | Descrição |
|---|---|---|---|---|
| name | string | Sim | `@NotBlank` | Nome da variável. |
| operator | string (enum) | Sim | `@NotNull` | Operador de comparação. Ver `VaribalesOperator`. |
| value | any (objeto) | Sim | `@NotNull` | Valor a comparar (qualquer tipo JSON: string/number/boolean/objeto). |

#### `TaskInstanceListPageDTO` (herda `PageDTO`)

| Campo (JSON) | Tipo | Obrigatório | Validação | Descrição |
|---|---|---|---|---|
| pageNumber | number | Não | — | Número da página atual (herdado). |
| pageSize | number | Não | — | Dimensão da página (herdado). |
| totalElements | number | Não | — | Total de elementos (herdado). |
| totalPages | number | Não | — | Total de páginas (herdado). |
| last | boolean | Não | — | Indica se é a última página (herdado). |
| first | boolean | Não | — | Indica se é a primeira página (herdado). |
| content | `TaskInstanceListDTO[]` | Não | `@Valid`; default `[]` | Itens da página. |

#### `TaskInstanceListDTO`

| Campo (JSON) | Tipo | Obrigatório | Validação | Descrição |
|---|---|---|---|---|
| id | string (UUID) | Não | — | Identificador da tarefa. |
| taskKey | string | Não | — | Chave da tarefa. |
| formKey | string | Não | — | Chave do formulário. |
| name | string | Não | — | Nome da tarefa. |
| candidateGroups | string | Não | — | Grupos candidatos. |
| candidateUsers | string | Não | — | Utilizadores candidatos. |
| processInstanceId | string | Não | — | ID da instância de processo. |
| processNumber | string | Não | — | Número do processo. |
| processName | string | Não | — | Nome do processo. |
| processKey | string | Não | — | Chave do processo. |
| businessKey | string | Não | — | Business key. |
| priority | number | Não | — | Prioridade. |
| assignedBy | string | Não | — | Quem atribuiu. |
| assignedAt | string (ISO-8601 date-time) | Não | — | Data/hora de atribuição. |
| startedBy | string | Não | — | Quem iniciou. |
| startedAt | string (ISO-8601 date-time) | Não | — | Data/hora de início. |
| endedBy | string | Não | — | Quem terminou. |
| endedAt | string (ISO-8601 date-time) | Não | — | Data/hora de fim. |
| status | string (enum) | Não | — | Estado da tarefa. Ver `TaskInstanceStatus`. |
| statusDesc | string | Não | — | Descrição do estado. |
| variables | `ProcessVariableDTO[]` | Não | `@Valid`; default `[]` | Variáveis. |
| applicationBase | string | Não | — | Aplicação base. |
| forms | `ProcessVariableDTO[]` | Não | `@Valid`; default `[]` | Formulários. |
| processVariables | `ProcessVariableDTO[]` | Não | `@Valid`; default `[]` | Variáveis do processo. |
| dueDate | string (ISO-8601 date-time) | Não | — | Data limite. |
| userProfileEndedBy | `UserProfileDTO` | Não | `@Valid` | Perfil de quem terminou. |
| userProfileAssignedBy | `UserProfileDTO` | Não | `@Valid` | Perfil de quem atribuiu. |
| userProfileStartedBy | `UserProfileDTO` | Não | `@Valid` | Perfil de quem iniciou. |

#### `TaskInstanceDTO`

| Campo (JSON) | Tipo | Obrigatório | Validação | Descrição |
|---|---|---|---|---|
| id | string (UUID) | Não | — | Identificador da tarefa. |
| taskKey | string | Não | — | Chave da tarefa. |
| formKey | string | Não | — | Chave do formulário. |
| name | string | Não | — | Nome da tarefa. |
| externalId | string | Não | — | ID externo (motor de processos). |
| candidateGroups | string | Não | — | Grupos candidatos. |
| candidateUsers | string | Não | — | Utilizadores candidatos. |
| processInstanceId | string (UUID) | Não | — | ID da instância de processo. |
| processKey | string | Não | — | Chave do processo. |
| processNumber | string | Não | — | Número do processo. |
| businessKey | string | Não | — | Business key. |
| processName | string | Não | — | Nome do processo. |
| applicationBase | string | Não | — | Aplicação base. |
| assignedAt | string (ISO-8601 date-time) | Não | — | Data/hora de atribuição. |
| assignedBy | string | Não | — | Quem atribuiu. |
| searchTerms | string | Não | — | Termos de pesquisa indexados. |
| priority | number | Não | — | Prioridade. |
| startedAt | string (ISO-8601 date-time) | Não | — | Data/hora de início. |
| startedBy | string | Não | — | Quem iniciou. |
| status | string (enum) | Não | — | Estado da tarefa. Ver `TaskInstanceStatus`. |
| statusDesc | string | Não | — | Descrição do estado. |
| endedAt | string (ISO-8601 date-time) | Não | — | Data/hora de fim. |
| endedBy | string | Não | — | Quem terminou. |
| taskInstanceEvents | `TaskInstanceEventListDTO[]` | Não | `@Valid`; default `[]` | Histórico de eventos da tarefa. |
| variables | `ProcessVariableDTO[]` | Não | `@Valid`; default `[]` | Variáveis. |
| forms | `ProcessVariableDTO[]` | Não | `@Valid`; default `[]` | Formulários. |
| processVariables | `ProcessVariableDTO[]` | Não | `@Valid`; default `[]` | Variáveis do processo. |
| dueDate | string (ISO-8601 date-time) | Não | — | Data limite. |
| userProfileAssignedBy | `UserProfileDTO` | Não | `@Valid` | Perfil de quem atribuiu. |
| userProfileEndedBy | `UserProfileDTO` | Não | `@Valid` | Perfil de quem terminou. |
| userProfileStartedBy | `UserProfileDTO` | Não | `@Valid` | Perfil de quem iniciou. |

#### `TaskInstanceEventListDTO`

| Campo (JSON) | Tipo | Obrigatório | Validação | Descrição |
|---|---|---|---|---|
| id | string (UUID) | Não | — | Identificador do evento. |
| taskInstanceId | string (UUID) | Não | — | ID da instância de tarefa. |
| eventType | string | Não | — | Tipo de evento (valores em `TaskEventType`: `CREATE`, `CLAIM`, `ASSIGN`, `UNCLAIM`, `COMPLETE`). Serializado como string livre. |
| performedAt | string (ISO-8601 date-time) | Não | — | Data/hora do evento. |
| performedBy | string | Não | — | Quem realizou o evento. |
| obs | string | Não | — | Observação. |
| status | string (enum) | Não | — | Estado resultante. Ver `TaskInstanceStatus`. |
| userProfilePerformedBy | `UserProfileDTO` | Não | `@Valid` | Perfil de quem realizou. |

#### `UserProfileDTO`

| Campo (JSON) | Tipo | Obrigatório | Validação | Descrição |
|---|---|---|---|---|
| id | string (UUID) | Não | — | Identificador do utilizador. |
| username | string | Não | — | Nome de utilizador. |
| email | string | Não | — | Email. |
| firstName | string | Não | — | Primeiro nome. |
| lastName | string | Não | — | Apelido. |
| fullName | string | Não | — | Nome completo. |
| sub | string | Não | — | Subject (identificador do IdP). |

#### `UnclaimTaskDTO`

| Campo (JSON) | Tipo | Obrigatório | Validação | Descrição |
|---|---|---|---|---|
| note | string | Não | — | Nota associada à libertação. |

#### `AssignTaskDTO`

| Campo (JSON) | Tipo | Obrigatório | Validação | Descrição |
|---|---|---|---|---|
| user | string | Não | `@Size(min = 0)` | Utilizador a quem atribuir. |
| priority | number | Não | — | Prioridade. |
| note | string | Não | — | Nota. |
| candidateGroups | string | Não | — | Grupos candidatos. |
| candidateUsers | string | Não | — | Utilizadores candidatos. |

#### `TaskDataDTO`

| Campo (JSON) | Tipo | Obrigatório | Validação | Descrição |
|---|---|---|---|---|
| variables | `ProcessVariableDTO[]` | Não | `@Valid`; default `[]` | Variáveis de processo a submeter. |
| forms | `TaskVariableDTO[]` | Não | `@Valid`; default `[]` | Dados de formulário. |

#### `TaskVariablesFormsDTO`

| Campo (JSON) | Tipo | Obrigatório | Validação | Descrição |
|---|---|---|---|---|
| variables | `TaskVariableDTO[]` | Não | `@Valid`; default `[]` | Variáveis da tarefa. |
| forms | `TaskVariableDTO[]` | Não | `@Valid`; default `[]` | Formulários da tarefa. |

#### `ProcessVariableDTO` (pacote `processruntime`)

| Campo (JSON) | Tipo | Obrigatório | Validação | Descrição |
|---|---|---|---|---|
| name | string | Não | — | Nome da variável. |
| value | any (objeto) | Não | — | Valor (qualquer tipo JSON). |

#### `TaskVariableDTO`

| Campo (JSON) | Tipo | Obrigatório | Validação | Descrição |
|---|---|---|---|---|
| name | string | Não | — | Nome da variável. |
| value | any (objeto) | Não | — | Valor (qualquer tipo JSON). |

#### `TaskInstanceStatsDTO`

| Campo (JSON) | Tipo | Obrigatório | Validação | Descrição |
|---|---|---|---|---|
| totalTaskInstances | number | Não | — | Total de instâncias de tarefas. |
| totalAvailableTasks | number | Não | — | Total de tarefas disponíveis. |
| totalAssignedTasks | number | Não | — | Total de tarefas atribuídas. |
| totalSuspendedTasks | number | Não | — | Total de tarefas suspensas. |
| totalCompletedTasks | number | Não | — | Total de tarefas concluídas. |
| totalCanceledTasks | number | Não | — | Total de tarefas canceladas. |

#### `TaskAssignmentRuleListPageDTO` (herda `PageDTO`)

| Campo (JSON) | Tipo | Obrigatório | Validação | Descrição |
|---|---|---|---|---|
| pageNumber | number | Não | — | Número da página atual (herdado). |
| pageSize | number | Não | — | Dimensão da página (herdado). |
| totalElements | number | Não | — | Total de elementos (herdado). |
| totalPages | number | Não | — | Total de páginas (herdado). |
| last | boolean | Não | — | Última página? (herdado). |
| first | boolean | Não | — | Primeira página? (herdado). |
| content | `TaskAssignmentRuleListDTO[]` | Não | `@Valid`; default `[]` | Itens da página. |

#### `TaskAssignmentRuleListDTO`

| Campo (JSON) | Tipo | Obrigatório | Validação | Descrição |
|---|---|---|---|---|
| id | string (UUID) | Não | — | Identificador da regra. |
| processDefinitionKey | string | Não | — | Definição de processo. |
| processInstanceId | string (UUID) | Não | — | Instância de processo. |
| taskDefinitionKey | string | Não | — | Definição de tarefa. |
| assignee | string | Não | — | Responsável atribuído. |
| candidateUsers | string | Não | — | Utilizadores candidatos. |
| candidateGroups | string | Não | — | Grupos candidatos. |
| assignmentMode | string (enum) | Não | — | Modo de atribuição. Ver `TaskAssignmentMode`. |
| priority | number | Não | — | Prioridade. |
| consumed | boolean | Não | — | Indica se a regra já foi consumida. |
| active | boolean | Não | — | Indica se a regra está ativa. |
| createdByTask | string (UUID) | Não | — | Tarefa que criou a regra. |

#### `TaskAssignmentRuleUpdateDTO`

| Campo (JSON) | Tipo | Obrigatório | Validação | Descrição |
|---|---|---|---|---|
| assignee | string | Não | — | Novo responsável atribuído. |
| candidateUsers | string | Não | — | Novos utilizadores candidatos. |
| candidateGroups | string | Não | — | Novos grupos candidatos. |

#### `ConfigParameterDTO` (partilhado)

| Campo (JSON) | Tipo | Obrigatório | Validação | Descrição |
|---|---|---|---|---|
| label | string | Sim | `@NotBlank` | Etiqueta apresentável (descrição). |
| value | string | Sim | `@NotBlank` | Valor (código). |

#### `PageDTO` (partilhado — superclasse dos envelopes paginados)

| Campo (JSON) | Tipo | Obrigatório | Validação | Descrição |
|---|---|---|---|---|
| pageNumber | number | Não | — | Número da página atual. |
| pageSize | number | Não | — | Dimensão da página. |
| totalElements | number | Não | — | Total de elementos. |
| totalPages | number | Não | — | Total de páginas. |
| last | boolean | Não | — | Indica se é a última página. |
| first | boolean | Não | — | Indica se é a primeira página. |

#### Enumerações

**`TaskInstanceStatus`** (serializa pelo `code`): `CREATED` (Criado), `ASSIGNED` (Atribuido), `SUSPENDED` (Suspenso), `COMPLETED` (Completo), `CANCELED` (Cancelado).

**`TaskEventType`** (usado em `eventType`): `CREATE` (Criar), `CLAIM` (Assumir), `ASSIGN` (Atribuir), `UNCLAIM` (Libertar), `COMPLETE` (Terminar).

**`TaskAssignmentMode`**: `ALWAYS`, `ONE_TIME`. Desserialização case-insensitive (`@JsonCreator` aceita também minúsculas e espaços nas extremidades, fazendo `trim().toUpperCase()`); `null`/vazio → `null`.

**`VaribalesOperator`** (operadores de filtro de variáveis, serializa pelo `code`): `EQUALS`, `EQUALS_IGNORE_CASE`, `NOT_EQUALS`, `NOT_EQUALS_IGNORE_CASE`, `GREATER_THAN`, `GREATER_THAN_OR_EQUAL`, `LESS_THAN`, `LESS_THAN_OR_EQUAL`, `LIKE`, `LIKE_IGNORE_CASE`.

### Atividades (Activities)

Módulo de consulta de atividades de instâncias de processo. Rota base: `/activities`. Todos os endpoints são de leitura (GET), servidos via `QueryBus`, e não recebem corpo de pedido. A base path da aplicação é `/`, pelo que os caminhos completos começam em `/activities`.

#### `GET /activities/{id}`

Obtém uma atividade pelo seu identificador.

**Parâmetros de caminho**

| Nome | Tipo | Descrição |
|------|------|-----------|
| `id` | string | Identificador da atividade. |

**Resposta**

`200 OK` — `ActivityDTO`.

```json
{
  "id": "a1b2c3d4",
  "name": "Validar Documentos",
  "description": "Tarefa de validação documental do requerente",
  "processInstanceId": "proc-inst-9f8e",
  "parentId": "parent-77aa",
  "parentProcessInstanceId": "proc-inst-parent-12",
  "status": "ACTIVE",
  "type": "userTask",
  "variables": [
    { "name": "prioridade", "value": "ALTA" },
    { "name": "tentativas", "value": 2 }
  ]
}
```

#### `GET /activities/instances`

Lista as instâncias de atividade de um processo, opcionalmente filtradas por tipo.

**Parâmetros de query**

| Nome | Tipo | Obrig. | Descrição |
|------|------|--------|-----------|
| `processIdentifier` | string | Sim | Identificador do processo cujas instâncias de atividade se pretendem listar. |
| `type` | string | Não | Tipo de atividade a filtrar (ex.: `userTask`). No controller é `required = false`. |

Sem paginação.

**Resposta**

`200 OK` — `ActivityDTO[]` (array simples, sem envelope de paginação).

```json
[
  {
    "id": "a1b2c3d4",
    "name": "Validar Documentos",
    "description": "Tarefa de validação documental do requerente",
    "processInstanceId": "proc-inst-9f8e",
    "parentId": "parent-77aa",
    "parentProcessInstanceId": "proc-inst-parent-12",
    "status": "ACTIVE",
    "type": "userTask",
    "variables": [
      { "name": "prioridade", "value": "ALTA" }
    ]
  }
]
```

#### `GET /activities/progress`

Devolve o progresso das atividades de um processo (linha temporal/estado de cada atividade), opcionalmente filtrado por tipo.

**Parâmetros de query**

| Nome | Tipo | Obrig. | Descrição |
|------|------|--------|-----------|
| `processIdentifier` | string | Sim | Identificador do processo. |
| `type` | string | Não | Tipo de atividade a filtrar. No controller é `required = false`. |

Sem paginação.

**Resposta**

`200 OK` — `ActivityProgressDTO[]` (array simples, sem envelope de paginação).

```json
[
  {
    "activityKey": "validarDocumentos",
    "activityName": "Validar Documentos",
    "status": "COMPLETED",
    "type": "userTask",
    "processInstanceId": "proc-inst-9f8e",
    "assignee": "jsilva",
    "candidateUsers": "jsilva,mlopes",
    "candidateGroups": "analistas",
    "startTime": "2026-06-30T09:15:00",
    "endTime": "2026-06-30T09:42:10",
    "durationMillis": 1630000,
    "activityId": "a1b2c3d4",
    "variables": [
      { "name": "prioridade", "value": "ALTA" }
    ],
    "executionId": "exec-5521",
    "taskId": "task-8842",
    "activityInstanceId": "act-inst-0091",
    "treeNumber": "1.2",
    "forms": [
      { "name": "formularioValidacao", "value": "{...}" }
    ],
    "userProfileAssignee": {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "username": "jsilva",
      "email": "jsilva@example.cv",
      "firstName": "João",
      "lastName": "Silva",
      "fullName": "João Silva",
      "sub": "auth0|abc123"
    }
  }
]
```

#### Modelos de dados (DTOs)

#### `ActivityDTO`

| Campo (JSON) | Tipo | Obrigatório | Validação | Descrição |
|--------------|------|-------------|-----------|-----------|
| `id` | string | Não | — | Identificador da atividade. |
| `name` | string | Não | — | Nome da atividade. |
| `description` | string | Não | — | Descrição da atividade. |
| `processInstanceId` | string | Não | — | Identificador da instância de processo associada. |
| `parentId` | string | Não | — | Identificador da atividade-pai. |
| `parentProcessInstanceId` | string | Não | — | Identificador da instância de processo-pai. |
| `status` | string | Não | — | Estado da atividade (campo livre; não tipado como enum no DTO). |
| `type` | string | Não | — | Tipo da atividade (ex.: `userTask`). |
| `variables` | `TaskVariableDTO[]` | Não | `@Valid` (validação em cascata); default `[]` | Lista de variáveis da atividade. |

#### `ActivityProgressDTO`

| Campo (JSON) | Tipo | Obrigatório | Validação | Descrição |
|--------------|------|-------------|-----------|-----------|
| `activityKey` | string | Não | — | Chave (definition key) da atividade. |
| `activityName` | string | Não | — | Nome da atividade. |
| `status` | string | Não | — | Estado da atividade (campo livre; não tipado como enum no DTO). |
| `type` | string | Não | — | Tipo da atividade. |
| `processInstanceId` | string | Não | — | Identificador da instância de processo. |
| `assignee` | string | Não | — | Utilizador atribuído. |
| `candidateUsers` | string | Não | — | Utilizadores candidatos (string, tipicamente separada por vírgulas). |
| `candidateGroups` | string | Não | — | Grupos candidatos (string, tipicamente separada por vírgulas). |
| `startTime` | string (ISO-8601, `LocalDateTime`, sem timezone) | Não | — | Início da atividade. |
| `endTime` | string (ISO-8601, `LocalDateTime`, sem timezone) | Não | — | Fim da atividade. |
| `durationMillis` | integer (`Long`) | Não | — | Duração em milissegundos. |
| `activityId` | string | Não | — | Identificador da atividade. |
| `variables` | `TaskVariableDTO[]` | Não | `@Valid`; default `[]` | Variáveis da atividade. |
| `executionId` | string | Não | — | Identificador de execução. |
| `taskId` | string | Não | — | Identificador da tarefa. |
| `activityInstanceId` | string | Não | — | Identificador da instância da atividade. |
| `treeNumber` | string | Não | — | Número/posição na árvore de atividades. |
| `forms` | `TaskVariableDTO[]` | Não | `@Valid`; default `[]` | Formulários associados (reutiliza a forma de `TaskVariableDTO`). |
| `userProfileAssignee` | `UserProfileDTO` | Não | `@Valid` (validação em cascata) | Perfil do utilizador atribuído. |

#### `TaskVariableDTO`

| Campo (JSON) | Tipo | Obrigatório | Validação | Descrição |
|--------------|------|-------------|-----------|-----------|
| `name` | string | Não | — | Nome da variável. |
| `value` | any (`Object` — string, number, boolean, object ou array conforme a variável) | Não | — | Valor da variável; tipo dinâmico. |

#### `UserProfileDTO`

| Campo (JSON) | Tipo | Obrigatório | Validação | Descrição |
|--------------|------|-------------|-----------|-----------|
| `id` | string (UUID) | Não | — | Identificador do utilizador. |
| `username` | string | Não | — | Nome de utilizador. |
| `email` | string | Não | — | Email do utilizador. |
| `firstName` | string | Não | — | Primeiro nome. |
| `lastName` | string | Não | — | Apelido. |
| `fullName` | string | Não | — | Nome completo. |
| `sub` | string | Não | — | Identificador do subject (claim `sub` do token de identidade). |

> Nota: nenhum dos DTOs declara campos de auditoria nem herda de superclasse. Os campos `status` e `type` são `String` livres (não enums no contrato). Os parâmetros de query `type` em `/instances` e `/progress` são opcionais a nível HTTP (`required = false` no controller), embora os respetivos `Query` objects (`GetActivityInstancesQuery`, `GetActivityProgressQuery`) anotem `@NotBlank` em `type` — o contrato exposto ao cliente segue o controller (opcional).

---

## 10. Orientações para atualizar o Client SDK

Esta secção resume as decisões de design recomendadas para alinhar o client SDK (TypeScript) com o contrato descrito acima.

### 10.1 Geração automática a partir do OpenAPI (recomendado)

O serviço publica a especificação OpenAPI em **`/v3/api-docs`** (UI em `/swagger-ui.html`). Em vez de escrever tipos à mão, gerar o cliente a partir daí mantém o SDK sincronizado com o backend:

```bash
# Exemplo com openapi-typescript (apenas tipos) ou openapi-generator (cliente completo)
npx openapi-typescript https://<host>/v3/api-docs -o src/api/schema.ts
# ou
npx @openapitools/openapi-generator-cli generate \
  -i https://<host>/v3/api-docs -g typescript-fetch -o src/api
```

> Mesmo gerando automaticamente, validar os pontos de atenção desta secção (rota `tasks-instances`, listagens por `POST .../search`, envelope de paginação), porque são fáceis de implementar mal manualmente.

### 10.2 Configuração base do cliente

- **`baseURL` configurável por ambiente** (dev/staging/prod) — o serviço não tem *context-path*, mas costuma estar atrás de um *gateway* com prefixo.
- **Interceptor de autenticação:** injetar `Authorization: Bearer <token>` em todos os pedidos; em `401`, tentar *refresh* do token e, se falhar, redirecionar para login.
- **`Content-Type: application/json`** nos pedidos com corpo.

### 10.3 Tipos e modelos

- Gerar interfaces TypeScript para cada DTO descrito em §9, usando o mapeamento de tipos de §6.
- Modelar as enumerações como *union types* de literais (§7) — não como `enum` numérico.
- Reutilizar o tipo genérico `Page<T>` (§4) para todas as respostas `...ListPageDTO`.
- Tratar campos de data como `string` (ISO-8601) ao nível do transporte.
- Tratar valores de variáveis de processo/tarefa como `unknown` e refinar pelo campo `type` (`VariableType`).

### 10.4 Pesquisa e paginação

- As listagens principais de instâncias e tarefas são **`POST .../search`** (e `POST /tasks-instances/me`): enviam o **filtro no corpo** (`VariablesFilterDTO`) **e** `page`/`size` na **query string**. O método do SDK deve aceitar ambos.
- Enviar sempre `page` e `size` explícitos.
- Ler a paginação de `pageNumber`/`pageSize`/`totalElements`/`totalPages`/`first`/`last` (e não dos parâmetros de entrada).

### 10.5 Tratamento de erros

- Desserializar respostas de erro como **Problem Details** (RFC 7807): ler `title`, `status`, `detail`.
- Para `400` de validação, ler o mapa `errors` (`campo → mensagem`) e mapear para os campos do formulário.
- Para enum inválido, usar `AllowedValues`/`CurrentValue` para mensagens úteis.
- Distinguir `401` (renovar sessão) de `403` (sem permissão — mostrar mensagem, não redirecionar para login).
- Endpoints que devolvem `String`/`204` não trazem JSON estruturado — não tentar fazer `.json()` cego à resposta.

### 10.6 Checklist de *breaking changes* a validar nesta atualização

Ao comparar com a versão anterior do SDK, verificar especificamente:

- [ ] Rota base de tarefas é **`/tasks-instances`** (com `s`), não `/task-instances`.
- [ ] Listagens de instâncias/tarefas passaram a (ou continuam em) **`POST .../search`** com corpo de filtro + paginação na query.
- [ ] Endpoint `event_type` (tipos de evento de tarefa) usa *underscore*: `GET /tasks-instances/event_type`.
- [ ] Existem **dois** caminhos de criação de instância: `POST /process-instances/create` e `POST /process-instances` (criar+arrancar) — confirmar qual o SDK deve expor.
- [ ] Campos novos/renomeados nos DTOs (§9) — em especial nos modelos de tarefa, variáveis e regras de atribuição.
- [ ] Valores de enumerações (§7) sincronizados como *union types*.
- [ ] Envelope de paginação `Page<T>` com os nomes de campo corretos.

---

> **Como este documento foi produzido.** Os endpoints e DTOs em §9 foram extraídos diretamente dos *controllers* REST e classes DTO do serviço (`cv.igrp.platform.process.management`), e cada secção foi verificada contra o código-fonte. Em caso de dúvida ou divergência, a fonte de verdade é a especificação OpenAPI viva em `/v3/api-docs` e o próprio código.
