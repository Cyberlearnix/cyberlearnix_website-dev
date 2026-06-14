@echo off
setlocal
set DB_PASS=cyberlearnix_dev_pass
set DB_USER=postgres
set DB_HOST=127.0.0.1
set DB_PORT=5999
set DB_NAME=cyberlearnix_%1
set JWT_SECRET=cyberlearnix-secure-and-ultra-long-secret-key-for-jwt-signing-2026-highly-confidential-512bit
set REDIS_PASSWORD=cyberlearnix_redis_dev
set REDIS_HOST=127.0.0.1
set REDIS_PORT=6379
set CORS_ALLOWED_ORIGINS=http://localhost:3000,http://localhost:5173
set GMAIL_USER=cyberlearnixprivatelimited@gmail.com
set GMAIL_APP_PASSWORD=wxhqcbzyjfwhnoic

echo Starting %1 service...
"C:\Program Files\Java\jdk-21\bin\java.exe" ^
  -DDB_HOST=%DB_HOST% ^
  -DDB_PORT=%DB_PORT% ^
  -DDB_NAME=%DB_NAME% ^
  -DDB_USER=%DB_USER% ^
  -DDB_PASS=%DB_PASS% ^
  -DJWT_SECRET=%JWT_SECRET% ^
  -DREDIS_PASSWORD=%REDIS_PASSWORD% ^
  -DREDIS_HOST=%REDIS_HOST% ^
  -DREDIS_PORT=%REDIS_PORT% ^
  -DGMAIL_USER=%GMAIL_USER% ^
  -DGMAIL_APP_PASSWORD=%GMAIL_APP_PASSWORD% ^
  "-Dspring.datasource.url=jdbc:postgresql://%DB_HOST%:%DB_PORT%/%DB_NAME%?sslmode=disable" ^
  -jar %1-service\build\libs\%1-service-1.0.0-SNAPSHOT.jar
endlocal
