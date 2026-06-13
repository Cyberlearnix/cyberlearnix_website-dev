#!/bin/bash
# Trigger ArgoCD sync for production app
TOKEN=$(curl -sk -X POST "https://argocd.cyberlearnix.com/argocd/api/v1/session" \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"Cyb3rLearnix#ArgoCD2026!@1"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")

echo "Token acquired (first 20 chars): ${TOKEN:0:20}..."

RESULT=$(curl -sk -X POST "https://argocd.cyberlearnix.com/argocd/api/v1/applications/cyberlearnix-production/sync" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H 'Content-Type: application/json' \
  -d '{"revision":"production","prune":true,"force":true}')

echo "$RESULT" | python3 -c "
import sys, json
d = json.load(sys.stdin)
print('App:', d.get('metadata',{}).get('name','?'))
print('Sync:', d.get('status',{}).get('sync',{}).get('status','?'))
print('Health:', d.get('status',{}).get('health',{}).get('status','?'))
" 2>/dev/null || echo "Raw: ${RESULT:0:200}"
