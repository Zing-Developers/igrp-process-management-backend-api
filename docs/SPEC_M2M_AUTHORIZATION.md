# Spec — Autorização machine-to-machine (M2M)

> Revisto após painel de design (segurança + arquitetura + boas práticas Spring, coordenado, decisões
> arbitradas com o utilizador a 2026-08-27). As alterações face ao rascunho inicial estão nas decisões
> M-7r e M-10..M-15.

## 1. Problema

Outro backend (um **job**) chama esta API com um token Keycloak válido, mas **sem sessão IRN**. O token
passa a autenticação do resource server; falha logo a seguir, quando a autorização tenta resolver o
utilizador em `GET /Auth/me` do IRN — que exige a sessão do cookie. Resultado:

```
IrnAuthClient: Client error from IRN API: 401 UNAUTHORIZED
IrnMeCache:    SECURITY: failed to resolve the current user from IRN
```

O caminho de autorização atual assume sempre um **utilizador de browser com sessão IRN**. Um chamador de
máquina não tem sessão, e não conseguimos configurar o Keycloak do lado deles.

## 2. Decisão

Uma via **M2M** com **credencial única** que **nós** emitimos, controlamos e revogamos: uma **API key
opaca** que autentica e autoriza a chamada, sem tocar no IRN nem no Keycloak.

| # | Decisão |
|---|---|
| M-1 | **Credencial única.** A key M2M autentica **e** autoriza; sem Bearer Keycloak em paralelo. O painel de segurança validou o trade-off: um segundo bearer no mesmo canal TLS seria roubado junto em qualquer compromisso realista — a defesa real é ortogonal (fronteira de rede, barreira estrutural nas rotas de gestão, validação de permissões). |
| M-2 | **Nós emitimos e enviamos a key** (o parceiro não configura nada no Keycloak dele). |
| M-3 | **Prefixo no `Authorization`**: `Bearer igrpm2m_…` → via M2M; `Bearer eyJ…` (JWT) → caminho IRN atual. Mesmo header, erros RFC 6750 consistentes; precedente GitHub (`ghp_`)/Stripe. Um JWT compacto começa sempre por `eyJ` — colisão impossível. |
| M-4 | **Authorities da nossa config em `MODULO:acao`**, injetadas para **reutilizar as regras de rota** existentes. Sem modelo de autorização paralelo. |
| M-5 | **Store em BD** (tabela na mgmt) — revogação em runtime, sem restart. Config seria redeploy-para-revogar e hashes em git. |
| M-6 | **Gestão super-admin only** — APIs + UI para criar/listar/revogar/rodar keys. |
| M-7r | **Identidade = `m2m:<client_name>`, sempre.** (Revisto: o rascunho usava o email como identidade — o painel de segurança demonstrou que isso é spoofing de atribuição: ações de máquina indistinguíveis das do humano nas colunas `createdBy`/`startedBy`, com risco de repúdio e de incriminação.) O `email` é **metadado de contacto** opcional na tabela — nunca identidade, nunca em logs. |
| M-8r | **No `process-runtime-auth-core`**, não no `auth-irn`. (Revisto: M2M não tem nada de IRN, e o `auth-irn` está todo gated em `adapter=irn` — a via M2M tem de funcionar também com `adapter=default`.) É onde já vivem as SPIs e os defaults no-op. |
| M-9 | **Faseado.** Fase 1 desbloqueia o job (inclui **revogação**); Fase 2 é a consola. |
| M-10 | **Sem filtro custom: `authenticationManagerResolver` + `OpaqueTokenIntrospector`.** O mecanismo nativo do resource server para dois tipos de token no mesmo header. Elimina o filtro, o principal custom, o plumbing de `SecurityContext` e de 401 — e o bug real de o `BearerTokenAuthenticationFilter` não saltar contexto já autenticado. Convergência dos três revisores. |
| M-11 | **Validação das permissões na criação e na resolução**: regex `^[A-Z0-9_.]+:[a-z_]+$`, rejeição dura de `ROLE_*`/`GROUP_*`. Sem isto, uma key com `ROLE_DEPT_IGRP.superadmin` no campo `permissions` passa em todas as rotas (o `withSuperAdmin` anexa essa role a todas as regras). **Blocker de Fase 1.** |
| M-12 | **Rotas `/m2m-keys/**` com gate dedicado no `SecurityConfig`** — nunca no catálogo de rotas — e **estruturalmente barradas a M2M**: exigem `JwtAuthenticationToken` + super-admin. Uma key nunca pode cunhar keys, independentemente das permissões que carregue. |
| M-13 | **`DELETE /m2m-keys/{id}` na Fase 1.** Criar sem revogar é um loop de segurança incompleto; a revogação é o headline do design e tem de existir quando o risco é maior (o início). |
| M-14 | **HMAC-SHA-256 com pepper do servidor** (env/secret manager) em vez de SHA-256 puro — um dump da tabela fica inútil sem o segredo da app. Comparação com `MessageDigest.isEqual`. |
| M-15 | **Rede: fronteira do cluster.** Estamos em Kubernetes sem exposição externa e sem IPs estáticos dos chamadores — IP allowlist por key na app não faz sentido (pods efémeros). A restrição de origem faz-se com NetworkPolicy/ingress do cluster, não em código. |
| M-16 | **`ROLE_ACTIVITI_USER` auto-concedida no código** ao principal M2M — espelha o caminho JWT, que a dá a todos os autenticados (o motor exige-a para operar tarefas). **Nunca** `ROLE_ACTIVITI_ADMIN`. A M-11 continua a proibir `ROLE_*` no campo `permissions`: a role base vem do código, não da BD. |
| M-17 | **Backend completo nos DOIS backends, já** (mgmt + Studio): cada um com a sua tabela, impl da SPI e APIs `/m2m-keys` completas (POST/GET/DELETE/rotate). Keys são credenciais por-app — chamadores M2M do Studio usam keys do Studio. |
| M-18 | **Grace da rotação configurável por env** (`IGRP_M2M_ROTATE_GRACE`, default `7d`). |

## 3. Contrato do request

```
Authorization: Bearer igrpm2m_<32 bytes base64url>   → M2M
Authorization: Bearer eyJhbGciOiJSUzI1NiIs...        → JWT normal (Keycloak + sessão → IRN)
```

- **M2M:** key válida na BD → autenticado como `m2m:<client_name>` com as authorities da key. O IRN
  (`/Auth/me`) **não** é chamado.
- **JWT:** caminho atual, intocado.
- Key desconhecida/revogada/expirada → **401 `invalid_token`** (idêntico byte a byte a um JWT inválido —
  sai pelo entry point do resource server). Nunca cai para o caminho JWT.
- TLS obrigatório ponta a ponta; a key nunca em URLs.

## 4. Fluxo de autorização

```
oauth2ResourceServer(o -> o.authenticationManagerResolver(resolver))
  resolver (por request):
    token começa por "igrpm2m_"?
      SIM → ProviderManager(OpaqueTokenAuthenticationProvider(m2mIntrospector))
              m2mIntrospector (adapter sobre a SPI M2mKeyResolver):
                HMAC(key) → lookup BD (active, não expirada)
                  encontra → OAuth2AuthenticatedPrincipal(name="m2m:"+client_name,
                             authorities=permissions MODULO:acao)
                  não      → BadOpaqueTokenException → 401 invalid_token
      NÃO → AuthenticationManager JWT existente (converter atual → sessão → IRN)
  → SecurityConfig aplica as MESMAS regras de rota (hasAnyAuthority)
```

Notas de fluxo:
- O resultado M2M é um `BearerTokenAuthentication` **autenticado** — os guards
  `isAuthenticated()` do `SecurityUserContext` passam; `getName()` = `m2m:<client_name>` flui para
  Activiti (`startedBy`), auditoria (`AuthorizationAuditListener`) e `createdBy`.
- O `IAMUserProfileSyncFilter` ignora auth não-JWT (correto — máquinas não têm perfil IAM). Consequência
  documentada: `startedBy` M2M nunca resolve contra `user_profile`; UIs que fazem esse join têm de
  tolerar a ausência (já toleram — o enrichment devolve `null` e fica a string crua).
- `created_by` nas colunas de auditoria passa a poder conter `m2m:<client>` além de UUID/email — os
  consumidores tratam-no como string opaca (já é o caso).

## 5. Modelo de dados (mgmt, Flyway)

Tabela `m2m_api_key`:

| Coluna | Tipo | Notas |
|---|---|---|
| `id` | uuid | PK |
| `client_name` | text | **slug** (`^[a-z0-9._-]+$`), identidade `m2m:<client_name>`; único |
| `key_prefix` | text | primeiros chars (ex. `igrpm2m_ab12`) — identificação sem expor; nunca usado para lookup |
| `key_hash` | text | **HMAC-SHA-256(key, pepper)**; único, indexado |
| `permissions` | text | lista `MODULO:acao` por vírgulas (any-of); validada por M-11 |
| `email` | text NULL | **contacto/dono** (M-7r) — nunca identidade, nunca em logs |
| `active` | boolean | revogação em runtime = `false` |
| `expires_at` | timestamptz NULL | expiração opcional; o `rotate` estampa-a na key antiga |
| `created_by` | text | super-admin que criou |
| `created_at` | timestamptz | |
| `last_used_at` | timestamptz NULL | atualização **assíncrona/throttled** (≥60s), nunca no caminho de auth |
| `revoked_at` | timestamptz NULL | trilho de auditoria |

## 6. Desenho técnico

### 6.1 Framework — `process-runtime-auth-core` (M-8r)

- **SPI `M2mKeyResolver`**: `Optional<M2mKey> resolve(String rawKey)` — `M2mKey{clientName, permissions}`.
  Default no-op (`Optional.empty()`, `@ConditionalOnMissingBean`) em `CoreAuthorizationAutoConfiguration`
  — mesmo padrão do `DefaultRouteAuthorizationAdapter`. Studio (ou qualquer app sem impl) fica inerte.
- **`M2mOpaqueTokenIntrospector`**: adapter `OpaqueTokenIntrospector` sobre a SPI. Devolve
  `DefaultOAuth2AuthenticatedPrincipal("m2m:"+clientName, attrs, authorities)`; lança
  `BadOpaqueTokenException` em key desconhecida/revogada/expirada **e** em falha de BD (**fail closed** —
  nunca cair para o caminho JWT), logando apenas o `key_prefix`.
- **Factory do `AuthenticationManagerResolver`**: dado o `AuthenticationManager` JWT existente e o
  introspector, devolve o resolver por-request que decide pelo prefixo `igrpm2m_`.
- **Sem filtro, sem principal custom, sem plumbing de contexto** — tudo dentro do
  `BearerTokenAuthenticationFilter` nativo (M-10). Rollout trap-free: classes aditivas, nada
  auto-registado; cada app **opta** trocando a wiring (abaixo). Versão `0.1.0-beta.24.6`.

### 6.2 App — mgmt

- **Wiring no `SecurityConfig`**: trocar `.oauth2ResourceServer(o -> o.jwt(...))` por
  `.oauth2ResourceServer(o -> o.authenticationManagerResolver(m2mAwareResolver))` — uma linha; o
  converter JWT atual mantém-se dentro do manager JWT.
- **Gate dedicado (M-12), antes do loop do catálogo**:
  `requestMatchers("/m2m-keys/**").access(jwt-only AND super-admin)` — um `AuthorizationManager` que
  exige `authentication instanceof JwtAuthenticationToken` e a role de super-admin. Fail-closed e
  não-provisionável via IRN.
- **Impl da SPI** sobre a tabela: HMAC do input, lookup por hash (`MessageDigest.isEqual` na
  comparação), mapeia `permissions` → authorities (revalidando M-11 na leitura), `last_used_at`
  assíncrono/throttled e best-effort (falha de escrita nunca afeta a decisão de auth).
- **Migração Flyway** com a tabela.
- **APIs de gestão (super-admin only, Fase 1 = POST + DELETE):**
  - `POST /m2m-keys` → valida `client_name` (slug) e `permissions` (M-11), gera a key, devolve o
    **plaintext uma vez** (sem logging de request/response body nesta rota), guarda só o hash.
  - `DELETE /m2m-keys/{id}` → revoga (`active=false`, `revoked_at=now`). Efeito imediato (sem cache).
  - Fase 2: `GET /m2m-keys` (sem segredos), `POST /m2m-keys/{id}/rotate` — a rotação estampa
    automaticamente `expires_at = now() + grace` (ex. 7 dias) na key antiga, para o overlap não viver
    para sempre.

### 6.3 Geração da key

- `igrpm2m_` + 32 bytes `SecureRandom` em base64url — prefixo **detetável** em varrimento de segredos.
- Persiste-se `HMAC-SHA-256(key, pepper)`; o pepper vem de env/secret manager, nunca da BD. O plaintext
  nunca persiste nem se loga.

## 7. Segurança e leak

- **Revogação em runtime** (M-5/M-13): `active=false` → efeito imediato. **Sem cache** no lookup (índice
  por hash é barato); se a latência algum dia justificar, teto de 30s.
- **Rotação** sem downtime com corte forçado: duas keys ativas durante o grace, a antiga expira sozinha.
- **Least-privilege**: `permissions` só cobre as rotas que o job usa; formato validado (M-11); rotas de
  gestão estruturalmente fora do alcance de qualquer key (M-12).
- **Rede** (M-15): sem exposição fora do cluster; NetworkPolicy/ingress como fronteira. TLS sempre.
- **Auditoria**: cada chamada M2M identifica-se como `m2m:<client_name>` nos logs e nas colunas de
  auditoria; `last_used_at` para deteção de anomalia. **Email nunca em logs** (política no-PII); a key
  crua nunca é logada (só `key_prefix`); verificar que access logs não despejam `Authorization`.
- **Fail closed**: BD inacessível → 401 no M2M (o caminho JWT continua a funcionar).
- **Trade-off assumido (M-1):** credencial única — segurança assenta na key + BD + fronteira do cluster.

## 8. Faseamento

- **Fase 1 — backend completo (agora).** Framework 24.6 (`auth-core`: SPI + introspector + resolver
  factory, default no-op) · **mgmt E Studio** (M-17), cada um com: wiring do resolver, gate
  `/m2m-keys/**`, tabela Flyway, impl da SPI, e as **quatro** APIs — `POST` (criar), `GET` (listar sem
  segredos), `DELETE` (revogar), `POST /{id}/rotate` (grace por env, M-18) — com validação M-11 e
  auto-grant M-16. Gera-se a key, entrega-se ao parceiro, o 401 desaparece — com revogação e rotação
  disponíveis desde o dia um.
- **Entre fases — handoff frontend.** Protótipo + documentação das APIs `/m2m-keys` (contratos,
  fluxos de ecrã, one-time display da key) para passar à equipa de frontend antes da Fase 2.
- **Fase 2 — UIs.** Consolas super-admin nos frontends do Process Management **e** do Studio, cada uma
  contra o seu backend; visualização de `last_used_at`. Arranca com a tua luz verde.

## 9. Fechado em painel / ainda aberto

Fechado (painel + arbitragem):
- Header: fica o `Authorization: Bearer` com prefixo (vs `X-API-Key`) — M-3.
- Cache do lookup: começa **sem** cache — revogação instantânea.
- IP allowlist: **não** na app (M-15) — fronteira do cluster.
- Rotas de gestão: gate dedicado, fora do catálogo (M-12).
- Identidade: `m2m:<client_name>` (M-7r).

Fechado (2ª ronda de arbitragem):
- Roles Activiti: auto-grant `ROLE_ACTIVITI_USER` no código, nunca ADMIN (M-16).
- UI: Fase 2, nos frontends do Process Management e do Studio, com protótipo + docs de handoff antes.
- Studio: stack completa própria (tabela + SPI impl + APIs) já na Fase 1 (M-17).
- Grace da rotação: env `IGRP_M2M_ROTATE_GRACE`, default 7 dias (M-18).

Aberto:
- Canal de entrega da key ao parceiro (secret manager / one-time link) — decisão de infra.
