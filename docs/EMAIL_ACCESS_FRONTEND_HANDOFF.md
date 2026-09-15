# Handoff Frontend: Consola de Acessos por Email

Guia para as equipas de frontend construírem a consola de mapeamentos email → permissões
(`SPEC_EMAIL_ACCESS_MAPPING.md`). Protótipo clicável com os ecrãs e as regras de UX: artifact
**"Consola de Acessos por Email"**, em https://claude.ai/code/artifact/66f048ce-3cf7-452f-b1b0-b9e2c74bda8f (privado; pedir acesso a quem gere os artifacts do projeto).

A consola existe **nos dois frontends**, Process Management e Process Studio, cada uma contra o
**seu** backend (`/email-access-mappings` na Management API e na Studio API). Os mapeamentos são
independentes por backend; o UI é idêntico. É a irmã da consola de chaves M2M
(`M2M_FRONTEND_HANDOFF.md`): mesma navegação, mesmo gate, sem segredo para mostrar.

---

## 1. Autenticação e autorização da consola

- As rotas `/email-access-mappings/**` estão no catálogo de permissões como qualquer outra: o
  utilizador precisa de `EMAIL_ACCESS_MAPPINGS:visualizar` (listar), `:criar`, `:editar`, `:eliminar`
  (revogar), ou do código do próprio ecrã do frontend quando devops o apontar via `accept-also`. No
  Studio o módulo é `STUDIO_EMAIL_ACCESS_MAPPINGS`. O super admin passa sempre.
- A permissão só conta com o **cookie de sessão IRN** presente (o mesmo Bearer + cookie que o resto da
  app usa). Um token mapeado ou uma chave M2M recebem **403** seja qual for a permissão que carreguem.
- O frontend deve **esconder a entrada de menu** a quem não tem `:visualizar`, e as acções *Editar* /
  *Revogar* / *Novo* a quem não tem o verbo respectivo, mas o gate real é o backend.

## 2. Contratos da API

Base path: raiz da API respetiva (com o context path do deployment, ex.
`/igrp-process-runtime-backend/email-access-mappings`).

### 2.1 Criar mapeamento

```
POST /email-access-mappings
Content-Type: application/json

{
  "email": "svc-fila@parceiro.cv",               // obrigatório; guardado em minúsculas
  "permissions": ["TASK_INSTANCES:visualizar"],   // obrigatório, ≥1, formato MODULO:acao
  "description": "Job da fila de trabalho",       // opcional, uma linha
  "notes": "Pedido no ticket IRN-4521; contacto: ops@parceiro.cv",  // opcional, texto livre, máx. 2000 caracteres
  "expiresAt": "2027-01-01T00:00:00"              // opcional, LocalDateTime SEM zona
}
```

**201 Created**
```json
{
  "id": "e7824b29-e028-4e4a-a5df-c5f9bb038a35",
  "email": "svc-fila@parceiro.cv",
  "description": "Job da fila de trabalho",
  "notes": "Pedido no ticket IRN-4521; contacto: ops@parceiro.cv",
  "permissions": ["TASK_INSTANCES:visualizar"],
  "active": true,
  "expiresAt": null,
  "createdAt": "2026-09-11T15:05:00",
  "createdBy": "admin@nosi.cv",
  "userProfileCreatedBy": { "username": "admin", "email": "admin@nosi.cv", "fullName": "Ana Admin", "…": "…" },
  "updatedAt": "2026-09-11T15:05:00",
  "updatedBy": "admin@nosi.cv",
  "userProfileUpdatedBy": { "…": "…" },
  "revokedAt": null,
  "revokedBy": null,
  "userProfileRevokedBy": null
}
```

> `createdBy`/`updatedBy`/`revokedBy` são a string crua do principal; `userProfile*` é o perfil IAM
> enriquecido e **pode ser `null`**; nesse caso o UI mostra a string crua. Igual ao M2M.

> `permissions` vem como **lista** (no M2M vinha como string separada por vírgulas).

**400 Bad Request**, corpo `{"error": "<mensagem>"}`. Casos:
- `email` inválido;
- lista de permissões vazia;
- `notes` com mais de 2000 caracteres (`notes must be at most 2000 characters`);
- permissão fora do formato `MODULO:acao`, incluindo qualquer `ROLE_*`/`GROUP_*` (anti-escalada;
  mostrar a mensagem do backend tal como vem);
- o email já tem um mapeamento **activo e não expirado**: a mensagem traz o id
  (`email already has an active mapping (<id>): edit it, or revoke it before creating a new one`);
  o UI deve oferecer editar ou revogar esse mapeamento.

> **Expirado não bloqueia.** Um mapeamento expirado ainda conta como activo na tabela, mas criar
> outro para o mesmo email é aceite: o backend retira o expirado (fica como revogado por quem criou
> o novo) e cria o novo. Revogado também não bloqueia: pode criar-se outro para o mesmo email.

### 2.2 Listar (paginado)

```
GET /email-access-mappings?page=0&size=20&email=parceiro&status=active
```

Management API: `page` / `size`. Studio API: `pageNumber` / `pageSize` (os nomes dos outros endpoints
de cada app). Default: página 0, 20 por página, máximo 100. Ordem: mais recentes primeiro. Filtros
opcionais: `email` (contém, sem distinção de maiúsculas) e `status` = `active` (activo e não
expirado), `revoked` ou `expired`; sem `status` vêm todos, revogados incluídos.

**200 OK**, o objecto de página da plataforma, com as linhas em `content` (forma do 2.1):
```json
{
  "content": [ { "id": "…", "email": "svc-fila@parceiro.cv", "…": "…" } ],
  "pageNumber": 0,
  "pageSize": 20,
  "totalElements": 37,
  "totalPages": 2,
  "first": true,
  "last": false
}
```

**400** com `{"error": "status must be one of active, revoked, expired"}` se o `status` for outro.

Derivação do **estado** para o pill:
- `active=false` → **revogado**;
- `active=true` e `expiresAt` no passado → **expirado**;
- caso contrário → **activo**.

> **Datas:** `LocalDateTime` sem zona (`2026-09-11T15:05:00`), como no resto da plataforma.

### 2.3 Editar

```
PUT /email-access-mappings/{id}
{ "permissions": [...], "description": "...", "notes": "...", "expiresAt": "..." }   // email ignorado, nunca muda
```
**200 OK**, mapeamento actualizado. Substitui os quatro campos por inteiro (enviar sempre a lista
completa de permissões). **400** se o mapeamento estiver revogado ("create a new one"), se a lista
vier vazia ou com permissão inválida, ou se o id não existir.

### 2.4 Revogar

```
DELETE /email-access-mappings/{id}
```
**204 No Content.** Efeito no **pedido seguinte** do sistema externo (403 nas rotas mapeadas). Sem
undo; para voltar a dar acesso cria-se um mapeamento novo. **400** se o id não existir. Revogar um
mapeamento já revogado devolve **204** e não altera nada: quem revogou primeiro fica registado.

## 3. Ecrãs (ver o protótipo)

1. **Lista.** Pesquisa por email, selector de estado (todos / activos / expirados / revogados) e
   paginação (20 por página) por cima da tabela: email (+ descrição como sublinha), permissões em chips, pill de estado, expira
   em, criado (+ "por *utilizador*"), última alteração, ícone de notas com tooltip quando `notes` existe, acções *Editar*/*Revogar* (desactivadas em
   revogados). Revogado mostra "revogado a *data* por *utilizador*". Botão **+ Novo mapeamento**.
2. **Criar** (modal). Email, permissões como *chip input* (Enter adiciona; validar formato no cliente
   **e** mostrar erros 400 do backend), descrição opcional (uma linha), notas opcionais (texto livre,
   multi-linha, máximo 2000 caracteres com contador: quem pediu, ticket, contacto), expiração opcional.
3. **Editar** (modal). O mesmo formulário com o email bloqueado.
4. **Revogar** (confirmação). Email e consequência ("o sistema passa a receber 403 já no próximo
   pedido").

## 4. Regras de UX obrigatórias

| Regra | Porquê |
|---|---|
| Erros 400 do backend mostrados literalmente | As mensagens já são claras (formato, roles, duplicado) |
| Revogar sempre com confirmação; deixar claro que é sem undo | É o kill switch |
| `notes` limitado a 2000 caracteres no cliente, com contador | O backend devolve 400 acima disso |
| Email não editável depois de criado | Um mapeamento é um grant a um endereço; mudar o endereço é outro grant |
| Chips de permissão em maiúsculas/minúsculas tal como escritas; validar `MODULO:acao` no cliente | Evita ida ao backend por erro de digitação |
| A consola não é acessível por token mapeado nem por chave M2M | O backend devolve 403; não tratar como erro inesperado |
| Botões de acção seguem os verbos do perfil (`:criar`, `:editar`, `:eliminar`) | Um 403 numa acção é um perfil incompleto, não um bug |

## 5. Notas para QA

- Criar com permissão `ROLE_DEPT_IGRP.superadmin` → **400**.
- Criar duas vezes o mesmo email (activo) → segunda dá **400** com o id do existente; revogar a primeira e repetir → **201**.
- Mapeamento expirado no mesmo email → criar dá **201** e o expirado passa a revogado.
- Revogar e repetir um pedido do sistema externo → **403** no pedido seguinte.
- `PUT` num revogado → **400**; `DELETE` num revogado → **204** sem alterar `revokedBy`.
- Notas com 2001 caracteres → **400**; com 2000 → **201**.
- `GET ?status=revoked&size=1` → só linhas com `active=false`, `totalPages` > 1 quando há mais; `?status=deleted` → **400**.
- `GET ?email=_` procura um underscore literal; `%` e `_` não são wildcards.
- Utilizador sem a permissão do verbo (nem super admin): **403** nessa rota; com `:visualizar` só, a lista abre e as acções dão 403.
- Sistema externo: token com `email` mapeado e **sem cookie** → 200 nas rotas mapeadas, 403 nas
  outras; o mesmo token **com** cookie de sessão IRN → permissões do IRN, não as do mapeamento.
