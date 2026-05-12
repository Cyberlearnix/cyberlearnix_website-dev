#!/bin/bash
# Idempotent: creates all required service databases if they don't exist.
# Safe to run multiple times.
set -e

echo "=== Ensuring all CyberLearnix databases exist ==="

docker exec -i cyberlearnix-db psql -U postgres -v ON_ERROR_STOP=0 << 'SQL'
-- Create each database only if it doesn't already exist
SELECT 'CREATE DATABASE cyberlearnix_users'
  WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'cyberlearnix_users')\gexec

SELECT 'CREATE DATABASE cyberlearnix_courses'
  WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'cyberlearnix_courses')\gexec

SELECT 'CREATE DATABASE cyberlearnix_enrollments'
  WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'cyberlearnix_enrollments')\gexec

SELECT 'CREATE DATABASE cyberlearnix_shop'
  WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'cyberlearnix_shop')\gexec

SELECT 'CREATE DATABASE cyberlearnix_forms'
  WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'cyberlearnix_forms')\gexec

SELECT 'CREATE DATABASE cyberlearnix_admin'
  WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'cyberlearnix_admin')\gexec

SELECT 'CREATE DATABASE cyberlearnix_cms'
  WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'cyberlearnix_cms')\gexec

SELECT 'CREATE DATABASE cyberlearnix_gateway'
  WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'cyberlearnix_gateway')\gexec

-- cyberlearnix_instructor is NOT in the original init script but instructor-service needs it
SELECT 'CREATE DATABASE cyberlearnix_instructor'
  WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'cyberlearnix_instructor')\gexec

-- List all databases to confirm
\l cyberlearnix*
SQL

echo "=== Done ==="
