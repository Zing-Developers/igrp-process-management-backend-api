# Changelog — Plataforma de Process Management IRN

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
