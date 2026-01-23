# 报价单管理图标选择指南

## 📋 可用图标列表

根据Vue Element Admin自带的图标，以下是最适合"报价单管理"的图标选项：

---

## 🏆 推荐图标（按优先级排序）

### 1️⃣ **clipboard** ⭐⭐⭐⭐⭐ (强烈推荐)
- **含义**: 剪贴板/文档清单
- **特点**: 
  - 清晰表达"单据"的概念
  - 与报价单的业务场景完美匹配
  - 视觉上容易识别
- **适用场景**: 报价单、订单、清单类功能
- **图标样式**: 带夹子的文档板

### 2️⃣ **money** ⭐⭐⭐⭐
- **含义**: 金钱/价格
- **特点**:
  - 直接体现报价的"价格"属性
  - 突出财务相关功能
  - 符号识别度高（￥符号）
- **适用场景**: 报价、价格、财务相关功能
- **图标样式**: 人民币符号

### 3️⃣ **form** ⭐⭐⭐⭐
- **含义**: 表单/文档
- **特点**:
  - 表达"填写单据"的含义
  - 适合带编辑功能的文档
  - 带笔的文档，暗示可编辑
- **适用场景**: 表单类、可编辑文档
- **图标样式**: 文档加笔

### 4️⃣ **edit** ⭐⭐⭐
- **含义**: 编辑
- **特点**:
  - 强调可编辑性
  - 简洁明了
- **适用场景**: 编辑类功能
- **图标样式**: 铅笔

### 5️⃣ **list** (当前使用) ⭐⭐⭐
- **含义**: 列表
- **特点**:
  - 通用列表图标
  - 已被"销售订单"使用
  - 不够特色
- **适用场景**: 列表展示
- **图标样式**: 三横线

---

## 🎨 当前销售模块图标分布

```javascript
销售 (shopping)
├── 销售订单 (list)      ← 已使用
├── 报价单管理 (document) ← 当前
└── 样品记录 (table)      ← 已使用
```

---

## 💡 推荐方案

### 方案A：使用 clipboard (最佳选择)
**理由**:
- ✅ 语义清晰：剪贴板代表单据清单
- ✅ 视觉区分：与list、table有明显差异
- ✅ 业务贴合：报价单就是一份待确认的清单
- ✅ 专业感强：在ERP系统中很常见

```javascript
{
  path: 'quotations',
  component: () => import('@/views/sales/quotations'),
  name: 'SalesQuotations',
  meta: { title: '报价单管理', icon: 'clipboard', roles: ['sales', 'admin'] }
}
```

### 方案B：使用 money
**理由**:
- ✅ 突出报价的核心：价格
- ✅ 直观易懂：￥符号一目了然
- ✅ 与订单区分：强调价格而非订单
- ⚠️ 可能过于强调金钱概念

```javascript
{
  path: 'quotations',
  component: () => import('@/views/sales/quotations'),
  name: 'SalesQuotations',
  meta: { title: '报价单管理', icon: 'money', roles: ['sales', 'admin'] }
}
```

### 方案C：使用 form
**理由**:
- ✅ 强调表单填写
- ✅ 表达可编辑性
- ⚠️ 可能过于通用

```javascript
{
  path: 'quotations',
  component: () => import('@/views/sales/quotations'),
  name: 'SalesQuotations',
  meta: { title: '报价单管理', icon: 'form', roles: ['sales', 'admin'] }
}
```

---

## 📊 图标对比表

| 图标 | 语义清晰度 | 业务贴合度 | 视觉区分度 | 专业度 | 推荐指数 |
|------|----------|----------|----------|--------|---------|
| clipboard | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | 95% |
| money | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐ | 85% |
| form | ⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐⭐ | 75% |
| edit | ⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐ | 60% |
| document | ⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐ | ⭐⭐⭐ | 50% |

---

## 🎯 最终建议

### 推荐使用：**clipboard** 🏆

**修改后效果预览**:
```
销售 (🛒 shopping)
├── 销售订单 (📋 list)
├── 报价单管理 (📋 clipboard)  ← 新图标
└── 样品记录 (📊 table)
```

**优势**:
1. **专业性强** - 剪贴板是ERP/CRM系统中单据的标准图标
2. **语义准确** - 报价单本质就是一份待确认的清单
3. **视觉统一** - 与整体设计风格协调
4. **易于识别** - 用户能快速理解其含义

---

## 🚀 实施步骤

### 快速修改（推荐clipboard）

```powershell
# 自动替换图标
cd E:\vue\ERP
```

只需修改一行代码：
```javascript
// 文件: src/router/index.js (第96行)
// 修改前
meta: { title: '报价单管理', icon: 'document', roles: ['sales', 'admin'] }

// 修改后
meta: { title: '报价单管理', icon: 'clipboard', roles: ['sales', 'admin'] }
```

---

## 🎨 其他可用图标参考

如果您想尝试其他风格，这些图标也可用：

```
404.svg          - 404错误
bug.svg          - 调试/问题
chart.svg        - 图表
component.svg    - 组件
dashboard.svg    - 仪表盘
documentation.svg - 文档
drag.svg         - 拖拽
education.svg    - 教育
email.svg        - 邮件
example.svg      - 示例
excel.svg        - Excel
eye.svg          - 查看
guide.svg        - 指南
icon.svg         - 图标
international.svg - 国际化
language.svg     - 语言
link.svg         - 链接
lock.svg         - 锁定
message.svg      - 消息
nested.svg       - 嵌套
password.svg     - 密码
pdf.svg          - PDF
people.svg       - 人员
qq.svg           - QQ
search.svg       - 搜索
shopping.svg     - 购物
size.svg         - 大小
skill.svg        - 技能
star.svg         - 星标
tab.svg          - 标签
theme.svg        - 主题
tree.svg         - 树形
user.svg         - 用户
wechat.svg       - 微信
zip.svg          - 压缩
```

---

## 📝 修改检查清单

修改图标后，请检查：
- [ ] 保存文件
- [ ] 重启前端服务（npm run dev）
- [ ] 清除浏览器缓存（Ctrl+Shift+R）
- [ ] 刷新页面查看新图标
- [ ] 检查图标在菜单中的显示效果
- [ ] 确认图标大小和颜色正常

---

**建议**: 使用 **clipboard** 图标，最符合报价单的业务场景！ 🎯
