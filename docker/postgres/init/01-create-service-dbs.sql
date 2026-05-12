-- Create a separate database for each microservice.
-- This script runs automatically on first Postgres container startup
-- via the /docker-entrypoint-initdb.d mount.

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

SELECT 'CREATE DATABASE cyberlearnix_instructor'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'cyberlearnix_instructor')\gexec
