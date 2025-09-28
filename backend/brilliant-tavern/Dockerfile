# 使用多阶段构建优化镜像大小
FROM openjdk:17-jdk-alpine as builder

# 设置工作目录
WORKDIR /app

# 复制Maven配置文件
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .

# 设置执行权限
RUN chmod +x mvnw

# 下载依赖（利用Docker缓存）
RUN ./mvnw dependency:go-offline -B

# 复制源代码
COPY src ./src

# 构建应用
RUN ./mvnw clean package -DskipTests -B

# 运行阶段
FROM openjdk:17-jre-alpine

# 安装必要的工具
RUN apk add --no-cache curl

# 创建应用用户
RUN addgroup -g 1001 -S appgroup && adduser -u 1001 -S appuser -G appgroup

# 设置工作目录
WORKDIR /app

# 创建必要的目录
RUN mkdir -p /app/uploads /app/data && chown -R appuser:appgroup /app

# 从构建阶段复制JAR文件
COPY --from=builder /app/target/*.jar app.jar

# 设置文件权限
RUN chown appuser:appgroup /app/app.jar

# 切换到应用用户
USER appuser

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/api/actuator/health || exit 1

# 暴露端口
EXPOSE 8080

# 设置JVM参数和启动命令
ENTRYPOINT ["java", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-Dspring.profiles.active=docker", \
  "-Xmx1024m", \
  "-Xms512m", \
  "-XX:+UseG1GC", \
  "-XX:+UseContainerSupport", \
  "-jar", \
  "app.jar"]