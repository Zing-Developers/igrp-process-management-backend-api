#!/usr/bin/env bash
# e2e: email access mapping on both apps (docs/SPEC_EMAIL_ACCESS_MAPPING.md). Run with the stack up
# (see README); prints one line per check and a summary. Exit 1 on any failure.
set -u
cd "$(dirname "$0")"
SVC=$(./mint-token.sh svc svc@test.local); ADMIN=$(./mint-token.sh admin superadmin@test.local); MGR=$(./mint-token.sh manager access-manager@test.local); VIEWER=$(./mint-token.sh viewer mgmt-viewer@test.local)
pass=0; fail=0
check() { if [ "$2" = "$3" ]; then pass=$((pass+1)); printf 'ok   %-70s %s %s\n' "$1" "$3" "${4:-}"; else fail=$((fail+1)); printf 'FAIL %-70s expected %s got %s %s\n' "$1" "$2" "$3" "${4:-}"; fi; }
# call <method> <url> <token> [session] [json] -> sets CODE and BODY
call() { local m=$1 u=$2 t=$3 s=${4:-} d=${5:-}; local args=(-s -w '\n%{http_code}' -X "$m" "$u" -H "Authorization: Bearer $t")
  [ -n "$s" ] && args+=(-H "Cookie: session_id=$s"); [ -n "$d" ] && args+=(-H 'Content-Type: application/json' -d "$d")
  local out; out=$(curl "${args[@]}"); CODE=${out##*$'\n'}; BODY=${out%$'\n'*}; }
msg() { python3 -c 'import json,sys
try: print(json.load(sys.stdin).get("error",""))
except Exception: print("")' <<<"$BODY"; }

run_app() { # base module business-path business-perm viewer-session
  B=$1; MOD=$2; P=$3; PERM=$4; VS=$5; echo "=== $B ($MOD)"
  call GET $B$P "$SVC";                                   check "svc token, no cookie, $P" 403 $CODE
  call GET $B/email-access-mappings "$SVC";               check "console by mapped token (no mapping yet)" 403 $CODE
  call POST $B/email-access-mappings "$ADMIN" sess-admin '{"email":"SVC@test.local","permissions":["'$PERM'","'$MOD':visualizar","'$MOD':criar"],"description":"e2e","notes":"criado pelo e2e"}'
  check "super admin (session) creates mapping" 201 $CODE "$(msg)"
  ID=$(python3 -c 'import json,sys; d=json.load(sys.stdin); assert d["email"]=="svc@test.local" and d["notes"]=="criado pelo e2e"; print(d["id"])' <<<"$BODY")
  call POST $B/email-access-mappings "$ADMIN" sess-admin '{"email":"svc@test.local","permissions":["'$PERM'"]}';  check "duplicate active mapping" 400 $CODE "($(msg))"
  call POST $B/email-access-mappings "$ADMIN" sess-admin '{"email":"x@test.local","permissions":["ROLE_DEPT_IGRP.superadmin"]}'; check "ROLE_ in permissions" 400 $CODE "($(msg))"
  call GET $B$P "$SVC";                                   check "svc token, no cookie, $P (mapped)" 200 $CODE
  call POST $B$P "$SVC" "" '{}';                          check "svc token, no cookie, POST $P (not mapped)" 403 $CODE
  call GET $B$P "$SVC" bogus;                             check "svc token WITH bogus cookie (IRN path, denied)" 403 $CODE
  call GET $B$P "$SVC" $VS;                               check "svc token WITH viewer session: IRN decides" 200 $CODE
  call GET $B/email-access-mappings "$SVC";               check "console by mapped token carrying $MOD:* (no cookie)" 403 $CODE
  call GET $B/email-access-mappings "$SVC" bogus;         check "console by mapped token + bogus cookie" 403 $CODE
  call GET $B/email-access-mappings "$MGR" sess-access-manager; check "manager with session + $MOD:visualizar lists" 200 $CODE
  call GET $B/email-access-mappings "$MGR";               check "manager without cookie" 403 $CODE
  call PUT $B/email-access-mappings/$ID "$MGR" sess-access-manager '{"permissions":["'$PERM'"],"notes":"editado"}'; check "manager edits (PUT)" 200 $CODE "$(msg)"
  call PUT $B/email-access-mappings/$ID "$ADMIN" sess-admin '{"permissions":["'$PERM'"],"notes":"editado pelo criador"}'; check "creator edits own mapping (PUT)" 200 $CODE "$(msg)"
  call GET $B/email-access-mappings "$VIEWER" $VS;        check "viewer session without console permission" 403 $CODE
  call GET $B/email-access-mappings "$ADMIN";             check "super admin without cookie lists" 200 $CODE
  call POST $B/m2m-keys "$ADMIN" sess-admin '{"clientName":"e2e-job","permissions":["'$PERM'","'$MOD':visualizar"]}'
  K=$(python3 -c 'import json,sys; print(json.load(sys.stdin)["key"])' <<<"$BODY")
  call GET $B$P "$K";                                     check "m2m key on business route" 200 $CODE
  call GET $B/email-access-mappings "$K";                 check "m2m key on console" 403 $CODE
  call DELETE $B/email-access-mappings/$ID "$MGR" sess-access-manager; check "manager revokes (DELETE)" 204 $CODE
  call DELETE $B/email-access-mappings/$ID "$ADMIN" sess-admin; check "revoking again is a no-op 204" 204 $CODE
  call GET "$B/email-access-mappings?email=svc@test" "$ADMIN"
  check "second revoke kept the original revoker" "manager" "$(python3 -c 'import json,sys; print([m for m in json.load(sys.stdin)["content"] if m["id"]=="'$ID'"][0]["revokedBy"])' <<<"$BODY")"
  call GET $B$P "$SVC";                                   check "svc token after revoke" 403 $CODE
  call PUT $B/email-access-mappings/$ID "$MGR" sess-access-manager '{"permissions":["'$PERM'"]}'; check "PUT on revoked mapping" 400 $CODE "($(msg))"
  call POST $B/email-access-mappings "$ADMIN" sess-admin '{"email":"svc@test.local","permissions":["'$PERM'"]}'; check "re-create after revoke" 201 $CODE "$(msg)"
  # a whole catalogue in one mapping is longer than varchar(255): the column must be TEXT
  BIG=$(python3 -c "import json; mods=['AREAS','PROCESS_DEFINITIONS','PROCESS_INSTANCES','ACTIVITIES','TASK_INSTANCES','$MOD','STUDIO_PROJECTS','STUDIO_PROCESS_DEFINITIONS']; print(json.dumps([m+':'+a for m in mods for a in ('visualizar','criar','editar','eliminar')]))")
  call POST $B/email-access-mappings "$ADMIN" sess-admin '{"email":"big@test.local","permissions":'"$BIG"'}'; check "32-permission mapping (>255 chars) is accepted" 201 $CODE "$(msg)"
  # an expired mapping still holds the active slot: creating again must retire it, not fail
  call POST $B/email-access-mappings "$ADMIN" sess-admin '{"email":"old@test.local","permissions":["'$PERM'"],"expiresAt":"2020-01-01T00:00:00"}'; check "create already-expired mapping" 201 $CODE "$(msg)"
  OLD=$(./mint-token.sh old old@test.local)
  call GET $B$P "$OLD";                                   check "expired mapping grants nothing" 403 $CODE
  call POST $B/email-access-mappings "$ADMIN" sess-admin '{"email":"old@test.local","permissions":["'$PERM'"]}'; check "re-create over the expired one" 201 $CODE "$(msg)"
  call GET $B$P "$OLD";                                   check "new mapping works" 200 $CODE
  call GET $B/email-access-mappings?email=old "$ADMIN"
  check "list is a page; old one retired, new one active" "1 1" "$(python3 -c 'import json,sys; ms=[m for m in json.load(sys.stdin)["content"] if m["email"]=="old@test.local"]; print(sum(1 for m in ms if not m["active"]), sum(1 for m in ms if m["active"]))' <<<"$BODY")"
  call GET "$B/email-access-mappings?status=revoked&$PS=1" "$ADMIN"
  check "status=revoked, one per page: all rows inactive, totalPages>1" "true true" "$(python3 -c 'import json,sys; d=json.load(sys.stdin); print(str(all(not m["active"] for m in d["content"])).lower(), str(d["totalPages"]>1 and len(d["content"])==1).lower())' <<<"$BODY")"
  call GET "$B/email-access-mappings?status=active" "$ADMIN"
  check "status=active excludes revoked and expired" "true" "$(python3 -c 'import json,sys; d=json.load(sys.stdin); print(str(all(m["active"] and not (m.get("expiresAt") or "2999") < "2026" for m in d["content"]) and d["totalElements"]>0).lower())' <<<"$BODY")"
  call GET "$B/email-access-mappings?status=deleted" "$ADMIN";  check "bad status" 400 $CODE "($(msg))"
  call GET "$B/email-access-mappings?email=_" "$ADMIN";  check "email=_ is a literal underscore, not a wildcard" 0 "$(python3 -c 'import json,sys; print(json.load(sys.stdin)["totalElements"])' <<<"$BODY")"
  # two session cookies, blank first: adapter and gate must agree there is no session -> mapped token stays out
  call POST $B/email-access-mappings "$ADMIN" sess-admin '{"email":"dup@test.local","permissions":["'$MOD':visualizar","'$MOD':criar"]}'; check "mapping that carries console permissions (for the cookie test)" 201 $CODE "$(msg)"
  DUP=$(./mint-token.sh dup dup@test.local)
  CODE=$(curl -s -o /dev/null -w '%{http_code}' $B/email-access-mappings -H "Authorization: Bearer $DUP" -H "Cookie: session_id=; session_id=bogus"); check "duplicate cookies, blank first: console denied" 403 $CODE
  CODE=$(curl -s -o /dev/null -w '%{http_code}' $B/email-access-mappings -H "Authorization: Bearer $DUP" -H "Cookie: session_id=bogus; session_id="); check "duplicate cookies, bogus first: console denied" 403 $CODE
  LONG=$(python3 -c 'print("x"*2001)')
  call POST $B/email-access-mappings "$ADMIN" sess-admin '{"email":"notes@test.local","permissions":["'$PERM'"],"notes":"'$LONG'"}'; check "notes over 2000 chars" 400 $CODE "($(msg))"
}
PS=size run_app http://localhost:18080 EMAIL_ACCESS_MAPPINGS /areas AREAS:visualizar sess-mgmt-viewer
PS=pageSize run_app http://localhost:18082 STUDIO_EMAIL_ACCESS_MAPPINGS /api/v1/projects STUDIO_PROJECTS:visualizar sess-studio-viewer
echo "=== $pass passed, $fail failed"
[ "$fail" = 0 ]
