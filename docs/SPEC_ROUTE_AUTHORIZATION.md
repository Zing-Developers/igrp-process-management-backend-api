# Spec — Autorização de rotas via permissões IRN

**Aplicação:** iGRP Platform Process Management API (`cv.igrp.platform.process.management`)
**Estado:** proposta, por implementar
**Repos envolvidos:** este + `igrp-process-management-backend-monorepo` (branch `feature/irn-system-administration-integration`)
**Documento irmão:** `igrp-process-studio-backend-api/docs/SPEC_ROUTE_AUTHORIZATION.md` — mesma solução, catálogo próprio

---

## 1. Contexto e problema

`SecurityConfig` fecha as regras com `.anyRequest().authenticated()`. Não há `@EnableMethodSecurity`
nem um único `@PreAuthorize` aplicado — quatro controllers chegam a **importar**
`org.springframework.security.access.prepost.PreAuthorize` (linha 17 de cada) sem nunca o usarem.

Consequência: **qualquer token válido do realm chama qualquer um dos 55 endpoints**, incluindo
`deploy`, `import`, `archive`, `assign`, `unassign`, `claim` e `complete`.

É o P0 do `docs/SECURITY_RECOMMENDATIONS.md`:

> **P0 · Task/process access** — *Enforce authorization on task search, claim, assign, complete, import,
> deploy, and admin-style operations.*

Há uma segunda falha, do mesmo P0 (`SECURITY_RECOMMENDATIONS.md:114-127`): a pesquisa de tarefas só
restringe a visibilidade ao utilizador corrente **quando o cliente o pede** — ver RF-7.

O adapter IRN já sabe autenticar (chama `GET {irn.api.base-url}/api/v1/Auth/me` com o cookie
`session_id`), mas `IrnAuthorizationCacheService.getPermissions()` está **stubbed**: devolve sempre
`Set.of()`, com o código real em comentário. As `permissions` que o `/me` devolve nunca chegam ao
`SecurityContext`.

### 1.1 Restrições apuradas

**Os controllers são gerados.** Todos os cinco começam com:

```
/* THIS FILE WAS GENERATED AUTOMATICALLY BY iGRP STUDIO. */
/* DO NOT MODIFY IT BECAUSE IT COULD BE REWRITTEN AT ANY TIME. */
```

Anotar controllers com `@PreAuthorize` é o sítio errado — a próxima regeneração apaga tudo. As regras
têm de viver fora do código gerado.

**O iGRP Studio não é alternativa.** O motor de permissões está por implementar:
`igrp-studio-ide/src/main/engines/SpringEngine.ts:32` tem `createPermission()` a lançar
`Error('Method not implemented.')`, e `.igrpstudio/**/controllers/*.json` não tem nenhum campo de
autorização. É por isso que `.igrpstudio/permissions.json` está `[]` e os imports de `@PreAuthorize`
estão mortos.

**O mapeamento tem de ser configurável.** A mesma solução aplica-se ao Process Studio API, que tem base
paths e módulos diferentes. A implementação fica única, no módulo partilhado, alimentada por
configuração de cada aplicação.

---

## 2. Objetivo

Impor autorização por permissão em todas as rotas de negócio desta API, usando o array `permissions`
devolvido pelo `/Auth/me` do IRN, sem tocar em código gerado e sem duplicar a tabela de rotas em cada
aplicação.

### Decisões tomadas

| # | Decisão |
|---|---|
| D-1 | Usar **só** o array `permissions` do `/Auth/me`. `accessibleModuleCodes` fica de fora. |
| D-2 | Catálogo novo, derivado dos métodos HTTP, registado no System Administration. **Emendada pela D-6:** o catálogo mantém-se, mas cada rota passa a aceitar **também** as permissões IRN reais dos frontends (`accept-also`), porque na prática só os códigos dos frontends estão atribuídos aos perfis. |
| D-3 | O mapeamento rota→permissão **não vive no `SecurityConfig`** — é delegado ao adapter IRN por uma interface no `process-runtime-auth-core`. |
| D-4 | A mesma solução aplica-se ao `igrp-process-studio-backend-api`. |
| D-5 | Não declarar o catálogo no iGRP Studio por agora. |
| D-6 | **Híbrido multi-frontend.** Vários frontends IRN (`FILA_TRABALHO`, `TASK_MANAGEMENT`, `MY_TASKS`, `AVAILABLE_TASKS`) frenteiam os mesmos `/tasks-instances/*`, cada um com o seu código e verbos, e o backend não os distingue. Cada rota aceita **qualquer de**: a permissão derivada (`TASK_INSTANCES:acao`) **ou** as permissões IRN reais dos frontends. Ver §4.3. |
| D-7 | `pesquisar_todos` passa a ser uma **lista configurável** any-of (`igrp.authorization.task-search-all-permissions`), não uma constante — a mesma capacidade tem códigos diferentes por módulo. |

---

## 3. Requisitos funcionais

**RF-1** — O `SecurityConfig` não pode conter nenhuma rota de negócio literal. As regras são obtidas de
uma abstração injetada.

**RF-2** — O módulo `process-runtime-auth-core` expõe uma interface `IRouteAuthorizationAdapter` com as
regras de rota e a política para rotas não cobertas, seguindo o padrão já existente de
`IAuthorizationServiceAdapter` (interface + implementação `Default*` com `@ConditionalOnProperty`).

**RF-3** — A implementação IRN dessa interface deriva as regras de configuração da aplicação
(`irn.authorization.routes.*`), pela ordem declarada. Uma implementação serve as duas aplicações.

**RF-4** — `IrnAuthorizationCacheService.getPermissions()` passa a devolver as `permissions` do
`/Auth/me`. O `IrnMeResponse` não leva campos novos.

**RF-5** — O `GET /Auth/me` é chamado **no máximo uma vez por pedido HTTP**, com cache por `sessionId`.
Hoje são três chamadas (`getGroups`, `getPermissions`, `isSuperAdmin`, cada uma com o seu `@Cacheable`
mas sem cache sobre o `getMe` em si).

**RF-6** — Rotas não cobertas por nenhuma regra são **negadas** (fail closed) quando o adapter IRN está
ativo, para que um controller novo gerado pelo Studio não entre desprotegido.

**RF-7** — A pesquisa de tarefas devolve apenas as tarefas do utilizador corrente e dos seus grupos,
**exceto** se o utilizador tiver `TASK_INSTANCES:pesquisar_todos` ou for super admin. Os campos
`filterByCurrentUser`, `user`, `candidateGroups` e `candidateUsers` enviados pelo cliente deixam de ser
obedecidos por quem não tem essa permissão.

**RF-8** — Com `igrp.authorization.service.adapter=default` (deployments não-IRN) o comportamento atual
mantém-se **exatamente** igual.

**RF-9** — O super admin (`ROLE_DEPT_IGRP.superadmin`) passa em todas as regras, sem ter de constar de
cada uma.

**RF-10** — Falha no enriquecimento de authorities não pode conceder privilégios. O fallback atual, que
concede `ROLE_ACTIVITI_USER` no `catch`, mantém apenas essa role mínima (o Activiti precisa dela) e
nunca `ROLE_ACTIVITI_ADMIN`, com log ao nível de alerta.

---

## 4. Catálogo de permissões

Um módulo por base path, ação derivada do método HTTP, no formato IRN `MODULO:acao`.

| Método HTTP | Ação |
|---|---|
| `GET` | `visualizar` |
| `POST` | `criar` |
| `PUT` / `PATCH` | `editar` |
| `DELETE` | `eliminar` |

Registam-se só as combinações que existem — **17 permissões**:

| Módulo | Base path | Permissões |
|---|---|---|
| `AREAS` | `/areas` | `:visualizar` · `:criar` · `:editar` · `:eliminar` |
| `PROCESS_DEFINITIONS` | `/process-definitions` | `:visualizar` · `:criar` · `:editar` · `:eliminar` · `:publicar` |
| `PROCESS_INSTANCES` | `/process-instances` | `:visualizar` · `:criar` |
| `ACTIVITIES` | `/activities` | `:visualizar` |
| `TASK_INSTANCES` | `/tasks-instances` | `:visualizar` · `:criar` · `:editar` · `:eliminar` · `:pesquisar_todos` |

Duas ações **não** derivadas do método, por serem capacidades e não rotas CRUD:

- **`PROCESS_DEFINITIONS:publicar`** — o deploy de um processo no motor. É a operação mais sensível desta
  API: publica BPMN executável em produção. Não pode partilhar permissão com criar uma sequência.
- **`TASK_INSTANCES:pesquisar_todos`** — ver tarefas para além das próprias (RF-7).

O IRN já usa ações nomeadas assim (`GUIAS:validar`, `PEDIDOS:self_assign`,
`MODULOS_APLICACIONAIS:gerir_dependencias`), portanto é idiomático.

### 4.1 Overrides — rotas que fogem à regra do método

| Rota | Permissão | Em vez de | Porquê |
|---|---|---|---|
| `POST /process-definitions/deploy` | `PROCESS_DEFINITIONS:publicar` | `:criar` | publica BPMN executável no motor |
| `POST /process-instances/search` | `PROCESS_INSTANCES:visualizar` | `:criar` | pesquisa, filtro no body |
| `POST /tasks-instances/search` | `TASK_INSTANCES:visualizar` | `:criar` | idem |
| `POST /tasks-instances/me` | `TASK_INSTANCES:visualizar` | `:criar` | idem |

Sem os overrides de leitura, um utilizador só-leitura precisaria de `:criar` para pesquisar — o que lhe
daria também `claim`, `complete` e `assign`.

### 4.2 Mapa completo rota → permissão

**Leitura**

```
GET  /areas                                              AREAS:visualizar
GET  /areas/{id}                                         AREAS:visualizar
GET  /areas/{areaId}/process-definitions                 AREAS:visualizar
GET  /areas/status                                       AREAS:visualizar
GET  /process-definitions                                PROCESS_DEFINITIONS:visualizar
GET  /process-definitions/{id}/artifacts                 PROCESS_DEFINITIONS:visualizar
GET  /process-definitions/{id}/deployed-artifacts        PROCESS_DEFINITIONS:visualizar
GET  /process-definitions/{key}/sequence                 PROCESS_DEFINITIONS:visualizar
GET  /process-definitions/{key}/priorities               PROCESS_DEFINITIONS:visualizar
GET  /process-definitions/{id}/export                    PROCESS_DEFINITIONS:visualizar   (ver risco R-3)
POST /process-instances/search                           PROCESS_INSTANCES:visualizar     (override)
GET  /process-instances/{id}                             PROCESS_INSTANCES:visualizar
GET  /process-instances/status                           PROCESS_INSTANCES:visualizar
GET  /process-instances/{id}/task-status                 PROCESS_INSTANCES:visualizar
GET  /process-instances/stats                            PROCESS_INSTANCES:visualizar
GET  /activities/{id}                                    ACTIVITIES:visualizar
GET  /activities/instances                               ACTIVITIES:visualizar
GET  /activities/progress                                ACTIVITIES:visualizar
POST /tasks-instances/search                             TASK_INSTANCES:visualizar        (override)
POST /tasks-instances/me                                 TASK_INSTANCES:visualizar        (override)
GET  /tasks-instances/{id}                               TASK_INSTANCES:visualizar
GET  /tasks-instances/status                             TASK_INSTANCES:visualizar
GET  /tasks-instances/event_type                         TASK_INSTANCES:visualizar
GET  /tasks-instances/{id}/variables                     TASK_INSTANCES:visualizar
GET  /tasks-instances/stats                              TASK_INSTANCES:visualizar
GET  /tasks-instances/stats/me                           TASK_INSTANCES:visualizar
GET  /tasks-instances/assignment-rules                   TASK_INSTANCES:visualizar
```

**Escrita**

```
POST   /areas                                            AREAS:criar
POST   /areas/{areaId}/process-definitions               AREAS:criar
PUT    /areas/{id}                                       AREAS:editar
DELETE /areas/{id}                                       AREAS:eliminar
DELETE /areas/{areaId}/process-definitions/{pdId}        AREAS:eliminar
POST   /process-definitions/deploy                       PROCESS_DEFINITIONS:publicar     (override)
POST   /process-definitions/import                       PROCESS_DEFINITIONS:criar        (ver risco R-1)
POST   /process-definitions/{key}/sequence               PROCESS_DEFINITIONS:criar
POST   /process-definitions/{id}/assign                  PROCESS_DEFINITIONS:criar
POST   /process-definitions/{id}/unassign                PROCESS_DEFINITIONS:criar
POST   /process-definitions/{id}/unarchive               PROCESS_DEFINITIONS:criar
PUT    /process-definitions/{id}/artifacts/{taskKey}     PROCESS_DEFINITIONS:editar
PUT    /process-definitions/{key}/priorities             PROCESS_DEFINITIONS:editar
DELETE /process-definitions/artifacts/{id}               PROCESS_DEFINITIONS:eliminar
DELETE /process-definitions/{id}/archive                 PROCESS_DEFINITIONS:eliminar
DELETE /process-definitions/priorities/{id}              PROCESS_DEFINITIONS:eliminar
POST   /process-instances                                PROCESS_INSTANCES:criar
POST   /process-instances/create                         PROCESS_INSTANCES:criar
POST   /process-instances/event                          PROCESS_INSTANCES:criar
POST   /process-instances/{id}/start                     PROCESS_INSTANCES:criar
POST   /process-instances/{id}/timer/reschedule          PROCESS_INSTANCES:criar
POST   /tasks-instances/{id}/claim                       TASK_INSTANCES:criar
POST   /tasks-instances/{id}/unclaim                     TASK_INSTANCES:criar
POST   /tasks-instances/{id}/assign                      TASK_INSTANCES:criar
POST   /tasks-instances/{id}/complete                    TASK_INSTANCES:criar
POST   /tasks-instances/{id}/save                        TASK_INSTANCES:criar
PUT    /tasks-instances/assignment-rules/{id}            TASK_INSTANCES:editar
DELETE /tasks-instances/assignment-rules/{id}            TASK_INSTANCES:eliminar
```

**Capacidade (não é rota)**

```
TASK_INSTANCES:pesquisar_todos    → ver tarefas para além das próprias e as dos seus grupos (RF-7)
```

> `PROCESS_DEFINITIONS:publicar` **não** implica `:criar`, nem o contrário — são independentes. Quem só
> tem `:publicar` pode fazer deploy mas não configurar artefactos; quem só tem `:criar` pode preparar
> tudo menos publicar. É deliberado: permite separar quem desenha de quem põe em produção.

### 4.3 Frontends e códigos IRN aceites (`accept-also`)

Vários frontends IRN consomem os mesmos endpoints, mas cada um é um **módulo IRN com o seu próprio
código** — *Fila de Trabalho* = `FILA_TRABALHO`, *Gestão de Tarefas* = `TASK_MANAGEMENT`, *Minhas
Tarefas* = `MY_TASKS`, *Tarefas Disponíveis* = `AVAILABLE_TASKS` — e os quatro chamam
`/tasks-instances/*`. O backend não distingue qual frontend chamou: mesmo token, mesmo endpoint. Como na
prática só os códigos dos frontends estão atribuídos aos perfis (o catálogo `TASK_INSTANCES:*` da §4 não
foi adotado), gatear só pelo catálogo daria **403 a toda a gente**.

Solução (D-6): cada tier de ação aceita **qualquer de** — a permissão derivada `TASK_INSTANCES:acao`
**ou** as permissões IRN reais dos frontends, declaradas em `accept-also`, keyed pela ação derivada
(por isso os overrides de leitura herdam a lista de `visualizar`). O `anyAuthority` da regra já é um
`Set`; o `SecurityConfig` já chama `hasAnyAuthority`, nada muda aí.

```properties
irn.authorization.routes.modules[4].accept-also.visualizar=FILA_TRABALHO:visualizar,TASK_MANAGEMENT:ver,MY_TASKS:visualizar,AVAILABLE_TASKS:visualizar
irn.authorization.routes.modules[4].accept-also.criar=...   # verbos de operar, por confirmar
```

| Tier | Rotas | Aceita também (a confirmar no System Administration) |
|---|---|---|
| `visualizar` | search, me, {id}, status, stats, variables, assignment-rules | `FILA_TRABALHO:visualizar` · `TASK_MANAGEMENT:ver` ✓ · `MY_TASKS:visualizar` · `AVAILABLE_TASKS:visualizar` |
| `criar` | claim, unclaim, assign, complete, save | verbos de operar de cada frontend (por confirmar) |
| `editar`/`eliminar` | assignment-rules | por confirmar |

Só `TASK_MANAGEMENT:ver` está confirmado do `/Auth/me`; o resto são placeholders — **edições de uma
linha na config, sem recompilar**. Ausente/vazio → comportamento idêntico ao catálogo puro
(retrocompatível). O mecanismo está no `process-runtime-auth-irn` (`ModuleRoutes.acceptAlso` +
`IrnRouteAuthorizationAdapter.authoritiesFor`), a partir de `0.1.0-beta.24.5`.

O mecanismo não é exclusivo das tarefas: **todos** os módulos (`AREAS`, `PROCESS_DEFINITIONS`,
`PROCESS_INSTANCES`, `ACTIVITIES` e os `STUDIO_*`) já trazem as linhas `accept-also.<ação>`
**comentadas** no `application.properties`, cada uma com o seu botão `${IRN_..._ACCEPT_*}` e um TODO dos
candidatos (`FILA_TRABALHO`, `PROCESS_MAP`, `PROCESS_CONFIGURATION`, `CONFIGURADOR_PROCESSOS`). Só o
bloco de tarefas está ativo; nas restantes rotas basta descomentar o tier certo e pôr o código real
quando sair do System Administration.

**`pesquisar_todos`** (D-7) segue o mesmo padrão any-of, mas fora das rotas: é uma lista configurável
`igrp.authorization.task-search-all-permissions` (default `TASK_INSTANCES:pesquisar_todos`); o service
concede se o utilizador tiver **qualquer** uma, ou for super admin.

Verificado ao vivo na stack `irn-e2e`: `sess-fila-trabalho` (`FILA_TRABALHO:visualizar`, **sem**
`TASK_INSTANCES`) → `POST /search` **200**, `POST /{id}/complete` **403**; `sess-task-mgmt`
(`TASK_MANAGEMENT:ver`) → search **200**; `sess-mgmt-viewer` (`TASK_INSTANCES:visualizar`) → search
**200** (híbrido); `sess-none` → search **403**.

### 4.4 Chamadores de máquina (M2M, release 24.6)

Este spec cobre o caminho de **utilizador** (JWT Keycloak + sessão IRN). Sistemas externos sem sessão
(jobs, integrações) autenticam por **API key M2M** — `Authorization: Bearer igrpm2m_…` — resolvida
contra a nossa BD, com permissões `MODULO:acao` próprias que passam **nas mesmas regras de rota** desta
spec (any-of, sem caminho paralelo). As rotas de gestão `/m2m-keys/**` têm um **gate dedicado no
`SecurityConfig`** (JWT super-admin only), deliberadamente **fora** do catálogo
`irn.authorization.routes.*` — uma permissão provisionável tipo `M2MKEYS:criar` permitiria a um
não-super-admin (ou a uma key) cunhar keys. Detalhe completo: `docs/SPEC_M2M_AUTHORIZATION.md`.

### 4.5 Riscos assinalados

| # | Risco | Correção, se decidirem |
|---|---|---|
| R-1 | `POST /process-definitions/import` cai em `:criar`. Importar um pacote é o passo anterior ao deploy — quem importa não publica, mas escreve definições no sistema | se quiserem separar, `:importar` — mais uma linha de override, no mesmo molde do `:publicar` |
| R-2 | `DELETE /process-definitions/{id}/archive` é arquivar, não apagar; cai em `:eliminar` | aceitável, ou ação `:arquivar` |
| R-3 | `GET /process-definitions/{id}/export` fica em `:visualizar` pela regra do método, mas exporta o pacote BPMN inteiro | tratar como escrita — uma linha de override |

*(O deploy deixou de ser risco: passou a ter `:publicar` própria — secção 4.1.)*

---

## 5. Desenho técnico

### 5.1 `process-runtime-auth-core` (monorepo) — a interface

Pacote `cv.igrp.framework.process.runtime.auth.core.adapter`, ao lado do `IAuthorizationServiceAdapter`.

```java
public record RouteAuthorizationRule(
        HttpMethod method,        // null = qualquer método
        String pattern,           // ex. "/tasks-instances/**"
        Set<String> anyAuthority  // basta uma → hasAnyAuthority(...)
) {}

public interface IRouteAuthorizationAdapter {
    /** Ordem importa: a primeira regra que casa decide. */
    List<RouteAuthorizationRule> getRules();

    /** true → rotas não cobertas por nenhuma regra são negadas (fail closed). */
    boolean denyUnmatched();
}
```

`DefaultRouteAuthorizationAdapter` — mesmo padrão do `DefaultAuthorizationServiceAdapter`:

```java
@Component
@ConditionalOnProperty(name = "igrp.authorization.service.adapter",
                       havingValue = "default", matchIfMissing = true)
```

`getRules()` → `List.of()`, `denyUnmatched()` → `false`. Satisfaz RF-8.

`CoreAuthorizationAutoConfiguration` já faz `@ComponentScan` do pacote — nada a registar de novo.

`HttpMethod` vem de `org.springframework.http` (spring-web). O `pom.xml` do `auth-core` declara hoje
`jakarta.servlet-api`, `slf4j-api`, `spring-context`, `spring-boot-autoconfigure` e
`spring-security-oauth2-jose` — acrescentar **`spring-web` como `provided`**.

### 5.2 `process-runtime-auth-irn` (monorepo) — permissões e regras

**Permissões (RF-4, RF-5).** `IrnAuthorizationCacheService` tem dois defeitos:

1. `getPermissions()` (linhas ~63-86) devolve `Set.of()` com o código real comentado. Descomentar →
   `new HashSet<>(irnMeResponse.permissions())`.
2. `client.getMe()` é chamado três vezes por pedido. Introduzir um bean novo com
   `@Cacheable(value = "irnMeCache", key = "#sessionId") IrnMeResponse me(String sessionId)` e fazer
   `getGroups`, `getPermissions` e `isSuperAdmin` lerem dele.

O `@Cacheable` só funciona via proxy Spring, logo o `me()` tem de ficar **noutro bean** — é exatamente a
razão pela qual o `IrnAuthorizationCacheService` já está separado do `IrnAuthorizationServiceAdapter`.
Manter `unless = "#result.isEmpty()"` para não cachear falhas.

**Regras (RF-3).**

```java
@ConfigurationProperties(prefix = "irn.authorization.routes")
public record IrnRouteProperties(
        @DefaultValue("true") boolean denyUnmatched,
        List<ModuleRoutes> modules) {

    public record ModuleRoutes(
            String code,                // "PROCESS_DEFINITIONS"
            String pattern,             // "/process-definitions"  (ordem = a da lista)
            List<Override> overrides) {

        /** Rota que foge à regra do método: {método, sufixo do path, ação explícita}. */
        public record Override(HttpMethod method, String path, String action) {}
    }
}
```

```java
@Component
@ConditionalOnProperty(name = "igrp.authorization.service.adapter", havingValue = "irn")
public class IrnRouteAuthorizationAdapter implements IRouteAuthorizationAdapter { ... }
```

`getRules()` percorre `modules` **pela ordem declarada** e, para cada módulo, emite:

1. os `overrides` → `{método, pattern + path, code + ":" + action}`;
2. uma regra por método sobre `pattern` **e** `pattern + "/**"`, com o verbo da tabela da secção 4
   (`GET→visualizar`, `POST→criar`, `PUT/PATCH→editar`, `DELETE→eliminar`).

Os overrides vêm sempre antes das genéricas do mesmo módulo — é aí que está o conflito, e a primeira
regra que casa decide. É um mecanismo único, que serve tanto as leituras feitas por POST
(`POST /search → :visualizar`) como as capacidades sensíveis (`POST /deploy → :publicar`): acrescentar uma
ação nova é uma entrada de configuração, nunca uma alteração de código.

As duas variantes de padrão (`pattern` e `pattern/**`) são necessárias porque
`AreasController`, `ProcessDefinitionController` e `ProcessInstanceController` têm mappings sem `value`,
que batem na raiz.

Registar o record com `@EnableConfigurationProperties` no `IRNAuthorizationAutoConfiguration`, seguindo
o mecanismo que já regista o `IrnApiProperties`.

### 5.3 `SecurityConfig` desta API (RF-1, RF-6, RF-9)

O construtor passa a receber também `IRouteAuthorizationAdapter routeAuthorization`:

```java
.authorizeHttpRequests(authorize -> {
    authorize.requestMatchers(r -> r.getDispatcherType() == DispatcherType.ERROR).permitAll();
    authorize.requestMatchers(
        "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
        "/swagger-resources/**", "/webjars/**",
        "/actuator/health", "/actuator/health/**").permitAll();

    routeAuthorization.getRules().forEach(rule ->
        (rule.method() == null
            ? authorize.requestMatchers(rule.pattern())
            : authorize.requestMatchers(rule.method(), rule.pattern()))
        .hasAnyAuthority(withSuperAdmin(rule.anyAuthority())));

    if (routeAuthorization.denyUnmatched()) authorize.anyRequest().denyAll();
    else authorize.anyRequest().authenticated();
})
```

`withSuperAdmin(...)` é um helper privado que anexa
`ROLE_PREFIX + IgrpAuthorizationConstants.SUPER_ADMIN_ROLE` a cada regra (RF-9), evitando repetir a role
em toda a tabela.

O `permitAll` do dispatcher `ERROR` é **obrigatório** com `denyAll()`: sem ele os erros do Spring viram
403 em vez do estado real.

`hasAnyAuthority` aceita `:` sem escaping — `SimpleGrantedAuthority` é apenas uma string, e o converter
(linhas 136-138) já injeta cada permissão tal e qual, sem prefixo.

**Fail closed no converter (RF-10).** O `catch` das linhas 150-154 concede `ROLE_ACTIVITI_USER` quando o
enriquecimento falha. Com as regras acima o utilizador já leva 403, mas o `catch` continua a mascarar a
indisponibilidade do IRN: manter só a role mínima, garantir que `ROLE_ACTIVITI_ADMIN` nunca entra por
fallback, e subir o log a alerta de segurança (`SECURITY_RECOMMENDATIONS.md:76-94`).

### 5.4 Visibilidade da pesquisa de tarefas (RF-7)

`SECURITY_RECOMMENDATIONS.md:114-127`. As regras de rota **não** resolvem isto: dão acesso ao endpoint,
não delimitam *que tarefas* ele devolve.

**A falha.** `TaskInstanceService.getAllTaskInstances(filter)` (linha 243) só liga a visibilidade do
utilizador corrente quando o cliente o pede:

```java
if (filter.isFilterByCurrentUser()) {          // <- flag vinda do cliente
  final var currentUser = userContext.getCurrentUser();
  final var isSuperAdmin = userContext.isSuperAdmin();
  filter.bindCurrentUser(currentUser, isSuperAdmin);
  userContext.getCurrentGroups().forEach(filter::addContextUserGroup);
}
```

`ListTaskInstancesCommand` aceita do cliente `filterByCurrentUser`, `user`, `candidateGroups` e
`candidateUsers`. Basta enviar `filterByCurrentUser=false` para ler **todas as tarefas do sistema** —
incluindo variáveis de processo e formulários, que o próprio método enriquece a seguir.

**O sítio da correção.** `ListTaskInstancesCommandHandler` e `GetAllMyTasksCommandHandler` chamam **os
dois** `taskInstanceService.getAllTaskInstances(filter)`. É o ponto único por onde tudo passa — um guard
aí cobre os dois endpoints; um guard por handler deixaria o próximo caller a descoberto.

**A correção — inverter o default:**

```java
public PageableLista<TaskInstance> getAllTaskInstances(TaskInstanceFilter filter) {

  final boolean canSearchAll = userContext.isSuperAdmin()
      || userContext.hasPermission(TASK_INSTANCES_SEARCH_ALL);   // "TASK_INSTANCES:pesquisar_todos"

  if (canSearchAll) {
    if (filter.isFilterByCurrentUser()) {          // comportamento atual, preservado
      filter.bindCurrentUser(userContext.getCurrentUser(), true);
      userContext.getCurrentGroups().forEach(filter::addContextUserGroup);
    }
  } else {
    // ignora user/candidateGroups/candidateUsers do cliente e força o âmbito próprio
    filter.restrictToCurrentUser(userContext.getCurrentUser(), userContext.getCurrentGroups());
  }
  ...
}
```

Três peças pequenas:

- **`TaskInstanceFilter.restrictToCurrentUser(Code, List<String>)`** *(novo)* — limpa `candidateGroups`,
  `candidateUsers` e `user` (já são campos mutáveis do filtro), põe `isSuperAdmin=false`, e depois faz o
  que o `bindCurrentUser` + `addContextUserGroup` já fazem. Sendo um método do agregado, o descarte dos
  filtros do cliente fica impossível de esquecer.
- **`UserContext.hasPermission(String)`** *(novo)* + implementação em `SecurityUserContext` — match
  exato sobre `authentication.getAuthorities()`, no mesmo estilo do `isSuperAdmin()` que já lá está.
- A constante `TASK_INSTANCES:pesquisar_todos`, junto de `IgrpAuthorizationConstants`.

Os controllers e os DTOs gerados não são tocados — os campos continuam a ser aceites, apenas deixam de
ser obedecidos por quem não tem a permissão.

### 5.5 Componentes a reutilizar

- `IAuthorizationServiceAdapter` (`auth-core`) — já expõe `getGroups` / `getPermissions` /
  `isSuperAdmin` / `getActiveGroups` e recebe o `HttpServletRequest`, que é o que permite ao adapter IRN
  chegar ao cookie `session_id`. A interface nova segue o mesmo padrão.
- `IrnAuthClient` + `IrnApiProperties` — o cliente `/Auth/me` já existe e funciona.
- `RestClientSignedAuthorizationConfig` / `JwtTokenService` (`process-runtime-irn-integration`) — o JWT
  RS256 service-to-service já é injetado no `RestClient` por interceptor. **Não escrever assinatura nova.**
- Caffeine já está configurado globalmente (`spring.cache.type=caffeine`,
  `spring.cache.caffeine.spec=maximumSize=10000,expireAfterWrite=5m`) — o `@Cacheable` novo entra sem
  configuração adicional.
- `IgrpAuthorizationConstants.SUPER_ADMIN_ROLE` / `ROLE_PREFIX`, `ActivitiConstants.GROUP_PREFIX`.

### 5.6 Ficheiros afetados

**Monorepo** (`feature/irn-system-administration-integration`)

| Ficheiro | Ação |
|---|---|
| `auth-core/.../adapter/RouteAuthorizationRule.java` | novo |
| `auth-core/.../adapter/IRouteAuthorizationAdapter.java` | novo |
| `auth-core/.../adapter/DefaultRouteAuthorizationAdapter.java` | novo |
| `auth-core/pom.xml` | + `spring-web` (provided) |
| `auth-irn/.../adapter/IrnRouteProperties.java` | novo |
| `auth-irn/.../adapter/IrnRouteAuthorizationAdapter.java` | novo |
| `auth-irn/.../adapter/IrnAuthorizationCacheService.java` | cachear `me()`, ativar permissões |
| `auth-irn/.../IRNAuthorizationAutoConfiguration.java` | `@EnableConfigurationProperties` |
| `pom.xml` (raiz) + `process-runtime-bom/pom.xml` | bump de versão; o BOM tem de listar `auth-irn` e `irn-integration` — esta API declara-os **sem `<version>`** e depende do BOM |

**Esta API** (`features/security-harding`)

| Ficheiro | Ação |
|---|---|
| `shared/security/SecurityConfig.java` | consumir a interface (5.3) + fail closed no catch |
| `processruntime/domain/service/TaskInstanceService.java` | inverter o default da visibilidade (5.4) |
| `processruntime/domain/models/TaskInstanceFilter.java` | + `restrictToCurrentUser(...)` |
| `shared/security/util/UserContext.java` · `SecurityUserContext.java` | + `hasPermission(String)` |
| `src/main/resources/application.properties` · `.env.example` | secção 6 |
| `src/test/.../TaskInstanceServiceVisibilityTest.java` | novo |

**Nenhum controller é tocado, e nenhuma rota de negócio fica hardcoded no `SecurityConfig`.**

---

## 6. Configuração

`src/main/resources/application.properties`:

```properties
irn.api.base-url=${IRN_API_BASE_URL:}
irn.api.super-admin-email=${IRN_API_SUPER_ADMIN_EMAIL:}
irn.api.session-cookie-name=${IRN_API_SESSION_COOKIE_NAME:session_id}

irn.authorization.routes.deny-unmatched=true
irn.authorization.routes.modules[0].code=AREAS
irn.authorization.routes.modules[0].pattern=/areas
irn.authorization.routes.modules[1].code=PROCESS_DEFINITIONS
irn.authorization.routes.modules[1].pattern=/process-definitions
irn.authorization.routes.modules[1].overrides[0].method=POST
irn.authorization.routes.modules[1].overrides[0].path=/deploy
irn.authorization.routes.modules[1].overrides[0].action=publicar
irn.authorization.routes.modules[2].code=PROCESS_INSTANCES
irn.authorization.routes.modules[2].pattern=/process-instances
irn.authorization.routes.modules[2].overrides[0].method=POST
irn.authorization.routes.modules[2].overrides[0].path=/search
irn.authorization.routes.modules[2].overrides[0].action=visualizar
irn.authorization.routes.modules[3].code=ACTIVITIES
irn.authorization.routes.modules[3].pattern=/activities
irn.authorization.routes.modules[4].code=TASK_INSTANCES
irn.authorization.routes.modules[4].pattern=/tasks-instances
irn.authorization.routes.modules[4].overrides[0].method=POST
irn.authorization.routes.modules[4].overrides[0].path=/search
irn.authorization.routes.modules[4].overrides[0].action=visualizar
irn.authorization.routes.modules[4].overrides[1].method=POST
irn.authorization.routes.modules[4].overrides[1].path=/me
irn.authorization.routes.modules[4].overrides[1].action=visualizar
```

**Correção obrigatória no `.env.example`:** `IGRP_AUTHORIZATION_SERVICE_ADAPTER=igrp` → `irn`. O módulo
`process-runtime-auth-igrp` **não está no classpath** desta API; com `=igrp` nenhum bean
`IAuthorizationServiceAdapter` é criado e o construtor do `SecurityConfig` rebenta no arranque.

Chaves relacionadas, já documentadas em `IRN-CUSTOMIZATION.md` do monorepo:
`igrp.restclient.provider=irn`, `igrp.authorization.jwt.key`, `igrp.authorization.jwt.private-key`.

---

## 7. Registo no System Administration do IRN

**Não existe API de registo.** A varredura aos repos não encontrou nenhum cliente HTTP a apontar para
`system-administration`, `access-management`, `exp-cvt-system-administration` ou qualquer host IRN. As
permissões são consumidas, nunca publicadas.

O registo é **manual, na UI do System Administration**:

1. Criar os módulos `AREAS`, `PROCESS_DEFINITIONS`, `PROCESS_INSTANCES`, `ACTIVITIES`, `TASK_INSTANCES`.
2. Criar as 17 permissões da secção 4.
3. Associá-las aos perfis.

A partir daí aparecem no array `permissions` do `/Auth/me` e o gate funciona.

Duas permissões a tratar com cuidado ao desenhar os perfis:

> **`PROCESS_DEFINITIONS:publicar`** — põe BPMN executável em produção. Deve ser um perfil próprio, não
> um extra do perfil de quem desenha processos.
>
> **`TASK_INSTANCES:pesquisar_todos`** — decide quem vê as tarefas de toda a gente. Reservada a perfis de
> supervisão.

A tabela da secção 4 é o documento que se leva para o System Administration.

---

## 8. Critérios de aceitação

**CA-1 · Build** — `mvn -q install` no monorepo, depois `mvn -q -DskipTests package` nesta API.

**CA-2 · Regras de rota** — a ordem e a derivação das regras estão cobertas por
`IrnRouteAuthorizationAdapterTest` no monorepo (8 casos, incluindo o override do `/deploy` antes da
regra genérica). O teste HTTP de ponta a ponta requer o contexto completo (base de dados + Activiti),
ou seja Testcontainers — fica pendente; até lá, validar em DSV com a tabela seguinte:

| Authorities | Pedido | Esperado |
|---|---|---|
| `TASK_INSTANCES:visualizar` | `POST /tasks-instances/search` | passa o filtro de segurança |
| `TASK_INSTANCES:visualizar` | `POST /tasks-instances/{id}/complete` | **403** — prova o override |
| `TASK_INSTANCES:criar` | `POST /tasks-instances/{id}/complete` | passa |
| `TASK_INSTANCES:criar` | `POST /tasks-instances/search` | **403** |
| `PROCESS_DEFINITIONS:criar` | `POST /process-definitions/deploy` | **403** — prova que `:publicar` é independente |
| `PROCESS_DEFINITIONS:publicar` | `POST /process-definitions/deploy` | passa |
| `PROCESS_DEFINITIONS:publicar` | `POST /process-definitions/import` | **403** |
| *(nenhuma)* | qualquer rota de negócio | **403** |
| `ROLE_DEPT_IGRP.superadmin` | qualquer rota | passa |
| qualquer | `GET /rota-inexistente` | **403** — prova o `denyUnmatched` |
| qualquer | `GET /actuator/health` | **200** |

**CA-3 · Sem regressão (RF-8)** — com `igrp.authorization.service.adapter=default`, o comportamento
antigo mantém-se: só `authenticated()`, nenhuma rota negada por falta de permissão.

**CA-4 · Anti-drift** — o conjunto de authorities devolvido por `getRules()` é igual ao conjunto de
permissões derivadas de rota da secção 4. (`TASK_INSTANCES:pesquisar_todos` fica de fora — não é uma
permissão de rota.) A configuração e este documento não podem divergir.

**CA-5 · Visibilidade da pesquisa (RF-7)** — `TaskInstanceServiceVisibilityTest`, com `UserContext` mockado:

- sem `TASK_INSTANCES:pesquisar_todos`, e com `filterByCurrentUser=false` + `user=outra.pessoa` +
  `candidateGroups=OUTRO_GRUPO` no filtro → o filtro que chega ao `taskInstanceRepository.findAll` tem
  `user` = utilizador corrente e os `candidateGroups` do cliente **descartados**;
- com a permissão → o filtro passa intacto;
- super admin → idem.

**CA-6 · End-to-end** — depois de registar as 17 permissões no System Administration e atribuí-las a um
perfil: com `IGRP_AUTHORIZATION_SERVICE_ADAPTER=irn`, cookie `session_id=<sessionId>` e bearer do
Keycloak, o log DEBUG do `SecurityConfig` (linha `Authorities: {}`) mostra `AREAS:visualizar` etc.
Repetir com um perfil sem a permissão → 403. Com dois utilizadores distintos,
`POST /tasks-instances/search` com `filterByCurrentUser=false` devolve conjuntos diferentes — não o
universo inteiro.

**CA-7 · Cache (RF-5)** — dois pedidos seguidos com o mesmo `session_id` produzem **um** só
`GET /Auth/me` no log do `IrnAuthClient`. Hoje seriam três.

---

## 9. Fora do âmbito

- **Declarar o catálogo no iGRP Studio** (`.igrpstudio/<módulo>/permissions/*.json`) — adiado por decisão
  (D-5). Para quando se retomar: o ecrã lê de `.igrpstudio/<módulo>/permissions/`, **não** do
  `permissions.json` da raiz (esse está em `IGNORED_PATHS`,
  `igrp-studio-ide/src/main/helpers/index.ts:119`), e o formato é
  `{"type":"permission","name":"AREAS:visualizar","description":"…","endpoints":["listAreas", …]}`, com
  `endpoints` = os `actionName` dos `controllers/*.json`. Os ficheiros têm de ser escritos à mão — o
  *Save* da UI rebenta.
- **Implementar `SpringEngine.createPermission()`** no `igrp-studio-ide` e a emissão de `@PreAuthorize`
  no `@igrp/igrp-studio-springboot-engine`. Fecharia o ciclo Studio→código, mas é noutro repo e noutro
  package npm.
- **Visibilidade da pesquisa de process instances** (`POST /process-instances/search`) — tem a mesma
  forma que a das tarefas, mas o `ProcessInstanceService` não tem equivalente ao `bindCurrentUser`, por
  isso é desenho novo e não um guard de três linhas.
- Outros P0/P1 do `SECURITY_RECOMMENDATIONS.md`, tratados em specs próprios: consumidores Kafka
  (`AbstractStartProcessConsumer` autentica como `system-bot` com role de admin), CORS wildcard com
  `allowCredentials=true`, `JwtDecoderConfiguration` fail-open, sanitização do `GlobalExceptionHandler`,
  SSRF nos webhook delegates.
- **Relatório ENISA — Advanced Ethical Hacking**: o PDF não tem camada de texto (páginas exportadas como
  vetor/imagem), por isso não foi incorporado. Findings que mapeiem para endpoints específicos entram no
  ajuste da configuração da secção 6.
