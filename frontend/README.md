# BrilliantTavern 前端

## 安装依赖

```bash
npm install
```

## 开发模式运行

```bash
npm run serve
# 或者
npm run dev
```

应用将在 http://localhost:3000 启动

## 生产构建

```bash
npm run build
```

## 项目结构

```
frontend/
├── public/
│   └── index.html          # HTML模板
├── src/
│   ├── api/                # API接口
│   ├── components/         # 可复用组件
│   ├── router/             # 路由配置
│   ├── styles/             # 全局样式
│   ├── utils/              # 工具函数
│   ├── views/              # 页面组件
│   ├── App.vue             # 根组件
│   └── main.js             # 应用入口
├── package.json
├── vue.config.js           # Vue配置
└── README.md
```

## 功能特性

### 已完成
- ✅ 现代化UI设计（扁平化、白色系）
- ✅ 响应式布局
- ✅ 用户登录/注册页面
- ✅ 表单验证和错误处理
- ✅ 密码强度指示器
- ✅ JWT认证集成
- ✅ 路由守卫
- ✅ API请求封装
- ✅ 本地存储管理
- ✅ 动画效果和交互反馈
- ✅ 移动端适配

### 设计特点
- **扁平化设计**：简洁的界面风格，无多余装饰
- **白色系主色调**：以白色为主，搭配渐变色彩
- **强交互感**：丰富的hover效果、过渡动画
- **现代感**：毛玻璃效果、阴影、圆角设计
- **响应式**：完美适配桌面端和移动端

## 技术栈

- **Vue 3** - 渐进式JavaScript框架
- **Vue Router 4** - 官方路由管理器
- **Axios** - HTTP请求库
- **SCSS** - CSS预处理器

## API集成

前端已配置代理，自动将 `/api` 请求转发到后端 `http://localhost:8080`

### 已集成接口
- `POST /api/auth/login` - 用户登录
- `POST /api/auth/register` - 用户注册
- `POST /api/auth/logout` - 用户登出

## 开发说明

### 添加新页面
1. 在 `src/views/` 创建Vue组件
2. 在 `src/router/index.js` 添加路由配置

### 添加新API
1. 在 `src/api/index.js` 添加API方法
2. 在组件中导入使用

### 样式规范
- 使用SCSS变量统一颜色和尺寸
- 遵循BEM命名规范
- 优先使用flex布局
- 移动端优先的响应式设计

## 注意事项

1. 确保后端服务已启动（端口8080）
2. 开发时前端运行在端口3000
3. 所有表单都有完整的验证逻辑
4. 已处理JWT token的存储和过期
5. 支持自动跳转和路由守卫
