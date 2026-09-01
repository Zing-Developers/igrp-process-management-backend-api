# Guia de Instalação DevOps — Release 24.6 (Autorização IRN + CVE)

Aplica-se à **management API** e ao **Studio API**. A ordem das secções é a ordem de execução —
o passo 1 é pré-requisito de tudo: sem ele, o deploy resulta em 403 generalizado.

---

## 1. Registar permissões no System Administration do IRN

Não existe API de registo — é manual, na UI. Criar os **módulos** e as **permissões**, depois
associá-las aos perfis. Um utilizador sem permissão associada recebe 403 em todas as rotas.

### Management API — 5 módulos, 17 permissões

| Módulo | Permissões |
|---|---|
| `AREAS` | `visualizar` · `criar` · `editar` · `eliminar` |
| `PROCESS_DEFINITIONS` | `visualizar` · `criar` · `editar` · `eliminar` · `publicar` |
| `PROCESS_INSTANCES` | `visualizar` · `criar` |
| `ACTIVITIES` | `visualizar` |
| `TASK_INSTANCES` | `visualizar` · `criar` · `editar` · `eliminar` · `pesquisar_todos` |

### Studio API — 3 módulos, 8 permissões

| Módulo | Permissões |
|---|---|
| `STUDIO_PROJECTS` | `visualizar` · `criar` · `editar` |
| `STUDIO_PROCESS_DEFINITIONS` | `visualizar` · `criar` · `editar` · `publicar` |
| `STUDIO_PARAMETERIZATION` | `visualizar` |

### Permissões a atribuir com critério

- **`PROCESS_DEFINITIONS:publicar`** e **`STUDIO_PROCESS_DEFINITIONS:publicar`** — o *deploy* de
  BPMN executável. Perfil próprio de publicação; **não** vem incluída em `criar` (independentes
  nos dois sentidos, de propósito).
- **`TASK_INSTANCES:pesquisar_todos`** — ver tarefas de toda a gente. Só perfis de supervisão;
  sem ela, cada utilizador vê apenas as suas tarefas e as dos seus grupos, independentemente dos
  filtros que o cliente enviar. A partir da 24.5 é uma **lista** (`IGRP_TASK_SEARCH_ALL_PERMISSIONS`),
  por isso pode apontar-se ao código real do módulo em vez de `TASK_INSTANCES:pesquisar_todos`.
- O **super admin** é identificado pelo email do `/Auth/me` igual a `IRN_API_SUPER_ADMIN_EMAIL` —
  passa em todas as regras sem precisar de permissões.

### Frontends que partilham `/tasks-instances` (accept-also, 24.5)

Vários frontends IRN (`FILA_TRABALHO`, `TASK_MANAGEMENT`, `MY_TASKS`, `AVAILABLE_TASKS`) chamam os
mesmos endpoints, cada um com o **seu** código e verbos, e o backend não os distingue. Como na prática
são esses códigos que estão atribuídos aos perfis, as rotas de tarefas aceitam **qualquer de**: a
permissão derivada `TASK_INSTANCES:acao` **ou** os códigos reais dos frontends, via `accept-also`
(`IRN_TASKS_ACCEPT_*`, secção 3). Não é preciso registar novo catálogo — basta que os perfis já tenham
os códigos dos frontends. Só `TASK_MANAGEMENT:ver` está confirmado; os verbos de operar entram na env
var quando forem definidos no System Administration (sem recompilar).

Mapa completo rota→permissão: `docs/SPEC_ROUTE_AUTHORIZATION.md` em cada repo.

## 2. Segredos e chaves

O backend chama o IRN com um JWT RS256 assinado por chave privada **PKCS#1**:

```bash
openssl genrsa -out irn-private-key.pem 2048
openssl rsa -in irn-private-key.pem -pubout -out irn-public-key.pem
```

- Registar a **pública** junto da equipa IRN com o identificador que ficará em
  `IGRP_AUTHORIZATION_JWT_KEY` (claim `key` do token).
- Montar a **privada** read-only no container (Secret do k8s / volume) — nunca em variável de
  ambiente nem no repositório.

## 3. Variáveis de ambiente

Iguais nas duas apps salvo indicação. Referência viva: `.env.example` de cada repo.
Para ambiente de **desenvolvimento local** (adapter default, mocks, valores de arranque): `docs/DEV_ENVIRONMENT_GUIDE.md`.

| Variável | Valor | Notas |
|---|---|---|
| `IGRP_AUTHORIZATION_SERVICE_ADAPTER` | `irn` | **`igrp` é inválido** nestas apps — o arranque falha. `default` = sem regras de permissão (só dev). |
| `IRN_API_BASE_URL` | `https://<host-irn>/exp-cvt-system-administration` | o cliente acrescenta `/api/v1/Auth/me` |
| `IRN_API_SUPER_ADMIN_EMAIL` | email do super admin | comparação exata com o email do `/Auth/me` |
| `IRN_API_SESSION_COOKIE_NAME` | `session_id` (default) | nome do cookie de sessão IRN |
| `IGRP_RESTCLIENT_PROVIDER` | `irn` | ativa o RestClient assinado RS256 |
| `IGRP_AUTHORIZATION_JWT_KEY` | id da chave registada no IRN | |
| `IGRP_AUTHORIZATION_JWT_PRIVATE_KEY` | `file:/app/keys/irn-private-key.pem` | PKCS#1, montada read-only |
| `IRN_AUTHORIZATION_DENY_UNMATCHED` | `true` (default) | `false` = rotas sem regra ficam só autenticadas — não recomendado |
| `IRN_TASKS_ACCEPT_READ` | `FILA_TRABALHO:visualizar,TASK_MANAGEMENT:ver,MY_TASKS:visualizar,AVAILABLE_TASKS:visualizar` (default) | **management API, 24.5**: códigos IRN dos frontends de tarefas aceites em **leitura** (any-of, a par de `TASK_INSTANCES:visualizar`). Lista por vírgulas. Só `TASK_MANAGEMENT:ver` confirmado; ajustar aos verbos reais |
| `IRN_TASKS_ACCEPT_WRITE` | vazio | idem para **operar** (claim/complete/assign…). Vazio = só quem tiver `TASK_INSTANCES:criar`. Preencher com os verbos de operar de cada frontend quando existirem |
| `IGRP_TASK_SEARCH_ALL_PERMISSIONS` | `TASK_INSTANCES:pesquisar_todos` (default) | **management API, 24.5**: lista any-of que concede ver as tarefas de todos. Apontar aos códigos reais de supervisão dos módulos |
| `IGRP_PROCESS_ENGINE_BASE_URL` | URL da **management API** | **Studio API**: motor a que o Studio faz deploy. Vazio = cliente *mock* (deploy não chega a motor real — só dev). O Studio reencaminha o Bearer do utilizador; o motor reaplica `PROCESS_DEFINITIONS:publicar` |
| `IGRP_M2M_KEY_PEPPER` | segredo forte (secret manager) | **24.6, as duas apps — OBRIGATÓRIO em produção**: chaveia o HMAC dos hashes das API keys M2M; sem ele um dump da tabela expõe hashes não-apimentados. Mudá-lo invalida todas as keys existentes |
| `IGRP_M2M_ROTATE_GRACE` | `7d` (default) | quanto tempo a key antiga sobrevive após um `rotate` antes de expirar sozinha |
| `IGRP_DEFAULT_SUPER_ADMIN_EMAIL` | vazio (default) | **24.7, só `adapter=default` (dev)**: JWT cujo claim `email` bater (case-insensitive) vira super-admin — espelha o `IRN_API_SUPER_ADMIN_EMAIL` do adapter IRN. Vazio = ninguém, como antes. Sem efeito com `adapter=irn` |
| `IGRP_SECURITY_PRINCIPAL_CLAIM_NAME` | **`email`** em IRN (default `sub`) | identidade gravada em tarefas, colunas de auditoria e logs. Tem de bater com o formato das atribuições — o IRN atribui por email, senão o match "minhas tarefas" falha. Igual nas duas apps; decidir **antes** do go-live (mudar com dados existentes deixa tarefas antigas órfãs no match). Exige o scope `email` no token Keycloak. |
| `MANAGEMENT_HEALTH_MAIL_ENABLED` | `false` se não houver SMTP | **management API**: sem SMTP o `MailHealthIndicator` põe `/actuator/health` a 503 e mata os probes do k8s |
| `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE` | URL do Eureka | se service discovery ativo |
| `SPRING_ACTIVE_PROFILE` | **`production`** | default passou a `production`. Deixar por definir = production; **definir explicitamente** para dev/staging (senão `ddl-auto=update`/`show-sql` ficam desligados, o que é o correto em prod) |
| `IGRP_CORS_ALLOWED_ORIGINS` | ex. `https://app.irn.pt` (lista por vírgulas) | **vazio = zero acesso cross-origin**. Sem isto, um frontend noutro domínio falha silenciosamente no browser. Wildcard já não é possível. |
| `ENABLE_SWAGGER` | `false` (prod/staging) | controla **UI e** `/v3/api-docs` (o spec OpenAPI). `true` só em dev. |
| `IGRP_OUTBOUND_REQUIRE_HTTPS` | `true` | webhooks/assignment exigem https; `false` só em `development` |
| `IGRP_OUTBOUND_ALLOWED_HOSTS` | vazio, ou lista `host,*.sufixo` | **vazio = qualquer host público** (interno sempre bloqueado). Para um webhook a um serviço **interno**, listar o host aqui (é confiado como configurado). |
| `IGRP_OUTBOUND_ALLOWED_EXTRA_HEADERS` | vazio | headers de credenciais que uma variável de processo pode mesmo assim enviar (ex. `Authorization`); por omissão são filtrados. **Prefir** referenciar por `$[VAR]` (ver linha seguinte) a abrir isto |
| `IGRP_OUTBOUND_ALLOWED_ENV_VARS` | `IGRP_WEBHOOK_*` (exato ou `PREFIX*`) | que env vars um process definition pode referenciar via `$[VAR]`. Default deixa resolver credenciais de webhook nomeadas por convenção e **bloqueia** segredos (`POSTGRES_PASSWORD`, `KEYCLOAK_CLIENT_SECRET`). Governa **todos** os delegates (webhook, mail, parse, message). |
| `IGRP_OUTBOUND_MAX_RESPONSE_BYTES` | `1048576` (1MB) | tamanho máximo da resposta de webhook |

A tabela de rotas (`irn.authorization.routes.*`) vem no `application.properties` de cada app e
normalmente não se toca; qualquer módulo/override novo é acrescentado lá, nunca no código.

**accept-also noutras rotas:** cada módulo (AREAS, PROCESS_DEFINITIONS, PROCESS_INSTANCES, ACTIVITIES e
os `STUDIO_*`) já tem as linhas `accept-also.<ação>` **comentadas**, com o seu botão `${IRN_..._ACCEPT_*}`
e um TODO dos candidatos. Só o bloco de tarefas está ativo. Para aceitar um frontend IRN noutra rota:
descomentar a linha do tier certo e pôr o código real (via env var ou direto). É any-of, a par da
permissão derivada; sem código nem rebuild. Só `TASK_MANAGEMENT:ver` está confirmado hoje.

### 3.1 ConfigMap e precedência de propriedades

As apps carregam ConfigMaps **como variáveis de ambiente** (`envFrom`/`valueFrom` no Deployment) — **não**
como PropertySource do Spring. Só o `spring.cloud.kubernetes.discovery` está ativo; o carregamento de
ConfigMap/Secret como config (`spring.cloud.kubernetes.config`) **não** está ligado. Consequência: uma
env var vinda do ConfigMap **sobrepõe-se** ao `application.properties` empacotado no jar. Ordem de
precedência do Spring Boot, do mais forte ao mais fraco:

```
1. args de linha de comando
2. variáveis de ambiente do SO        ← ConfigMap via envFrom aterra aqui (ganha)
3. application-<profile>.properties
4. application.properties (no jar)     ← os defaults
```

**Duas regras a reter:**

1. **Nome tem de mapear.** Uma env var só bate uma propriedade se o nome corresponder (*relaxed binding*:
   `IRN_AUTHORIZATION_ROUTES_DENY_UNMATCHED` → `irn.authorization.routes.deny-unmatched`). As variáveis
   da tabela acima já têm nome próprio, logo funcionam diretamente.
2. **Chaves indexadas não se sobrepõem por env direto.** `irn.authorization.routes.modules[4].accept-also.visualizar`
   não é exprimível como nome de env var (os `[4]` e os pontos partem). Por isso essas propriedades têm
   um botão explícito — `${IRN_TASKS_ACCEPT_READ:...}` — e é **esse** nome que se põe no ConfigMap, não a
   chave indexada. Para mudar uma chave indexada que ainda não tenha `${VAR}`: ou se lhe acrescenta o
   botão, ou se ativa `spring.cloud.kubernetes.config` para carregar o ConfigMap como PropertySource (aí
   qualquer chave, indexada incluída, passa a poder ser sobreposta).

| Como entregas o ConfigMap | Sobrepõe o `application.properties`? |
|---|---|
| `envFrom`/`valueFrom` (env vars) | **Sim** — chaves com nome mapeável, ou os `${VAR}` definidos |
| Chave indexada (`modules[N]…`) por env direto | **Não** de forma fiável — usar o `${VAR}` correspondente |
| Ficheiro montado + `spring.config.additional-location` | Sim, se o Spring apontar para ele |
| `spring.cloud.kubernetes.config` (PropertySource) | Não se aplica — **não ativo** hoje (só discovery) |

## 4. Build e deploy

- O framework **0.1.0-beta.24.6** está publicado no Nexus (`igrp-framework-releases`) — os `docker build`
  resolvem-no de lá, sem `~/.m2` local. (24.5 acrescentou o `accept-also` multi-frontend; a 24.6
  acrescenta a via M2M. Sem config/keys novas, o comportamento é idêntico ao da 24.4.)
- **Runtime Java 25 obrigatório** nas duas apps (bytecode do framework). Dockerfiles já pinados:
  build `maven:3.9.16-eclipse-temurin-25`, runtime `eclipse-temurin:25-jre`.
- Branches a fazer build: management `features/security-harding` · studio `feacture/security-harding`
  · monorepo `feature/irn-system-administration-integration` (já deployado no Nexus).
- Ordem entre apps é indiferente; o pré-requisito comum é o passo 1.

## 5. Smoke pós-deploy

Com um token Keycloak válido (`$TOK`) e uma sessão IRN válida (`$SESS`):

```bash
# 1) health público
curl -s -o /dev/null -w '%{http_code}\n' https://<mgmt>/actuator/health          # 200

# 2) sem token -> 401
curl -s -o /dev/null -w '%{http_code}\n' https://<mgmt>/areas                    # 401

# 3) token sem cookie de sessão -> 403 (o /me nunca é resolvido)
curl -s -o /dev/null -w '%{http_code}\n' -H "Authorization: Bearer $TOK" https://<mgmt>/areas   # 403

# 4) token + sessão com AREAS:visualizar -> 200
curl -s -o /dev/null -w '%{http_code}\n' -H "Authorization: Bearer $TOK" \
  -H "Cookie: session_id=$SESS" https://<mgmt>/areas                             # 200

# 5) rota sem regra -> 403 (deny-unmatched, mesmo para super admin)
curl -s -o /dev/null -w '%{http_code}\n' -H "Authorization: Bearer $TOK" \
  -H "Cookie: session_id=$SESS" https://<mgmt>/rota-inexistente                  # 403

# 6) studio: GET anónimo tem de dar 401 (regressão-chave)
curl -s -o /dev/null -w '%{http_code}\n' https://<studio>/api/v1/projects        # 401
```

Cada 403 gera uma linha de auditoria — é a ferramenta de diagnóstico número um:

```
WARN AuthorizationAuditListener : Authorization denied: GET /areas for [<sub>] (required any of: ROLE_DEPT_IGRP.superadmin,AREAS:visualizar)
```

Diz exatamente que permissão falta. Se um utilizador reporta 403, esta linha responde porquê.

## 6. Observabilidade

- Logs seguem por OTLP (starter OTel já configurado; `OTEL_DISABLED=false` + endpoint do collector).
- As negações levam atributos estruturados: `event=authorization_denied`,
  `http.request.method`, `url.path`, `enduser.id`, `security.required_authorities` — dá para
  alertar/agregar por rota ou utilizador e saltar do log para o trace do pedido.
- Caminho feliz de leitura não loga nada em INFO; mutações de negócio logam uma linha com IDs
  (sem PII — política estrita).

## 6b. Mudanças de default nesta vaga (verificar antes de subir)

Quatro defaults mudaram — um ambiente que dependia do valor antigo comporta-se de forma diferente:

- **Profile → `production`**: se corrias sem `SPRING_ACTIVE_PROFILE`, definir agora `development` ou
  `staging` onde for esse o caso.
- **CORS → fechado**: definir `IGRP_CORS_ALLOWED_ORIGINS` com os domínios dos frontends, senão as
  chamadas cross-origin do browser falham.
- **OIDC no arranque → fail-closed**: se o Keycloak não estiver disponível quando a app arranca, ela
  **não sobe**. Usar readiness probes e garantir a ordem de arranque.
- **Webhooks → SSRF-guarded**: um webhook para um serviço **interno** (loopback/IP privado) deixa de
  passar; listar o host em `IGRP_OUTBOUND_ALLOWED_HOSTS`. Verificar os process definitions que usam
  webhook delegates antes de subir.
- **Referências `$[VAR]` → restritas**: um process definition que refira `$[SOME_VAR]` fora de
  `IGRP_WEBHOOK_*` (email, tópico Kafka, payload, URL…) **falha no runtime** até a var estar em
  `IGRP_OUTBOUND_ALLOWED_ENV_VARS`. Inventariar os `$[...]` usados nos processos existentes e listar as
  vars legítimas; nunca listar segredos de infra (BD, Keycloak) — é o que a restrição protege.
  Para credenciais de webhook, a boa prática é nomeá-las `IGRP_WEBHOOK_*` e referenciar
  `Authorization: Bearer $[IGRP_WEBHOOK_XYZ]` no header (passa por proveniência, sem abrir extra-headers).

Imagens correm como **UID 1001** (non-root): volumes montados (ex. a chave privada) têm de ser legíveis
por esse UID — `chmod`/`fsGroup` conforme o caso.

## 7. Notas operacionais

- **Com `PRINCIPAL_CLAIM_NAME=email`, o identificador funcional do utilizador é um email** — aparece
  como `enduser.id` na auditoria e nos INFOs de ciclo de vida. Exceção deliberada e documentada à
  política de logs sem PII: é o ID que faz o match de tarefas funcionar no IRN.
- **Cache do `/Auth/me`: 5 minutos por sessão.** Alterações de permissões no System Administration
  demoram até 5 min a refletir-se (ou reinício da app / nova sessão). Não é bug.
- Se o IRN estiver em baixo, o enriquecimento falha **fechado**: utilizadores levam 403 (a app não
  cai) e o log marca `SECURITY: failed to enrich authorities`. Recupera sozinho — falhas não são
  cacheadas.
- `IRN_AUTHORIZATION_DENY_UNMATCHED=false` existe como escape para diagnóstico; não deixar em
  produção (controllers novos nasceriam abertos).
- E2E local reproduzível: `e2e/` no repo da management API (compose `irn-e2e`, mocks OIDC+IRN,
  `mint-token.sh`, overlay `docker-compose.eureka.yml` para testar service discovery).

## 8. Rollback

Reverter para a imagem anterior é suficiente (sem migrações de BD nesta release). Duas
consequências a assumir:

1. Reabrem os 51 CVEs do relatório Accenture (incl. bcprov CRÍTICO).
2. O Studio volta a servir GETs anónimos e a management API volta a aceitar qualquer token do
   realm em qualquer endpoint.

O rollback é operacionalmente trivial mas regressivo em segurança — tratar como último recurso e
com prazo curto.
