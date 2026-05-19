@echo off
echo ============================================
echo  Cinepolis QA Runner Agent
echo ============================================

REM Set environment variables (edit these or use system env)
set BACKEND_URL=https://qautomation-production.up.railway.app
set RUNNER_TOKEN=runner-local-token
set POLL_INTERVAL_MS=5000
set WORK_DIR=..
set TEST_COMMAND=gradlew.bat test

echo Backend: %BACKEND_URL%
echo Work Dir: %WORK_DIR%
echo.

cd /d "%~dp0"
mvn package -q
java -jar target\cinepolis-runner.jar
pause
