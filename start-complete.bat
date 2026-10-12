@echo off
title EasyCloudDisk Complete System

echo ========================================
echo    EasyCloudDisk Complete System
echo    BS + CS GUI Architecture
echo ========================================
echo.

echo [STEP 1] Compiling project...
mvn clean compile

if %errorlevel% neq 0 (
    echo [ERROR] Compilation failed!
    pause
    exit /b 1
)

echo [STEP 2] Copying dependencies...
mvn dependency:copy-dependencies

echo.
echo [STEP 3] Starting server...
echo [INFO] BS Architecture: http://localhost:8080
echo [INFO] CS Architecture: Socket port 8889
echo.

REM 启动服务端
start "EasyCloudDisk Server" cmd /k "mvn spring-boot:run"

echo [INFO] Waiting for server to start...
timeout /t 15 /nobreak > nul

echo.
echo [STEP 4] Starting CS GUI Client...
echo [INFO] GUI client will start in a new window
echo.

REM 启动CS图形客户端
start "EasyCloudDisk CS GUI" cmd /k "java -cp target/classes;target/dependency/* cs.client.CSClientGUI"

echo.
echo [SUCCESS] System started successfully!
echo [INFO] BS Web Interface: http://localhost:8080
echo [INFO] CS GUI Client: Check the new window
echo.
pause
