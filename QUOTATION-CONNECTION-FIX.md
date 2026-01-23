# 报价单连接问题修复指南

## 📅 修复日期
2026-01-05

---

## ✅ 已修复的问题

### 问题：报价单没有连上后端服务器

**原因分析**：
路由配置指向了错误的文件。系统中存在两个报价单文件：
- `quote.vue`（旧文件）← 路由原本指向这里
- `quotations.vue`（新文件）← 应该指向这里

**修复方案**：
修改前端路由配置 `src/router/index.js`

---

## 🔧 修复内容

### 修改的文件
`E:\vue\ERP\src\router\index.js`

### 修改前（错误配置）
```javascript
{
  path: 'quote',
  component: () => import('@/views/sales/quote'),
  name: 'SalesQuote',
  meta: { title: '报价单', icon: 'document', roles: ['sales', 'admin'] }
}
```

### 修改后（正确配置）
```javascript
{
  path: 'quotations',
  component: () => import('@/views/sales/quotations'),
  name: 'SalesQuotations',
  meta: { title: '报价单管理', icon: 'document', roles: ['sales', 'admin'] }
}
```

---

## 📊 完整配置验证

### 1. 后端配置 ✅
```properties
# application.properties
server.port=8090
```

### 2. 前端配置 ✅
```env
# .env.development
VUE_APP_BASE_API = http://localhost:8090
```

### 3. API接口 ✅
```javascript
// src/api/quotation.js
export function getQuotationList() {
  return request({
    url: '/quotation/list',  // → http://localhost:8090/quotation/list
    method: 'get'
  })
}
```

### 4. 后端控制器 ✅
```java
// QuotationController.java
@RestController
@RequestMapping("/quotation")
public class QuotationController {
    @GetMapping("/list")
    public ResponseResult getAllQuotations() {
        // ...
    }
}
```

### 5. 前端路由 ✅（已修复）
```javascript
// src/router/index.js
{
  path: 'quotations',  // 访问地址: /sales/quotations
  component: () => import('@/views/sales/quotations'),
  name: 'SalesQuotations',
  meta: { title: '报价单管理', icon: 'document', roles: ['sales', 'admin'] }
}
```

---

## 🚀 重启服务

修改路由后，需要重启前端服务才能生效：

### 方法1：手动重启
```powershell
# 停止前端服务（Ctrl+C）
# 然后重新启动
cd E:\vue\ERP
npm run dev
```

### 方法2：热重载
前端开发服务器通常支持热重载，刷新浏览器即可。

---

## 🧪 测试步骤

### 1. 确认服务运行
```powershell
# 检查后端端口
netstat -ano | findstr :8090

# 检查前端端口
netstat -ano | findstr :9527
```

### 2. 访问报价单页面
1. 打开浏览器：`http://localhost:9527`
2. 使用admin账号登录
3. 点击左侧菜单：**销售** → **报价单管理**
4. 新的URL应该是：`http://localhost:9527/#/sales/quotations`

### 3. 测试功能
- [ ] 列表是否正常加载
- [ ] 点击"新增报价单"按钮
- [ ] 填写表单并提交
- [ ] 查看报价单详情
- [ ] 编辑报价单
- [ ] 删除报价单

---

## 🔍 常见错误及解决

### 错误1：401 Unauthorized
```
错误信息: Request failed with status code 401
```

**原因**：未登录或token过期

**解决**：
1. 重新登录系统
2. 检查浏览器控制台是否有token
3. 确认localStorage中有token

---

### 错误2：403 Forbidden
```
错误信息: Request failed with status code 403
```

**原因**：当前用户没有admin权限

**解决**：
1. 使用admin账号登录
2. 检查用户角色配置

**后端权限配置**：
```java
@PreAuthorize("hasAuthority('admin')")
public class QuotationController {
    // ...
}
```

---

### 错误3：404 Not Found
```
错误信息: Request failed with status code 404
```

**可能原因**：
1. 后端服务未启动
2. API路径错误
3. 控制器映射错误

**排查步骤**：
```powershell
# 1. 检查后端是否运行
netstat -ano | findstr :8090

# 2. 测试API端点
Invoke-WebRequest -Uri "http://localhost:8090/quotation/list" -Method Get

# 3. 查看后端日志
# 检查控制台输出的映射信息
```

---

### 错误4：Network Error
```
错误信息: Network Error
```

**原因**：前端无法连接到后端

**解决**：
```powershell
# 1. 确认后端正在运行
cd E:\java\MES
mvn spring-boot:run

# 2. 确认端口正确
# 检查 .env.development 文件
VUE_APP_BASE_API = http://localhost:8090

# 3. 确认防火墙未阻止
# 测试端口连通性
Test-NetConnection -ComputerName localhost -Port 8090
```

---

## 📋 诊断工具

### 自动诊断脚本
```powershell
cd E:\java\MES
.\diagnose-quotation-connection.ps1
```

这个脚本会自动检查：
- ✅ 后端服务状态
- ✅ 报价单API可访问性
- ✅ 前端配置
- ✅ 路由配置
- ✅ API文件完整性

---

## 📊 API请求流程

```
前端页面 (quotations.vue)
    ↓
API接口 (quotation.js)
    ↓
Axios请求 (request.js)
    ↓ 添加token
http://localhost:8090/quotation/list
    ↓
后端控制器 (QuotationController)
    ↓ 权限验证
服务层 (QuotationService)
    ↓
数据库 (MySQL)
    ↓
返回数据
```

---

## 🎯 完整访问路径

### 前端访问
```
浏览器地址: http://localhost:9527/#/sales/quotations
          ↓
前端路由: /sales/quotations
          ↓
组件: @/views/sales/quotations.vue
```

### API调用
```
前端API: getQuotationList()
          ↓
请求URL: /quotation/list
          ↓
完整URL: http://localhost:8090/quotation/list
          ↓
后端方法: QuotationController.getAllQuotations()
```

---

## ✅ 修复验证清单

- [x] 修改路由配置（quote → quotations）
- [x] 创建诊断脚本
- [x] 创建修复文档
- [ ] 重启前端服务
- [ ] 测试报价单列表
- [ ] 测试报价单CRUD功能

---

## 📞 如遇其他问题

### 查看后端日志
```powershell
# 后端控制台输出会显示：
# - API映射信息
# - 请求日志
# - 错误堆栈
```

### 查看前端日志
```
浏览器按F12 → Console标签
查看：
- 网络请求
- API响应
- JavaScript错误
```

### 使用诊断脚本
```powershell
.\diagnose-quotation-connection.ps1
```

---

## 🎉 预期结果

修复完成后，您应该能够：
1. ✅ 访问报价单管理页面
2. ✅ 看到报价单列表
3. ✅ 新增报价单
4. ✅ 编辑报价单
5. ✅ 删除报价单
6. ✅ 查看报价单详情

---

*修复时间：2026-01-05*  
*状态：已修复 ✅*  
*需要重启：前端服务*
