# AI_Agent 目前版本 1.1

## 版本更新记录

1. v1.0 实现对话持久化
2. v1.1 实现 RabbitMQ 解耦
3. v1.2 更新缓存穿透、击穿、雪崩缓存问题
4. v1.3 实现 MySql 只存取每次对话总结，减少成本
# AI_Agent 🤖

## 📖 项目介绍
基于 Spring AI Alibaba 实现的智能对话Agent系统
支持对话记忆、消息持久化、缓存优化等功能

## 🛠️ 技术栈
| 技术 | 版本 | 说明 |
|------|------|------|
| Spring Boot | 3.5.13 | 基础框架 |
| Spring AI Alibaba | 1.0.0-M6.1 | AI对话框架(通义千问) |
| Redis | 7.0+ | 缓存/对话记忆存储 |
| MySQL | 8.0+ | 对话数据持久化 |
| RabbitMQ | 3.x | 消息队列异步解耦 |
| MyBatis-Plus | 3.5.15 | ORM框架 |
| Hutool | 5.8.38 | 工具类库 |
| Knife4j | 4.4.0 | API接口文档 |
| LangChain4j | 1.0.0-beta2 | AI链式调用 |
| Lombok | latest | 简化代码 |

## ⚙️ 环境要求
- JDK 21+
- Maven 3.6+
- Redis 7.0+
- RabbitMQ 3.x
- MySQL 8.0+
- 通义千问 API-KEY

## 🚀 快速启动

### 1. 克隆项目
\```bash
git clone https://github.com/xudongma52-star/AI_Agent.git
cd AI_Agent
\```

### 2. 创建数据库
\```sql
CREATE DATABASE ai_agent CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
\```

### 3. 修改配置文件
修改 `src/main/resources/application.yml`

\```yaml
# 数据库配置
spring:
datasource:
url: jdbc:mysql://127.0.0.1:3306/ai_agent
username: 你的用户名
password: 你的密码

# Redis配置
data:
redis:
host: 127.0.0.1
port: 6379
password: 你的Redis密码  # 没有密码则留空

# RabbitMQ配置
rabbitmq:
host: 127.0.0.1
port: 5672
username: guest
password: guest

# 通义千问 API配置
ai:
dashscope:
api-key: 你的API-KEY   # 替换为真实KEY
chat:
options:
model: qwen-plus
\```

### 4. 确保中间件已启动
- ✅ MySQL 已启动
- ✅ Redis 已启动
- ✅ RabbitMQ 已启动（默认端口5672）

### 5. 启动项目
\```bash
mvn spring-boot:run
\```

### 6. 访问地址
| 服务 | 地址 |
|------|------|
| 项目接口 | http://localhost:10002 |
| API文档(Knife4j) | http://localhost:10002/doc.html |
| RabbitMQ控制台 | http://localhost:15672 |

## 📁 项目结构
\```
AI_Agent/
├── src/main/java/com/max/ai_agent/
│   ├── config/          # 配置类 (Redis/RabbitMQ/MybatisPlus)
│   ├── dto/             # 数据传输对象
│   ├── entity/          # 数据库实体类
│   ├── exception/       # 全局异常处理
│   ├── mapper/          # MyBatis-Plus Mapper
│   ├── memory/          # 对话记忆模块
│   └── utils/           # 工具类
├── src/main/resources/
│   ├── mapper/          # MyBatis XML文件
│   └── application.yml  # 配置文件
└── pom.xml
\```

## 📝 版本记录
- **v1.0** 实现对话持久化
- **v1.1** 实现 RabbitMQ 消息队列解耦
- **v1.2** 解决缓存穿透、击穿、雪崩问题
- **v1.3** MySQL 只存储每次对话总结，减少 Token 成本

## ⚠️ 注意事项
- `api-key` 请勿上传至公开仓库，防止泄露
- 建议将 `application.yml` 加入 `.gitignore`
- 生产环境请修改 MySQL、Redis 默认密码