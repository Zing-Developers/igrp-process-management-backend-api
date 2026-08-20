# Guia de Instalação DevOps — Release 24.4 (Autorização IRN + CVE)

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
  filtros que o cliente enviar.
- O **super admin** é identificado pelo email do `/Auth/me` igual a `IRN_API_SUPER_ADMIN_EMAIL` —
  passa em todas as regras sem precisar de permissões.

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
| `IGRP_SECURITY_PRINCIPAL_CLAIM_NAME` | **`email`** em IRN (default `sub`) | identidade gravada em tarefas, colunas de auditoria e logs. Tem de bater com o formato das atribuições — o IRN atribui por email, senão o match "minhas tarefas" falha. Igual nas duas apps; decidir **antes** do go-live (mudar com dados existentes deixa tarefas antigas órfãs no match). Exige o scope `email` no token Keycloak. |
| `MANAGEMENT_HEALTH_MAIL_ENABLED` | `false` se não houver SMTP | **management API**: sem SMTP o `MailHealthIndicator` põe `/actuator/health` a 503 e mata os probes do k8s |
| `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE` | URL do Eureka | se service discovery ativo |

A tabela de rotas (`irn.authorization.routes.*`) vem no `application.properties` de cada app e
normalmente não se toca; qualquer módulo/override novo é acrescentado lá, nunca no código.

## 4. Build e deploy

- O framework **0.1.0-beta.24.4** está publicado no Nexus (`igrp-framework-releases`) — os
  `docker build` resolvem sem `~/.m2` local.
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
