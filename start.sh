#!/bin/bash

echo "启动EasyCloudDisk云盘系统..."
echo

echo "检查Java环境..."
java -version
if [ $? -ne 0 ]; then
    echo "错误: 未找到Java环境，请先安装Java 11或更高版本"
    exit 1
fi

echo
echo "检查Maven环境..."
mvn -version
if [ $? -ne 0 ]; then
    echo "错误: 未找到Maven环境，请先安装Maven"
    exit 1
fi

echo
echo "编译项目..."
mvn clean compile
if [ $? -ne 0 ]; then
    echo "错误: 项目编译失败"
    exit 1
fi

echo
echo "启动应用..."
echo "应用将在 http://localhost:8080 启动"
echo "H2数据库控制台: http://localhost:8080/h2-console"
echo "数据库URL: jdbc:h2:mem:clouddisk"
echo "用户名: sa"
echo "密码: (空)"
echo
echo "按 Ctrl+C 停止应用"
echo

mvn spring-boot:run

