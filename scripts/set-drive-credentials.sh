#!/bin/bash
# set-drive-credentials.sh
# ─────────────────────────────────────────────────────────────────────────────
# Patches the Google Drive credentials into the cyberlearnix-secrets k8s secret
# so all upload/stream APIs (course-service, cms-service, user-service,
# lab-service) can connect to Google Drive.
#
# Usage (run on the server):
#
#   # Option A — pass credentials as env vars
#   export GOOGLE_DRIVE_CREDENTIALS_JSON_B64="$(base64 -w 0 /path/to/service-account.json)"
#   export GOOGLE_DRIVE_FOLDER_ID="<your-folder-id>"
#   bash ~/scripts/set-drive-credentials.sh
#
#   # Option B — interactive prompts
#   bash ~/scripts/set-drive-credentials.sh
#
# After running, restart the affected pods:
#   kubectl rollout restart deployment/course-service deployment/cms-service \
#     deployment/user-service deployment/lab-service -n cyberlearnix
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

NAMESPACE="${NAMESPACE:-cyberlearnix}"
SECRET_NAME="cyberlearnix-secrets"

log()  { echo "  ➜  $*"; }
ok()   { echo "  ✔  $*"; }
err()  { echo "  ✖  $*" >&2; exit 1; }

# ── Collect credentials ───────────────────────────────────────────────────────

if [[ -z "${GOOGLE_DRIVE_CREDENTIALS_JSON_B64:-}" ]]; then
  echo ""
  echo "Paste the path to your Google Service Account JSON file:"
  read -r JSON_PATH
  [[ -f "$JSON_PATH" ]] || err "File not found: $JSON_PATH"
  GOOGLE_DRIVE_CREDENTIALS_JSON_B64="$(base64 -w 0 "$JSON_PATH")"
fi

if [[ -z "${GOOGLE_DRIVE_FOLDER_ID:-}" ]]; then
  echo ""
  echo "Paste your Google Drive Folder ID"
  echo "(from the folder URL: https://drive.google.com/drive/folders/<FOLDER_ID>):"
  read -r GOOGLE_DRIVE_FOLDER_ID
fi

[[ -n "$GOOGLE_DRIVE_CREDENTIALS_JSON_B64" ]] || err "GOOGLE_DRIVE_CREDENTIALS_JSON_B64 is empty"
[[ -n "$GOOGLE_DRIVE_FOLDER_ID" ]]            || err "GOOGLE_DRIVE_FOLDER_ID is empty"

# ── Validate the base64 JSON decodes to a valid service account ───────────────
log "Validating service account JSON..."
DECODED="$(echo "$GOOGLE_DRIVE_CREDENTIALS_JSON_B64" | base64 -d 2>/dev/null)" \
  || err "GOOGLE_DRIVE_CREDENTIALS_JSON_B64 is not valid base64"

echo "$DECODED" | python3 -c "
import sys, json
d = json.load(sys.stdin)
assert d.get('type') == 'service_account', 'Not a service_account JSON'
print('  client_email:', d.get('client_email','?'))
print('  project_id  :', d.get('project_id','?'))
" || err "JSON is not a valid Google service account file"

ok "Service account JSON is valid"

# ── Patch the k8s secret ──────────────────────────────────────────────────────
log "Patching secret '$SECRET_NAME' in namespace '$NAMESPACE'..."

kubectl patch secret "$SECRET_NAME" \
  -n "$NAMESPACE" \
  --type merge \
  -p "{\"stringData\":{
    \"google-drive-credentials-json-b64\":\"$GOOGLE_DRIVE_CREDENTIALS_JSON_B64\",
    \"google-drive-folder-id\":\"$GOOGLE_DRIVE_FOLDER_ID\"
  }}"

ok "Secret patched successfully"

# ── Rolling restart affected services ─────────────────────────────────────────
log "Rolling restart of Drive-enabled services..."
kubectl rollout restart deployment/course-service deployment/cms-service \
  deployment/user-service deployment/lab-service \
  -n "$NAMESPACE"

ok "Rollout triggered. Monitor with:"
echo "    kubectl get pods -n $NAMESPACE -w"
echo ""
echo "  To verify Drive is active after pods are Running:"
echo "    kubectl logs -n $NAMESPACE deployment/cms-service | grep -i 'google drive'"
echo "    kubectl logs -n $NAMESPACE deployment/course-service | grep -i 'google drive'"
