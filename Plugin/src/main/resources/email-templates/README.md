# 📧 LibreLogin 邮件模板系统

## 概述

LibreLogin 支持自定义 HTML 邮件模板，为玩家提供专业美观的邮件体验。所有模板都支持动态内容替换，并且完全可定制。

## 🎨 可用模板

### 1. 密码找回邮件 (`password-reset.html`)
**用途：** 当玩家请求重置密码时发送
**触发条件：** 执行 `/resetpassword` 命令
**可用占位符：**
- `%server%` - 服务器名称
- `%name%` - 玩家昵称
- `%code%` - 验证码
- `%ip%` - 请求IP地址
- `%timestamp%` - 请求时间戳

### 2. 邮箱绑定验证 (`email-verification.html`)
**用途：** 当玩家更改邮箱时发送验证邮件
**触发条件：** 执行 `/setemail` 命令
**可用占位符：**
- `%server%` - 服务器名称
- `%name%` - 玩家昵称
- `%code%` - 验证码
- `%timeout%` - 验证码有效时间（分钟）
- `%timestamp%` - 发送时间戳

### 3. 注册验证邮件 (`email-register-verification.html`)
**用途：** 邮箱注册功能的验证邮件
**触发条件：** 通过邮箱注册对话框注册账户
**可用占位符：**
- `%server%` - 服务器名称
- `%name%` - 玩家昵称
- `%code%` - 验证码
- `%timeout%` - 验证码有效时间（分钟）
- `%timestamp%` - 发送时间戳

## ⚙️ 配置

在 `config.yml` 中添加以下配置：

```yaml
mail:
  # 启用 HTML 模板（推荐）
  use-html-templates: true
  
  # 其他邮件相关配置...
  enabled: true
  host: "smtp.gmail.com"
  port: 587
  # ... 更多配置
```

## 🛠️ 模板定制

### 安全考虑
- ✅ 使用 "访问凭证"、"身份验证" 等词汇
- ✅ 使用 "账户安全验证"、"验证码" 等表述
- ❌ 避免直接使用 "密码" 等敏感词汇
- ❌ 避免可能被邮件过滤器拦截的词汇

### 定制指南

1. **修改样式**
   - 所有 CSS 都内联在 HTML 中，确保兼容性
   - 可以修改颜色、字体、布局等
   - 建议保持响应式设计

2. **添加自定义内容**
   - 可以添加服务器 Logo（Base64 编码）
   - 可以添加自定义文本和链接
   - 可以调整布局结构

3. **占位符使用**
   ```html
   <!-- 正确的占位符格式 -->
   <p>欢迎，%name%！</p>
   <p>您的验证码是：%code%</p>
   ```

### 示例定制

#### 添加服务器 Logo
```html
<div class="header">
    <img src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8/5+hHgAHggJ/PchI7wAAAABJRU5ErkJggg==" 
         alt="Server Logo" style="height: 40px; margin-bottom: 10px;">
    <h1>🔐 账户安全验证</h1>
</div>
```

#### 自定义颜色主题
```css
/* 将主色调从蓝紫色改为绿色 */
.header {
    background: linear-gradient(135deg, #48bb78 0%, #38a169 100%);
}

.verification-code {
    color: #48bb78;
    border: 2px solid #48bb78;
}
```

## 🔄 重新加载模板

修改模板后，使用以下命令重新加载：

```bash
# 重新加载邮件模板
/librelogin reloademails

# 或使用别名
/ll reload-email-templates
```

**权限要求：** `librelogin.admin.reload`

## 🐛 故障排除

### 模板不生效
1. 检查 `config.yml` 中 `mail.use-html-templates` 是否为 `true`
2. 确保 HTML 文件语法正确（可以用浏览器打开测试）
3. 使用 `/ll reloademails` 重新加载模板
4. 查看控制台是否有错误信息

### 邮件显示异常
1. 确保 HTML 标签正确关闭
2. 检查 CSS 语法（建议使用内联样式）
3. 测试不同邮件客户端的兼容性
4. 避免使用外部资源（图片、字体等）

### 占位符未替换
1. 确保占位符格式正确：`%placeholder%`
2. 检查占位符名称是否拼写正确
3. 确认该占位符在对应模板中可用

## 📝 开发提示

### 调试模式
在 `config.yml` 中启用调试模式可以看到更多日志：
```yaml
debug: true
```

### 备份建议
- 修改模板前请备份原文件
- 建议使用版本控制管理模板文件
- 可以创建多个主题版本便于切换

### 性能优化
- 模板内容会被缓存（调试模式下除外）
- 避免过大的 HTML 文件影响发送速度
- 优化图片大小（如使用 Base64 编码）

## 🆘 技术支持

如需帮助，请：
1. 查看控制台错误日志
2. 确认邮件服务器配置正确
3. 测试基础邮件功能是否正常
4. 联系服务器管理员或插件开发者

---

*LibreLogin Email Template System v1.0*
*最后更新：2024年11月*
