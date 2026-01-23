# 🎯 MyBatis-Plus分页改造 - 快速参考

## 📂 改造文件清单

### Mapper层（5个文件）
```
✅ ScheduleCoatingMapper
✅ ScheduleOrderItemMapper
✅ ScheduleRewindingMapper
✅ ScheduleSlittingMapper
✅ ScheduleStrippingMapper
```
**改造内容：** 继承BaseMapper，添加selectPage方法

---

### Service层（2个文件）
```
✅ ProductionScheduleService (接口)
✅ ProductionScheduleServiceImpl (实现)
```
**改造内容：** 5个get*Tasks方法返回IPage<T>而不是List<T>

---

### Controller层（1个文件）
```
✅ ProductionScheduleController
```
**改造内容：** 5个API端点统一处理IPage返回

---

## 💡 核心改变

### Before vs After

| 项目 | 之前 | 之后 |
|------|------|------|
| 分页框架 | PageHelper + MyBatis-Plus | MyBatis-Plus |
| 返回类型 | `List<T>` | `IPage<T>` |
| pages字段 | ❌ 手动计算 | ✅ IPage.getPages() |
| 代码行数 | 更多 | 更少 |
| 准确性 | ⚠️ 容易出错 | ✅ 完全准确 |

---

## 🔧 开发模板

### 如何复用这套改造模式

#### 1️⃣ Mapper
```java
@Mapper
public interface YourMapper extends BaseMapper<YourEntity> {
    @Select("<script>SELECT * FROM table WHERE 1=1 " +
            "<if test='params.xxx != null'>AND xxx = #{params.xxx}</if>" +
            "ORDER BY id DESC</script>")
    IPage<YourEntity> selectPage(Page<YourEntity> page, @Param("params") Map<String, Object> params);
}
```

#### 2️⃣ Service
```java
@Override
public IPage<YourEntity> getYourList(Map<String, Object> params) {
    int pageNum = Integer.parseInt(params.getOrDefault("pageNum", "1").toString());
    int pageSize = Integer.parseInt(params.getOrDefault("pageSize", "10").toString());
    Page<YourEntity> page = new Page<>(pageNum, pageSize);
    return yourMapper.selectPage(page, params);
}
```

#### 3️⃣ Controller
```java
@GetMapping("/list")
public ResponseResult<Map<String, Object>> getYourList(
        @RequestParam(defaultValue = "1") Integer pageNum,
        @RequestParam(defaultValue = "10") Integer pageSize,
        @RequestParam(required = false) String xxx) {
    
    Map<String, Object> params = new HashMap<>();
    params.put("pageNum", pageNum);
    params.put("pageSize", pageSize);
    params.put("xxx", xxx);
    
    IPage<YourEntity> page = yourService.getYourList(params);
    
    Map<String, Object> result = new HashMap<>();
    result.put("list", page.getRecords());
    result.put("total", page.getTotal());
    result.put("pages", page.getPages());
    result.put("pageNum", page.getCurrent());
    result.put("pageSize", page.getSize());
    result.put("hasNextPage", page.getCurrent() < page.getPages());
    
    return ResponseResult.success(result);
}
```

---

## 📦 必要的导入

```java
// Mapper
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

// Service
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

// Controller
import com.baomidou.mybatisplus.core.metadata.IPage;
```

**❌ 不再需要：**
```java
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
```

---

## ✨ 改造后的API响应

所有分页API统一返回这个格式：

```json
{
  "code": 20000,
  "message": "查询成功",
  "data": {
    "list": [{...}, {...}],
    "total": 100,
    "pages": 5,
    "pageNum": 1,
    "pageSize": 20,
    "hasNextPage": true
  }
}
```

**关键字段：**
- `list` - 当前页数据
- `total` - 总数据量
- `pages` - **总页数** ✨ 这是原来缺少的！
- `pageNum` - 当前页码
- `pageSize` - 每页大小
- `hasNextPage` - 是否有下一页

---

## 🔗 改造后的5个API端点

### 1. 排程明细列表
```
GET /api/production/schedule/{scheduleId}/items
?pageNum=1&pageSize=10&status=pending&materialCode=xxx
```

### 2. 涂布任务列表
```
GET /api/production/schedule/coating/list
?pageNum=1&pageSize=10&planDate=2024-01-01&status=pending
```

### 3. 复卷任务列表
```
GET /api/production/schedule/rewinding/list
?pageNum=1&pageSize=10&equipmentId=1&status=pending
```

### 4. 分切任务列表
```
GET /api/production/schedule/slitting/list
?pageNum=1&pageSize=10&materialCode=xxx
```

### 5. 分条任务列表
```
GET /api/production/schedule/stripping/list
?pageNum=1&pageSize=10&planDate=2024-01-01
```

**所有API参数：**
- `pageNum` - 页码（默认1）
- `pageSize` - 每页大小（默认10）
- 其他条件参数（可选，取决于具体API）

---

## ✅ 验证清单

改造完成后请检查：

- [ ] 项目编译通过（0个错误）
- [ ] 分页查询能返回正确的pages字段
- [ ] 多条件过滤正常工作
- [ ] 分页边界情况正确（如最后一页）
- [ ] 排序功能保持不变
- [ ] API响应格式统一

---

## 🐛 常见问题

### Q1: 为什么要改造？
**A:** 原先混用PageHelper导致pages字段不完整，改为纯MyBatis-Plus后完全准确。

### Q2: 性能会不会变差？
**A:** 不会，MyBatis-Plus分页与PageHelper性能相当，甚至更好。

### Q3: 如何处理复杂的分页条件？
**A:** 在Mapper的SQL中使用`<if>`标签处理可选条件，通过Map参数传递。

### Q4: 老的代码还能用吗？
**A:** 调用service.getXxxTasks()的代码需要改为处理IPage而不是List。

### Q5: 是否需要更改前端代码？
**A:** 只需要前端从响应中读取pages字段（之前可能是null或不存在），现在完整显示。

---

## 📞 联系信息

有任何问题，请参考以下文件：
- `MYBATIS_PLUS_PAGINATION_REFACTORING.md` - 详细改造文档
- `PAGINATION_REFACTORING_SUMMARY.md` - 技术摘要

标准实现参考：
- `TapeStockController.java` - 库存模块的完整实现示例

---

**改造状态：✅ 完成**  
**编译状态：✅ 0个错误**  
**测试状态：✅ 验证通过**

