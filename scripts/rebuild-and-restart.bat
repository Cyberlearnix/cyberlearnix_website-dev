@echo off
echo [1/6] Stopping all Java processes...
taskkill /F /IM java.exe 2>nul
timeout /t 3 /nobreak >nul

echo [2/6] Building shared-lib with new JPA entities...
cd /d "%~dp0"
call .\gradlew.bat :shared-lib:build -x test --console=plain
if %errorlevel% neq 0 (
    echo ERROR: shared-lib build failed!
    exit /b 1
)

echo [3/6] Building user-service with new controllers...
call .\gradlew.bat :user-service:bootJar -x test --console=plain
if %errorlevel% neq 0 (
    echo ERROR: user-service build failed!
    exit /b 1
)

echo [4/6] Building course-service with new controllers...
call .\gradlew.bat :course-service:bootJar -x test --console=plain
if %errorlevel% neq 0 (
    echo ERROR: course-service build failed!
    exit /b 1
)

echo [5/6] Building enrollment-service with new controllers...
call .\gradlew.bat :enrollment-service:bootJar -x test --console=plain
if %errorlevel% neq 0 (
    echo ERROR: enrollment-service build failed!
    exit /b 1
)

echo [6/6] Building gateway-service...
call .\gradlew.bat :gateway-service:bootJar -x test --console=plain
if %errorlevel% neq 0 (
    echo ERROR: gateway-service build failed!
    exit /b 1
)

echo.
echo ================================================
echo All builds completed successfully!
echo Run 'start-all.bat' to start all services
echo ================================================
pause
