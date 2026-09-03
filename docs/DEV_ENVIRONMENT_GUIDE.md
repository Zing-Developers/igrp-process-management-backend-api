# Guia de Ambiente de Desenvolvimento

Como pôr a **management API** e o **Studio API** a correr localmente, sem IRN e sem infraestrutura de
produção. Cobre as duas apps; as diferenças estão assinaladas. Para staging/produção, o documento é o
`DEVOPS_DEPLOYMENT_GUIDE.md` — este guia usa deliberadamente valores que **nunca** devem sair de dev.

## 0. Duas maneiras de correr

| Modo | Quando usar | IRN? |
|---|---|---|
| **A. Stack e2e Docker** (`e2e/docker-compose.e2e.yml`, projeto `irn-e2e`) | Testar a autorização IRN completa (permissões, sessões, perfis) | Mock WireMock + OIDC mock, já configurados — ver `e2e/README.md` |
| **B. App local com `adapter=default`** | Desenvolver funcionalidades sem se preocupar com permissões | Não é preciso — só autenticação |

O resto deste guia é o **modo B**. Pré-requisitos: Java **25**, Postgres, e um emissor OIDC (Keycloak
local, ou o `mock-oidc` da stack e2e em `http://localhost:18090/realms/e2e`).

## 1. Perfil e comportamento em dev

```bash
SPRING_ACTIVE_PROFILE=development
```

O default é `production` — **tem de se definir explicitamente**. Em `development`:
`ddl-auto=update` + `show-sql` ligados, Swagger UI disponível, HTTPS não exigido nos webhooks.
O auditor (`created_by`/`last_modified_by`) preenche-se em **todos** os perfis a partir do token
(o Studio tinha um guard que gravava string vazia em dev/staging — removido).

## 2. Variáveis obrigatórias (o boot falha sem elas)

Estes placeholders não têm default no `application.properties` — a app morre no arranque com
`Could not resolve placeholder` se faltarem. Valores de dev:

```bash
# --- Base de dados (as duas apps; cada uma com a sua BD) ---
POSTGRES_HOST=localhost
POSTGRES_PORT=5432
POSTGRES_DATABASE=igrp_dev            # studio: igrp_studio_dev
POSTGRES_USER=igrp_dev
POSTGRES_PASSWORD=igrp_dev

# --- OIDC (autenticação; o arranque FALHA se o emissor estiver inacessível — fail closed) ---
AUTH_JWT_ISSUER=http://localhost:8180/realms/dev    # o teu Keycloak local

# --- Infra que não existe em dev: desligar/apontar para lado nenhum ---
KUBERNETES_DISCOVERY_ENABLED=false
EUREKA_CLIENT_ENABLED=false
OTEL_COLLECTOR_ENDPOINT=http://localhost:4318       # com OTEL_DISABLED=true nunca é contactado
OTEL_DISABLED=true
SERVICE_PORT=8080                                   # studio: 8082 (o springdoc lê-o — sem ele o boot morre)

# --- Management API apenas: mail e kafka têm placeholders sem default ---
SPRING_MAIL_HOST=localhost
SPRING_MAIL_USERNAME=x
SPRING_MAIL_PASSWORD=x
MANAGEMENT_HEALTH_MAIL_ENABLED=false                # senão o health fica 503 sem SMTP real
SPRING_KAFKA_USERNAME=x
SPRING_KAFKA_PASSWORD=x
IGRP_MESSAGE_BROKER_PROVIDER=none                   # sem Kafka/Rabbit em dev
```

> **Studio apenas:** `DATABASE_URL`/`DATABASE_USERNAME`/`DATABASE_PASSWORD` (perfis não-dev usam estes
> nomes) e `IGRP_PROCESS_ENGINE_BASE_URL` — vazio liga o **cliente mock** de deploy (o BPMN não chega a
> motor nenhum); aponta-o a `http://localhost:8080` se tiveres a management API local.

## 3. Autorização em dev — `adapter=default`

```bash
IGRP_AUTHORIZATION_SERVICE_ADAPTER=default
```

Sem IRN: **qualquer utilizador autenticado passa em qualquer rota de negócio** (não há permissões nem
regras — só autenticação). Consequências:

- O catálogo `MODULO:acao`, o `accept-also` e o `deny-unmatched` ficam inertes (são do adapter `irn`).
- `pesquisar_todos` não existe — cada um vê só as suas tarefas.
- **Super-admin (24.7):** por omissão ninguém é. Para testares as áreas gated a super-admin (a consola
  `/m2m-keys`), define:

```bash
IGRP_DEFAULT_SUPER_ADMIN_EMAIL=dev@local.test      # o claim email do TEU token Keycloak de dev
```

Um JWT cujo claim `email` bata (case-insensitive) ganha a role de super-admin. **Só existe no adapter
default; nunca definir em staging/produção.**

## 4. M2M em dev

```bash
IGRP_M2M_KEY_PEPPER=                # vazio é aceitável EM DEV (obrigatório em produção)
IGRP_M2M_ROTATE_GRACE=7d
```

Fluxo: com o `IGRP_DEFAULT_SUPER_ADMIN_EMAIL` definido, cria uma key via
`POST /m2m-keys` (Swagger UI serve) e usa-a com `Authorization: Bearer igrpm2m_…` — sem cookie, sem
Keycloak. Nota: mudar o pepper depois invalida as keys criadas.

## 5. O resto — defaults de dev já corretos

Estas têm default são e só se mexem se precisares do comportamento:

```bash
IGRP_CORS_ALLOWED_ORIGINS=http://localhost:3000    # vazio = frontend noutro porto NÃO fala com a API
ENABLE_SWAGGER=true                                # UI + /v3/api-docs (default true em development)
IGRP_SECURITY_PRINCIPAL_CLAIM_NAME=sub             # em IRN é email; em dev tanto faz, mas sê consistente
IGRP_OUTBOUND_REQUIRE_HTTPS=false                  # já é o comportamento do profile development
IGRP_OUTBOUND_ALLOWED_HOSTS=localhost              # para testares webhooks contra um servidor local
IGRP_OUTBOUND_ALLOWED_ENV_VARS=IGRP_WEBHOOK_*
IGRP_DELEGATE_WEBHOOK_AUTH_TOKEN=
```

## 6. `.env` completo de arranque rápido (management API)

O `spring.config.import=optional:file:.env[.properties]` carrega um `.env` na raiz do repo — cola isto
e ajusta o issuer:

```properties
SPRING_ACTIVE_PROFILE=development
SERVICE_PORT=8080
POSTGRES_HOST=localhost
POSTGRES_PORT=5432
POSTGRES_DATABASE=igrp_dev
POSTGRES_USER=igrp_dev
POSTGRES_PASSWORD=igrp_dev
AUTH_JWT_ISSUER=http://localhost:8180/realms/dev
KUBERNETES_DISCOVERY_ENABLED=false
EUREKA_CLIENT_ENABLED=false
OTEL_DISABLED=true
OTEL_COLLECTOR_ENDPOINT=http://localhost:4318
SPRING_MAIL_HOST=localhost
SPRING_MAIL_USERNAME=x
SPRING_MAIL_PASSWORD=x
MANAGEMENT_HEALTH_MAIL_ENABLED=false
SPRING_KAFKA_USERNAME=x
SPRING_KAFKA_PASSWORD=x
IGRP_MESSAGE_BROKER_PROVIDER=none
IGRP_AUTHORIZATION_SERVICE_ADAPTER=default
IGRP_DEFAULT_SUPER_ADMIN_EMAIL=dev@local.test
IGRP_CORS_ALLOWED_ORIGINS=http://localhost:3000
```

Studio: igual, trocando `SERVICE_PORT=8082`, a BD, e acrescentando
`IGRP_PROCESS_ENGINE_BASE_URL=http://localhost:8080` (ou vazio para o mock).

## 7. Verificação rápida

```bash
curl -s localhost:8080/actuator/health            # 200 {"status":"UP"}
# token do teu Keycloak dev com o email do escape:
curl -s -o /dev/null -w '%{http_code}\n' localhost:8080/m2m-keys -H "Authorization: Bearer $TOK"   # 200 = super-admin ok
curl -s -o /dev/null -w '%{http_code}\n' localhost:8080/areas -H "Authorization: Bearer $TOK"      # 200 = auth-only ok
```

Swagger UI: `http://localhost:8080/swagger-ui.html`.

## 8. O que NUNCA levar de dev para produção

| Variável | Em produção |
|---|---|
| `IGRP_AUTHORIZATION_SERVICE_ADAPTER=default` | `irn` — o default deixa qualquer autenticado em qualquer rota |
| `IGRP_DEFAULT_SUPER_ADMIN_EMAIL` | **vazia/ausente** — o escape é só de dev |
| `IGRP_M2M_KEY_PEPPER` vazio | segredo forte de secret manager |
| `SPRING_ACTIVE_PROFILE=development` | `production` (ou omitir) |
| `OTEL_DISABLED=true` | `false`, com collector real |
| `ENABLE_SWAGGER=true` | `false` |
