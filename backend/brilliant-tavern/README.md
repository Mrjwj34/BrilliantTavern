# BrilliantTavern 后端启动指南

## 环境要求

- Java 21+
- PostgreSQL 12+
- Maven 3.6+

## 数据库设置

1. 创建PostgreSQL数据库：
```sql
CREATE DATABASE brilliant_tavern;
```

2. 执行初始化脚本：
```bash
psql -U postgres -d brilliant_tavern -f scripts/init_database.sql
```

## 配置文件

修改 `src/main/resources/application.yml` 中的数据库配置：

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/brilliant_tavern
    username: postgres
    password: your_password_here  # 请替换为你的数据库密码
```

## 启动应用

1. 使用Maven启动：
```bash
mvn spring-boot:run
```

2. 或者先编译再启动：
```bash
mvn clean package
java -jar target/brilliant-tavern-0.0.1-SNAPSHOT.jar
```

应用将在 http://localhost:8080 启动。

## API接口

### 认证接口

所有接口都在 `/api` 路径下：

#### 用户注册
```
POST /api/auth/register
Content-Type: application/json

{
    "username": "testuser",
    "email": "test@example.com",
    "password": "password123",
    "confirmPassword": "password123"
}
```

#### 用户登录
```
POST /api/auth/login
Content-Type: application/json

{
    "username": "testuser",
    "password": "password123"
}
```

#### 获取用户信息（需要认证）
```
GET /api/test/profile
Authorization: Bearer <your_jwt_token>
```

#### 公开接口测试
```
GET /api/test/public
```

## 测试

1. 可以使用预创建的管理员账户登录：
   - 用户名: `admin`
   - 密码: `password123`

2. API测试示例（使用curl）：

```bash
# 注册新用户
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "newuser",
    "email": "newuser@example.com", 
    "password": "password123",
    "confirmPassword": "password123"
  }'

# 用户登录
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin",
    "password": "password123"
  }'

# 获取用户信息（替换<token>为登录返回的JWT token）
curl -X GET http://localhost:8080/api/test/profile \
  -H "Authorization: Bearer <token>"
```

## 数据库查看

登录数据库查看数据：

```sql
-- 查看用户表
SELECT * FROM users;

-- 查看角色卡表
SELECT * FROM character_cards;

-- 查看数据库结构
\d+ users
\d+ character_cards
```

## 下一步开发

用户登录系统已经完成，接下来可以开发：

1. 角色卡管理功能
2. WebSocket聊天功能
3. AI集成
4. 文件上传功能

## 故障排除

1. **数据库连接失败**: 检查PostgreSQL是否启动，用户名密码是否正确
2. **端口冲突**: 修改application.yml中的server.port
3. **JWT错误**: 检查JWT secret配置是否正确

## 注意事项

- JWT secret在生产环境中应使用更强的密钥
- 数据库密码不要提交到版本控制系统
- 日志级别在生产环境中应调整为INFO或WARN
