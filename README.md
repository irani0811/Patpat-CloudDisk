# EasyCloudDisk - 企业级云盘系统


## 📋 项目简介

EasyCloudDisk是一个基于Spring Boot的企业级云盘系统，提供完整的文件管理、同步、分享和版本控制功能。系统支持多用户、多设备同步，具备断点续传、文件去重、智能压缩等高级特性。

**🎯 核心特色：统一BS+CS架构**

- **BS架构**：浏览器-服务器模式，提供Web界面
- **CS架构**：客户端-服务器模式，提供桌面应用
- **统一后端**：共享数据库和业务逻辑
- **实时同步**：BS和CS客户端数据实时同步

## ✨ 核心功能

### 🔐 用户管理

- **用户注册/登录** - JWT令牌认证
- **多用户支持** - 独立的文件空间
- **权限控制** - 基于用户的访问控制
- **统一认证** - BS和CS架构共享用户系统

### 📁 文件管理

- **文件上传/下载** - 支持大文件传输
- **文件夹管理** - 创建、删除、重命名目录
- **文件列表** - 分页显示用户文件
- **文件搜索** - 按名称、类型搜索
- **多端同步** - BS和CS客户端数据实时同步

### 🚀 高级上传功能

- **智能上传** - 自动判断文件大小选择上传方式
- **断点续传** - 大文件分块上传，支持断点恢复
- **多线程上传** - 并行处理，提高上传速度
- **批量上传** - 一次上传多个文件
- **文件去重** - MD5哈希去重，节省存储空间

### 📥 高级下载功能

- **单文件下载** - 直接下载文件
- **多线程下载** - 并行下载，提高速度
- **批量下载** - ZIP压缩包下载
- **下载链接** - 生成临时下载链接

### 🔄 双向同步

- **实时监控** - 监控本地文件夹变化
- **自动同步** - 本地→云端实时同步
- **定时同步** - 云端→本地定时同步
- **冲突处理** - 智能处理文件冲突
- **增量同步** - 只同步变化的文件
- **多端同步** - BS和CS客户端数据同步

### 🔗 文件分享

- **分享链接** - 生成公开分享链接
- **权限控制** - 设置分享权限和有效期
- **访问统计** - 记录分享访问次数

### 📊 版本控制

- **文件版本** - 自动保存文件历史版本
- **版本回滚** - 恢复到任意历史版本
- **版本比较** - 比较不同版本差异

### 🛡️ 安全特性

- **文件加密** - AES加密存储
- **压缩存储** - GZIP压缩节省空间
- **MD5验证** - 文件完整性校验
- **访问日志** - 记录所有操作日志

## 🏗️ 技术架构

### 统一架构图

```
┌─────────────────┐    ┌─────────────────┐
│   BS客户端      │    │   CS客户端      │
│  (浏览器)       │    │  (桌面应用)     │
│  端口: 8080     │    │  端口: 8889     │
└─────────────────┘    └─────────────────┘
         │                       │
         │ HTTP/REST API         │ Socket/NIO
         │                       │
         └───────────┬───────────┘
                     │
         ┌─────────────────┐
         │   统一服务端    │
         │  (Spring Boot)   │
         └─────────────────┘
                     │
         ┌─────────────────┐
         │   统一数据库    │
         │  (MySQL/H2)     │
         └─────────────────┘
```

### 后端技术栈

- **Spring Boot 2.7.14** - 主框架
- **Spring Data JPA** - 数据访问层
- **Spring Security** - 安全框架（开发环境已禁用）
- **H2 Database** - 内存数据库（开发）
- **MySQL 8.0** - 生产数据库
- **Maven** - 项目管理工具
- **NIO Socket** - CS架构通信

### 前端技术栈

- **HTML5/CSS3/JavaScript** - 原生前端
- **Bootstrap** - UI框架
- **AJAX** - 异步请求

### 核心组件

- **JWT认证** - 无状态身份验证
- **文件监控** - DirectoryWatcher实时监控
- **多线程处理** - 并行文件操作
- **去重算法** - 基于MD5的文件去重
- **压缩算法** - GZIP/Zlib压缩
- **加密算法** - AES-256加密
- **Socket通信** - CS架构NIO通信

## 📁 项目结构

```
Demo/
├── src/main/java/
│   ├── CloudDiskApplication.java          # 主启动类
│   ├── config/
│   │   ├── DevSecurityConfig.java         # 开发安全配置
│   │   └── FileUploadConfig.java          # 文件上传配置
│   ├── controller/
│   │   ├── AuthController.java            # 认证控制器
│   │   ├── FileController.java            # 文件控制器
│   │   ├── ResumeController.java          # 断点续传控制器
│   │   ├── ShareController.java           # 分享控制器
│   │   ├── SyncController.java            # 同步控制器
│   │   ├── DeltaSyncController.java       # 增量同步控制器
│   │   ├── VersionController.java          # 版本控制器
│   │   └── CSController.java              # CS管理控制器
│   ├── model/
│   │   ├── User.java                      # 用户实体
│   │   ├── FileMetadata.java             # 文件元数据
│   │   ├── FileInfoDTO.java               # 文件信息DTO
│   │   ├── FileChunk.java                 # 文件块
│   │   ├── FileShare.java                 # 文件分享
│   │   ├── FileVersion.java               # 文件版本
│   │   └── SyncEvent.java                 # 同步事件
│   ├── dto/                               # 传输对象
│   │   ├── ChunkDescriptor.java           # 分块描述
│   │   ├── ChunkIndexResponse.java        # 分块索引响应
│   │   ├── DeltaBlock.java                # 差异块
│   │   ├── DeltaApplyRequest.java         # 应用差异请求
│   │   └── DeltaApplyResponse.java        # 应用差异响应
│   ├── repository/
│   │   ├── UserRepository.java            # 用户数据访问
│   │   ├── FileMetadataRepository.java    # 文件数据访问
│   │   ├── FileChunkRepository.java       # 文件块数据访问
│   │   ├── FileShareRepository.java       # 分享数据访问
│   │   ├── SyncEventRepository.java       # 同步事件数据访问
│   │   └── FileVersionRepository.java     # 版本数据访问
│   ├── service/
│   │   ├── UserService.java               # 用户服务
│   │   ├── FileService.java               # 文件服务
│   │   ├── ResumeService.java             # 断点续传服务
│   │   ├── ShareService.java              # 分享服务
│   │   ├── SyncService.java               # 同步服务（监控、本地/双向增量触发）
│   │   ├── DeltaSyncService.java          # 增量同步服务（服务端重建/校验）
│   │   └── VersionService.java            # 版本服务
│   ├── utils/
│   │   ├── JwtUtil.java                   # JWT工具
│   │   ├── FileWatcherUtil.java           # 文件监控工具
│   │   ├── DeduplicationUtil.java         # 去重工具
│   │   ├── CompressionUtil.java           # 压缩工具
│   │   ├── EncryptionUtil.java            # 加密工具
│   │   ├── ChunkingUtil.java              # 分块/哈希工具（32KB）
│   │   └── DeltaSyncUtil.java             # 本地增量重建工具
│   └── cs/                               # CS架构
│       ├── CSProtocol.java                # CS协议定义
│       ├── CSMessageHandler.java          # CS消息处理
│       ├── CSSocketServer.java            # CS Socket服务器
│       ├── CSController.java              # CS管理接口
│       └── client/                        # CS客户端
│           ├── CSClient.java              # 客户端核心
│           └── CSClientGUI.java           # 图形界面
├── src/main/resources/
│   ├── application.yml                    # 主配置文件
│   ├── application-mysql.yml              # MySQL配置
│   ├── static/index.html                  # 前端页面
│   └── sql/init-mysql.sql                 # MySQL初始化脚本
├── storage/                               # 文件存储目录
├── sync/                                  # 同步目录
├── pom.xml                                # Maven配置
├── start.bat                              # 主启动脚本 (多模式选择)
├── start-complete.bat                     # 完整系统启动脚本
├── start.sh                               # Linux/Mac启动脚本
├── docker-compose.yml                     # Docker配置
├── Dockerfile                            # Docker镜像配置
└── CS-INTEGRATION-GUIDE.md               # CS架构集成指南
```

## 🚀 快速开始

### 环境要求

- **Java 11+** - 运行环境
- **Maven 3.6+** - 构建工具
- **MySQL 8.0+** (可选) - 生产数据库
- **Docker** (可选) - 容器化部署

### 方式一：H2数据库启动（推荐开发使用）

#### 1. 克隆项目

```bash
git clone <repository-url>
cd CloudItem/Demo
```

#### 2. 启动应用

```bash
# Windows - 主启动脚本 (推荐)
start.bat

# 选择启动模式:
# 1. 基础模式 (H2数据库 + BS+CS架构)
# 2. 生产模式 (MySQL数据库 + BS+CS架构)  
# 3. 完整模式 (H2数据库 + BS+CS架构 + 图形客户端)
# 4. 仅CS客户端

# Linux/Mac
start.sh
或 mvn spring-boot:run
```

#### 3. 访问应用

- **Web界面**: http://localhost:8080
- **H2控制台**: http://localhost:8080/h2-console
  - JDBC URL: `jdbc:h2:mem:clouddisk`
  - 用户名: `sa`
  - 密码: (空)

### 方式二：MySQL数据库启动（推荐生产使用）

#### 1. 安装MySQL

```bash
# 下载并安装MySQL 8.0+
# 创建数据库
mysql -u root -p
CREATE DATABASE clouddisk;
```

#### 2. 配置数据库

```bash
# 修改application-mysql.yml中的数据库连接信息
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/clouddisk
    username: your_username
    password: your_password
```

#### 3. 启动应用

```bash
# Windows - 选择生产模式
start.bat
# 然后选择 "2. 生产模式 (MySQL数据库 + BS架构)"

# Linux/Mac
mvn spring-boot:run -Dspring.profiles.active=mysql
```

### 方式三：Docker部署

#### 1. 构建镜像

```bash
docker build -t easyclouddisk .
```

#### 2. 启动服务

```bash
docker-compose up -d
```

#### 3. 访问应用

- **Web界面**: http://localhost:8080
- **MySQL**: localhost:3306

## 📱 使用方式

### BS架构 (浏览器)

1. **访问首页**: http://localhost:8080
2. **注册用户**: 填写用户名、邮箱、密码
3. **登录系统**: 使用注册的账户登录
4. **文件管理**: 使用Web界面进行文件操作
5. **CS管理**: 在"CS架构管理"部分管理CS服务器

### CS架构 (桌面客户端)

#### 启动CS客户端

**方式一：主启动脚本 (推荐)**

```bash
# Windows - 选择启动模式
start.bat

# 选择 "3. 完整模式" 启动BS+CS架构 + 图形客户端
# 选择 "4. 仅CS客户端" 只启动CS客户端
```

**方式二：直接运行CS客户端**

```bash
# 直接运行图形客户端
java -cp "target/classes;target/dependency/*" cs.client.CSClientGUI
```

**方式三：一键启动完整系统**

```bash
# 启动服务端 + 图形客户端
start-complete.bat
```

#### CS客户端功能

**🎯 GUI界面特点:**

- **与Web界面完全一致** - 包含用户认证、文件管理、同步功能、文件下载四个模块
- **人性化设计** - 直观的选项卡界面，操作简单易懂
- **真实可用** - 所有按钮功能都经过测试，确保真实可用
- **实时反馈** - 操作日志实时显示，进度条显示上传/下载状态
- **功能完整** - 支持文件上传、下载、同步、分享、版本管理等所有功能

**图形界面客户端特性：**

- **完整功能** - 与BS浏览器端功能完全一致
- **用户注册** - 支持用户注册和登录
- **文件管理** - 上传、下载、列表、删除、分享
- **高级功能** - 版本控制、批量下载、文件同步
- **直观操作** - 点击按钮即可操作
- **文件浏览** - 图形化文件选择对话框
- **实时状态** - 连接和登录状态一目了然
- **操作日志** - 实时显示操作结果
- **多面板布局** - 认证、文件操作、上传、同步分区管理

**CS客户端功能：**

1. **连接服务器**: 自动连接到localhost:8889
2. **用户注册/登录**: 支持用户注册和登录
3. **文件操作**: 上传、下载、列表、删除、分享
4. **高级功能**: 版本控制、批量下载、文件同步
5. **图形界面**: 直观的图形化操作界面

## 🔧 CS协议说明

### 消息格式

```
消息类型::Token::数据
```

### 支持的消息类型

- `LOGIN` - 用户登录
- `LOGOUT` - 用户登出
- `UPLOAD` - 文件上传
- `DOWNLOAD` - 文件下载
- `LIST` - 文件列表
- `DELETE` - 文件删除
- `SYNC_START` - 开始同步
- `SYNC_STOP` - 停止同步
- `SYNC_EVENTS` - 获取同步事件

### 响应格式

```
状态::数据
```

### 响应状态

- `SUCCESS` - 操作成功
- `ERROR` - 操作失败
- `AUTH_REQUIRED` - 需要认证

## 📊 功能对比

| 功能       | BS架构 | CS架构      |
| ---------- | ------ | ----------- |
| 用户认证   | ✅     | ✅          |
| 用户注册   | ✅     | ✅          |
| 文件上传   | ✅     | ✅          |
| 文件下载   | ✅     | ✅          |
| 文件列表   | ✅     | ✅          |
| 文件删除   | ✅     | ✅          |
| 文件同步   | ✅     | ✅          |
| 断点续传   | ✅     | ✅          |
| 文件分享   | ✅     | ✅          |
| 版本控制   | ✅     | ✅          |
| 批量操作   | ✅     | ✅          |
| 图形界面   | ✅     | ✅ (GUI)    |
| 命令行界面 | ❌     | ❌ (已删除) |

## 📖 使用指南

### 🔐 用户注册与登录

1. **访问首页**: http://localhost:8080
2. **注册用户**: 填写用户名、邮箱、密码
3. **登录系统**: 使用注册的账户登录

### 📁 文件管理

#### 基础文件操作

1. **上传文件**: 选择文件，设置路径，点击上传
2. **下载文件**: 输入文件ID，点击下载
3. **创建目录**: 输入目录名，点击创建
4. **获取文件列表**: 查看当前用户的所有文件

#### 高级文件操作

1. **智能上传**: 系统自动判断文件大小选择上传方式
2. **断点续传**: 大文件分块上传，支持断点恢复
3. **多线程上传**: 设置线程数，并行上传提高速度
4. **批量上传**: 一次选择多个文件上传
5. **文件验证**: 验证文件MD5哈希值

### 🔄 双向同步

#### 设置同步

1. **选择同步路径**: 点击"📁 选择文件夹"按钮
2. **输入路径**: 手动输入本地文件夹完整路径
   - Windows: `D:\Cloud_CS\CloudItem\Demo\sync`
   - 相对路径: `./sync` 或 `sync`
   - 当前目录: `.`
3. **开始同步**: 点击"开始同步"按钮

#### 同步功能

- **实时监控**: 自动监控本地文件夹变化
- **自动上传**: 本地文件变化立即同步到云端
- **定时同步**: 每30秒检查云端文件变化
- **冲突处理**: 自动处理文件冲突，创建冲突版本
- **多端同步**: BS和CS客户端数据实时同步

### 📥 文件下载

#### 单文件下载

1. **输入文件ID**: 在"文件ID"输入框中输入文件ID
2. **选择下载方式**: 普通下载或多线程下载
3. **点击下载**: 文件将自动下载到本地

#### 批量下载

1. **输入文件ID列表**: 用逗号分隔多个文件ID
2. **选择格式**: ZIP压缩包或TAR压缩包
3. **点击批量下载**: 生成压缩包下载

#### 生成下载链接

1. **输入文件ID**: 选择要分享的文件
2. **设置有效期**: 选择链接有效期（分钟）
3. **生成链接**: 获得可分享的下载链接

### 🔗 文件分享

1. **选择文件**: 输入要分享的文件ID
2. **设置权限**: 选择分享权限（只读/读写）
3. **设置有效期**: 选择分享链接有效期
4. **生成分享**: 获得分享链接和访问码

### 📊 版本管理

1. **查看版本**: 输入文件ID查看所有版本
2. **创建版本**: 上传新版本自动创建版本记录
3. **版本回滚**: 选择历史版本进行回滚
4. **版本比较**: 比较不同版本的差异

### 🖥️ CS客户端使用

#### 图形界面客户端 (推荐)

**启动方式：**

```bash
# 一键启动完整系统
start-gui-complete.bat

# 或单独启动图形客户端
start-cs-gui.bat
```

**界面功能：**

- **连接管理** - 连接/断开服务器按钮
- **用户认证** - 用户名/密码输入框和登录按钮
- **文件操作** - 文件ID输入、下载路径设置、列表/下载/删除按钮
- **文件上传** - 本地文件选择、云端路径设置、上传按钮
- **文件同步** - 同步路径选择、开始/停止同步按钮
- **操作日志** - 实时显示操作结果和状态信息

#### 直接运行CS客户端

**启动方式：**

```bash
# 直接运行图形客户端
java -cp "target/classes;target/dependency/*" cs.client.CSClientGUI
```

**图形界面功能：**

- **连接管理** - 连接/断开服务器按钮
- **用户认证** - 用户名/密码输入框和登录按钮
- **文件操作** - 文件ID输入、下载路径设置、列表/下载/删除按钮
- **文件上传** - 本地文件选择、云端路径设置、上传按钮
- **文件同步** - 同步路径选择、开始/停止同步按钮
- **操作日志** - 实时显示操作结果和状态信息

## ⚙️ 配置说明

### 数据库配置

#### H2配置（开发环境）

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:clouddisk
    driver-class-name: org.h2.Driver
    username: sa
    password: 
```

#### MySQL配置（生产环境）

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/clouddisk
    driver-class-name: com.mysql.cj.jdbc.Driver
    username: your_username
    password: your_password
```

### 文件存储配置

```yaml
file:
  storage:
    path: ./storage/                    # 文件存储路径
    max-size: 1073741824               # 最大文件大小(1GB)
    allowed-types:                     # 允许的文件类型
      - image/jpeg
      - image/png
      - application/pdf
      - text/plain
```

### 上传大小限制

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 1GB               # 单个文件最大大小
      max-request-size: 1GB            # 总请求最大大小
      enabled: true                    # 启用文件上传

server:
  tomcat:
    max-http-form-post-size: 1GB       # HTTP表单POST最大大小
    max-swallow-size: 1GB              # 最大吞入大小
```

### JWT配置

```yaml
jwt:
  secret: your-secret-key              # JWT密钥
  expiration: 86400000                 # 过期时间(24小时)
```

### CS服务器配置

```yaml
cs:
  server:
    port: 8889                        # CS服务器端口
    max-connections: 100              # 最大连接数
    thread-pool-size: 10              # 线程池大小
```

## 🔧 开发指南

### 项目构建

```bash
# 编译项目
mvn clean compile

# 运行测试
mvn test

# 打包应用
mvn clean package

# 运行应用
java -jar target/EasyCloudDisk-1.0-SNAPSHOT.jar
```

### 数据库操作

```bash
# H2数据库
# 访问: http://localhost:8080/h2-console
# JDBC URL: jdbc:h2:mem:clouddisk
# 用户名: sa, 密码: (空)

# MySQL数据库
mysql -u root -p
USE clouddisk;
SHOW TABLES;
```

### 日志配置

```yaml
logging:
  level:
    com.clouddisk: DEBUG              # 应用日志级别
    org.springframework.web: DEBUG    # Web请求日志
    org.hibernate.SQL: DEBUG          # SQL日志
```

### CS架构开发

#### 扩展CS功能

1. **添加新消息类型**:

   ```java
   // 在CSProtocol.java中添加
   public static final String NEW_OPERATION = "NEW_OPERATION";
   ```
2. **处理新消息**:

   ```java
   // 在CSMessageHandler.java中添加
   case CSProtocol.NEW_OPERATION:
       return handleNewOperation(csMessage);
   ```
3. **客户端支持**:

   ```java
   // 在CSClient.java中添加
   public boolean newOperation(String data) {
       // 实现新功能
   }
   ```

## 🐛 故障排除

### 常见问题

#### 1. 启动失败

**问题**: 应用启动失败
**解决**:

- 检查Java版本（需要Java 11+）
- 检查端口占用（8080端口）
- 检查数据库连接

#### 2. 文件上传失败

**问题**: 文件上传超过大小限制
**解决**:

- 检查 `application.yml`中的上传大小配置
- 确保 `FileUploadConfig.java`配置正确
- 重启应用使配置生效

#### 3. 同步功能不工作

**问题**: 文件同步不生效
**解决**:

- 检查同步路径是否正确
- 确保路径存在且有读写权限
- 查看控制台调试信息

#### 4. 数据库连接失败

**问题**: MySQL连接失败
**解决**:

- 检查MySQL服务是否启动
- 验证数据库连接信息
- 确保数据库已创建

#### 5. CS服务器启动失败

**问题**: CS服务器无法启动
**解决**:

- 检查端口8889是否被占用
- 检查Java版本 (需要Java 11+)
- 查看控制台错误信息

#### 6. CS客户端连接失败

**问题**: 客户端无法连接到服务器
**解决**:

- 确保CS服务器已启动
- 检查网络连接
- 验证服务器地址和端口

#### 7. 认证失败

**问题**: CS客户端登录失败
**解决**:

- 确保用户已在BS端注册
- 检查用户名和密码
- 验证JWT token配置

### 调试技巧

#### 1. 查看日志

```bash
# 查看应用日志
tail -f logs/application.log

# 查看错误日志
grep ERROR logs/application.log
```

#### 2. 数据库调试

```sql
-- 查看用户表
SELECT * FROM users;

-- 查看文件表
SELECT * FROM file_metadata;

-- 查看同步事件
SELECT * FROM sync_event;
```

#### 3. 文件系统调试

```bash
# 检查存储目录
ls -la storage/

# 检查同步目录
ls -la sync/

# 检查文件权限
chmod -R 755 storage/
```

#### 4. CS服务器调试

```bash
# 查看CS服务器状态
curl http://localhost:8080/api/cs/status

# 测试Socket连接
telnet localhost 8889

# 查看CS服务器日志
# 服务端日志：控制台输出
# 客户端日志：控制台输出
```

## 📈 性能优化

### 数据库优化

- **索引优化**: 为常用查询字段添加索引
- **连接池**: 配置合适的数据库连接池大小
- **查询优化**: 使用分页查询避免大量数据加载

### 文件存储优化

- **去重存储**: 相同文件只存储一份
- **压缩存储**: 自动压缩文件节省空间
- **分块存储**: 大文件分块存储提高性能

### 同步优化

- **增量同步**: 只同步变化的文件
- **异步处理**: 文件操作异步执行
- **缓存机制**: 缓存常用数据减少数据库查询

### CS架构优化

- **连接池配置**: 调整CS服务器线程池大小
- **消息处理优化**: 异步处理消息，批量处理操作
- **Socket缓冲区**: 优化Socket缓冲区大小

## 🔒 安全考虑

### 数据安全

- **文件加密**: 敏感文件AES加密存储
- **传输加密**: HTTPS传输保护
- **访问控制**: 基于用户的权限控制

### 系统安全

- **输入验证**: 严格验证用户输入
- **SQL注入防护**: 使用参数化查询
- **XSS防护**: 输出转义防止跨站脚本

### 文件安全

- **类型检查**: 验证文件类型防止恶意文件
- **大小限制**: 限制文件大小防止资源耗尽
- **病毒扫描**: 集成病毒扫描功能（可选）

### CS架构安全

- **认证安全**: JWT token过期时间，密码加密存储，会话管理
- **网络安全**: Socket连接加密（计划中），消息内容加密，访问控制
- **数据安全**: 文件加密存储，传输数据加密，权限控制

## 🚀 部署指南

### 生产环境部署

#### 1. 环境准备

```bash
# 安装Java 11+
sudo apt-get install openjdk-11-jdk

# 安装MySQL 8.0+
sudo apt-get install mysql-server

# 安装Maven
sudo apt-get install maven
```

#### 2. 数据库配置

```sql
CREATE DATABASE clouddisk;
CREATE USER 'clouddisk'@'localhost' IDENTIFIED BY 'password';
GRANT ALL PRIVILEGES ON clouddisk.* TO 'clouddisk'@'localhost';
FLUSH PRIVILEGES;
```

#### 3. 应用配置

```yaml
# application-prod.yml
spring:
  profiles:
    active: mysql
  datasource:
    url: jdbc:mysql://localhost:3306/clouddisk
    username: clouddisk
    password: password

# CS服务器配置
cs:
  server:
    port: 8889
    max-connections: 100
    thread-pool-size: 10
```

#### 4. 启动应用

```bash
# 打包应用
mvn clean package

# 启动应用
java -jar target/EasyCloudDisk-1.0-SNAPSHOT.jar --spring.profiles.active=mysql
```

### Docker部署

#### 1. 构建镜像

```bash
docker build -t easyclouddisk:latest .
```

#### 2. 运行容器

```bash
docker run -d \
  --name easyclouddisk \
  -p 8080:8080 \
  -p 8889:8889 \
  -v /data/storage:/app/storage \
  easyclouddisk:latest
```

#### 3. 使用Docker Compose

```bash
docker-compose up -d
```

## 📊 监控与维护

### 系统监控

- **应用监控**: 监控应用运行状态
- **数据库监控**: 监控数据库性能
- **文件系统监控**: 监控存储空间使用
- **CS服务器监控**: 监控CS连接状态

### 日志管理

- **日志轮转**: 自动轮转日志文件
- **日志分析**: 分析错误日志和访问日志
- **告警机制**: 设置关键指标告警

### 备份策略

- **数据库备份**: 定期备份数据库
- **文件备份**: 备份重要文件
- **配置备份**: 备份配置文件

## 🎯 未来计划

### 短期计划

- [X] CS图形界面客户端 ✅
- [ ] 文件拖拽上传
- [ ] 实时同步状态显示
- [ ] 移动端支持

### 中期计划

- [ ] 移动端CS客户端
- [ ] 离线模式支持
- [ ] 高级同步功能
- [ ] 分布式架构

### 长期计划

- [ ] 分布式架构
- [ ] 负载均衡
- [ ] 高可用性
- [ ] 微服务架构

## 🤝 贡献指南

### 开发流程

1. **Fork项目**: 创建项目分支
2. **开发功能**: 实现新功能或修复bug
3. **测试验证**: 确保功能正常
4. **提交代码**: 提交Pull Request

### 代码规范

- **Java代码**: 遵循Java编码规范
- **注释规范**: 添加必要的代码注释
- **测试覆盖**: 确保测试覆盖率

### 问题反馈

- **Bug报告**: 详细描述问题和复现步骤
- **功能建议**: 提出新功能建议
- **性能优化**: 提出性能优化建议

## 📄 许可证

本项目采用MIT许可证，详情请查看[LICENSE](LICENSE)文件。

## 📞 联系方式

- **项目地址**: [GitHub Repository]
- **问题反馈**: [GitHub Issues]
- **技术交流**: [技术交流群]

## 🙏 致谢

感谢所有为项目做出贡献的开发者和用户！

---

**EasyCloudDisk** - 统一BS+CS架构的企业级云盘系统！让文件管理更简单，让数据同步更智能！ 🚀
