@echo off
setlocal
set DB_PASS=cyberlearnix_dev_pass
set DB_USER=postgres
set DB_HOST=127.0.0.1
set DB_PORT=5999
set JWT_SECRET=cyberlearnix-secure-and-ultra-long-secret-key-for-jwt-signing-2026-highly-confidential-512bit
set REDIS_PASSWORD=cyberlearnix_redis_dev
set REDIS_HOST=127.0.0.1
set REDIS_PORT=6379
echo DB_PASS is: %DB_PASS%
echo DB_HOST is: %DB_HOST%
gradlew.bat :user-service:bootRun --no-daemon --info 2>&1 | findstr /I "datasource\|password\|connection\|JDBC\|postgres\|Started\|ERROR\|Caused by" > C:\Users\sridh\service-run.log 2>&1
endlocal
