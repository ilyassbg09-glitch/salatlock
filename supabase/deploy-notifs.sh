#!/usr/bin/env bash
# Installe les notifications iPhone sur le serveur Supabase.
# Prérequis : un jeton d'accès Supabase (sbp_...) dans SUPABASE_ACCESS_TOKEN.
#   (Dans la session Claude, le jeton est injecté par le proxy : une valeur factice au bon format suffit.)
# Usage : bash supabase/deploy-notifs.sh   (depuis la racine du dépôt)
set -euo pipefail
REF=efagehkrrahnjshhvdhp
API=https://api.supabase.com/v1/projects/$REF
: "${SUPABASE_ACCESS_TOKEN:?SUPABASE_ACCESS_TOKEN manquant}"
CLI=${CLI:-npx -y supabase@3.3.0}
WEBPUSH=${WEBPUSH:-npx -y web-push@3.6.7}
auth=(-H "Authorization: Bearer $SUPABASE_ACCESS_TOKEN" -H "Content-Type: application/json")

sql() { # exécute un fichier SQL sur la base
  python3 -c 'import json,sys; print(json.dumps({"query": open(sys.argv[1]).read()}))' "$1" > /tmp/sl_query.json
  curl -sS --fail-with-body "${auth[@]}" -X POST "$API/database/query" --data @/tmp/sl_query.json > /dev/null
  echo "  ✓ $(basename "$1")"
}

echo "1/5 Base de données (iPhone + notifications)"
sql supabase/salatlock-v3-iphone.sql
sql supabase/salatlock-v4-notifs.sql

echo "2/5 Clés de notification (VAPID) + secret du minuteur"
KEYS=$($WEBPUSH generate-vapid-keys --json)
PUB=$(python3 -c 'import json,sys; print(json.loads(sys.argv[1])["publicKey"])' "$KEYS")
PRIV=$(python3 -c 'import json,sys; print(json.loads(sys.argv[1])["privateKey"])' "$KEYS")
CRON=$(python3 -c 'import secrets; print(secrets.token_hex(24))')
python3 - "$PUB" "$PRIV" "$CRON" > /tmp/sl_secrets.json <<'PY'
import json, sys
print(json.dumps([{"name": "VAPID_PUBLIC", "value": sys.argv[1]}, {"name": "VAPID_PRIVATE", "value": sys.argv[2]}, {"name": "CRON_SECRET", "value": sys.argv[3]}]))
PY
curl -sS --fail-with-body "${auth[@]}" -X POST "$API/secrets" --data @/tmp/sl_secrets.json > /dev/null
rm -f /tmp/sl_secrets.json
echo "  ✓ secrets enregistrés sur Supabase"

echo "3/5 Fonction d'envoi"
$CLI functions deploy push --project-ref "$REF" --no-verify-jwt --use-api --workdir . > /tmp/sl_deploy.log 2>&1 || { cat /tmp/sl_deploy.log; exit 1; }
echo "  ✓ fonction « push » déployée"

echo "4/5 Minuteur : vérification chaque minute"
cat > /tmp/sl_cron.sql <<SQL
create extension if not exists pg_cron with schema pg_catalog;
create extension if not exists pg_net with schema extensions;
select cron.unschedule(jobid) from cron.job where jobname = 'salatlock-push';
select cron.schedule('salatlock-push', '* * * * *', \$\$
  select net.http_post(url := 'https://$REF.supabase.co/functions/v1/push',
    headers := '{"Content-Type": "application/json", "x-cron-secret": "$CRON"}'::jsonb, body := '{}'::jsonb)
\$\$);
SQL
sql /tmp/sl_cron.sql; rm -f /tmp/sl_cron.sql

echo "5/5 Clé publique dans l'app iPhone"
sed -i "s|^const VAPID_PUBLIC = '[^']*';|const VAPID_PUBLIC = '$PUB';|" docs/app/app.js
grep -q "$PUB" docs/app/app.js && echo "  ✓ docs/app/app.js mis à jour (à publier)"
echo "Terminé ✅"
