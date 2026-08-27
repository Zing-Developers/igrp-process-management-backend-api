# Handoff Frontend — Consola de Chaves M2M

Guia para as equipas de frontend construírem a consola de gestão de API keys M2M (Fase 2 do
`SPEC_M2M_AUTHORIZATION.md`). Protótipo interativo com os fluxos e regras de UX: artifact
**"Consola de Chaves M2M"** (pedir o link a quem gere os artifacts do projeto).

A mesma consola existe **nos dois frontends** — Process Management e Process Studio — cada uma contra
o **seu** backend (`/m2m-keys` na Management API e na Studio API). As chaves são independentes por
backend; o UI é idêntico.

---

## 1. Autenticação e autorização da consola

- As rotas `/m2m-keys/**` exigem um **JWT de utilizador com role de super-admin** (o mesmo
  Bearer+cookie de sessão IRN que o resto da app usa). Qualquer outro utilizador — ou uma chave M2M,
  sejam quais forem as suas permissões — recebe **403**.
- O frontend deve **esconder a entrada de menu** para não-super-admins, mas o gate real é o backend.

## 2. Contratos da API

Base path: raiz da API respetiva (com o context path do deployment, ex.
`/igrp-process-runtime-backend/m2m-keys`).

### 2.1 Criar chave

```
POST /m2m-keys
Content-Type: application/json

{
  "clientName": "fila-trabalho-job",              // obrigatório, slug ^[a-z0-9._-]+$
  "permissions": ["TASK_INSTANCES:visualizar"],   // obrigatório, ≥1, formato MODULO:acao
  "email": "responsavel@parceiro.cv",             // opcional — só metadado de contacto
  "expiresAt": "2027-01-01T00:00:00Z"             // opcional, ISO-8601
}
```

**201 Created**
```json
{
  "id": "e7824b29-e028-4e4a-a5df-c5f9bb038a35",
  "clientName": "fila-trabalho-job",
  "key": "igrpm2m_Bq9_SEVSfIck3VfiGz55UiXxBsLkgth-d-FsuYQX6ZM"
}
```

> **`key` é o plaintext e só existe nesta resposta.** O backend guarda apenas o hash — não há
> endpoint para o reler. Ver as regras de UX (§4).

**400 Bad Request** — validação. Corpo: `{"error": "<mensagem>"}`. Casos:
- `clientName` não é slug;
- lista de permissões vazia;
- permissão fora do formato `MODULO:acao` — inclui qualquer `ROLE_*`/`GROUP_*` (anti-escalada;
  mostrar a mensagem do backend tal como vem).

### 2.2 Listar chaves

```
GET /m2m-keys
```

**200 OK** — array, **sem segredos** (nunca vem hash nem plaintext):
```json
[{
  "id": "…",
  "clientName": "fila-trabalho-job",
  "keyPrefix": "igrpm2m_Bq9_",
  "permissions": "TASK_INSTANCES:visualizar",     // string separada por vírgulas
  "email": "responsavel@parceiro.cv",             // pode ser null
  "active": true,
  "expiresAt": null,                              // pode ser null
  "createdAt": "2026-08-27T19:05:00Z",
  "lastUsedAt": "2026-08-27T19:40:00Z",           // pode ser null (nunca usada)
  "revokedAt": null
}]
```

Derivação do **estado** para o pill do UI:
- `active=false` → **revogada**;
- `active=true` e `expiresAt` no passado → **expirada**;
- `active=true` e `expiresAt` no futuro → **a expirar** (tipicamente pós-rotação);
- caso contrário → **ativa**.

### 2.3 Revogar

```
DELETE /m2m-keys/{id}
```
**204 No Content.** Efeito **imediato** — o próximo pedido com essa chave recebe 401. Sem undo.
**400** com `{"error":…}` se o id não existir.

### 2.4 Rodar

```
POST /m2m-keys/{id}/rotate
```
**201 Created** — mesmo corpo do criar (nova chave, plaintext uma vez). Efeito lateral: a chave
antiga ganha `expiresAt = agora + grace` (default **7 dias**, env `IGRP_M2M_ROTATE_GRACE`) e expira
sozinha. O UI deve comunicar **os dois factos**: chave nova + data em que a antiga morre.

## 3. Ecrãs (ver o protótipo)

1. **Lista** — tabela: cliente (+email como sublinha), `keyPrefix…`, permissões em chips, pill de
   estado, criada, último uso, ações *Rodar*/*Revogar* (desativadas em chaves mortas). Botão
   **+ Nova chave**. Se a app servir os dois backends, um seletor; senão, cada frontend mostra só o seu.
2. **Criar** (modal) — nome (slug), permissões como *chip input* (Enter adiciona; validar formato no
   cliente **e** mostrar erros 400 do backend), email opcional, expiração opcional.
3. **Chave criada / rodada** (modal one-time) — plaintext em destaque mono, botão copiar, banda de
   aviso "só aparece uma vez". Botão único "Já copiei — fechar".
4. **Revogar** (confirmação) — nome da chave + consequência ("o sistema começa a receber 401 já").
5. **Rodar** (confirmação) — explica a chave nova + o grace da antiga; resultado usa o modal one-time.

## 4. Regras de UX obrigatórias

| Regra | Porquê |
|---|---|
| Plaintext mostrado **uma vez**; nunca guardado em state global, storage, logs ou analytics | O backend não o consegue reler; é um segredo |
| Não fechar o modal one-time por clique fora/Esc sem confirmação explícita | Fechar sem copiar = chave perdida |
| Erros 400 do backend mostrados literalmente | As mensagens de validação já são claras (slug, formato, roles) |
| Revogar sempre com confirmação; deixar claro que é imediato e sem undo | Kill switch de leak |
| `lastUsedAt` é *best effort* com granularidade ≥60s | Não prometer tempo-real |
| A consola não é acessível por chave M2M | O backend devolve 403; não tratar como erro inesperado |

## 5. Notas para QA

- Criar com permissão `ROLE_DEPT_IGRP.superadmin` → **400** (nunca 201).
- Revogar e repetir um pedido com a chave → **401** imediato.
- `GET /m2m-keys` nunca devolve `key`/`keyHash`.
- Chave rodada: a antiga continua a funcionar até ao grace; a nova funciona já.
- Utilizador sem super-admin: **403** em todos os `/m2m-keys/**`.
