@echo off
chcp 65001
title EasyCloudDisk Cloud Storage System

echo ========================================
echo    EasyCloudDisk Cloud Storage System
echo ========================================
echo.
echo 请选择启动模式:
echo 1. 基础模式 (H2数据库 + BS架构)
echo 2. 生产模式 (MySQL数据库 + BS架构)  
echo 3. 完整模式 (H2数据库 + BS+CS架构)
echo 4. 仅CS客户端
echo.
set /p choice=请输入选择 (1-4): 

if "%choice%"=="1" goto basic
if "%choice%"=="2" goto mysql
if "%choice%"=="3" goto complete
if "%choice%"=="4" goto cs_only
echo 无效选择，默认启动基础模式
goto basic

:basic
echo.
echo [模式1] 启动基础模式 (H2数据库 + BS架构)...
echo.
goto start_server

:mysql
echo.
echo [模式2] 启动生产模式 (MySQL数据库 + BS架构)...
echo.
set profile=mysql
goto start_server

:complete
echo.
echo [模式3] 启动完整模式 (H2数据库 + BS+CS架构)...
echo.
call start-complete.bat
goto end

:cs_only
echo.
echo [模式4] 启动CS客户端...
echo.
goto start_cs

:start_server
echo 检查Java环境...
java -version
if %errorlevel% neq 0 (
    echo 错误: 未找到Java环境，请先安装Java 11或更高版本
    pause
    exit /b 1
)

echo.
echo 检查Maven环境...
mvn -version
if %errorlevel% neq 0 (
    echo 错误: 未找到Maven环境，请先安装Maven
    pause
    exit /b 1
)

echo.
echo 编译项目...
set JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF-8
set MAVEN_OPTS=-Dfile.encoding=UTF-8
mvn clean compile "-Dfile.encoding=UTF-8" "-Dmaven.compiler.encoding=UTF-8"
if %errorlevel% neq 0 (
    echo 错误: 项目编译失败
    pause
    exit /b 1
)

echo.
echo 启动应用...
if defined profile (
    echo [生产模式] 使用MySQL数据库
    echo 应用将在 http://localhost:8080 启动
    echo CS架构: Socket服务器端口 8889
    echo MySQL数据库: localhost:3306/clouddisk
    mvn spring-boot:run -Dspring-boot.run.profiles=mysql "-Dfile.encoding=UTF-8"
) else (
    echo [基础模式] 使用H2数据库
    echo 应用将在 http://localhost:8080 启动
    echo CS架构: Socket服务器端口 8889
    echo H2数据库控制台: http://localhost:8080/h2-console
    echo 数据库URL: jdbc:h2:mem:clouddisk
    echo 用户名: sa
    echo 密码: (空)
    mvn spring-boot:run "-Dfile.encoding=UTF-8"
)
goto end

:start_cs
echo 编译项目...
mvn clean compile
if %errorlevel% neq 0 (
    echo 错误: 项目编译失败
    pause
    exit /b 1
)

echo 复制依赖...
mvn dependency:copy-dependencies

echo.
echo 启动CS客户端...
echo 请确保服务器已启动 (端口8889)
java -cp "target/classes;target/dependency/*" cs.client.CSClientGUI
pause
goto end

:end

