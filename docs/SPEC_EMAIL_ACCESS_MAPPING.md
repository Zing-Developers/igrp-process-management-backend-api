# Spec: Mapeamento de acesso por email (framework 24.9, hasSession no 24.10)

> Decidido em reunião a 2026-09-11, com revisão de segurança ao desenho. Aplica-se à management API e
> ao Studio API, cada um com a sua tabela e a sua consola.

## 1. Problema

O mesmo do `SPEC_M2M_AUTHORIZATION.md` §1: um backend externo chama a API com um token Keycloak
válido mas **sem sessão IRN**; passa a autenticação e chumba na autorização, porque o adaptador IRN só
resolve permissões via cookie `session_id` + `/Auth/me`. O M2M resolveu-o com uma credencial nossa,
o que passou para devops a distribuição e a rotação de segredos fora do Keycloak. Os integradores
já têm tokens Keycloak (client credentials) com claim `email`; a equipa não quer alterar isso.

## 2. Decisão

Continuar a usar o token Keycloak. Uma tabela **email → permissões**, gerida por super admin, é
consultada quando o pedido **não traz sessão IRN**, e concede ao portador do token as permissões
`MODULO:acao` mapeadas ao claim `email` do JWT validado.

| # | Decisão |
|---|---|
| E-1 | **Sessão primeiro.** Com cookie `session_id`, o IRN decide como hoje e uma falha do IRN continua a negar. Não há fallback ao mapeamento com sessão presente: reviveria utilizadores que o IRN revogou. Só um pedido sem cookie (ou com cookie em branco) vai ao mapeamento. É a regra que o 24.8 já aplicava ao super admin. |
| E-2 | **Identidade = claim `email` do JWT validado**, trimado e em minúsculas. Sem exigir `email_verified` (decisão da equipa). A fronteira de confiança é quem administra o realm: ver pré-condições em §6. Alternativa registada e não adoptada: chavear por `azp`/`client_id`, que fecharia essas pré-condições com uma coluna; reabrir se o realm não as puder cumprir. |
| E-3 | **Só `MODULO:acao`.** Mesma regex do M2M (`^[A-Z0-9_.]+:[a-z_]+$`) mais rejeição dos prefixos `ROLE_`/`GROUP_`, que a regex sozinha deixava passar (`ROLE_X:y`). Validado na escrita pela app e na leitura pelo framework (`PermissionFormat`, partilhado com o introspector M2M). O mapeamento nunca dá grupos, nem super admin, nem `ROLE_ACTIVITI_ADMIN`. |
| E-4 | **Quem não está mapeado fica como hoje**: sem sessão, zero permissões, 403 nas rotas do catálogo. |
| E-5 | **SPI no `process-runtime-auth-core`, store em cada app**, como o M2M: `EmailAccessResolver` com default no-op; tabela `t_email_access_mapping`, `DbEmailAccessResolver` e `/email-access-mappings` em cada backend. |
| E-6 | **Gestão por permissão do catálogo, só com sessão IRN.** As rotas `/email-access-mappings` entram no catálogo como qualquer outra (`EMAIL_ACCESS_MAPPINGS:visualizar/criar/editar/eliminar`, `STUDIO_` no Studio, com `accept-also` por env para o código real do frontend), mas o `SecurityConfig` só aceita a permissão num pedido **com sessão IRN**, perguntando ao adaptador (`IAuthorizationServiceAdapter.hasSession`, 24.10), a mesma regra que decide o caminho de sessão do próprio adaptador: com sessão o mapeamento nunca é consultado (E-1), logo a permissão veio do System Administration. Um token mapeado não tem sessão e um cookie forjado, ou um par de cookies com o primeiro vazio, manda-o para o caminho de sessão, onde o IRN o nega. O gate não lê cookies. Super admin passa sem sessão. Sem catálogo (`adapter=default`) fica super-admin only. Chave M2M continua barrada por não ser `JwtAuthenticationToken`. `/m2m-keys` mantém-se super-admin only (M-12). |
| E-7 | **Criar, listar, editar, revogar.** Sem rotate: não há segredo. Revogação é soft (`active=false`, `revoked_by/at`), efectiva no pedido seguinte; sem cache. |
| E-8 | **Uma linha activa por email**, garantido por índice único parcial na BD (`email WHERE active`). Depois de revogar pode criar-se outra. Um mapeamento expirado ainda ocupa o lugar activo; criar outro para o mesmo email retira-o automaticamente (fica revogado por quem criou o novo) em vez de falhar. Um mapeamento vivo é conflito real: 400 com o id. |
| E-9 | **M2M mantém-se** tal como está. Quem preferir chave opaca continua a poder usá-la. |
| E-10 | **Um humano mapeado que tire o cookie recebe o mapeamento.** Aceite por desenho (foi um super admin que o mapeou), mas o uso previsto são endereços dedicados de service account. A criação avisa (WARN) se o email pertencer a um perfil humano conhecido. |

## 3. Fluxo

```
Authorization: Bearer <JWT Keycloak>        sem Cookie: session_id
   │
   ├─ resource server valida o JWT (assinatura, issuer, exp)
   ├─ converter (SecurityConfig): getActiveGroups(token)  → vazio (sem sessão)
   │                             getPermissions(jwt, req) → IrnAuthorizationServiceAdapter
   │                                  sem cookie → EmailAccessResolver.resolve(email) → PermissionFormat
   │                             isSuperAdmin(jwt, req)   → email == IRN_API_SUPER_ADMIN_EMAIL
   └─ regras de rota (irn.authorization.routes.*) como para qualquer utilizador
```

Com cookie presente, `getPermissions` vai ao `/Auth/me` e o resolver nunca é chamado.

## 4. Modelo de dados (`V10__create_email_access_mapping.sql`; Studio `V6`)

`t_email_access_mapping`: `id`, `email` (minúsculas), `description`, `notes` (texto livre para os operadores, máx. 2000 caracteres validados no serviço, V11/V7, nunca entra numa decisão), `permissions` (CSV
`MODULO:acao`), `active`, `expires_at`, `created_by/at`, `updated_by/at`, `revoked_by/at`.
Índice único parcial `uq_email_access_mapping_active_email ON (email) WHERE active`. Sem
`AuditEntity`/Envers, como `t_m2m_api_key`. Sem `last_used_at`: só serviria para limpeza, acrescenta-se
quando alguém precisar de saber se um mapeamento ainda é usado.

## 5. API (`/email-access-mappings`, JWT com `EMAIL_ACCESS_MAPPINGS:<acao>` e sessão IRN, ou super admin)

| Método | Rota | Resposta |
|---|---|---|
| `POST` | `/email-access-mappings` | `201` mapeamento |
| `GET` | `/email-access-mappings` | `200` página (`content` + `pageNumber`/`pageSize`/`totalElements`/`totalPages`/`first`/`last`), mais recentes primeiro, revogados incluídos; filtros `email` (contém) e `status` (`active`/`revoked`/`expired`); `page`/`size` na management, `pageNumber`/`pageSize` no Studio, máx. 100 |
| `PUT` | `/email-access-mappings/{id}` | `200` mapeamento (permissões, descrição, notas, expiração substituídas; email nunca muda) |
| `DELETE` | `/email-access-mappings/{id}` | `204` revogação; idempotente (revogar de novo não altera `revokedBy`) |

Erros de validação: `400 {"error": "..."}` (email inválido, lista vazia, permissão fora do formato,
email já com mapeamento activo, `notes` acima de 2000 caracteres, `status` de filtro inválido, mapeamento revogado no `PUT`). Outros erros de integridade da base de dados são 500 com a causa no log, nunca disfarçados de duplicado. Contratos completos para frontend em
`EMAIL_ACCESS_FRONTEND_HANDOFF.md`.

## 6. Pré-condições operacionais (devops)

- Registar no System Administration o módulo `EMAIL_ACCESS_MAPPINGS` (Studio: `STUDIO_EMAIL_ACCESS_MAPPINGS`) com `visualizar`, `criar`, `editar`, `eliminar`, e atribuí-lo aos perfis que gerem acessos; ou apontar o código real do ecrã do frontend nas env `IRN_EMAIL_ACCESS_MAPPINGS_ACCEPT_READ/WRITE/EDIT/DELETE`.
- O token do service account traz o claim `email` (scope `email`), e o email é um endereço dedicado
  num domínio controlado, nunca o de um humano.
- Realm Keycloak com "Duplicate emails" desligado e auto-registo desligado (ou verificação de email
  ligada); senão alguém pode registar o email do service account antes dele existir.
- O email de super admin pertence a uma conta humana real: desde o 24.8 um service account com esse
  email num token válido também é super admin sem sessão.
- O sistema externo não envia cookies. Um `session_id` obsoleto dá 403 e um ERROR por pedido.
- `IGRP_SECURITY_PRINCIPAL_CLAIM_NAME=email`: o email do service account passa a aparecer em
  `created_by`/`started_by`, que é a atribuição que se quer.
- Sem pepper nem segredos novos. O JWT decoder não valida audience.

## 7. Testes

Framework: `PermissionFormatTest`, `DefaultAuthorizationServiceAdapterTest`,
`IrnAuthorizationServiceAdapterTest` (sessão primeiro, cookie em branco, sem claim, roles descartadas,
falha propaga). App: `EmailAccessMappingServiceTest`, `DbEmailAccessResolverTest` e
`SecurityConfigEmailAccessTest`, a primeira slice MockMvc do filter chain: token mapeado passa só na
rota mapeada, role escrita na coluna não chega a `/email-access-mappings` nem a `/m2m-keys`, token sem
email é negado sem consulta, falha do store nega até ao super admin, chave M2M não chega à gestão,
gestor com sessão IRN e permissão usa a consola só nos verbos que tem, a permissão da consola vinda
do mapeamento nunca conta (com ou sem cookie forjado), sessão sem a permissão é negada.
