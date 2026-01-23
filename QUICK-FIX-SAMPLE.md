# ⚡ 样品管理功能 - 3步快速修复

## 问题
页面显示"获取数据失败" → **数据库表未创建**

---

## 解决方案（3步）

### 📝 步骤1: 打开数据库客户端

**使用Navicat:**
1. 打开Navicat
2. 连接信息:
   - 主机: `ssdw8127.mysql.rds.aliyuncs.com`
   - 用户: `david`
   - 密码: `dadazhengzheng@feng`
   - 数据库: `erp`

**或使用MySQL Workbench:**
- 使用相同的连接信息创建新连接

---

### 💾 步骤2: 执行SQL脚本

1. 在数据库客户端中打开查询窗口
2. 复制文件内容: `E:\java\MES\create-sample-tables.sql`
3. 粘贴到查询窗口
4. 点击"运行"或"执行"按钮 ⚡

**验证成功:**
```sql
SHOW TABLES LIKE 'sample%';
```
应该看到4张表:
- sample_orders
- sample_items  
- sample_status_history
- sample_logistics_records

---

### 🔄 步骤3: 重启后端

在PowerShell中执行:

```powershell
# 停止后端
Get-Process -Name "java" | Stop-Process -Force

# 等待2秒
Start-Sleep -Seconds 2

# 启动后端
cd E:\java\MES
mvn spring-boot:run
```

**等待30-60秒让服务启动**

---

## ✅ 测试

1. 打开浏览器: http://localhost:8080
2. 登录系统
3. 进入: **销售管理** → **送样管理**
4. 应该看到空白表格（不再显示"获取数据失败"）
5. 点击"新建送样"测试创建功能

---

## 🎯 快速检查

```powershell
# 检查后端
Get-Process -Name "java"
netstat -ano | findstr ":8090"

# 检查前端
(Get-Item "E:\vue\ERP\src\views\sales\samples.vue").Length
# 应该显示: 26147
```

---

## 💡 重要提示

- ⭐ **最关键:** 必须执行SQL脚本创建表
- 🔄 创建表后必须重启后端
- ⏰ 后端启动需要30-60秒
- 🌐 使用Chrome或Edge浏览器测试

---

## 📄 详细文档

如需更详细的说明，请查看:
- `SAMPLE-DATA-FAILURE-FIX.md` - 完整的问题诊断和修复指南
- `SAMPLE-FIX-GUIDE.md` - 系统修复指南
- `create-sample-tables.sql` - 数据库建表脚本

---

**祝您成功! 🎉**
