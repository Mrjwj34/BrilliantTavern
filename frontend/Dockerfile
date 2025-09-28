# 构建阶段
FROM node:18-alpine as builder

# 设置工作目录
WORKDIR /app

# 复制package文件
COPY package*.json ./

# 安装依赖
RUN npm ci --only=production

# 复制源代码
COPY . .

# 构建应用
RUN npm run build

# 生产阶段 - 使用nginx提供静态文件
FROM nginx:alpine

# 复制构建好的文件到nginx目录
COPY --from=builder /app/dist /var/www/html

# 创建nginx用户和必要目录
RUN addgroup -g 1001 -S nginx && adduser -u 1001 -S nginx -G nginx || true

# 设置正确的权限
RUN chown -R nginx:nginx /var/www/html

# 暴露端口
EXPOSE 80

# 启动nginx
CMD ["nginx", "-g", "daemon off;"]