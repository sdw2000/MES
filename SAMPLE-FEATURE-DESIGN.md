# 送样功能完整设计方案

**创建日期**: 2026-01-05  
**功能模块**: 销售管理 - 送样管理

---

## 📋 功能需求分析

### 核心需求
1. ✅ **基本信息**：客户、送样日期、状态、备注
2. ✅ **联系人信息**：联系人姓名、电话、地址
3. ✅ **样品明细**：产品、型号、规格、数量（**不需要单价、平米数、金额**）
4. ✅ **物流信息**：快递公司、快递单号、发货日期、送达日期
5. ✅ **状态管理**：待发货、已发货、已送达、已拒收、已取消
6. ✅ **快递单号维护**：单独功能可以更新快递信息

### 扩展功能建议
1. 🎯 **样品追踪**：查看样品历史状态变更
2. 🎯 **客户反馈**：记录客户对样品的反馈意见
3. 🎯 **转订单**：如果样品满意，可以快速转为正式订单
4. 🎯 **提醒功能**：超过N天未收到反馈自动提醒
5. 🎯 **统计报表**：送样数量、转化率统计

---

## 🗄️ 数据库设计

### 1. 送样主表 (sample_orders)

```sql
CREATE TABLE `sample_orders` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `sample_no` VARCHAR(50) NOT NULL COMMENT '送样编号',
  `customer_name` VARCHAR(200) NOT NULL COMMENT '客户名称',
  `customer_id` BIGINT(20) DEFAULT NULL COMMENT '客户ID（关联客户表）',
  
  -- 联系人信息
  `contact_name` VARCHAR(100) NOT NULL COMMENT '联系人姓名',
  `contact_phone` VARCHAR(50) NOT NULL COMMENT '联系电话',
  `contact_address` VARCHAR(500) NOT NULL COMMENT '收货地址',
  
  -- 送样信息
  `send_date` DATE NOT NULL COMMENT '送样日期',
  `expected_feedback_date` DATE DEFAULT NULL COMMENT '期望反馈日期',
  
  -- 物流信息
  `express_company` VARCHAR(100) DEFAULT NULL COMMENT '快递公司',
  `tracking_number` VARCHAR(100) DEFAULT NULL COMMENT '快递单号',
  `ship_date` DATE DEFAULT NULL COMMENT '发货日期',
  `delivery_date` DATE DEFAULT NULL COMMENT '送达日期',
  
  -- 状态管理
  `status` VARCHAR(20) NOT NULL DEFAULT '待发货' COMMENT '状态：待发货、已发货、已送达、已拒收、已取消',
  
  -- 反馈信息
  `customer_feedback` TEXT DEFAULT NULL COMMENT '客户反馈',
  `feedback_date` DATE DEFAULT NULL COMMENT '反馈日期',
  `is_satisfied` TINYINT(1) DEFAULT NULL COMMENT '是否满意：0-否，1-是',
  
  -- 转订单
  `converted_to_order` TINYINT(1) DEFAULT 0 COMMENT '是否已转订单：0-否，1-是',
  `order_no` VARCHAR(50) DEFAULT NULL COMMENT '关联订单号',
  
  -- 备注
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `internal_note` VARCHAR(500) DEFAULT NULL COMMENT '内部备注',
  
  -- 系统字段
  `create_by` VARCHAR(50) DEFAULT NULL COMMENT '创建人',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` VARCHAR(50) DEFAULT NULL COMMENT '更新人',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` TINYINT(1) DEFAULT 0 COMMENT '是否删除：0-否，1-是',
  
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sample_no` (`sample_no`),
  KEY `idx_customer_name` (`customer_name`),
  KEY `idx_status` (`status`),
  KEY `idx_send_date` (`send_date`),
  KEY `idx_tracking_number` (`tracking_number`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='送样订单主表';
```

### 2. 送样明细表 (sample_items)

```sql
CREATE TABLE `sample_items` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `sample_no` VARCHAR(50) NOT NULL COMMENT '送样编号',
  
  -- 产品信息
  `material_code` VARCHAR(50) DEFAULT NULL COMMENT '物料代码',
  `material_name` VARCHAR(200) NOT NULL COMMENT '物料名称/产品名称',
  `specification` VARCHAR(200) DEFAULT NULL COMMENT '规格',
  `model` VARCHAR(100) DEFAULT NULL COMMENT '型号',
  
  -- 尺寸信息（可选）
  `length` DECIMAL(10,2) DEFAULT NULL COMMENT '长度(mm)',
  `width` DECIMAL(10,2) DEFAULT NULL COMMENT '宽度(mm)',
  `thickness` DECIMAL(10,3) DEFAULT NULL COMMENT '厚度(mm)',
  
  -- 数量信息（不需要单价、平米数、金额）
  `quantity` INT(11) NOT NULL DEFAULT 1 COMMENT '数量/卷数',
  `unit` VARCHAR(20) DEFAULT '个' COMMENT '单位',
  
  -- 备注
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
  
  -- 系统字段
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  
  PRIMARY KEY (`id`),
  KEY `idx_sample_no` (`sample_no`),
  KEY `idx_material_code` (`material_code`),
  CONSTRAINT `fk_sample_items_sample_no` FOREIGN KEY (`sample_no`) REFERENCES `sample_orders` (`sample_no`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='送样明细表';
```

### 3. 送样状态历史表 (sample_status_history)

```sql
CREATE TABLE `sample_status_history` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `sample_no` VARCHAR(50) NOT NULL COMMENT '送样编号',
  `old_status` VARCHAR(20) DEFAULT NULL COMMENT '原状态',
  `new_status` VARCHAR(20) NOT NULL COMMENT '新状态',
  `change_reason` VARCHAR(500) DEFAULT NULL COMMENT '变更原因',
  `operator` VARCHAR(50) DEFAULT NULL COMMENT '操作人',
  `change_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '变更时间',
  
  PRIMARY KEY (`id`),
  KEY `idx_sample_no` (`sample_no`),
  KEY `idx_change_time` (`change_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='送样状态历史表';
```

---

## 🎨 前端界面设计

### 主列表页面字段
```
| 送样编号 | 客户名称 | 联系人 | 联系电话 | 快递单号 | 状态 | 送样日期 | 操作 |
```

### 操作按钮
- **查看详情**：显示完整信息和明细
- **编辑**：修改基本信息和明细
- **维护物流**：专门维护快递信息的对话框
- **添加反馈**：记录客户反馈
- **转订单**：将送样转为正式订单
- **删除**：逻辑删除

### 详情页面布局
```
┌─────────────────────────────────────────┐
│ 基本信息                                │
│ 客户：XXX  送样编号：SP20260105001      │
│ 联系人：XXX  电话：XXX  地址：XXX       │
│ 送样日期：2026-01-05  状态：已发货      │
├─────────────────────────────────────────┤
│ 物流信息                                │
│ 快递公司：顺丰  单号：SF1234567890      │
│ 发货日期：2026-01-05  送达：2026-01-06  │
│ [更新物流信息]                          │
├─────────────────────────────────────────┤
│ 样品明细                                │
│ ┌─────────────────────────────────────┐ │
│ │产品│型号│规格│数量│单位│备注│操作│ │
│ └─────────────────────────────────────┘ │
├─────────────────────────────────────────┤
│ 客户反馈                                │
│ 反馈时间：2026-01-10                    │
│ 满意度：☆☆☆☆☆                         │
│ 反馈内容：XXXXXX                        │
│ [添加反馈] [转为订单]                   │
└─────────────────────────────────────────┘
```

---

## 🔧 后端接口设计

### 1. 送样订单管理接口

```java
@RestController
@RequestMapping("/api/sales/samples")
public class SampleController {
    
    // 查询列表（支持分页、筛选）
    @GetMapping
    public Result<PageResult<SampleOrder>> list(SampleQueryDTO query);
    
    // 查询详情
    @GetMapping("/{sampleNo}")
    public Result<SampleOrderDTO> detail(@PathVariable String sampleNo);
    
    // 创建送样单
    @PostMapping
    public Result<String> create(@RequestBody SampleOrderDTO dto);
    
    // 更新送样单
    @PutMapping
    public Result<String> update(@RequestBody SampleOrderDTO dto);
    
    // 删除送样单（逻辑删除）
    @DeleteMapping("/{sampleNo}")
    public Result<String> delete(@PathVariable String sampleNo);
    
    // 更新物流信息
    @PutMapping("/{sampleNo}/logistics")
    public Result<String> updateLogistics(@PathVariable String sampleNo, 
                                          @RequestBody LogisticsDTO dto);
    
    // 更新状态
    @PutMapping("/{sampleNo}/status")
    public Result<String> updateStatus(@PathVariable String sampleNo, 
                                       @RequestBody StatusUpdateDTO dto);
    
    // 添加客户反馈
    @PostMapping("/{sampleNo}/feedback")
    public Result<String> addFeedback(@PathVariable String sampleNo, 
                                      @RequestBody FeedbackDTO dto);
    
    // 转为订单
    @PostMapping("/{sampleNo}/convert-to-order")
    public Result<String> convertToOrder(@PathVariable String sampleNo);
    
    // 查询状态历史
    @GetMapping("/{sampleNo}/history")
    public Result<List<StatusHistory>> getHistory(@PathVariable String sampleNo);
    
    // 统计报表
    @GetMapping("/statistics")
    public Result<SampleStatistics> getStatistics(@RequestParam String startDate,
                                                   @RequestParam String endDate);
}
```

---

## 📦 实体类设计

### SampleOrder.java
```java
@Data
@TableName("sample_orders")
public class SampleOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String sampleNo;          // 送样编号
    private String customerName;      // 客户名称
    private Long customerId;          // 客户ID
    
    // 联系人信息
    private String contactName;       // 联系人
    private String contactPhone;      // 电话
    private String contactAddress;    // 地址
    
    // 送样信息
    private LocalDate sendDate;       // 送样日期
    private LocalDate expectedFeedbackDate; // 期望反馈日期
    
    // 物流信息
    private String expressCompany;    // 快递公司
    private String trackingNumber;    // 快递单号
    private LocalDate shipDate;       // 发货日期
    private LocalDate deliveryDate;   // 送达日期
    
    // 状态
    private String status;            // 状态
    
    // 反馈
    private String customerFeedback;  // 客户反馈
    private LocalDate feedbackDate;   // 反馈日期
    private Boolean isSatisfied;      // 是否满意
    
    // 转订单
    private Boolean convertedToOrder; // 是否已转订单
    private String orderNo;           // 关联订单号
    
    // 备注
    private String remark;            // 备注
    private String internalNote;      // 内部备注
    
    // 系统字段
    @TableField(fill = FieldFill.INSERT)
    private String createBy;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    
    @TableField(fill = FieldFill.UPDATE)
    private String updateBy;
    
    @TableField(fill = FieldFill.UPDATE)
    private LocalDateTime updateTime;
    
    @TableLogic
    private Boolean isDeleted;
    
    // 明细列表（非数据库字段）
    @TableField(exist = false)
    private List<SampleItem> items;
}
```

### SampleItem.java
```java
@Data
@TableName("sample_items")
public class SampleItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String sampleNo;          // 送样编号
    
    // 产品信息
    private String materialCode;      // 物料代码
    private String materialName;      // 物料名称
    private String specification;     // 规格
    private String model;             // 型号
    
    // 尺寸（可选）
    private BigDecimal length;        // 长度
    private BigDecimal width;         // 宽度
    private BigDecimal thickness;     // 厚度
    
    // 数量（不需要单价、金额）
    private Integer quantity;         // 数量
    private String unit;              // 单位
    
    private String remark;            // 备注
    
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
```

---

## 🚀 实现步骤

### 第一阶段：基础功能（1-2小时）
1. ✅ 创建数据库表
2. ✅ 创建后端实体类
3. ✅ 创建 Mapper 接口
4. ✅ 实现 Service 层
5. ✅ 实现 Controller 层
6. ✅ 完善前端页面（列表、新增、编辑、详情）

### 第二阶段：物流功能（30分钟）
1. ✅ 快递单号维护界面
2. ✅ 状态自动更新逻辑
3. ✅ 快递公司选择（顺丰、圆通、中通等）

### 第三阶段：扩展功能（1小时）
1. ✅ 客户反馈功能
2. ✅ 转订单功能
3. ✅ 状态历史追踪
4. ✅ 提醒功能

### 第四阶段：优化完善（30分钟）
1. ✅ 数据验证和错误处理
2. ✅ 权限控制
3. ✅ 统计报表
4. ✅ 导出功能

---

## 💡 业务规则

### 编号规则
```
SP + 年月日 + 3位流水号
例如：SP20260105001
```

### 状态流转
```
待发货 → 已发货 → 已送达 → [已反馈]
         ↓
       已取消/已拒收
```

### 自动化规则
1. 填写快递单号 → 自动更新状态为"已发货"
2. 超过7天未反馈 → 发送提醒通知
3. 客户反馈满意 → 显示"转订单"按钮

---

## 📊 统计指标

1. **送样统计**
   - 本月送样数量
   - 按状态分布
   - 按客户统计

2. **转化率分析**
   - 送样转订单率
   - 平均反馈时间
   - 客户满意度

3. **物流统计**
   - 平均配送时长
   - 快递公司分布

---

## 🎯 关键优化点

1. **性能优化**
   - 列表查询添加索引
   - 使用分页查询
   - 缓存常用数据

2. **用户体验**
   - 快递单号可以点击跳转查询
   - 自动填充联系人信息（从客户表）
   - 一键复制送样信息

3. **数据安全**
   - 逻辑删除
   - 操作日志
   - 权限控制

---

## ✅ 验收标准

- [ ] 能创建送样单并添加明细
- [ ] 能维护快递单号和物流信息
- [ ] 能查看送样状态历史
- [ ] 能添加客户反馈
- [ ] 能将送样转为正式订单
- [ ] 状态流转正确
- [ ] 编号自动生成
- [ ] 列表支持搜索和筛选
- [ ] 数据验证完整
- [ ] 界面美观易用

---

**准备开始实现了吗？我将按照以下顺序进行：**

1. 创建数据库表结构
2. 创建后端实体类和接口
3. 完善前端页面
4. 添加物流维护功能
5. 实现扩展功能

**需要我立即开始吗？** 🚀
