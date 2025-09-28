# BrilliantTavern 生产环境部署指南

本文档提供了 BrilliantTavern 在生产环境中的完整部署和运维指南。

## 目录

- [架构概览](#架构概览)
- [服务器要求](#服务器要求)
- [生产环境配置](#生产环境配置)
- [SSL证书配置](#ssl证书配置)
- [数据库优化](#数据库优化)
- [监控和日志](#监控和日志)
- [备份策略](#备份策略)
- [扩容方案](#扩容方案)
- [安全加固](#安全加固)
- [故障恢复](#故障恢复)

## 架构概览

```
[Internet] 
    ↓
[Load Balancer/CDN]
    ↓
[Nginx (SSL Termination)]
    ↓
[Docker Compose Stack]
├── Frontend (Static Files)
├── Backend (Spring Boot)
├── PostgreSQL (with pgvector)
├── Redis (Cache)
└── Fish Speech TTS (Optional)
```

## 服务器要求

### 最小生产配置
- **CPU**: 4核心 (Intel Xeon 或 AMD EPYC)
- **内存**: 8GB RAM
- **存储**: 100GB SSD (系统) + 500GB SSD (数据)
- **网络**: 100Mbps 稳定带宽
- **操作系统**: Ubuntu 20.04 LTS / CentOS 8

### 推荐生产配置
- **CPU**: 8核心或更多
- **内存**: 16GB RAM 或更多
- **存储**: 
  - 系统盘: 200GB NVMe SSD
  - 数据盘: 1TB NVMe SSD (RAID 1)
  - 备份盘: 2TB HDD
- **网络**: 1Gbps 带宽
- **GPU**: NVIDIA RTX 3080 或更高 (如果使用本地TTS)

### 高可用配置
- **负载均衡器**: 2台 (主备)
- **应用服务器**: 3台或更多
- **数据库**: PostgreSQL 主从复制 + pgpool
- **缓存**: Redis Sentinel 集群
- **存储**: 分布式存储 (Ceph/GlusterFS)

## 生产环境配置

### 1. 系统优化

#### 内核参数优化
```bash
# 编辑 /etc/sysctl.conf
cat >> /etc/sysctl.conf << EOF
# 网络优化
net.core.somaxconn = 65535
net.core.netdev_max_backlog = 65535
net.ipv4.tcp_max_syn_backlog = 65535
net.ipv4.tcp_fin_timeout = 10
net.ipv4.tcp_keepalive_time = 1200
net.ipv4.tcp_max_tw_buckets = 5000

# 文件句柄限制
fs.file-max = 2097152
fs.nr_open = 2097152

# 虚拟内存优化
vm.swappiness = 10
vm.dirty_ratio = 15
vm.dirty_background_ratio = 5
EOF

# 应用配置
sysctl -p
```

#### 用户限制配置
```bash
# 编辑 /etc/security/limits.conf
cat >> /etc/security/limits.conf << EOF
* soft nofile 65535
* hard nofile 65535
* soft nproc 65535
* hard nproc 65535
EOF
```

### 2. Docker 生产配置

#### Docker Daemon 配置
```bash
# 创建 /etc/docker/daemon.json
cat > /etc/docker/daemon.json << EOF
{
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "100m",
    "max-file": "3"
  },
  "storage-driver": "overlay2",
  "live-restore": true,
  "userland-proxy": false,
  "registry-mirrors": ["https://mirror.ccs.tencentyun.com"],
  "exec-opts": ["native.cgroupdriver=systemd"],
  "default-ulimits": {
    "nofile": {
      "Hard": 64000,
      "Name": "nofile",
      "Soft": 64000
    }
  }
}
EOF

# 重启Docker
systemctl restart docker
```

### 3. 环境变量配置

#### 生产环境 .env 文件
```bash
# =============================================================================
# BrilliantTavern 生产环境配置
# =============================================================================

# 服务器配置
SERVER_PORT=8080
CONTEXT_PATH=/api
APP_NAME=brilliant-tavern

# 数据库配置
DATABASE_URL=jdbc:postgresql://postgres:5432/brilliant_tavern
DATABASE_USERNAME=postgres
DATABASE_PASSWORD=<STRONG_PASSWORD>

# Redis配置
REDIS_HOST=redis
REDIS_PASSWORD=<STRONG_PASSWORD>

# JWT认证配置
JWT_SECRET=<STRONG_JWT_SECRET_AT_LEAST_32_CHARS>
JWT_EXPIRATION=86400000
JWT_HEADER=Authorization
JWT_TOKEN_PREFIX=Bearer 

# Google Cloud Vertex AI配置
VERTEX_AI_PROJECT_ID=<YOUR_GCP_PROJECT>
VERTEX_AI_LOCATION=us-central1
VERTEX_AI_MODEL=gemini-2.5-flash
VERTEX_AI_EMBEDDING_MODEL=text-embedding-004

# 角色记忆配置
CHARACTER_MEMORY_SIMILARITY_THRESHOLD=0.75
CHARACTER_MEMORY_MAX_RESULTS=5

# TTS服务配置
TTS_SERVICE_URL=https://your-tts-service.com
TTS_WARMUP_ENABLED=true
TTS_WARMUP_TIMEOUT=30s

# GenAI预热配置
GENAI_WARMUP_ENABLED=true
GENAI_WARMUP_TIMEOUT=60s
GENAI_WARMUP_MAINTAIN_INTERVAL=3600000

# CORS配置 (生产域名)
CORS_ALLOWED_ORIGINS=https://your-domain.com,https://www.your-domain.com
CORS_ALLOWED_METHODS=GET,POST,PUT,DELETE,OPTIONS,PATCH
CORS_ALLOWED_HEADERS=*
CORS_ALLOW_CREDENTIALS=true
CORS_MAX_AGE=3600

# WebSocket配置
WEBSOCKET_ALLOWED_ORIGINS=https://your-domain.com,https://www.your-domain.com

# 日志级别配置 (生产环境)
LOG_LEVEL_ROOT=WARN
LOG_LEVEL_APP=INFO
LOG_LEVEL_SQL=WARN
```

## SSL证书配置

### 1. 使用 Let's Encrypt

#### 安装 Certbot
```bash
# Ubuntu/Debian
sudo apt-get update
sudo apt-get install certbot

# CentOS/RHEL
sudo yum install certbot
```

#### 申请证书
```bash
# 停止现有的Web服务
docker-compose down

# 申请证书
sudo certbot certonly --standalone \
  -d your-domain.com \
  -d www.your-domain.com \
  --email your-email@domain.com \
  --agree-tos \
  --no-eff-email

# 设置自动续期
echo "0 2 * * * certbot renew --quiet && docker-compose restart nginx" | sudo crontab -
```

### 2. 更新 Nginx 配置

创建生产环境的 nginx 配置：

```bash
# nginx-prod.conf
user nginx;
worker_processes auto;
error_log /var/log/nginx/error.log warn;
pid /var/run/nginx.pid;

events {
    worker_connections 4096;
    use epoll;
    multi_accept on;
}

http {
    include /etc/nginx/mime.types;
    default_type application/octet-stream;

    # 日志格式
    log_format main '$remote_addr - $remote_user [$time_local] "$request" '
                    '$status $body_bytes_sent "$http_referer" '
                    '"$http_user_agent" "$http_x_forwarded_for" '
                    '$request_time $upstream_response_time';

    access_log /var/log/nginx/access.log main;

    # 性能优化
    sendfile on;
    tcp_nopush on;
    tcp_nodelay on;
    keepalive_timeout 65;
    types_hash_max_size 2048;
    client_max_body_size 100M;

    # Gzip压缩
    gzip on;
    gzip_vary on;
    gzip_min_length 1024;
    gzip_proxied any;
    gzip_comp_level 6;
    gzip_types
        text/plain
        text/css
        text/xml
        text/javascript
        application/javascript
        application/json
        application/xml+rss
        application/atom+xml
        image/svg+xml;

    # 限流配置
    limit_req_zone $binary_remote_addr zone=api:10m rate=100r/m;
    limit_req_zone $binary_remote_addr zone=login:10m rate=5r/m;

    # 上游后端服务
    upstream backend {
        least_conn;
        server backend:8080 max_fails=3 fail_timeout=30s;
        keepalive 32;
    }

    # HTTP重定向到HTTPS
    server {
        listen 80;
        server_name your-domain.com www.your-domain.com;
        return 301 https://$server_name$request_uri;
    }

    # HTTPS主站
    server {
        listen 443 ssl http2;
        server_name your-domain.com www.your-domain.com;

        # SSL配置
        ssl_certificate /etc/letsencrypt/live/your-domain.com/fullchain.pem;
        ssl_certificate_key /etc/letsencrypt/live/your-domain.com/privkey.pem;
        ssl_protocols TLSv1.2 TLSv1.3;
        ssl_ciphers ECDHE-RSA-AES256-GCM-SHA512:DHE-RSA-AES256-GCM-SHA512:ECDHE-RSA-AES256-GCM-SHA384:DHE-RSA-AES256-GCM-SHA384;
        ssl_prefer_server_ciphers off;
        ssl_session_timeout 1d;
        ssl_session_cache shared:SSL:50m;
        ssl_stapling on;
        ssl_stapling_verify on;

        # 安全头部
        add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
        add_header X-Frame-Options DENY always;
        add_header X-Content-Type-Options nosniff always;
        add_header X-XSS-Protection "1; mode=block" always;
        add_header Referrer-Policy "strict-origin-when-cross-origin" always;
        add_header Content-Security-Policy "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data: https:; font-src 'self' https:; connect-src 'self' wss: https:;" always;

        # 前端静态文件
        location / {
            root /var/www/html;
            index index.html;
            try_files $uri $uri/ /index.html;
            
            # 缓存配置
            location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff|woff2|ttf|eot)$ {
                expires 1y;
                add_header Cache-Control "public, immutable";
                access_log off;
            }
        }

        # API代理到后端
        location /api/ {
            limit_req zone=api burst=20 nodelay;
            
            proxy_pass http://backend;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto $scheme;
            
            # WebSocket支持
            proxy_http_version 1.1;
            proxy_set_header Upgrade $http_upgrade;
            proxy_set_header Connection "upgrade";
            
            # 超时设置
            proxy_connect_timeout 60s;
            proxy_send_timeout 60s;
            proxy_read_timeout 60s;
            
            # 缓冲设置
            proxy_buffering off;
            proxy_request_buffering off;
        }

        # 登录接口限流
        location /api/auth/login {
            limit_req zone=login burst=3 nodelay;
            
            proxy_pass http://backend;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto $scheme;
        }

        # WebSocket专用代理
        location /api/ws/ {
            proxy_pass http://backend;
            proxy_http_version 1.1;
            proxy_set_header Upgrade $http_upgrade;
            proxy_set_header Connection "upgrade";
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto $scheme;
            
            # WebSocket长连接设置
            proxy_read_timeout 86400;
            proxy_send_timeout 86400;
        }

        # 用户上传文件
        location /api/uploads/ {
            alias /var/www/html/uploads/;
            
            # 安全设置
            location ~* \.(php|jsp|asp|sh|bash|exe)$ {
                deny all;
            }
            
            # 缓存设置
            expires 30d;
            add_header Cache-Control "public";
        }

        # 健康检查
        location /health {
            access_log off;
            return 200 "healthy\n";
            add_header Content-Type text/plain;
        }

        # 禁止访问敏感文件
        location ~ /\. {
            deny all;
            access_log off;
            log_not_found off;
        }

        location ~ \.(sql|conf|bak|backup|log)$ {
            deny all;
            access_log off;
            log_not_found off;
        }
    }
}
```

### 3. 更新 Docker Compose

在 docker-compose.yml 中添加SSL证书挂载：

```yaml
  nginx:
    image: nginx:alpine
    container_name: brilliant-tavern-nginx
    volumes:
      - ./nginx-prod.conf:/etc/nginx/nginx.conf:ro
      - /etc/letsencrypt:/etc/letsencrypt:ro
      - frontend_dist:/var/www/html:ro
      - app_uploads:/var/www/html/uploads:ro
    ports:
      - "80:80"
      - "443:443"
    networks:
      - brilliant-tavern-network
    depends_on:
      - backend
      - frontend
```

## 数据库优化

### 1. PostgreSQL 配置优化

创建生产环境的 PostgreSQL 配置：

```bash
# postgresql-prod.conf
# 内存配置
shared_buffers = 2GB                    # 系统内存的25%
effective_cache_size = 6GB              # 系统内存的75%
work_mem = 64MB                         # 根据并发查询数调整
maintenance_work_mem = 512MB

# 连接配置
max_connections = 200
listen_addresses = '*'

# 写入性能优化
wal_buffers = 64MB
checkpoint_segments = 64
checkpoint_completion_target = 0.9
wal_writer_delay = 200ms

# 查询优化
random_page_cost = 1.1                  # SSD存储使用较低值
effective_io_concurrency = 200          # SSD存储

# 日志配置
log_destination = 'stderr'
logging_collector = on
log_directory = 'pg_log'
log_filename = 'postgresql-%Y-%m-%d_%H%M%S.log'
log_rotation_age = 1d
log_min_duration_statement = 1000ms     # 记录慢查询
log_checkpoints = on
log_connections = on
log_disconnections = on
log_lock_waits = on

# 统计信息
track_activities = on
track_counts = on
track_io_timing = on
track_functions = all
```

### 2. 数据库连接池

在应用中配置HikariCP：

```yaml
spring:
  datasource:
    type: com.zaxxer.hikari.HikariDataSource
    hikari:
      pool-name: BrilliantTavernPool
      maximum-pool-size: 20              # 根据数据库max_connections调整
      minimum-idle: 5
      idle-timeout: 300000               # 5分钟
      max-lifetime: 1800000              # 30分钟
      connection-timeout: 30000          # 30秒
      validation-timeout: 5000           # 5秒
      leak-detection-threshold: 60000    # 1分钟
      connection-test-query: SELECT 1
```

### 3. 数据库监控

```sql
-- 创建监控用户
CREATE USER monitor_user WITH PASSWORD 'monitor_password';
GRANT CONNECT ON DATABASE brilliant_tavern TO monitor_user;
GRANT USAGE ON SCHEMA public TO monitor_user;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO monitor_user;
GRANT SELECT ON ALL SEQUENCES IN SCHEMA public TO monitor_user;

-- 启用pg_stat_statements扩展
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;

-- 监控查询
-- 查看慢查询
SELECT query, calls, total_time, mean_time 
FROM pg_stat_statements 
ORDER BY mean_time DESC 
LIMIT 10;

-- 查看数据库连接状态
SELECT state, count(*) 
FROM pg_stat_activity 
GROUP BY state;

-- 查看表大小
SELECT schemaname, tablename, 
       pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) as size
FROM pg_tables 
WHERE schemaname = 'public'
ORDER BY pg_total_relation_size(schemaname||'.'||tablename) DESC;
```

## 监控和日志

### 1. 应用监控

#### 配置 Prometheus 监控

```yaml
# 在 docker-compose.yml 中添加
  prometheus:
    image: prom/prometheus:latest
    container_name: brilliant-tavern-prometheus
    ports:
      - "9090:9090"
    volumes:
      - ./monitoring/prometheus.yml:/etc/prometheus/prometheus.yml
      - prometheus_data:/prometheus
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--storage.tsdb.path=/prometheus'
      - '--web.console.libraries=/etc/prometheus/console_libraries'
      - '--web.console.templates=/etc/prometheus/consoles'
      - '--storage.tsdb.retention.time=200h'
      - '--web.enable-lifecycle'
    networks:
      - brilliant-tavern-network

  grafana:
    image: grafana/grafana:latest
    container_name: brilliant-tavern-grafana
    ports:
      - "3001:3000"
    environment:
      - GF_SECURITY_ADMIN_PASSWORD=admin_password
    volumes:
      - grafana_data:/var/lib/grafana
      - ./monitoring/grafana/provisioning:/etc/grafana/provisioning
    networks:
      - brilliant-tavern-network
```

#### Prometheus 配置文件

```yaml
# monitoring/prometheus.yml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

rule_files:
  - "alert_rules.yml"

scrape_configs:
  - job_name: 'brilliant-tavern-backend'
    static_configs:
      - targets: ['backend:8080']
    metrics_path: '/api/actuator/prometheus'
    scrape_interval: 30s

  - job_name: 'postgres-exporter'
    static_configs:
      - targets: ['postgres-exporter:9187']

  - job_name: 'redis-exporter'
    static_configs:
      - targets: ['redis-exporter:9121']

  - job_name: 'node-exporter'
    static_configs:
      - targets: ['node-exporter:9100']

alerting:
  alertmanagers:
    - static_configs:
        - targets:
          - alertmanager:9093
```

### 2. 日志管理

#### ELK Stack 配置

```yaml
# 在 docker-compose.yml 中添加
  elasticsearch:
    image: docker.elastic.co/elasticsearch/elasticsearch:8.6.0
    container_name: brilliant-tavern-elasticsearch
    environment:
      - discovery.type=single-node
      - "ES_JAVA_OPTS=-Xms1g -Xmx1g"
      - xpack.security.enabled=false
    volumes:
      - elasticsearch_data:/usr/share/elasticsearch/data
    ports:
      - "9200:9200"
    networks:
      - brilliant-tavern-network

  logstash:
    image: docker.elastic.co/logstash/logstash:8.6.0
    container_name: brilliant-tavern-logstash
    volumes:
      - ./logging/logstash.conf:/usr/share/logstash/pipeline/logstash.conf
    ports:
      - "5044:5044"
    networks:
      - brilliant-tavern-network
    depends_on:
      - elasticsearch

  kibana:
    image: docker.elastic.co/kibana/kibana:8.6.0
    container_name: brilliant-tavern-kibana
    environment:
      - ELASTICSEARCH_HOSTS=http://elasticsearch:9200
    ports:
      - "5601:5601"
    networks:
      - brilliant-tavern-network
    depends_on:
      - elasticsearch
```

#### 日志轮转配置

```bash
# /etc/logrotate.d/brilliant-tavern
/var/log/brilliant-tavern/*.log {
    daily
    missingok
    rotate 30
    compress
    delaycompress
    notifempty
    create 644 root root
    postrotate
        docker-compose exec backend kill -USR1 1
    endscript
}
```

## 备份策略

### 1. 数据库备份

#### 自动备份脚本

```bash
#!/bin/bash
# backup-database.sh

BACKUP_DIR="/opt/backups/brilliant-tavern"
DATE=$(date +%Y%m%d_%H%M%S)
RETENTION_DAYS=7

# 创建备份目录
mkdir -p $BACKUP_DIR

# 数据库备份
docker-compose exec -T postgres pg_dump -U postgres \
    -h postgres brilliant_tavern | gzip > $BACKUP_DIR/db_backup_$DATE.sql.gz

# 上传到云存储 (示例: AWS S3)
aws s3 cp $BACKUP_DIR/db_backup_$DATE.sql.gz \
    s3://your-backup-bucket/database/

# 清理本地旧备份
find $BACKUP_DIR -name "db_backup_*.sql.gz" -mtime +$RETENTION_DAYS -delete

# 清理云存储旧备份
aws s3 ls s3://your-backup-bucket/database/ --recursive | \
    while read -r line; do
        createDate=$(echo $line | awk '{print $1" "$2}')
        createDate=$(date -d"$createDate" +%s)
        olderThan=$(date -d"$RETENTION_DAYS days ago" +%s)
        if [[ $createDate -lt $olderThan ]]; then
            fileName=$(echo $line | awk '{print $4}')
            aws s3 rm s3://your-backup-bucket/database/$fileName
        fi
    done

echo "Database backup completed: $DATE"
```

### 2. 应用数据备份

```bash
#!/bin/bash
# backup-app-data.sh

BACKUP_DIR="/opt/backups/brilliant-tavern"
DATE=$(date +%Y%m%d_%H%M%S)

# 备份用户上传文件
docker run --rm \
    -v brilliant-tavern_app_uploads:/data:ro \
    -v $BACKUP_DIR:/backup \
    alpine tar czf /backup/uploads_$DATE.tar.gz -C /data .

# 备份应用配置
tar czf $BACKUP_DIR/config_$DATE.tar.gz \
    docker-compose.yml \
    .env \
    nginx.conf \
    monitoring/ \
    scripts/

# 上传到云存储
aws s3 cp $BACKUP_DIR/uploads_$DATE.tar.gz \
    s3://your-backup-bucket/uploads/
aws s3 cp $BACKUP_DIR/config_$DATE.tar.gz \
    s3://your-backup-bucket/config/

echo "App data backup completed: $DATE"
```

### 3. 定时备份

```bash
# 添加到 crontab
# 每天凌晨2点备份数据库
0 2 * * * /opt/scripts/backup-database.sh

# 每天凌晨3点备份应用数据
0 3 * * * /opt/scripts/backup-app-data.sh

# 每周日凌晨4点进行完整备份
0 4 * * 0 /opt/scripts/full-backup.sh
```

## 扩容方案

### 1. 垂直扩容 (Scale Up)

#### 应用服务器扩容
```yaml
# docker-compose.yml 中的资源限制
services:
  backend:
    deploy:
      resources:
        limits:
          cpus: '4.0'      # 增加到4核
          memory: 4G       # 增加到4GB
        reservations:
          cpus: '2.0'
          memory: 2G
```

#### 数据库扩容
```bash
# 增加PostgreSQL内存配置
shared_buffers = 4GB
effective_cache_size = 12GB
work_mem = 128MB
```

### 2. 水平扩容 (Scale Out)

#### 负载均衡配置
```yaml
# nginx upstream 配置
upstream backend {
    least_conn;
    server backend1:8080 max_fails=3 fail_timeout=30s;
    server backend2:8080 max_fails=3 fail_timeout=30s;
    server backend3:8080 max_fails=3 fail_timeout=30s;
    keepalive 32;
}
```

#### 多节点部署脚本
```bash
#!/bin/bash
# deploy-cluster.sh

NODES=("node1.example.com" "node2.example.com" "node3.example.com")

for node in "${NODES[@]}"; do
    echo "Deploying to $node..."
    
    # 同步代码和配置
    rsync -avz --exclude='.git' ./ $node:/opt/brilliant-tavern/
    
    # 在远程节点执行部署
    ssh $node "cd /opt/brilliant-tavern && docker-compose up -d"
    
    echo "Deployment to $node completed"
done
```

### 3. 数据库读写分离

```yaml
# 配置主从数据库
services:
  postgres-master:
    image: pgvector/pgvector:pg15
    environment:
      POSTGRES_DB: ${POSTGRES_DB}
      POSTGRES_USER: ${POSTGRES_USER}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
      POSTGRES_REPLICATION_USER: replicator
      POSTGRES_REPLICATION_PASSWORD: repl_password
    volumes:
      - postgres_master_data:/var/lib/postgresql/data
    
  postgres-slave:
    image: pgvector/pgvector:pg15
    environment:
      PGUSER: ${POSTGRES_USER}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
      POSTGRES_MASTER_SERVICE: postgres-master
      POSTGRES_REPLICATION_USER: replicator
      POSTGRES_REPLICATION_PASSWORD: repl_password
    volumes:
      - postgres_slave_data:/var/lib/postgresql/data
```

## 安全加固

### 1. 网络安全

#### 防火墙配置
```bash
# UFW 防火墙配置
sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw allow ssh
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw enable

# iptables 高级规则
# 限制每IP连接数
iptables -A INPUT -p tcp --dport 80 -m connlimit --connlimit-above 20 -j REJECT
iptables -A INPUT -p tcp --dport 443 -m connlimit --connlimit-above 20 -j REJECT

# DDoS防护
iptables -A INPUT -p tcp --dport 80 -m limit --limit 25/minute --limit-burst 100 -j ACCEPT
iptables -A INPUT -p tcp --dport 443 -m limit --limit 25/minute --limit-burst 100 -j ACCEPT
```

#### Fail2Ban 配置
```bash
# 安装 fail2ban
sudo apt-get install fail2ban

# 配置 /etc/fail2ban/jail.local
[DEFAULT]
bantime = 3600
findtime = 600
maxretry = 5

[nginx-http-auth]
enabled = true
filter = nginx-http-auth
logpath = /var/log/nginx/error.log

[nginx-limit-req]
enabled = true
filter = nginx-limit-req
logpath = /var/log/nginx/error.log
maxretry = 10

[sshd]
enabled = true
port = ssh
logpath = %(sshd_log)s
backend = %(sshd_backend)s
```

### 2. 应用安全

#### 敏感信息管理
```bash
# 使用 Docker Secrets
echo "your-secret-password" | docker secret create postgres_password -
echo "your-jwt-secret" | docker secret create jwt_secret -

# 在 docker-compose.yml 中使用
services:
  backend:
    secrets:
      - postgres_password
      - jwt_secret
    environment:
      DATABASE_PASSWORD_FILE: /run/secrets/postgres_password
      JWT_SECRET_FILE: /run/secrets/jwt_secret

secrets:
  postgres_password:
    external: true
  jwt_secret:
    external: true
```

#### 容器安全加固
```dockerfile
# Dockerfile 安全最佳实践
FROM openjdk:17-jre-alpine

# 使用非root用户
RUN addgroup -g 1001 -S appgroup && \
    adduser -u 1001 -S appuser -G appgroup

# 设置安全的文件权限
COPY --chown=appuser:appgroup app.jar /app/app.jar

# 移除不必要的包
RUN apk del --purge curl wget

USER appuser

# 限制容器能力
# docker run --cap-drop=ALL --cap-add=SETGID --cap-add=SETUID
```

### 3. 数据安全

#### 数据库加密
```sql
-- 启用透明数据加密
ALTER SYSTEM SET ssl = on;
ALTER SYSTEM SET ssl_cert_file = '/path/to/server.crt';
ALTER SYSTEM SET ssl_key_file = '/path/to/server.key';

-- 启用行级安全
ALTER TABLE users ENABLE ROW LEVEL SECURITY;
CREATE POLICY user_policy ON users FOR ALL TO authenticated_users 
USING (user_id = current_user_id());
```

#### 备份加密
```bash
# 加密备份文件
gpg --symmetric --cipher-algo AES256 backup_file.sql
aws s3 cp backup_file.sql.gpg s3://encrypted-backup-bucket/
```

## 故障恢复

### 1. 服务恢复流程

#### 快速故障检测
```bash
#!/bin/bash
# health-check.sh

services=("nginx" "backend" "postgres" "redis")
failed_services=()

for service in "${services[@]}"; do
    if ! docker-compose ps $service | grep -q "Up"; then
        failed_services+=($service)
    fi
done

if [ ${#failed_services[@]} -ne 0 ]; then
    echo "ALERT: Failed services: ${failed_services[*]}"
    # 发送告警通知
    curl -X POST -H 'Content-type: application/json' \
        --data '{"text":"BrilliantTavern服务故障: '"${failed_services[*]}"'"}' \
        $SLACK_WEBHOOK_URL
    
    # 自动重启失败的服务
    for service in "${failed_services[@]}"; do
        docker-compose restart $service
    done
fi
```

#### 数据恢复流程
```bash
#!/bin/bash
# restore-database.sh

BACKUP_FILE=$1
if [ -z "$BACKUP_FILE" ]; then
    echo "Usage: $0 <backup_file>"
    exit 1
fi

# 停止应用服务
docker-compose stop backend

# 备份当前数据库
docker-compose exec postgres pg_dump -U postgres brilliant_tavern > current_backup.sql

# 恢复数据库
if [[ $BACKUP_FILE == *.gz ]]; then
    gunzip -c $BACKUP_FILE | docker-compose exec -T postgres psql -U postgres brilliant_tavern
else
    docker-compose exec -T postgres psql -U postgres brilliant_tavern < $BACKUP_FILE
fi

# 重启应用服务
docker-compose start backend

echo "Database restore completed"
```

### 2. 灾难恢复计划

#### RTO/RPO 目标
- **RTO (恢复时间目标)**: 30分钟
- **RPO (恢复点目标)**: 1小时

#### 完整恢复流程
```bash
#!/bin/bash
# disaster-recovery.sh

echo "开始灾难恢复流程..."

# 1. 检查基础设施
echo "1. 检查基础设施状态..."
docker info || exit 1

# 2. 恢复配置文件
echo "2. 恢复配置文件..."
aws s3 cp s3://backup-bucket/config/config_latest.tar.gz ./
tar -xzf config_latest.tar.gz

# 3. 恢复数据库
echo "3. 恢复数据库..."
aws s3 cp s3://backup-bucket/database/db_backup_latest.sql.gz ./
docker-compose up -d postgres redis
sleep 30
gunzip -c db_backup_latest.sql.gz | docker-compose exec -T postgres psql -U postgres brilliant_tavern

# 4. 恢复用户数据
echo "4. 恢复用户数据..."
aws s3 cp s3://backup-bucket/uploads/uploads_latest.tar.gz ./
docker volume create brilliant-tavern_app_uploads
docker run --rm -v brilliant-tavern_app_uploads:/data -v $(pwd):/backup alpine tar -xzf /backup/uploads_latest.tar.gz -C /data

# 5. 启动所有服务
echo "5. 启动应用服务..."
docker-compose up -d

# 6. 验证服务状态
echo "6. 验证服务状态..."
sleep 60
curl -f http://localhost/health || echo "WARNING: Health check failed"

echo "灾难恢复完成"
```

### 3. 监控告警

#### 告警规则配置
```yaml
# monitoring/alert_rules.yml
groups:
  - name: brilliant-tavern-alerts
    rules:
      - alert: ServiceDown
        expr: up == 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "Service {{ $labels.instance }} is down"
          
      - alert: HighCPUUsage
        expr: cpu_usage_percent > 80
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High CPU usage on {{ $labels.instance }}"
          
      - alert: HighMemoryUsage
        expr: memory_usage_percent > 85
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High memory usage on {{ $labels.instance }}"
          
      - alert: DatabaseConnectionHigh
        expr: postgres_connections_active / postgres_connections_max > 0.8
        for: 2m
        labels:
          severity: warning
        annotations:
          summary: "Database connection pool usage is high"
```

---

**注意**: 这是一个全面的生产环境部署指南，请根据实际业务需求和基础设施情况进行调整。在生产环境实施前，建议在测试环境充分验证所有配置和流程。