#!/bin/bash
echo "=== Databases in Postgres ==="
docker exec -i cyberlearnix-db psql -U postgres -t -c "SELECT datname FROM pg_database WHERE datname LIKE 'cyberlearnix%' ORDER BY datname"
echo "=== End of list ==="
