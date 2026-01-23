# 送样数据渲染问题诊断指南

**问题**: 查询到了数据，但是数据不能正确渲染

---

## 🔍 诊断步骤

### 步骤1: 检查浏览器控制台日志

1. **打开浏览器开发者工具**
   - 按 `F12`
   - 切换到 `Console` 标签

2. **刷新送样管理页面**
   - 访问: `http://localhost:8080/#/sales/samples`

3. **查看控制台输出**（按顺序查看）:
   ```
   📤 发送请求参数: {current: 1, size: 10}
   📥 收到响应: {...}
   📊 响应数据: {code: 20000, data: {...}}
   ✅ 解析后的data: {records: [...], total: 2}
   📋 records数组: [...]
   🔢 total数量: 2
   🎯 最终赋值 this.samples: [...]
   🎯 最终赋值 this.pagination.total: 2
   ```

---

## 🎯 关键检查点

### 检查点1: 响应码
```javascript
// 应该看到
📊 响应数据: {code: 20000, msg: "查询成功", data: {...}}
```

**如果不是 20000**:
- ❌ 问题：后端返回了错误
- 🔧 解决：检查后端日志

---

### 检查点2: data.records 结构
```javascript
// 应该看到
📋 records数组: [
  {
    id: 1,
    sampleNo: "SP20260105001",
    customerName: "阿里巴巴集团",
    contactName: "张经理",
    contactPhone: "13800138001",
    trackingNumber: "SF1234567890",
    sendDate: "2026-01-05",
    status: "已发货",
    ...
  }
]
```

**如果 records 是空数组 `[]`**:
- ❌ 问题：数据库没有数据
- 🔧 解决：执行 `fix-sample-complete.sql`

**如果 records 是 `undefined` 或 `null`**:
- ❌ 问题：后端返回数据结构不对
- 🔧 解决：检查后端 Controller

---

### 检查点3: 字段名是否正确
```javascript
// 检查每条记录的字段名
records[0] = {
  sampleNo: "...",        // ✅ 驼峰命名
  customerName: "...",    // ✅ 驼峰命名
  trackingNumber: "...",  // ✅ 驼峰命名
  sendDate: "..."         // ✅ 驼峰命名
}

// 错误示例（不应该出现）:
records[0] = {
  sample_no: "...",       // ❌ 下划线命名
  customer_name: "...",   // ❌ 下划线命名
}
```

**如果是下划线命名**:
- ❌ 问题：MyBatis-Plus 没有正确转换
- 🔧 解决：检查 `application.properties` 配置

---

### 检查点4: 表格是否显示数据
```javascript
// 最终赋值后
🎯 最终赋值 this.samples: [{...}, {...}]  // 应该有数据
🎯 最终赋值 this.pagination.total: 2      // 应该>0
```

**如果有数据但表格空白**:
- ❌ 问题：前端模板字段名不匹配
- 🔧 解决：检查 `<el-table-column prop="xxx">` 中的字段名

---

## 🔧 常见问题及解决方案

### 问题A: `code` 不是 20000

**症状**: 控制台显示
```
❌ 响应码不是20000: {code: 50000, msg: "xxx错误"}
```

**原因**: 后端处理出错

**解决**:
1. 查看后端控制台日志
2. 检查数据库连接
3. 检查数据库表是否存在

---

### 问题B: `records` 是空数组

**症状**: 控制台显示
```
📋 records数组: []
🔢 total数量: 0
```

**原因**: 数据库没有数据

**解决**:
```sql
-- 在Navicat中执行
SELECT COUNT(*) FROM sample_orders;

-- 如果是0，执行修复脚本
source e:\java\MES\fix-sample-complete.sql;
```

---

### 问题C: `data.records` 是 undefined

**症状**: 控制台显示
```
📋 records数组: undefined
```

**原因**: 后端返回的数据结构不对

**解决**:
1. 浏览器直接访问: `http://localhost:8090/api/sales/samples?current=1&size=10`
2. 检查返回的JSON结构
3. 应该是: `{code: 20000, data: {records: [...], total: 0}}`

---

### 问题D: 字段名是下划线格式

**症状**: 控制台显示
```
📋 records数组: [{sample_no: "...", customer_name: "..."}]
```

**原因**: MyBatis-Plus 驼峰转换未生效

**解决**:
检查 `application.properties`:
```properties
# 应该有这一行
mybatis-plus.configuration.map-underscore-to-camel-case=true
```

---

### 问题E: 表格有数据但不显示

**症状**: 控制台显示
```
🎯 最终赋值 this.samples: [{...}, {...}]  // 有2条数据
```
但表格是空白的

**原因**: 模板字段名不匹配

**解决**:
检查表格列定义:
```vue
<!-- 应该是驼峰命名 -->
<el-table-column prop="sampleNo" label="送样编号" />  ✅
<el-table-column prop="customerName" label="客户名称" />  ✅

<!-- 不应该是下划线 -->
<el-table-column prop="sample_no" label="送样编号" />  ❌
```

---

## 📋 完整诊断流程

### 1. 打开控制台
```
F12 → Console 标签
```

### 2. 刷新页面
```
访问: http://localhost:8080/#/sales/samples
```

### 3. 查看日志，回答以下问题

**问题1**: 是否看到 `📤 发送请求参数`？
- ✅ 是 → 继续
- ❌ 否 → 页面没有加载，检查路由

**问题2**: 是否看到 `📥 收到响应`？
- ✅ 是 → 继续
- ❌ 否 → 请求失败，检查后端是否启动

**问题3**: `code` 是否为 20000？
- ✅ 是 → 继续
- ❌ 否 → 查看错误信息，检查后端

**问题4**: `records` 是否是数组且有数据？
- ✅ 是 → 继续
- ❌ 否 → 数据库没有数据，执行修复脚本

**问题5**: `records[0]` 的字段名是驼峰命名吗？
- ✅ 是 (如: `sampleNo`) → 继续
- ❌ 否 (如: `sample_no`) → 检查MyBatis配置

**问题6**: 表格是否显示数据？
- ✅ 是 → 问题解决！
- ❌ 否 → 检查表格列的 `prop` 属性

---

## 🚀 快速检查命令

### 1. 检查后端是否运行
```powershell
netstat -ano | findstr 8090
```

**期望**: 看到端口8090在监听

---

### 2. 测试后端API
在浏览器访问:
```
http://localhost:8090/api/sales/samples?current=1&size=10
```

**期望返回**:
```json
{
  "code": 20000,
  "msg": "查询成功",
  "data": {
    "records": [
      {
        "id": 1,
        "sampleNo": "SP20260105001",
        "customerName": "阿里巴巴集团",
        ...
      }
    ],
    "total": 2,
    "size": 10,
    "current": 1
  }
}
```

---

### 3. 检查数据库数据
```sql
-- 在Navicat中执行
SELECT * FROM sample_orders LIMIT 5;
SELECT * FROM sample_items LIMIT 5;
```

**期望**: 至少有几条测试数据

---

## 📊 诊断结果模板

请按以下格式反馈问题:

```
【控制台日志】
📤 发送请求参数: [复制这里的内容]
📥 收到响应: [复制这里的内容]
📊 响应数据: [复制这里的内容]

【问题描述】
- code值: [填写]
- records是否有数据: [是/否]
- 字段名格式: [驼峰/下划线]
- 表格是否显示: [是/否]

【截图】
[粘贴控制台截图]
[粘贴页面截图]
```

---

## ✅ 预期正常输出示例

```javascript
📤 发送请求参数: {current: 1, size: 10}
📥 收到响应: {data: {…}, status: 200, statusText: "OK", ...}
📊 响应数据: {code: 20000, msg: "查询成功", data: {…}}
✅ 解析后的data: {records: Array(2), total: 2, size: 10, current: 1, ...}
📋 records数组: (2) [{…}, {…}]
  0: {id: 1, sampleNo: "SP20260105001", customerName: "阿里巴巴集团", ...}
  1: {id: 2, sampleNo: "SP20260105002", customerName: "腾讯科技有限公司", ...}
🔢 total数量: 2
🎯 最终赋值 this.samples: (2) [{…}, {…}]
🎯 最终赋值 this.pagination.total: 2
```

如果你看到类似上面的输出，说明数据获取正常！

---

**现在请你**:

1. 打开浏览器按 `F12`
2. 访问送样管理页面
3. 查看控制台输出
4. 把控制台的内容复制给我

这样我就能准确判断问题了！ 🎯
