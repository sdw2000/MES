# 样品管理功能 - 数据获取失败问题修复

## 🔍 问题现象
- ✅ 页面可以进入（说明路由和前端代码正常）
- ❌ 提示"获取数据失败"（说明API调用出错）

## 🎯 根本原因
**数据库表未创建！**

后端服务虽然在运行，但当API尝试查询`sample_orders`表时，由于表不存在，数据库会抛出异常，导致前端显示"获取数据失败"。

## 🔧 解决方案

### **步骤1: 连接数据库并创建表** ⭐ **必须完成**

#### 使用Navicat（推荐）

1. **打开Navicat并连接数据库**
   - 主机: `ssdw8127.mysql.rds.aliyuncs.com`
   - 端口: `3306`
   - 用户: `david`
   - 密码: `dadazhengzheng@feng`
   - 数据库: `erp`

2. **执行SQL脚本**
   - 点击"查询" → "新建查询"
   - 打开文件: `E:\java\MES\create-sample-tables.sql`
   - 点击"运行"按钮

3. **验证表创建成功**
   - 刷新数据库表列表
   - 应该看到4张新表:
     ```
     ✓ sample_orders           (送样主表)
     ✓ sample_items            (送样明细表)
     ✓ sample_status_history   (状态历史表)
     ✓ sample_logistics_records (物流记录表)
     ```

#### 使用MySQL Workbench

1. 连接到数据库（使用上述连接信息）
2. File → Open SQL Script → 选择 `E:\java\MES\create-sample-tables.sql`
3. 点击⚡闪电图标执行
4. 检查左侧Tables列表确认表已创建

#### 使用命令行（如果MySQL已配置）

```bash
mysql -h ssdw8127.mysql.rds.aliyuncs.com -u david -p"dadazhengzheng@feng" erp < E:\java\MES\create-sample-tables.sql
```

---

### **步骤2: 验证数据库连接**

在数据库客户端中执行以下SQL验证:

```sql
-- 查看表是否存在
SHOW TABLES LIKE 'sample%';

-- 查看主表结构
DESC sample_orders;

-- 测试编号生成函数
SELECT generate_sample_no() AS 新编号;
```

预期结果:
```
SHOW TABLES: 显示4张表
DESC: 显示表结构（53列）
生成编号: SP20260105001
```

---

### **步骤3: 重启后端服务**

创建表后需要重启后端以清除可能的缓存:

```powershell
# 1. 停止后端
Get-Process -Name "java" | Stop-Process -Force

# 2. 等待2秒
Start-Sleep -Seconds 2

# 3. 启动后端
cd E:\java\MES
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd E:\java\MES; mvn spring-boot:run"
```

**等待30-60秒让服务完全启动**

---

### **步骤4: 测试功能**

1. **打开浏览器**
   - 访问: http://localhost:8080
   - 登录系统（admin/admin）

2. **进入样品管理**
   - 点击左侧菜单: **销售管理**
   - 点击子菜单: **送样管理**

3. **验证功能**
   - ✅ 页面显示正常，无"获取数据失败"错误
   - ✅ 显示空数据表格（因为还没有数据）
   - ✅ 可以点击"新建送样"按钮
   - ✅ 对话框正常打开

4. **创建第一条测试数据**
   ```
   客户名称: 测试客户
   联系人: 张三
   联系电话: 13800138000
   收货地址: 广东省深圳市南山区
   送样日期: (选择今天)
   
   明细:
   - 物料名称: 测试样品
   - 数量: 1
   - 单位: 卷
   ```

5. **保存并验证**
   - 点击"确定"
   - 应该弹出"创建成功"提示
   - 刷新列表应该看到新创建的记录
   - 送样编号格式: SP20260105001

---

## 📋 快速检查清单

### 问题排查步骤:

```powershell
# 1. 检查后端是否运行
Get-Process -Name "java"

# 2. 检查端口
netstat -ano | findstr ":8090"

# 3. 测试API（创建表后执行）
cd E:\java\MES
powershell .\test-sample-api.ps1

# 4. 查看前端文件大小
(Get-Item "E:\vue\ERP\src\views\sales\samples.vue").Length
# 应该是: 26147 字节
```

### 成功标志:

✅ **数据库:** 4张sample表存在  
✅ **后端:** Java进程运行在8090端口  
✅ **前端:** samples.vue文件大小26KB  
✅ **API:** test-sample-api.ps1返回成功  
✅ **页面:** 显示数据表格，无错误提示  

---

## ❓ 常见问题

### Q1: 执行SQL脚本时报错 "Table doesn't exist"
**原因:** 数据库名称错误  
**解决:** 确认连接的是`erp`数据库，SQL脚本第7行有`USE erp_system;`，如果数据库名是`erp`，请改为`USE erp;`

### Q2: 页面还是显示"获取数据失败"
**原因:** 后端未重启或表创建失败  
**解决:** 
1. 在数据库中执行`SHOW TABLES LIKE 'sample%';`确认表存在
2. 停止并重启后端服务
3. 检查后端日志有无错误

### Q3: 新建送样时提示"客户ID不能为空"
**原因:** 客户下拉列表未加载  
**解决:** 这是正常的，需要先在系统中创建客户数据，或者修改前端代码允许手动输入客户名称

### Q4: 快递查询功能不工作
**原因:** 当前使用模拟数据（正常）  
**说明:** 这是设计行为，后续需要对接真实的快递100 API

---

## 📞 仍然有问题？

如果完成以上步骤后仍然失败，请检查:

1. **后端日志** - 运行`mvn spring-boot:run`的窗口
2. **浏览器控制台** - F12 → Console标签
3. **网络请求** - F12 → Network标签，查看`/api/sales/samples`请求的响应

常见错误信息:
- `Table 'erp.sample_orders' doesn't exist` → 表未创建
- `Connection refused` → 后端未启动
- `401 Unauthorized` → Token过期，重新登录
- `500 Internal Server Error` → 后端代码错误，查看日志

---

## 🎯 总结

**核心问题:** 数据库表未创建  
**解决办法:** 执行 `create-sample-tables.sql` 脚本  
**验证方法:** 数据库中可以看到4张sample表  
**完成标志:** 页面正常显示，无"获取数据失败"错误

**执行顺序:**
1. 创建数据库表 ← **最重要！**
2. 重启后端服务
3. 刷新浏览器页面
4. 测试功能
