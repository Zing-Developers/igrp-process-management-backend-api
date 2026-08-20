# Changelog — Plataforma de Process Management IRN

## 2026-08 (b) · Endurecimento de segurança (pós-24.4)

Segunda vaga sobre a release 24.4, a fechar itens abertos do `docs/SECURITY_RECOMMENDATIONS.md`.
Só configuração e código de app — **framework 24.4 mantém-se** (exceto o item residual assinalado no
fim). Ver o novo estado na tabela de prioridades do `SECURITY_RECOMMENDATIONS.md` (Feito 11 · Aberto 3).

### ⚠️ Breaking / mudanças de comportamento

1. **CORS deixa de ser wildcard.** Sem `IGRP_CORS_ALLOWED_ORIGINS` definido, **nenhuma** origem
   cross-origin é aceite (antes: qualquer origem, com credenciais). Frontends noutro domínio param de
   funcionar até a origem ser listada. Ver guia DevOps §3.
2. **Profile default passa a `production`.** Sem `SPRING_ACTIVE_PROFILE`, a app arranca em `production`
   (antes: `development`, com `ddl-auto=update` e `show-sql`). Ambientes que dependiam do default de
   desenvolvimento têm de o definir explicitamente.
3. **OIDC inacessível no arranque = arranque falha.** O JWT decoder deixou de devolver um decoder que
   rejeita tudo silenciosamente; se o Keycloak estiver em baixo ao arrancar, a app **não sobe** (usar
   readiness probes). Falha visível em vez de degradação silenciosa.
4. **Webhooks: destinos internos bloqueados.** Chamadas de saída dos delegates (webhook + assignment)
   para loopback/IPs privados/metadata deixam de passar. Um webhook legítimo para um serviço interno
   exige agora o host em `IGRP_OUTBOUND_ALLOWED_HOSTS`. HTTPS obrigatório fora de `development`.
5. **Imagens correm como não-root** (UID 1001) — volumes/paths montados têm de ser legíveis por esse UID.

### Segurança — itens fechados

- **SSRF nos webhooks (P0)** — `OutboundRequestGuard` em todos os delegates com URL vinda de variáveis
  de processo: bloqueia loopback/RFC1918/link-local (incl. `169.254.169.254` metadata)/CGNAT/IPv6-ULA/
  irresolúveis/`user:pass@`; allowlist opcional (`igrp.delegate.outbound.allowed-hosts`, exato ou
  `*.sufixo`); HTTPS obrigatório fora de dev; headers de credenciais (`Authorization`/`Cookie`/`Proxy-*`/
  `X-Forwarded-*`) filtrados das variáveis de processo; respostas limitadas a 1MB; **redirects
  desativados** no client (o factory Apache seguia-os por default — um destino público podia 302 para o
  espaço bloqueado). 8 testes unitários.
- **JWT decoder fail-closed (P0)** — try/catch removido; arranque falha se o OIDC estiver inacessível.
- **CORS restrito (P0)** — origens por `IGRP_CORS_ALLOWED_ORIGINS`; vazio = sem cross-origin; wildcard
  eliminado nas duas apps.
- **Erros saneados (P1)** — `GlobalExceptionHandler`: NPE/IllegalState/PSQL/Jackson só no log do
  servidor; `IllegalArgument` e exceções de engine mantêm as mensagens de negócio (o frontend depende).
- **Segredos (P1)** — default público `delegate-secret-token` removido; password de exemplo → placeholder.
- **Fuga do OpenAPI (P2)** — `springdoc.api-docs.enabled` amarrado ao `ENABLE_SWAGGER`: o `/v3/api-docs`
  deixou de servir o contrato completo anonimamente em staging (e em production no Studio). O toggle de UI
  sozinho não o cobria — **achado do deep test**.
- **Defaults seguros (P2)** — profile `production`, swagger staging `false`, `USER 1001` non-root nos 2
  Dockerfiles.

### Validação

- Testes: management **300/300** (8 novos do `OutboundRequestGuard`).
- Deep test (Docker, 35 verificações, 5 perfis): fail-closed provado com container de issuer inválido
  (morre em ~4s), CORS nos dois sentidos, imagem non-root com build **real contra o Nexus** (`id -un` →
  `appuser`), erros saneados com corpos como evidência, matriz de autorização intacta.

### Residual (framework, tarefa separada)

Com `igrp.restclient.provider=irn`, os delegates usam o RestClient **assinado**, cujo interceptor anexa o
token RS256 da plataforma a **todos** os pedidos — incluindo webhooks para terceiros. A validação de URL
aplica-se na mesma (o guard vive nos delegates), mas a separação do cliente assinado é uma correção do
monorepo (prevista para 24.5).

### Ficheiros

**Management** (`features/security-harding`): `7e1906e` (quick-wins), `439f9ef` (fuga api-docs),
`70fa329` (SSRF). Novos: `shared/delegates/outbound/{OutboundRequestGuard,OutboundGuardProperties}.java`
+ teste; `e2e/docker-compose.deeptest.yml`.
**Studio** (`feacture/security-harding`): `4bc8280` (quick-wins), `e1cb759` (fuga api-docs).

---

## 2026-08 · Autorização IRN + Remediação CVE

**Framework:** `cv.igrp.framework:*` **0.1.0-beta.24.4** (publicado no Nexus `igrp-framework-releases`)
**Apps:** management API (branch `features/security-harding`) · studio API (branch `feacture/security-harding`)

### ⚠️ Breaking changes

1. **Fail closed por omissão.** Toda a rota de negócio exige agora uma permissão IRN (`MODULO:acao`
   vinda do `/Auth/me`). Sem as permissões registadas no System Administration **e associadas aos
   perfis**, qualquer utilizador autenticado recebe **403** em tudo exceto health/swagger.
   Registar as permissões **antes** do deploy (ver guia DevOps).
2. **Studio: fim dos GET anónimos.** `requestMatchers(GET).permitAll()` foi removido — listar
   projetos/diagramas passa a exigir token + sessão + permissão.
3. **Java 25 obrigatório em runtime.** Os jars do framework 24.x são bytecode Java 25; um JRE
   inferior morre com `UnsupportedClassVersionError`. O Dockerfile do Studio passou de chainguard
   `latest` para `eclipse-temurin:25-jre` pinado (o da management API já lá estava).
4. **`IGRP_AUTHORIZATION_SERVICE_ADAPTER=igrp` é inválido** nestas apps (o módulo auth-igrp não está
   no classpath): o arranque falha. Valores válidos: `irn` (produção IRN) ou `default` (sem regras).
5. **Pedidos ao backend exigem dois credenciais:** `Authorization: Bearer <jwt keycloak>` **e**
   `Cookie: session_id=<sessão IRN>`. Frontends que não enviem o cookie recebem 403.

### Framework 0.1.0-beta.24.4 (monorepo)

- **Novo — SPI de autorização de rotas** (`process-runtime-auth-core`): `IRouteAuthorizationAdapter`
  + `RouteAuthorizationRule`; adapter no-op por omissão via `@ConditionalOnMissingBean`.
- **Novo — adapter de rotas IRN** (`process-runtime-auth-irn`): deriva regras Spring Security da
  configuração `irn.authorization.routes.*` — ação pelo método HTTP (GET→`visualizar`, POST→`criar`,
  PUT/PATCH→`editar`, DELETE→`eliminar`) com *overrides* ordenados (pesquisas por POST; `deploy`
  exige ação própria `publicar`).
- **`/Auth/me` ativado e cacheado**: `getPermissions()` deixou de devolver vazio; `IrnMeCache`
  reduz 3 chamadas HTTP por pedido a **1 por sessão** (TTL 5 min, Caffeine). Sessão nula
  curto-circuitada antes do interceptor de cache.
- Beans IRN condicionados a `adapter=irn` — deployments não-IRN arrancam sem `RestClient`.
- **Parent Spring Boot 3.5.3 → 3.5.14** — desbloqueia jackson/spring/spring-security nos
  consumidores (o BOM importado prendia-os aos níveis de 3.5.3).
- **bcprov-jdk18on 1.79 → 1.84** — CVE-2025-14813 (CRÍTICO) + CVE-2026-0636.

Commits: `0f7ca34` (SPI + adapter), `9e39cdc` (release 24.4).

### Management API

- **Autorização por rota** (fecha o P0 do SECURITY_RECOMMENDATIONS): `SecurityConfig` consome o
  adapter — nenhuma rota de negócio hardcoded; `denyAll()` para rotas sem regra (controllers novos
  gerados pelo Studio nascem fechados); super admin (`ROLE_DEPT_IGRP.superadmin`) passa em todas as
  regras; enriquecimento de authorities fail-closed (sem role de admin no fallback).
- **Visibilidade da pesquisa de tarefas fail-closed**: `user`/`candidateGroups`/`candidateUsers`/
  `filterByCurrentUser` enviados pelo cliente são ignorados salvo permissão
  `TASK_INSTANCES:pesquisar_todos` ou super admin.
- **Auditoria de 403**: um WARN estruturado por negação (método, path, utilizador, authorities
  exigidas) com atributos OTLP; 401 em DEBUG.
- **Higiene de logs PII-estrita**: payloads Kafka, corpos de webhook, endereços de email,
  identidades em regras de atribuição e formulários/variáveis de tarefas removidos dos logs em
  todos os níveis; um INFO por mutação concluída (IDs apenas).
- **Remediação CVE** (relatório Accenture, 51 CVEs / 17 grupos): jackson 2.21.6, spring 6.2.18,
  spring-security 6.5.10 (pin `oauth2-client:6.3.7` removido), tomcat 10.1.54, logback 1.5.38,
  spring-kafka 3.3.16, postgresql 42.7.13, commons-lang3 3.18.0, guava 33.7.1-jre, otel-api 1.62.0,
  bcprov 1.84. Gate OWASP `failBuildOnCVSS` 10 → 7.
- **Harness e2e Docker** (`e2e/`): stack completo com mocks OIDC + IRN, matriz de perfis, overlay
  Eureka (validação guava 33), cunhagem de tokens.

Commits: `11a895a` (segurança), `203fdc4` (logs), `2708d1d` (CVE), `9e05707` (e2e).

### Studio API

- **Autorização por rota + fim dos GET anónimos** (mesmo desenho da management API); catálogo
  `STUDIO_*` próprio; ordem de padrões garante que `/api/v1/projects/process-definitions` não é
  engolido pelo prefixo `/api/v1/projects`.
- **CSRF desativado** (API stateless de bearer tokens — estava ativo por o `disable` original viver
  num bloco comentado, e bloqueava **todas** as escritas com 403 antes das permissões).
- Claim de principal configurável (`igrp.security.principal-claim-name`, default `sub`) — alinhado
  com a management API; auditor com a mesma cadeia de fallback.
- **Logs**: um INFO por mutação de projeto/definição; parsing BPMN por elemento em DEBUG; cliente
  MOCK do engine promovido a WARN.
- **Mesma remediação CVE** (tinha exposição idêntica, incluindo o mesmo pin 6.3.7) + Java 25.

Commits: `814b7d9` (segurança + CVE), `e9d54f0` (logs), `3064c32` (Dockerfile).

### Validação

- Testes: monorepo 8/8 · management **292/292** · studio 18/19 (falha única = `contextLoads`
  pré-existente por `SERVICE_PORT` sem default).
- E2E (Docker, 36+39 verificações em duas rondas): matriz completa de perfis, overrides,
  independência `publicar`↛`criar`, `denyUnmatched` vence super admin, cache 3 pedidos → 1 `/Auth/me`.
- Performance (ab, stack local): leitura autorizada p50 6-7 ms / ~1100 rps c10, sustentado
  2800-3700 rps; negação 403 (com auditoria) **mais barata** que o caminho autorizado; cache do
  `/Auth/me` corta p50 de 10.7→4.4 ms com o mock local (ganho real maior com o IRN na rede).
- Guava 14→33 validado com Eureka **ligado** (overlay e2e): registo UP, renovações, zero erros de
  classe nos dois serviços.

### Pendentes

- Registo das 17+8 permissões no System Administration (pré-requisito do deploy — ver guia).
- Re-scan Accenture contra o build novo (os IDs de 2026 foram fechados por versão-alvo de grupo).
- Confirmação de rotina do guava/Eureka em staging.
- Push das três branches para os remotes.
