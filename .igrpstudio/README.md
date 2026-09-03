# `.igrpstudio` — modelo do gerador iGRP Studio

Este diretório é a **fonte de verdade do gerador**: uma regeneração reescreve os ficheiros marcados
`THIS FILE WAS GENERATED AUTOMATICALLY BY iGRP STUDIO` (DTOs em `application/dto`, entidades,
controllers). Um campo que só exista no Java é apagado na próxima regeneração.

## Regra

Sempre que se toca num DTO gerado, actualiza-se o JSON correspondente aqui — no mesmo commit.
Verificação: `python3 scripts/check_igrpstudio_drift.py` (falha com exit 1 se houver drift).

## Convenções deste repo

- DTO: `name` **com** sufixo `DTO` (ex.: `TaskInstanceDTO`); referências a outro DTO usam
  `"objectType":"dto","type":"UserProfileDTO","module":"processruntime"`; listas com `"collectionType":"list"`.
- Datas: `"type":"datetime"` (gera `LocalDateTime`, serializado ISO sem zona).
- Trio de auditoria da plataforma: `createdAt`/`updatedAt` + `createdBy`/`updatedBy` +
  `userProfileCreatedBy`/`userProfileUpdatedBy` (perfil resolvido pelo `AuditUserResponseAdvice`).

## Código feito à mão que agora está modelado (atenção ao regenerar)

| Ficheiro | Onde vive no código | Nota |
|---|---|---|
| `shared/models/M2mApiKeyEntity.json` | `shared/infrastructure/persistence/entity/M2mApiKeyEntity` | Sem `AuditEntity`/Envers **por design** (`audit:false`, `revision:false`). A implementação manual tem regras (hash HMAC, `active`, `expires_at`) que o gerador não conhece — reconciliar, não substituir. |
| `shared/controllers/M2mKeyController.json` | `shared/security/m2m/M2mKeyController` | Gate super-admin JWT-only está no `SecurityConfig`, não no controller. |
| `shared/dto/M2mKey*DTO.json` | `shared/application/dto/M2mKey*DTO` | Payloads das rotas `/m2m-keys`. |

Fora do gerador, sem modelo (infra de segurança): `SecurityConfig`, `IAMUserProfileSyncFilter`,
`AuditUserResponseAdvice`, `AuditedResponse`/`AuditedPage`, `AuditMapping`, `ApplicationAuditorAware`.
