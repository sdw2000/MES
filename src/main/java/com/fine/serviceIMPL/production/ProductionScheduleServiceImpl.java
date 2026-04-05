package com.fine.serviceIMPL.production;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fine.Dao.production.*;
import com.fine.Dao.SalesOrderItemMapper;
import com.fine.model.production.*;
import com.fine.modle.Customer;
import com.fine.modle.stock.TapeStock;
import com.fine.service.production.ProductionScheduleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 生产排程Service实现类
 */
@Service
public class ProductionScheduleServiceImpl implements ProductionScheduleService {
    
    private static final Logger log = LoggerFactory.getLogger(ProductionScheduleServiceImpl.class);

    // 复卷默认速度与换卷耗时
    private static final int DEFAULT_REWIND_SPEED = 100; // m/min
    private static final int CHANGEOVER_MINUTES_PER_ROLL = 2;
    
    @Autowired
    private ProductionScheduleMapper scheduleMapper;
    
    @Autowired
    private ScheduleOrderItemMapper orderItemMapper;
    
    @Autowired
    private ScheduleCoatingMapper coatingMapper;
    
    @Autowired
    private ScheduleRewindingMapper rewindingMapper;
    
    @Autowired
    private ScheduleSlittingMapper slittingMapper;
    
    @Autowired
    private ScheduleStrippingMapper strippingMapper;
    
    @Autowired
    private ProductionReportMapper reportMapper;
    
    @Autowired
    private EquipmentMapper equipmentMapper;
    
    // @Autowired
    // private TapeStockMapper stockMapper; // Unused
    
    @Autowired
    private SchedulePrintingMapper printingMapper;
    
    @Autowired
    private SalesOrderItemMapper salesOrderItemMapper;
    
    @Autowired
    private UrgentOrderLogMapper urgentOrderMapper;
    
    @Autowired
    private ScheduleApprovalLogMapper approvalLogMapper;
    
    @Autowired
    private QualityInspectionMapper inspectionMapper;
    
    @Autowired
    private com.fine.Dao.PendingScheduleOrderMapper pendingOrderMapper;
    
    @Autowired
    private com.fine.Dao.MaterialProductionConfigMapper materialConfigMapper;
    
    // ========== 新增：动态排程核心Service ==========
    @Autowired(required = false)
    private com.fine.service.schedule.CustomerPriorityService customerPriorityService;

    @Autowired(required = false)
    private com.fine.Dao.CustomerMapper customerMapper;
    
    @Autowired(required = false)
    private com.fine.service.schedule.MaterialLockService materialLockService;
    
    @Autowired(required = false)
    private com.fine.mapper.schedule.PendingCoatingOrderPoolMapper pendingCoatingPoolMapper;

    @Autowired(required = false)
    private com.fine.mapper.schedule.PendingRewindingOrderPoolMapper pendingRewindingPoolMapper;

    @Autowired(required = false)
    private com.fine.mapper.schedule.PendingSlittingOrderPoolMapper pendingSlittingPoolMapper;
    
    // ========== 排程主表操作 ==========
    
    @Override
    public IPage<ProductionSchedule> getScheduleList(Map<String, Object> params) {
        Integer pageNum = 1;
        Integer pageSize = 10;
        
        if (params.containsKey("pageNum")) {
            pageNum = Integer.parseInt(params.get("pageNum").toString());
        }
        if (params.containsKey("pageSize")) {
            pageSize = Integer.parseInt(params.get("pageSize").toString());
        }
        
        Page<ProductionSchedule> page = new Page<>(pageNum, pageSize);
        return scheduleMapper.selectPageList(page, params);
    }
    
    @Override
    public ProductionSchedule getScheduleById(Long id) {
        ProductionSchedule schedule = scheduleMapper.selectById(id);
        if (schedule != null) {
            // 加载关联数据
            schedule.setOrderItems(orderItemMapper.selectByScheduleId(id));
            
            // 加载涂布任务并同步filmWidth和planSqm字段（用于前端显示）
            List<ScheduleCoating> coatingTasks = coatingMapper.selectByScheduleId(id);
            if (coatingTasks != null) {
                for (ScheduleCoating task : coatingTasks) {
                    // 将数据库字段jumboWidth同步到前端显示字段filmWidth
                    if (task.getJumboWidth() != null) {
                        task.setFilmWidth(task.getJumboWidth());
                    }
                    
                    // 计算未排程面积：查询该料号的总订单面积 - 已排程面积
                    if (task.getMaterialCode() != null && task.getOrderItemId() != null) {
                        com.fine.modle.SalesOrderItem orderItem = salesOrderItemMapper.selectById(task.getOrderItemId());
                        if (orderItem != null) {
                            // 订单总面积
                            BigDecimal totalSqm = orderItem.getSqm();
                            // 已排程面积（查询该订单明细的所有涂布任务的计划面积总和）
                            BigDecimal scheduledSqm = coatingMapper.sumPlanSqmByOrderItemId(task.getOrderItemId());
                            if (scheduledSqm == null) {
                                scheduledSqm = BigDecimal.ZERO;
                            }
                            // 未排程面积 = 总面积 - 已排程面积
                            BigDecimal pendingSqm = totalSqm.subtract(scheduledSqm);
                            task.setPendingSqm(pendingSqm);
                        }
                    }
                }
            }
            schedule.setCoatingTasks(coatingTasks);
            
            schedule.setRewindingTasks(rewindingMapper.selectByScheduleId(id));
            schedule.setSlittingTasks(slittingMapper.selectByScheduleId(id));
            schedule.setStrippingTasks(strippingMapper.selectByScheduleId(id));
        }
        return schedule;
    }
    
    @Override
    public ProductionSchedule getScheduleByNo(String scheduleNo) {
        return scheduleMapper.selectByScheduleNo(scheduleNo);
    }
    
    @Override
    @Transactional
    public ProductionSchedule createSchedule(ProductionSchedule schedule) {
        // 生成排程单号
        String scheduleNo = scheduleMapper.generateScheduleNo();
        schedule.setScheduleNo(scheduleNo);
        schedule.setStatus("draft");
        
        scheduleMapper.insert(schedule);
        
        // 保存订单关联
        if (schedule.getOrderItems() != null && !schedule.getOrderItems().isEmpty()) {
            for (ScheduleOrderItem item : schedule.getOrderItems()) {
                item.setScheduleId(schedule.getId());
                item.setStatus("pending");
            }
            orderItemMapper.batchInsert(schedule.getOrderItems());
        }
        
        return schedule;
    }
    
    @Override
    @Transactional
    public int updateSchedule(ProductionSchedule schedule) {
        return scheduleMapper.update(schedule);
    }
    
    @Override
    @Transactional
    public int deleteSchedule(Long id, String operator) {
        ProductionSchedule schedule = scheduleMapper.selectById(id);
        if (schedule == null) {
            return 0;
        }
        
        // 只能删除草稿状态的排程
        if (!"draft".equals(schedule.getStatus())) {
            throw new RuntimeException("只能删除草稿状态的排程");
        }
        
        // 删除关联数据
        orderItemMapper.deleteByScheduleId(id);
        coatingMapper.deleteByScheduleId(id);
        rewindingMapper.deleteByScheduleId(id);
        slittingMapper.deleteByScheduleId(id);
        strippingMapper.deleteByScheduleId(id);
        
        return scheduleMapper.deleteById(id, operator);
    }
    
    @Override
    @Transactional
    public int confirmSchedule(Long id, String operator) {
        ProductionSchedule schedule = new ProductionSchedule();
        schedule.setId(id);
        schedule.setStatus("confirmed");
        schedule.setConfirmedBy(operator);
        schedule.setConfirmedTime(new Date());
        schedule.setUpdateBy(operator);
        return scheduleMapper.update(schedule);
    }
    
    @Override
    @Transactional
    public int cancelSchedule(Long id, String operator) {
        ProductionSchedule schedule = scheduleMapper.selectById(id);
        if (schedule == null) {
            return 0;
        }
        
        // 不能取消已完成的排程
        if ("completed".equals(schedule.getStatus())) {
            throw new RuntimeException("不能取消已完成的排程");
        }
        
        ProductionSchedule update = new ProductionSchedule();
        update.setId(id);
        update.setStatus("cancelled");
        update.setUpdateBy(operator);
        return scheduleMapper.update(update);
    }
    
    /**
     * 生成涂布任务
     * @return 最后一个任务的结束时间
     */
    private Date generateCoatingTasks(ProductionSchedule schedule, Map<String, List<Map<String, Object>>> groupedItems, String operator) {
        // 获取可用涂布设备
        List<Equipment> coatingEquipments = equipmentMapper.selectAvailableByType("COATING");
        if (coatingEquipments.isEmpty()) {
            return new Date(); // 返回当前时间
        }
        
        int equipmentIndex = 0;
        // 初始开始时间：排程日期的当天8:00开始，或者当前时间（如果排程日期是今天且已过8点）
        Calendar calendar = Calendar.getInstance();
        Date scheduleDate = schedule.getScheduleDate();
        calendar.setTime(scheduleDate);
        
        // 设置为排程日期的8:00
        calendar.set(Calendar.HOUR_OF_DAY, 8);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        Date scheduledStartTime = calendar.getTime();
        
        // 如果排程日期的8:00已经过去，使用当前时间
        Date now = new Date();
        Date currentStartTime;
        if (now.after(scheduledStartTime)) {
            currentStartTime = now;
        } else {
            currentStartTime = scheduledStartTime;
        }
        
        for (Map.Entry<String, List<Map<String, Object>>> entry : groupedItems.entrySet()) {
            String materialCode = entry.getKey();
            List<Map<String, Object>> items = entry.getValue();
            
            if (items.isEmpty()) continue;
            
            // 取第一个的信息作为参考
            Map<String, Object> firstItem = items.get(0);
            
            ScheduleCoating coating = new ScheduleCoating();
            coating.setScheduleId(schedule.getId());
            coating.setTaskNo(coatingMapper.generateTaskNo(schedule.getScheduleDate()));
            
            // 分配设备
            Equipment equipment = coatingEquipments.get(equipmentIndex % coatingEquipments.size());
            coating.setEquipmentId(equipment.getId());
            coating.setEquipmentCode(equipment.getEquipmentCode());
            equipmentIndex++;
            
            coating.setPlanDate(schedule.getScheduleDate());
            coating.setMaterialCode(materialCode);
            coating.setMaterialName((String)firstItem.get("material_name"));
            // 假设颜色相同
            coating.setColorCode((String)firstItem.get("color_code")); 
            
            // 计算总长度、总面积和平均宽度
            int totalLength = 0;
            BigDecimal totalSqm = BigDecimal.ZERO;
            int avgWidth = 0; // 平均宽度（mm）
            int totalQty = 0;
            
            for (Map<String, Object> item : items) {
                int len = item.get("length") != null ? ((Number)item.get("length")).intValue() : 0;
                int qty = ((Number)item.get("pending_qty")).intValue();
                int width = item.get("width") != null ? ((Number)item.get("width")).intValue() : 0;
                
                totalLength += len * qty;
                totalQty += qty;
                avgWidth += width * qty; // 加权平均
                
                // 面积 m² = 长度(m) × 宽度(mm) × 卷数 / 1000
                BigDecimal sqm = new BigDecimal(len).multiply(new BigDecimal(qty))
                    .multiply(new BigDecimal(width)).divide(new BigDecimal(1000), 2, BigDecimal.ROUND_HALF_UP);
                totalSqm = totalSqm.add(sqm);
            }
            
            // 计算加权平均宽度
            if (totalQty > 0) {
                avgWidth = avgWidth / totalQty;
            }
            
            coating.setPlanLength(new BigDecimal(totalLength));
            // coating.setPlanSqm(totalSqm); // Use if exists or calculate appropriately
            
            // 默认工艺
            coating.setCoatingSpeed(new BigDecimal(40)); // 40m/min（线速度）
            // coating.setOvenTemperature(new BigDecimal(120)); // Method undefined
            
            // 时长计算：总面积(㎡) / [速度(m/min) × 宽度(m)]
            // 公式：时间(min) = 面积(㎡) / [速度(m/min) × 宽度(m)]
            int duration;
            if (avgWidth > 0) {
                double widthInMeters = avgWidth / 1000.0; // 宽度转换为米
                duration = (int) Math.ceil(totalSqm.doubleValue() / (40.0 * widthInMeters));
            } else {
                // 如果宽度为0，使用长度计算（兜底逻辑）
                duration = (int) Math.ceil(totalLength / 40.0);
            }
            coating.setPlanDuration(Math.max(duration, 10));
            
            // 设置开始结束时间
            coating.setPlanStartTime(currentStartTime);
            Calendar cal = Calendar.getInstance();
            cal.setTime(currentStartTime);
            cal.add(Calendar.MINUTE, Math.max(duration, 10));
            Date endTime = cal.getTime();
            coating.setPlanEndTime(endTime);
            
            coating.setStatus("pending");
            coating.setCreateBy(operator);
            
            coatingMapper.insert(coating);
            
            // 更新下一个任务的开始时间（留10分钟换料时间）
            cal.add(Calendar.MINUTE, 10);
            currentStartTime = cal.getTime();
        }
        
        return currentStartTime; // 返回最后任务的结束时间（加上准备时间）
    }

    /**
     * 从库存生成复卷任务（带库存匹配）
     */
    private Date generateRewindingTasksFromStock(ProductionSchedule schedule,
                                                  List<Map<String, Object>> items,
                                                  Map<Map<String, Object>, TapeStock> itemStockMap,
                                                  String operator,
                                                  Date startTime) {
        // 获取可用的复卷设备
        List<Equipment> rewindingEquipments = equipmentMapper.selectAvailableByType("REWINDING");
        if (rewindingEquipments.isEmpty()) {
            return startTime;
        }
        
        int equipmentIndex = 0;
        Date currentStartTime = startTime;
        
        for (Map<String, Object> item : items) {
            TapeStock stock = itemStockMap.get(item);
            if (stock == null) continue;
            
            ScheduleRewinding rewinding = new ScheduleRewinding();
            rewinding.setScheduleId(schedule.getId());
            
            // 分配设备（轮询）
            Equipment equipment = rewindingEquipments.get(equipmentIndex % rewindingEquipments.size());
            rewinding.setEquipmentId(equipment.getId());
            rewinding.setEquipmentCode(equipment.getEquipmentCode());
            equipmentIndex++;
            
            rewinding.setPlanDate(schedule.getScheduleDate());
            rewinding.setTaskNo(rewindingMapper.generateTaskNo(rewinding.getPlanDate()));
            rewinding.setSourceBatchNo(stock.getBatchNo());
            rewinding.setSourceStockId(stock.getId());
            
            // 设置规格
            rewinding.setJumboWidth(stock.getWidth());
            rewinding.setJumboLength(stock.getLength() != null ? new BigDecimal(stock.getLength()) : null);
            rewinding.setMaterialCode((String)item.get("material_code"));
            rewinding.setMaterialName((String)item.get("material_name"));
            
            if (item.get("thickness") != null) {
                rewinding.setThickness(new BigDecimal(item.get("thickness").toString()));
            }
            if (item.get("length") != null) {
                rewinding.setSlitLength(((Number)item.get("length")).intValue());
            }
            
            int planRolls = ((Number)item.get("pending_qty")).intValue();
            rewinding.setPlanRolls(planRolls);
            
            // 设置默认工艺参数
            rewinding.setRewindingSpeed(new BigDecimal(50)); // 50米/分钟
            rewinding.setTension(new BigDecimal(20)); // 张力20N
            
            // 计算预计时长（分钟）：卷数 * 每卷长度 / 复卷速度
            int length = item.get("length") != null ? ((Number)item.get("length")).intValue() : 0;
            int duration = (int)Math.ceil(planRolls * length / 50.0); // 长度单位是米，速度50m/min
            rewinding.setPlanDuration(Math.max(duration, 1));
            
            // 设置开始结束时间
            rewinding.setPlanStartTime(currentStartTime);
            Calendar cal = Calendar.getInstance();
            cal.setTime(currentStartTime);
            cal.add(Calendar.MINUTE, Math.max(duration, 1));
            Date endTime = cal.getTime();
            rewinding.setPlanEndTime(endTime);
            
            rewinding.setStatus("pending");
            rewinding.setCreateBy(operator);
            
            rewindingMapper.insert(rewinding);
            
            // 更新下一个任务的开始时间（留5分钟准备时间）
            cal.add(Calendar.MINUTE, 5);
            currentStartTime = cal.getTime();
        }
        
        return currentStartTime; // 返回最后结束时间
    }

    /**
     * 生成复卷任务（简化版，不依赖库存匹配）
     */
    private Date generateRewindingTasks(ProductionSchedule schedule,
                                        List<Map<String, Object>> items,
                                        String operator,
                                        Date startTime) {
        // 获取可用的复卷设备
        List<Equipment> rewindingEquipments = equipmentMapper.selectAvailableByType("REWINDING");
        if (rewindingEquipments.isEmpty()) {
            return startTime;
        }
        
        int equipmentIndex = 0;
        Date currentStartTime = startTime;
        
        for (Map<String, Object> item : items) {
            ScheduleRewinding rewinding = new ScheduleRewinding();
            rewinding.setScheduleId(schedule.getId());
            
            // 分配设备（轮询）
            Equipment equipment = rewindingEquipments.get(equipmentIndex % rewindingEquipments.size());
            rewinding.setEquipmentId(equipment.getId());
            rewinding.setEquipmentCode(equipment.getEquipmentCode());
            equipmentIndex++;
            
            rewinding.setPlanDate(schedule.getScheduleDate());
            rewinding.setTaskNo(rewindingMapper.generateTaskNo(rewinding.getPlanDate()));
            
            // 设置规格
            rewinding.setMaterialCode((String)item.get("material_code"));
            rewinding.setMaterialName((String)item.get("material_name"));
            
            if (item.get("thickness") != null) {
                rewinding.setThickness(new BigDecimal(item.get("thickness").toString()));
            }
            if (item.get("length") != null) {
                rewinding.setSlitLength(((Number)item.get("length")).intValue());
            }
            if (item.get("width") != null) {
                rewinding.setJumboWidth(((Number)item.get("width")).intValue());
            }
            
            int planRolls = ((Number)item.get("pending_qty")).intValue();
            rewinding.setPlanRolls(planRolls);
            
            // 设置默认工艺参数
            rewinding.setRewindingSpeed(new BigDecimal(50)); // 50米/分钟
            rewinding.setTension(new BigDecimal(20)); // 张力20N
            
            // 计算预计时长（分钟）：卷数 * 每卷长度 / 复卷速度
            int length = item.get("length") != null ? ((Number)item.get("length")).intValue() : 0;
            int duration = (int)Math.ceil(planRolls * length / 50.0); // 长度单位是米，速度50m/min
            rewinding.setPlanDuration(Math.max(duration, 1));
            
            // 设置开始结束时间
            rewinding.setPlanStartTime(currentStartTime);
            Calendar cal = Calendar.getInstance();
            cal.setTime(currentStartTime);
            cal.add(Calendar.MINUTE, Math.max(duration, 1));
            Date endTime = cal.getTime();
            rewinding.setPlanEndTime(endTime);
            
            rewinding.setStatus("pending");
            rewinding.setCreateBy(operator);
            
            rewindingMapper.insert(rewinding);
            
            // 更新下一个任务的开始时间（留5分钟准备时间）
            cal.add(Calendar.MINUTE, 5);
            currentStartTime = cal.getTime();
        }
        
        return currentStartTime; // 返回最后结束时间
    }

    /**
     * 生成分切任务
     */
    private Date generateSlittingTasks(ProductionSchedule schedule,
                                       List<Map<String, Object>> items,
                                       String operator,
                                       Date startTime) {
        // 获取可用的分切设备
        List<Equipment> slittingEquipments = equipmentMapper.selectAvailableByType("SLITTING");
        if (slittingEquipments.isEmpty()) {
            return startTime;
        }
        
        int equipmentIndex = 0;
        Date currentStartTime = startTime;
        
        for (Map<String, Object> item : items) {
            ScheduleSlitting slitting = new ScheduleSlitting();
            slitting.setScheduleId(schedule.getId());
            slitting.setTaskNo(slittingMapper.generateTaskNo(schedule.getScheduleDate()));
            
            // ========== 订单关联 ==========
            slitting.setOrderId((Long)item.get("order_id"));
            slitting.setOrderNo((String)item.get("order_no"));
            slitting.setOrderItemId((Long)item.get("order_item_id"));
            slitting.setOrderDetailNo((String)item.get("order_detail_no"));
            
            // 分配设备（轮询）
            Equipment equipment = slittingEquipments.get(equipmentIndex % slittingEquipments.size());
            slitting.setEquipmentId(equipment.getId());
            slitting.setEquipmentCode(equipment.getEquipmentCode());
            equipmentIndex++;
            
            slitting.setPlanDate(schedule.getScheduleDate());
            
            // 设置规格
            slitting.setMaterialCode((String)item.get("material_code"));
            slitting.setMaterialName((String)item.get("material_name"));
            
            // 优先从订单明细表回填规格字段（保证来源为订单明细）
            Integer planRolls = null;
            if (item.get("order_item_id") != null) {
                try {
                    Long orderItemId = ((Number)item.get("order_item_id")).longValue();
                    com.fine.modle.SalesOrderItem soi = salesOrderItemMapper.selectById(orderItemId);
                    if (soi != null) {
                        if (soi.getThickness() != null) slitting.setThickness(soi.getThickness());
                        if (soi.getWidth() != null) slitting.setTargetWidth(soi.getWidth().intValue());
                        if (soi.getLength() != null) {
                            // sales_order_items.length 存储为 mm，转换为米
                            slitting.setSlitLength(soi.getLength().divide(new java.math.BigDecimal(1000)).intValue());
                        }
                        if (soi.getRolls() != null) planRolls = soi.getRolls();
                    }
                } catch (Exception ex) {
                    // 回退到 map 中的字段（若 conversion/查询出错）
                }
            }

            // 回退：如果没有从订单明细获取到值，则使用传入的 item map
            if (slitting.getThickness() == null && item.get("thickness") != null) {
                slitting.setThickness(new BigDecimal(item.get("thickness").toString()));
            }
            if (slitting.getTargetWidth() == null && item.get("width") != null) {
                slitting.setTargetWidth(((Number)item.get("width")).intValue());
            }
            if (slitting.getSlitLength() == null && item.get("length") != null) {
                slitting.setSlitLength(((Number)item.get("length")).intValue());
            }

            if (planRolls == null) {
                planRolls = item.get("pending_qty") != null ? ((Number)item.get("pending_qty")).intValue() : 0;
            }
            slitting.setPlanRolls(planRolls);
            
            // 设置规格综合显示字段
            slitting.setSpec(String.format("%s-%dmm x %dm x %d卷", 
                slitting.getMaterialCode(),
                slitting.getTargetWidth() != null ? slitting.getTargetWidth() : 0,
                slitting.getSlitLength() != null ? slitting.getSlitLength() : 0,
                planRolls));
            
            // 设置默认工艺参数
            slitting.setSlittingSpeed(new BigDecimal(60)); // 60米/分钟
            
            // 计算预计时长（分钟）：卷数 * 每卷长度 / 分切速度
            int length = item.get("length") != null ? ((Number)item.get("length")).intValue() : 0;
            int duration = (int)Math.ceil(planRolls * length / 60.0); // 长度单位是米，速度60m/min
            slitting.setPlanDuration(Math.max(duration, 1));
            
            // 设置开始结束时间（精确到10分钟）
            slitting.setPlanStartTime(roundTimeToTenMinutes(currentStartTime));
            Calendar cal = Calendar.getInstance();
            cal.setTime(slitting.getPlanStartTime());
            cal.add(Calendar.MINUTE, Math.max(duration, 1));
            Date endTime = cal.getTime();
            slitting.setPlanEndTime(roundTimeToTenMinutes(endTime));
            
            slitting.setStatus("pending");
            slitting.setCreateBy(operator);
            slitting.setOperatorName(operator); // 创建人作为初始操作人
            
            slittingMapper.insert(slitting);
            
            // 更新下一个任务的开始时间（留5分钟准备时间）
            cal.add(Calendar.MINUTE, 5);
            currentStartTime = cal.getTime();
        }
        
        return currentStartTime; // 返回最后结束时间
    }
    
    /**
     * 将时间四舍五入到最近的10分钟
     */
    private Date roundTimeToTenMinutes(Date date) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        int minute = cal.get(Calendar.MINUTE);
        int roundedMinute = (minute + 5) / 10 * 10;
        if (roundedMinute == 60) {
            cal.add(Calendar.HOUR, 1);
            roundedMinute = 0;
        }
        cal.set(Calendar.MINUTE, roundedMinute);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }

    private java.time.LocalDateTime toLocalDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof java.time.LocalDateTime) {
            return (java.time.LocalDateTime) value;
        }
        if (value instanceof Date) {
            return java.time.LocalDateTime.ofInstant(((Date) value).toInstant(), java.time.ZoneId.systemDefault());
        }
        if (value instanceof java.sql.Timestamp) {
            return ((java.sql.Timestamp) value).toLocalDateTime();
        }
        try {
            return java.time.LocalDateTime.parse(value.toString().replace(" ", "T"));
        } catch (Exception ex) {
            return null;
        }
    }

    private java.math.BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return java.math.BigDecimal.ZERO;
        }
        if (value instanceof java.math.BigDecimal) {
            return (java.math.BigDecimal) value;
        }
        try {
            return new java.math.BigDecimal(value.toString());
        } catch (Exception ex) {
            return java.math.BigDecimal.ZERO;
        }
    }

    @Override
    public IPage<Map<String, Object>> getPendingOrderItems(Map<String, Object> params) {
        Integer pageNum = 1;
        Integer pageSize = 10;
        
        if (params.containsKey("pageNum")) {
            pageNum = Integer.parseInt(params.get("pageNum").toString());
        }
        if (params.containsKey("pageSize")) {
            pageSize = Integer.parseInt(params.get("pageSize").toString());
        }
        
        List<Map<String, Object>> list = orderItemMapper.selectPendingOrderItems(params);

        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.LocalDateTime deadline = now.plusHours(48);

        for (Map<String, Object> row : list) {
            if (row == null) {
                continue;
            }
            if (customerPriorityService == null) {
                row.put("priorityScore", java.math.BigDecimal.ZERO);
                continue;
            }

            Long customerId = null;
            Object customerIdObj = row.get("customer_id");
            if (customerIdObj instanceof Number) {
                customerId = ((Number) customerIdObj).longValue();
            }
            if (customerId == null && row.get("customer_code") != null && customerMapper != null) {
                Customer customer = customerMapper.selectByCustomerCode(row.get("customer_code").toString());
                if (customer != null) {
                    customerId = customer.getId();
                    row.put("customer_id", customerId);
                }
            }

            Map<String, Object> detail = null;
            try {
                if (customerId != null) {
                    detail = customerPriorityService.getCustomerPriorityDetail(customerId);
                }
            } catch (Exception ignore) {
                detail = null;
            }

            if (detail != null && detail.get("totalScore") != null) {
                try {
                    row.put("priorityScore", new java.math.BigDecimal(detail.get("totalScore").toString()));
                } catch (Exception ex) {
                    row.put("priorityScore", java.math.BigDecimal.ZERO);
                }
            } else {
                row.put("priorityScore", java.math.BigDecimal.ZERO);
            }
        }

        list.sort((a, b) -> {
            java.time.LocalDateTime da = toLocalDateTime(a != null ? a.get("delivery_date") : null);
            java.time.LocalDateTime db = toLocalDateTime(b != null ? b.get("delivery_date") : null);

            boolean aWithin = da != null && !da.isAfter(deadline);
            boolean bWithin = db != null && !db.isAfter(deadline);

            if (aWithin != bWithin) {
                return aWithin ? -1 : 1; // 48h内优先
            }
            if (!aWithin && !bWithin) {
                java.math.BigDecimal pa = toBigDecimal(a != null ? a.get("priorityScore") : null);
                java.math.BigDecimal pb = toBigDecimal(b != null ? b.get("priorityScore") : null);
                int cmp = pb.compareTo(pa);
                if (cmp != 0) return cmp;
            }
            if (da == null && db == null) return 0;
            if (da == null) return 1;
            if (db == null) return -1;
            return da.compareTo(db);
        });

        Page<Map<String, Object>> page = new Page<>(pageNum, pageSize);
        int total = list.size();
        int start = (pageNum - 1) * pageSize;
        int end = Math.min(start + pageSize, total);
        page.setRecords(start < total ? list.subList(start, end) : new ArrayList<>());
        page.setTotal(total);
        return page;
    }
    
    @Override
    public Map<String, Object> getScheduleStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        // 统计各状态数量
        List<Map<String, Object>> statusCounts = scheduleMapper.countByStatus();
        Map<String, Long> statusMap = new HashMap<>();
        for (Map<String, Object> item : statusCounts) {
            statusMap.put((String)item.get("status"), ((Number)item.get("count")).longValue());
        }
        stats.put("statusCounts", statusMap);
        
        // 今日排程数
        stats.put("todaySchedules", scheduleMapper.countTodaySchedules());
        
        // 今日产量
        stats.put("todayOutput", 0); // Temporary fix due to missing method in mapper
        
        return stats;
    }
    
    // ========== 生产报工操作 ==========
    
    @Override
    public IPage<ProductionReport> getReportList(Map<String, Object> params) {
        int pageNum = 1;
        int pageSize = 10;
        
        if (params.containsKey("pageNum")) {
            pageNum = Integer.parseInt(params.get("pageNum").toString());
        }
        if (params.containsKey("pageSize")) {
            pageSize = Integer.parseInt(params.get("pageSize").toString());
        }
        
        Page<ProductionReport> page = new Page<>(pageNum, pageSize);
        return reportMapper.selectList(page, params);
    }
    
    @Override
    @Transactional
    public ProductionReport submitReport(ProductionReport report) {
        report.setReportNo(reportMapper.generateReportNo());
        reportMapper.insert(report);
        
        // 更新对应任务的实际产出
        updateTaskActualOutput(report);
        
        return report;
    }
    
    /**
     * 更新任务实际产出
     */
    private void updateTaskActualOutput(ProductionReport report) {
        String taskType = report.getTaskType();
        Long taskId = report.getTaskId();
        
        switch (taskType) {
            case "COATING":
                ScheduleCoating coating = new ScheduleCoating();
                coating.setId(taskId);
                coating.setActualLength(report.getOutputLength());
                coating.setActualSqm(report.getOutputSqm());
                coatingMapper.update(coating);
                break;
            case "REWINDING":
                ScheduleRewinding rewinding = new ScheduleRewinding();
                rewinding.setId(taskId);
                rewinding.setActualRolls(report.getOutputQty());
                rewindingMapper.update(rewinding);
                break;
            case "SLITTING":
                ScheduleSlitting slitting = new ScheduleSlitting();
                slitting.setId(taskId);
                slitting.setActualRolls(report.getOutputQty());
                slittingMapper.update(slitting);
                break;
            case "STRIPPING":
                ScheduleStripping stripping = new ScheduleStripping();
                stripping.setId(taskId);
                stripping.setActualRolls(report.getOutputQty());
                strippingMapper.update(stripping);
                break;
        }
    }
    
    @Override
    public List<Map<String, Object>> getTodayOutput() {
        // Fix: Method countTodayOutput is undefined in ProductionReportMapper
        // Since we don't have aggregation logic in mapper, let's return empty or implement a simple one
        // For now, return empty to fix compilation
        return new ArrayList<>();
    }

    @Override
    public Map<String, Object> getShiftProductionAreaSummary(String shiftCode) {
        String normalizedShiftCode = shiftCode == null ? "" : shiftCode.trim().toUpperCase();
        Map<String, Object> raw = reportMapper.selectShiftProductionAreaSummary(normalizedShiftCode);
        if (raw == null) {
            raw = new HashMap<>();
        }

        BigDecimal monthArea = toBigDecimal(raw.get("monthArea"));
        BigDecimal yearArea = toBigDecimal(raw.get("yearArea"));

        Map<String, Object> result = new HashMap<>();
        result.put("shiftCode", normalizedShiftCode);
        result.put("monthArea", monthArea.setScale(2, java.math.RoundingMode.HALF_UP));
        result.put("yearArea", yearArea.setScale(2, java.math.RoundingMode.HALF_UP));
        return result;
    }

    // ========== 排程明细 ==========
    @Override
    public IPage<ScheduleOrderItem> getScheduleOrderItems(Map<String, Object> params) {
        Page<ScheduleOrderItem> page = new Page<>(
            getParamInt(params, "pageNum", 1),
            getParamInt(params, "pageSize", 10)
        );
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ScheduleOrderItem> query = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        if (params.get("scheduleId") != null) {
            query.eq("schedule_id", params.get("scheduleId"));
        }
        return orderItemMapper.selectPage(page, query);
    }
    
    // ========== 印刷计划 ==========
    
    @Override
    public IPage<SchedulePrinting> getPrintingTasks(Map<String, Object> params) {
        Page<SchedulePrinting> page = new Page<>(
            getParamInt(params, "pageNum", 1),
            getParamInt(params, "pageSize", 10)
        );
        return printingMapper.selectPrintingTasks(page, params);
    }
    
    @Override
    public SchedulePrinting addPrintingTask(SchedulePrinting printing) {
        printing.setTaskNo(printingMapper.generateTaskNo());
        printing.setStatus("pending");
        printingMapper.insert(printing);
        return printing;
    }
    
    @Override
    public int updatePrintingTask(SchedulePrinting printing) {
        return printingMapper.updateById(printing);
    }
    
    @Override
    public int startPrintingTask(Long taskId, String operator) {
        SchedulePrinting task = printingMapper.selectById(taskId);
        if (task == null) return 0;
        
        task.setStatus("processing");
        task.setActualStartTime(new Date());
        return printingMapper.updateById(task);
    }
    
    @Override
    public int completePrintingTask(Long taskId, String outputBatchNo, String operator) {
        SchedulePrinting task = printingMapper.selectById(taskId);
        if (task == null) return 0;
        
        task.setStatus("completed");
        task.setActualEndTime(new Date());
        return printingMapper.updateById(task);
    }
    
    // ========== 涂布计划 ==========
    
    @Override
    public IPage<ScheduleCoating> getCoatingTasks(Map<String, Object> params) {
        Page<ScheduleCoating> page = new Page<>(
            getParamInt(params, "pageNum", 1),
            getParamInt(params, "pageSize", 10)
        );
         com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ScheduleCoating> query = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        if (params.get("scheduleId") != null) {
            query.eq("schedule_id", params.get("scheduleId"));
        }
        return coatingMapper.selectPage(page, query);
    }
    
    @Override
    public ScheduleCoating addCoatingTask(ScheduleCoating coating) {
        coating.setTaskNo(coatingMapper.generateTaskNo(coating.getPlanDate()));
        coating.setStatus("pending");
        coatingMapper.insert(coating);
        return coating;
    }
    
    @Override
    public int updateCoatingTask(ScheduleCoating coating) {
        return coatingMapper.updateById(coating);
    }
    
    @Override
    public int startCoatingTask(Long taskId, String operator) {
        ScheduleCoating task = coatingMapper.selectById(taskId);
        if (task == null) return 0;
        task.setStatus("processing");
        task.setActualStartTime(new Date());
        return coatingMapper.updateById(task);
    }
    
    @Override
    public int completeCoatingTask(Long taskId, String outputBatchNo, String operator) {
        ScheduleCoating task = coatingMapper.selectById(taskId);
        if (task == null) return 0;
        task.setStatus("completed");
        task.setActualEndTime(new Date());
        return coatingMapper.updateById(task);
    }
    
    // ========== 复卷计划 ==========
    
    @Override
    public IPage<ScheduleRewinding> getRewindingTasks(Map<String, Object> params) {
        Page<ScheduleRewinding> page = new Page<>(
            getParamInt(params, "pageNum", 1),
            getParamInt(params, "pageSize", 10)
        );

        // 使用自定义分页SQL，支持设备名联表与多条件筛选
        Map<String, Object> filters = new HashMap<>();
        if (params.get("scheduleId") != null) {
            filters.put("scheduleId", params.get("scheduleId"));
        }
        String planDateStr = null;
        if (params.get("planDate") != null) {
            planDateStr = params.get("planDate").toString();
            if (!planDateStr.isEmpty()) {
                filters.put("planDate", planDateStr);
            }
        }
        if (params.get("status") != null && !params.get("status").toString().isEmpty()) {
            filters.put("status", params.get("status"));
        }
        if (params.get("equipmentId") != null) {
            filters.put("equipmentId", params.get("equipmentId"));
        }
        if (params.get("materialCode") != null && !params.get("materialCode").toString().isEmpty()) {
            filters.put("materialCode", params.get("materialCode"));
        }

        IPage<ScheduleRewinding> result = rewindingMapper.selectPage(page, filters);
        if (result != null && result.getRecords() != null) {
            for (ScheduleRewinding r : result.getRecords()) {
                hydrateRewindingDefaults(r);
            }
            if (shouldRecalcRewinding(result.getRecords())) {
                recalcRewindingFromRecords(result.getRecords(), planDateStr);
                result = rewindingMapper.selectPage(page, filters);
                if (result != null && result.getRecords() != null) {
                    for (ScheduleRewinding r : result.getRecords()) {
                        hydrateRewindingDefaults(r);
                    }
                }
            }
        }
        return result;
    }
    
    @Override
    public ScheduleRewinding addRewindingTask(ScheduleRewinding rewinding) {
        // schedule_id 为非空字段，兼容前端未传排程ID的场景
        if (rewinding.getScheduleId() == null) {
            rewinding.setScheduleId(0L);
        }

        // 如未指定计划日期，默认今天，避免看板按日期筛选时被漏掉
        if (rewinding.getPlanDate() == null) {
            rewinding.setPlanDate(new Date());
        }

        // 映射前端字段：长度 -> slitLength，数量 -> planRolls，宽度默认 500mm
        if (rewinding.getSlitLength() == null && rewinding.getLength() != null) {
            rewinding.setSlitLength(rewinding.getLength());
        }
        if (rewinding.getPlanRolls() == null) {
            if (rewinding.getRequiredRolls() != null && rewinding.getRequiredRolls() > 0) {
                rewinding.setPlanRolls(rewinding.getRequiredRolls());
            } else if (rewinding.getQuantity() != null) {
                rewinding.setPlanRolls(rewinding.getQuantity());
            }
        }
        if (rewinding.getJumboWidth() == null && rewinding.getWidth() != null) {
            rewinding.setJumboWidth(rewinding.getWidth());
        }
        if (rewinding.getJumboWidth() == null) {
            rewinding.setJumboWidth(500);
        }

        // 估算时长，后续统一按机台时间线重新排（先占位，插入后再全链重算）
        int rolls = rewinding.getPlanRolls() != null ? rewinding.getPlanRolls() : 0;
        int len = rewinding.getSlitLength() != null ? rewinding.getSlitLength() : 0;
        int speed = rewinding.getRewindingSpeed() != null ? rewinding.getRewindingSpeed().intValue() : DEFAULT_REWIND_SPEED;
        int duration = computeRewindingDurationMinutes(rolls, len, speed);

        Date startAt = planDateAtEight(new SimpleDateFormat("yyyy-MM-dd").format(rewinding.getPlanDate()));
        Date endAt = new Date(startAt.getTime() + duration * 60L * 1000L);
        rewinding.setPlanStartTime(startAt);
        rewinding.setPlanEndTime(endAt);
        rewinding.setPlanDuration(duration);
        try {
            rewinding.setPlanDate(new SimpleDateFormat("yyyy-MM-dd").parse(new SimpleDateFormat("yyyy-MM-dd").format(startAt)));
        } catch (Exception ignore) { }

        // 记录关联订单号到备注（表无专用字段，仅便于查看）
        if (rewinding.getOrderNos() != null && !rewinding.getOrderNos().isEmpty()) {
            rewinding.setRemark("订单:" + String.join(",", rewinding.getOrderNos()));
            rewinding.setOrderNosText(String.join(",", rewinding.getOrderNos()));
        }

        rewinding.setTaskNo(rewindingMapper.generateTaskNo(rewinding.getPlanDate()));
        rewinding.setStatus("pending");
        rewindingMapper.insert(rewinding);

        // 插入后从该任务起顺推本机台时间线，保证串联
        try {
            if (rewinding.getEquipmentId() != null && rewinding.getId() != null) {
                recalcRewindingPlanFromTask(rewinding.getEquipmentId(), rewinding.getId(), 10);
            }
        } catch (Exception ignore) { }

        // 如果带上了待涂布池ID（单个或列表），提交成功后将其移除，避免列表继续显示
        if (rewinding.getPendingPoolId() != null) {
            removePendingPoolRecord(rewinding.getPendingPoolId());
        }
        if (rewinding.getPendingPoolIds() != null && !rewinding.getPendingPoolIds().isEmpty()) {
            for (Long pid : rewinding.getPendingPoolIds()) {
                removePendingPoolRecord(pid);
            }
        }
        return rewinding;
    }

    private void removePendingPoolRecord(Long poolId) {
        if (poolId == null) return;
        try {
            if (pendingCoatingPoolMapper != null) pendingCoatingPoolMapper.deleteById(poolId);
        } catch (Exception ignore) { }
        try {
            if (pendingRewindingPoolMapper != null) pendingRewindingPoolMapper.deleteById(poolId);
        } catch (Exception ignore) { }
        try {
            if (pendingSlittingPoolMapper != null) pendingSlittingPoolMapper.deleteById(poolId);
        } catch (Exception ignore) { }
    }
    
    @Override
    public int updateRewindingTask(ScheduleRewinding rewinding) {
        return rewindingMapper.updateById(rewinding);
    }
    
    // ========== 分切计划 ==========
    
    @Override
    public IPage<ScheduleSlitting> getSlittingTasks(Map<String, Object> params) {
        Page<ScheduleSlitting> page = new Page<>(
            getParamInt(params, "pageNum", 1),
            getParamInt(params, "pageSize", 10)
        );
        Map<String, Object> filters = new HashMap<>();
        if (params.get("scheduleId") != null) {
            filters.put("scheduleId", params.get("scheduleId"));
        }
        if (params.get("planDate") != null) {
            String planDate = params.get("planDate").toString();
            if (!planDate.isEmpty()) {
                filters.put("planDate", planDate);
            }
        }
        if (params.get("status") != null) {
            filters.put("status", params.get("status"));
        }
        if (params.get("equipmentId") != null) {
            filters.put("equipmentId", params.get("equipmentId"));
        }
        if (params.get("materialCode") != null) {
            filters.put("materialCode", params.get("materialCode"));
        }
        IPage<ScheduleSlitting> pageResult = slittingMapper.selectPage(page, filters);

        // 同步订单明细中的规格信息，优先使用订单明细表的数据
        List<ScheduleSlitting> records = pageResult.getRecords();
        if (records != null && !records.isEmpty()) {
            for (ScheduleSlitting task : records) {
                if (task.getOrderItemId() == null) continue;
                try {
                    Long orderItemId = task.getOrderItemId();
                    Map<String, Object> item = salesOrderItemMapper.selectFullItemById(orderItemId);
                    if (item == null) continue;

                    // thickness (可能为数字或字符串)
                    if (item.get("thickness") != null) {
                        try {
                            task.setThickness(new java.math.BigDecimal(item.get("thickness").toString()));
                        } catch (Exception ignore) { }
                    }

                    // width -> targetWidth (存储为数字，单位 mm)
                    if (item.get("width") != null) {
                        try {
                            task.setTargetWidth(((Number)item.get("width")).intValue());
                        } catch (Exception ex) {
                            try { task.setTargetWidth(Integer.parseInt(item.get("width").toString())); } catch (Exception ignore) { }
                        }
                    }

                    // length: sales_order_items.length 存为 mm，转换为 m
                    if (item.get("length") != null) {
                        try {
                            int lenMm = ((Number)item.get("length")).intValue();
                            task.setSlitLength(lenMm / 1000);
                        } catch (Exception ex) {
                            try {
                                int lenMm = Integer.parseInt(item.get("length").toString());
                                task.setSlitLength(lenMm / 1000);
                            } catch (Exception ignore) { }
                        }
                    }

                    // rolls
                    if (item.get("rolls") != null) {
                        try { task.setPlanRolls(((Number)item.get("rolls")).intValue()); } catch (Exception ex) {
                            try { task.setPlanRolls(Integer.parseInt(item.get("rolls").toString())); } catch (Exception ignore) { }
                        }
                    }

                    // material code 从 task 或 item 回填
                    if ((task.getMaterialCode() == null || task.getMaterialCode().isEmpty()) && item.get("material_code") != null) {
                        try { task.setMaterialCode(item.get("material_code").toString()); } catch (Exception ignore) { }
                    }

                    // 重新构建显示用 spec 字段
                    try {
                        String mat = task.getMaterialCode() != null ? task.getMaterialCode() : (item.get("material_code") != null ? item.get("material_code").toString() : "");
                        int w = task.getTargetWidth() != null ? task.getTargetWidth() : 0;
                        int l = task.getSlitLength() != null ? task.getSlitLength() : 0;
                        int r = task.getPlanRolls() != null ? task.getPlanRolls() : 0;
                        task.setSpec(String.format("%s-%dmm x %dm x %d卷", mat, w, l, r));
                    } catch (Exception ignore) { }

                } catch (Exception ex) {
                    // 单条失败不影响整体返回
                }
            }
        }

        return pageResult;
    }
    
    @Override
    public ScheduleSlitting addSlittingTask(ScheduleSlitting slitting) {
        // 验证必填字段
        if (slitting.getMaterialCode() == null || slitting.getMaterialCode().isEmpty()) {
            throw new RuntimeException("物料编码不能为空");
        }
        
        // 设置任务号
        slitting.setTaskNo(slittingMapper.generateTaskNo(slitting.getPlanDate()));
        
        // 如未指定规格，自动生成
        if (slitting.getSpec() == null || slitting.getSpec().isEmpty()) {
            slitting.setSpec(String.format("%s-%dmm x %dm x %d卷", 
                slitting.getMaterialCode(),
                slitting.getTargetWidth() != null ? slitting.getTargetWidth() : 0,
                slitting.getSlitLength() != null ? slitting.getSlitLength() : 0,
                slitting.getPlanRolls() != null ? slitting.getPlanRolls() : 0));
        }
        
        // 时间精确到10分钟
        if (slitting.getPlanStartTime() != null) {
            slitting.setPlanStartTime(roundTimeToTenMinutes(slitting.getPlanStartTime()));
        }
        if (slitting.getPlanEndTime() != null) {
            slitting.setPlanEndTime(roundTimeToTenMinutes(slitting.getPlanEndTime()));
        }
        
        // 设置默认状态
        slitting.setStatus("pending");
        
        // 插入数据库
        slittingMapper.insert(slitting);
        
        return slitting;
    }
    
    @Override
    public int updateSlittingTask(ScheduleSlitting slitting) {
        return slittingMapper.updateById(slitting);
    }
    
    /**
     * 删除分切任务
     */
    @Override
    @Transactional
    public int deleteSlittingTask(Long id) {
        if (id == null) {
            throw new RuntimeException("分切任务ID不能为空");
        }
        return slittingMapper.deleteById(id);
    }

    @Override
    public int startSlittingTask(Long taskId, String operator) {
        if (taskId == null) {
            throw new RuntimeException("分切任务ID不能为空");
        }
        ScheduleSlitting task = slittingMapper.selectById(taskId);
        if (task == null) {
            throw new RuntimeException("分切任务不存在");
        }
        if (task.getActualStartTime() == null) {
            task.setActualStartTime(new Date());
        }
        task.setStatus("in_progress");
        task.setUpdateBy(operator);
        return slittingMapper.updateById(task);
    }

    @Override
    public int completeSlittingTask(Long taskId, Integer actualRolls, String operator) {
        if (taskId == null) {
            throw new RuntimeException("分切任务ID不能为空");
        }
        ScheduleSlitting task = slittingMapper.selectById(taskId);
        if (task == null) {
            throw new RuntimeException("分切任务不存在");
        }
        if (task.getActualStartTime() == null) {
            task.setActualStartTime(new Date());
        }
        task.setActualEndTime(new Date());
        if (actualRolls != null) {
            task.setActualRolls(actualRolls);
        } else if (task.getPlanRolls() != null) {
            task.setActualRolls(task.getPlanRolls());
        }
        if (task.getActualStartTime() != null && task.getActualEndTime() != null) {
            long minutes = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(task.getActualEndTime().getTime() - task.getActualStartTime().getTime());
            task.setActualDuration((int) Math.max(minutes, 0));
        }
        task.setStatus("completed");
        task.setUpdateBy(operator);
        return slittingMapper.updateById(task);
    }
    
    // ========== 分条计划 ==========
    
    @Override
    public IPage<ScheduleStripping> getStrippingTasks(Map<String, Object> params) {
        Page<ScheduleStripping> page = new Page<>(
            getParamInt(params, "pageNum", 1),
            getParamInt(params, "pageSize", 10)
        );
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ScheduleStripping> query = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        if (params.get("scheduleId") != null) {
            query.eq("schedule_id", params.get("scheduleId"));
        }
        return strippingMapper.selectPage(page, query);
    }
    
    @Override
    public ScheduleStripping addStrippingTask(ScheduleStripping stripping) {
        stripping.setTaskNo(strippingMapper.generateTaskNo());
        stripping.setStatus("pending");
        strippingMapper.insert(stripping);
        return stripping;
    }
    
    @Override
    public int updateStrippingTask(ScheduleStripping stripping) {
        return strippingMapper.updateById(stripping);
    }
    
    // ========== 生产看板 ==========
    
    @Override
    public List<Map<String, Object>> getEquipmentBoard(String planDate) {
        // Implement simple stub or actual logic
        return new ArrayList<>();
    }
    
    @Override
    public Map<String, Object> getProgressBoard(String planDate) {
        return new HashMap<>();
    }
    
    // ========== 审批流程 ==========
    
    @Override
    public int submitApproval(Long scheduleId, String operator) {
        ProductionSchedule schedule = scheduleMapper.selectById(scheduleId);
        if (schedule != null) {
            schedule.setStatus("approving");
            return scheduleMapper.update(schedule);
        }
        return 0;
    }
    
    @Override
    public int approveSchedule(Long scheduleId, boolean approved, String remark, String operator) {
         ProductionSchedule schedule = scheduleMapper.selectById(scheduleId);
        if (schedule != null) {
            schedule.setStatus(approved ? "approved" : "rejected");
            // Log approval
            ScheduleApprovalLog log = new ScheduleApprovalLog();
            log.setScheduleId(scheduleId);
            log.setAction(approved ? "approve" : "reject");
            log.setOpinion(remark); // Correct field is 'opinion' not 'remark'
            log.setOperatorName(operator);
            log.setCreateTime(new Date());
            
            approvalLogMapper.insert(log);
            
            return scheduleMapper.update(schedule);
        }
        return 0;
    }
    
    @Override
    public List<ScheduleApprovalLog> getApprovalLogs(Long scheduleId) {
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ScheduleApprovalLog> query = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        query.eq("schedule_id", scheduleId);
        query.orderByDesc("create_time");
        return approvalLogMapper.selectList(query);
    }
    
    // ========== 质检反馈 ==========
    
    @Override
    public IPage<QualityInspection> getInspectionList(Map<String, Object> params) {
        Page<QualityInspection> page = new Page<>(
            getParamInt(params, "pageNum", 1),
            getParamInt(params, "pageSize", 10)
        );
        return inspectionMapper.selectInspectionList(page, params);
    }
    
    @Override
    public QualityInspection getInspectionById(Long id) {
        return inspectionMapper.selectById(id);
    }
    
    @Override
    public QualityInspection submitInspection(QualityInspection inspection) {
        inspection.setInspectionNo(inspectionMapper.generateInspectionNo());
        inspection.setInspectionTime(new Date());
        inspectionMapper.insert(inspection);
        return inspection;
    }
    
    @Override
    public int updateInspection(QualityInspection inspection) {
        return inspectionMapper.updateById(inspection);
    }
    
    @Override
    public List<QualityInspection> getInspectionByTask(String taskType, Long taskId) {
        return inspectionMapper.selectByTask(taskType, taskId);
    }
    
    @Override
    public Map<String, Object> getInspectionStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("todayCount", inspectionMapper.getTodayInspectionCount());
        stats.put("todayPassRate", inspectionMapper.getTodayPassRate());
        return stats;
    }
    
    // ========== 紧急插单 ==========
    
    @Override
    public UrgentOrderLog createUrgentOrder(UrgentOrderLog urgentOrder) {
        urgentOrder.setCreateTime(new Date());
        urgentOrder.setStatus("pending");
        urgentOrderMapper.insert(urgentOrder);
        return urgentOrder;
    }
    
    @Override
    public IPage<UrgentOrderLog> getUrgentOrderList(Map<String, Object> params) {
        Page<UrgentOrderLog> page = new Page<>(
            getParamInt(params, "pageNum", 1),
            getParamInt(params, "pageSize", 10)
        );
        return urgentOrderMapper.selectUrgentOrderList(page, params);
    }
    
    @Override
    public boolean approveUrgentOrder(Long id, boolean approved, String remark, String operator) {
        UrgentOrderLog log = urgentOrderMapper.selectById(id);
        if (log != null) {
            log.setStatus(approved ? "approved" : "rejected");
            urgentOrderMapper.updateById(log);
            return approved;
        }
        return false;
    }
    
    @Override
    public boolean executeUrgentOrder(Long id, String operator) {
         UrgentOrderLog log = urgentOrderMapper.selectById(id);
        if (log != null && "approved".equals(log.getStatus())) {
            // Logic to adjust schedule priority
            log.setStatus("executed");
            urgentOrderMapper.updateById(log);
            return true;
        }
        return false;
    }
    
    // ========== 甘特图 ==========
    
    @Override
    public List<Map<String, Object>> getGanttData(Map<String, Object> params) {
        // Simple stub
        return new ArrayList<>();
    }
    
    // ========== 自动排程 ==========
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProductionSchedule autoSchedule(Map<String, Object> params) {
        try {
            System.out.println("=== 开始自动排程 ===");
            System.out.println("参数: " + params);
            
            // 解析参数
            String scheduleDate = (String) params.get("scheduleDate");
            String scheduleType = (String) params.getOrDefault("scheduleType", "order");
            String operator = (String) params.getOrDefault("operator", "admin");
            
            List<Long> orderItemIds = new ArrayList<>();
            Map<Long, Integer> scheduleQtyMap = new HashMap<>();
            
            // 支持两种参数格式
            // 格式1: { orderItemIds: [1,2,3], scheduleDate, operator }
            // 格式2: { details: [{order_item_id, schedule_qty}], scheduleDate, operator }
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> details = (List<Map<String, Object>>) params.get("details");
            
            if (details != null && !details.isEmpty()) {
                // 格式2：使用details数组
                for (Map<String, Object> detail : details) {
                    Long orderItemId = Long.parseLong(detail.get("order_item_id").toString());
                    Integer scheduleQty = Integer.parseInt(detail.get("schedule_qty").toString());
                    
                    orderItemIds.add(orderItemId);
                    scheduleQtyMap.put(orderItemId, scheduleQty);
                }
            } else {
                // 格式1：使用orderItemIds数组
                @SuppressWarnings("unchecked")
                List<Object> orderItemIdsList = (List<Object>) params.get("orderItemIds");
                
                if (orderItemIdsList == null || orderItemIdsList.isEmpty()) {
                    throw new RuntimeException("排程明细不能为空");
                }
                
                for (Object idObj : orderItemIdsList) {
                    Long orderItemId = Long.parseLong(idObj.toString());
                    orderItemIds.add(orderItemId);
                    // 如果没有指定数量，查询订单的待排程数量
                    Map<String, Object> itemInfo = salesOrderItemMapper.selectFullItemById(orderItemId);
                    if (itemInfo != null) {
                        Object rollsObj = itemInfo.get("rolls");
                        Integer rolls = rollsObj != null ? Integer.parseInt(rollsObj.toString()) : 0;
                        scheduleQtyMap.put(orderItemId, rolls);
                    }
                }
            }
            
            if (orderItemIds.isEmpty()) {
                throw new RuntimeException("排程明细不能为空");
            }
            
            System.out.println("订单明细ID: " + orderItemIds);
            System.out.println("排程数量映射: " + scheduleQtyMap);
            
            // 调用原有的autoSchedule方法
            ProductionSchedule schedule = autoSchedule(orderItemIds, scheduleQtyMap, scheduleDate, operator);
            
            System.out.println("=== 排程创建成功 ===");
            System.out.println("排程ID: " + schedule.getId());
            System.out.println("排程单号: " + schedule.getScheduleNo());
            
            return schedule;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("自动排程失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProductionSchedule autoSchedule(List<Long> orderItemIds, Map<Long, Integer> scheduleQtyMap, 
                                          String scheduleDate, String operator) {
        System.out.println("=== 🚀 动态排程系统启动 ===");
        
        // ========== 步骤1：计算客户优先级并排序 ==========
        System.out.println("步骤1：计算客户优先级...");
        List<Long> sortedOrderItemIds = sortOrderItemsByPriority(orderItemIds, operator);
        System.out.println("✅ 优先级排序完成，共 " + sortedOrderItemIds.size() + " 个订单");
        
        // 创建排程主记录
        ProductionSchedule schedule = new ProductionSchedule();
        schedule.setScheduleNo(generateScheduleNo());
        
        // 将字符串日期转换为Date
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            schedule.setScheduleDate(sdf.parse(scheduleDate));
        } catch (Exception e) {
            schedule.setScheduleDate(new Date());
        }
        
        schedule.setScheduleType("order");
        schedule.setStatus("pending");
        schedule.setCreateBy(operator);
        schedule.setCreateTime(new Date());
        schedule.setUpdateBy(operator);
        schedule.setUpdateTime(new Date());
        
        // 插入排程主记录
        scheduleMapper.insert(schedule);
        System.out.println("排程主记录已创建，ID: " + schedule.getId());
        
        // ========== 步骤2：按优先级逐单处理 ==========
        System.out.println("\n步骤2：按优先级逐单处理（有料/无料分流）...");
        List<Map<String, Object>> hasStockOrders = new ArrayList<>();   // 有料订单
        List<Map<String, Object>> noStockOrders = new ArrayList<>();    // 无料订单
        int priority = 1;
        
        for (Long orderItemId : sortedOrderItemIds) {
            // 查询订单明细信息
            Map<String, Object> orderItem = salesOrderItemMapper.selectFullItemById(orderItemId);
            if (orderItem == null) {
                System.out.println("⚠️ 订单明细不存在，ID=" + orderItemId);
                continue;
            }
            
            Integer scheduleQty = scheduleQtyMap.get(orderItemId);
            if (scheduleQty == null || scheduleQty <= 0) {
                scheduleQty = (Integer) orderItem.get("rolls");
            }
            
            // 创建排程订单项
            ScheduleOrderItem item = createScheduleOrderItem(schedule.getId(), orderItem, scheduleQty, priority++);
            orderItemMapper.insert(item);
            
            // ========== 步骤2.1：查询库存并尝试锁定 ==========
            String materialCode = (String) orderItem.get("material_code");
            Long orderId = (Long) orderItem.get("order_id");
            String orderNo = (String) orderItem.get("order_no");
            BigDecimal customerPriority = getCustomerPriority(orderId);
            
            List<com.fine.model.schedule.OrderMaterialLock> locks = lockMaterialForOrder(
                orderId, orderNo, orderItemId, materialCode, scheduleQty, customerPriority, operator
            );
            
            // 统计已锁定数量
            int lockedQty = locks.stream().mapToInt(com.fine.model.schedule.OrderMaterialLock::getLockedQty).sum();
            int shortageQty = scheduleQty - lockedQty;
            
            // 准备订单数据
            Map<String, Object> itemForTask = new HashMap<>(orderItem);
            itemForTask.put("schedule_qty", scheduleQty);
            itemForTask.put("pending_qty", scheduleQty);
            itemForTask.put("locked_qty", lockedQty);
            itemForTask.put("shortage_qty", shortageQty);
            itemForTask.put("customer_priority", customerPriority);
            itemForTask.put("order_item_id", orderItemId);
            
            if (lockedQty >= scheduleQty) {
                // ✅ 有料订单：库存充足
                System.out.println("✅ 有料订单：" + orderNo + " " + materialCode + " x " + scheduleQty);
                hasStockOrders.add(itemForTask);
            } else {
                // ❌ 无料订单：需要涂布
                System.out.println("❌ 无料订单：" + orderNo + " " + materialCode + " 缺口=" + shortageQty);
                noStockOrders.add(itemForTask);
                
                // 加入待涂布订单池
                addToPendingCoatingPool(orderItem, shortageQty, customerPriority);
            }
        }
        
        System.out.println("分流完成：有料订单 " + hasStockOrders.size() + " 个，无料订单 " + noStockOrders.size() + " 个");
        
        // ========== 步骤3：处理有料订单（直接排产复卷/分切） ==========
        Date nextStartTime = new Date();
        if (!hasStockOrders.isEmpty()) {
            System.out.println("\n步骤3：生成有料订单的复卷/分切任务...");
            nextStartTime = generateRewindingTasks(schedule, hasStockOrders, operator, nextStartTime);
            nextStartTime = generateSlittingTasks(schedule, hasStockOrders, operator, nextStartTime);
            System.out.println("✅ 有料订单任务生成完成");
            // 清理待涂布池：这些订单已有库存并已按复卷/分切排产，无需继续显示在涂布汇总
            try {
                if (pendingCoatingPoolMapper != null) {
                    for (Map<String, Object> item : hasStockOrders) {
                        Object oid = item.get("order_item_id");
                        if (oid instanceof Number) {
                            Long orderItemId = ((Number) oid).longValue();
                            try {
                                pendingCoatingPoolMapper.deleteByOrderItemId(orderItemId);
                            } catch (Exception ignore) { }
                        }
                    }
                }
            } catch (Exception ignore) { }
        }
        
        // ========== 步骤4：处理无料订单（动态涂布排程） ==========
        if (!noStockOrders.isEmpty()) {
            System.out.println("\n步骤4：启动动态涂布排程...");
            nextStartTime = generateDynamicCoatingTasks(schedule, noStockOrders, operator, nextStartTime);
            System.out.println("✅ 涂布任务生成完成");
            
            // 涂布后继续生成复卷/分切任务
            nextStartTime = generateRewindingTasks(schedule, noStockOrders, operator, nextStartTime);
            nextStartTime = generateSlittingTasks(schedule, noStockOrders, operator, nextStartTime);
        }
        
        // ========== 步骤5：更新销售订单数量和统计 ==========
        System.out.println("\n步骤5：更新订单状态...");
        for (Long orderItemId : sortedOrderItemIds) {
            Integer scheduleQty = scheduleQtyMap.get(orderItemId);
            if (scheduleQty != null && scheduleQty > 0) {
                salesOrderItemMapper.decreaseRolls(orderItemId, scheduleQty);
            }
        }
        
        // 更新排程统计数据
        updateScheduleStatistics(schedule.getId());
        
        System.out.println("\n=== ✅ 动态排程系统完成 ===");
        System.out.println("排程ID: " + schedule.getId() + " | 排程单号: " + schedule.getScheduleNo());
        
        // 返回完整的排程信息（包含关联的任务）
        return getScheduleById(schedule.getId());
    }
    
    // ========== 新增：动态排程辅助方法 ==========
    
    /**
     * 按客户优先级排序订单明细
     */
    private List<Long> sortOrderItemsByPriority(List<Long> orderItemIds, String operator) {
        if (customerPriorityService == null) {
            System.out.println("⚠️ 客户优先级服务未启用，使用原始顺序");
            return orderItemIds;
        }
        
        try {
            // 收集订单ID和相关信息用于优先级计算
            Map<Long, Map<String, Object>> orderInfoMap = new HashMap<>();
            List<Long> orderIds = new ArrayList<>();
            
            for (Long orderItemId : orderItemIds) {
                Map<String, Object> itemInfo = salesOrderItemMapper.selectFullItemById(orderItemId);
                if (itemInfo != null) {
                    Long orderId = (Long) itemInfo.get("order_id");
                    if (!orderIds.contains(orderId)) {
                        orderIds.add(orderId);
                        orderInfoMap.put(orderId, itemInfo);
                    }
                }
            }
            
            // 批量计算优先级
            customerPriorityService.batchCalculateOrderPriority(orderIds);
            
            // 按优先级排序
            List<Long> sortedOrderIds = customerPriorityService.sortOrdersByPriority(orderIds);
            
            // 根据排序后的订单ID重新排列订单明细ID
            List<Long> sortedItemIds = new ArrayList<>();
            for (Long orderId : sortedOrderIds) {
                for (Long itemId : orderItemIds) {
                    Map<String, Object> info = salesOrderItemMapper.selectFullItemById(itemId);
                    if (info != null && orderId.equals(info.get("order_id"))) {
                        sortedItemIds.add(itemId);
                    }
                }
            }
            
            return sortedItemIds;
        } catch (Exception e) {
            System.out.println("⚠️ 优先级排序失败，使用原始顺序：" + e.getMessage());
            return orderItemIds;
        }
    }
    
    /**
     * 创建排程订单项
     */
    private ScheduleOrderItem createScheduleOrderItem(Long scheduleId, Map<String, Object> orderItem, 
                                                       Integer scheduleQty, int priority) {
        ScheduleOrderItem item = new ScheduleOrderItem();
        item.setScheduleId(scheduleId);
        item.setOrderId((Long) orderItem.get("order_id"));
        item.setOrderItemId((Long) orderItem.get("id"));
        item.setOrderNo((String) orderItem.get("order_no"));
        item.setCustomer((String) orderItem.get("customer"));
        item.setCustomerLevel((String) orderItem.get("customer_level"));
        item.setMaterialCode((String) orderItem.get("material_code"));
        item.setMaterialName((String) orderItem.get("material_name"));
        item.setColorCode((String) orderItem.get("color_code"));
        
        if (orderItem.get("thickness") != null) {
            item.setThickness(new BigDecimal(orderItem.get("thickness").toString()));
        }
        if (orderItem.get("width") != null) {
            item.setWidth(new BigDecimal(orderItem.get("width").toString()));
        }
        if (orderItem.get("length") != null) {
            item.setLength(new BigDecimal(orderItem.get("length").toString()));
        }
        
        item.setOrderQty((Integer) orderItem.get("rolls"));
        item.setScheduleQty(scheduleQty);
        
        Object deliveryDateObj = orderItem.get("delivery_date");
        if (deliveryDateObj != null) {
            if (deliveryDateObj instanceof Date) {
                item.setDeliveryDate((Date) deliveryDateObj);
            } else {
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                    item.setDeliveryDate(sdf.parse(deliveryDateObj.toString()));
                } catch (Exception e) {
                    // 忽略解析错误
                }
            }
        }
        
        item.setPriority(priority);
        item.setSourceType("production");
        item.setStatus("pending");
        
        return item;
    }
    
    /**
     * 获取客户优先级得分
     */
    private BigDecimal getCustomerPriority(Long orderId) {
        if (customerPriorityService == null) {
            return new BigDecimal("10.00"); // 默认得分
        }
        
        try {
            com.fine.model.schedule.OrderCustomerPriority priority = 
                customerPriorityService.calculateOrderPriority(orderId, "", 0L, "", BigDecimal.ZERO, new Date());
            return priority != null ? priority.getTotalScore() : new BigDecimal("10.00");
        } catch (Exception e) {
            return new BigDecimal("10.00");
        }
    }
    
    /**
     * 锁定物料
     */
    private List<com.fine.model.schedule.OrderMaterialLock> lockMaterialForOrder(
            Long orderId, String orderNo, Long orderItemId, String materialCode,
            Integer requiredQty, BigDecimal customerPriority, String operator) {
        
        if (materialLockService == null) {
            System.out.println("⚠️ 物料锁定服务未启用");
            return new ArrayList<>();
        }
        
        try {
            return materialLockService.lockMaterial(orderId, orderNo, orderItemId, 
                materialCode, requiredQty, customerPriority, operator);
        } catch (Exception e) {
            System.out.println("⚠️ 物料锁定失败：" + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    /**
     * 加入待涂布订单池
     */
    private void addToPendingCoatingPool(Map<String, Object> orderItem, int shortageQty, 
                                         BigDecimal customerPriority) {
        if (pendingCoatingPoolMapper == null) {
            return;
        }
        
        try {
            com.fine.model.schedule.PendingCoatingOrderPool poolItem = 
                new com.fine.model.schedule.PendingCoatingOrderPool();
            
            String materialCode = (String) orderItem.get("material_code");
            poolItem.setPoolNo("POOL-" + materialCode);
            poolItem.setMaterialCode(materialCode);
            poolItem.setMaterialName((String) orderItem.get("material_name"));
            poolItem.setOrderId((Long) orderItem.get("order_id"));
            poolItem.setOrderNo((String) orderItem.get("order_no"));
            poolItem.setOrderItemId((Long) orderItem.get("id"));
            poolItem.setCustomerName((String) orderItem.get("customer"));
            poolItem.setCustomerPriority(customerPriority);
            poolItem.setShortageQty(shortageQty);
            
            // 计算缺口面积
            int width = orderItem.get("width") != null ? ((Number)orderItem.get("width")).intValue() : 0;
            int length = orderItem.get("length") != null ? ((Number)orderItem.get("length")).intValue() : 0;
            BigDecimal area = new BigDecimal(shortageQty).multiply(new BigDecimal(width))
                .multiply(new BigDecimal(length)).divide(new BigDecimal(1000000), 2, BigDecimal.ROUND_HALF_UP);
            poolItem.setShortageArea(area);
            
            poolItem.setPoolStatus("WAITING");
            poolItem.setAddedAt(new Date());
            
            pendingCoatingPoolMapper.insert(poolItem);
            System.out.println("➕ 加入待涂布池：" + materialCode + " 缺口=" + shortageQty);
        } catch (Exception e) {
            System.out.println("⚠️ 加入待涂布池失败：" + e.getMessage());
        }
    }
    
    /**
     * 动态涂布排程（支持MOQ、同料号合并、时间调整）
     */
    private Date generateDynamicCoatingTasks(ProductionSchedule schedule, 
                                             List<Map<String, Object>> orders,
                                             String operator, Date startTime) {
        // 按料号分组
        Map<String, List<Map<String, Object>>> groupedByMaterial = orders.stream()
            .collect(Collectors.groupingBy(item -> 
                item.get("material_code") != null ? item.get("material_code").toString() : "UNKNOWN"
            ));
        
        Date currentTime = startTime;
        
        for (Map.Entry<String, List<Map<String, Object>>> entry : groupedByMaterial.entrySet()) {
            String materialCode = entry.getKey();
            List<Map<String, Object>> materialOrders = entry.getValue();
            
            // 计算总需求面积
            BigDecimal totalArea = BigDecimal.ZERO;
            for (Map<String, Object> order : materialOrders) {
                Integer qty = (Integer) order.get("shortage_qty");
                if (qty == null) qty = (Integer) order.get("pending_qty");
                int width = order.get("width") != null ? ((Number)order.get("width")).intValue() : 0;
                int length = order.get("length") != null ? ((Number)order.get("length")).intValue() : 0;
                BigDecimal area = new BigDecimal(qty).multiply(new BigDecimal(width))
                    .multiply(new BigDecimal(length)).divide(new BigDecimal(1000000), 2, BigDecimal.ROUND_HALF_UP);
                totalArea = totalArea.add(area);
            }
            
            System.out.println("📊 料号 " + materialCode + " 合并涂布：" + materialOrders.size() + 
                " 个订单，总面积 " + totalArea + "㎡");
            
            // 生成合并的涂布任务
            currentTime = generateMergedCoatingTask(schedule, materialCode, materialOrders, totalArea, operator, currentTime);
        }
        
        return currentTime;
    }
    
    /**
     * 生成合并的涂布任务
     */
    private Date generateMergedCoatingTask(ProductionSchedule schedule, String materialCode,
                                           List<Map<String, Object>> orders, BigDecimal totalArea,
                                           String operator, Date startTime) {
        // 获取涂布设备
        List<Equipment> coatingEquipments = equipmentMapper.selectAvailableByType("COATING");
        if (coatingEquipments.isEmpty()) {
            System.out.println("⚠️ 无可用涂布设备");
            return startTime;
        }
        
        Equipment equipment = coatingEquipments.get(0);
        Map<String, Object> firstOrder = orders.get(0);
        
        ScheduleCoating coating = new ScheduleCoating();
        coating.setScheduleId(schedule.getId());
        coating.setTaskNo(coatingMapper.generateTaskNo(schedule.getScheduleDate()));
        coating.setEquipmentId(equipment.getId());
        coating.setEquipmentCode(equipment.getEquipmentCode());
        coating.setPlanDate(schedule.getScheduleDate());
        coating.setMaterialCode(materialCode);
        coating.setMaterialName((String) firstOrder.get("material_name"));
        coating.setColorCode((String) firstOrder.get("color_code"));
        coating.setPlanSqm(totalArea);
        
        // 默认使用1300mm宽度
        coating.setFilmWidth(1300);
        coating.setJumboWidth(1300);
        coating.setCoatingSpeed(new BigDecimal(30));
        
        // 计算涂布时长：面积÷(速度×宽度)
        int duration = (int) Math.ceil(totalArea.doubleValue() / (30.0 * 1.3));
        coating.setPlanDuration(Math.max(duration, 10));
        
        coating.setPlanStartTime(startTime);
        Calendar cal = Calendar.getInstance();
        cal.setTime(startTime);
        cal.add(Calendar.MINUTE, Math.max(duration, 10));
        coating.setPlanEndTime(cal.getTime());
        
        coating.setStatus("pending");
        coating.setCreateBy(operator);
        
        coatingMapper.insert(coating);
        
        System.out.println("✅ 涂布任务：" + coating.getTaskNo() + " 面积=" + totalArea + "㎡ 时长=" + duration + "分钟");
        
        // 留10分钟换料时间
        cal.add(Calendar.MINUTE, 10);
        return cal.getTime();
    }

    private int getParamInt(Map<String, Object> params, String key, int defaultValue) {
        if (params.containsKey(key)) {
            try {
                return Integer.parseInt(params.get(key).toString());
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
    
    /**
     * 更新排程统计数据（订单数、明细数、总面积）
     */
    private void updateScheduleStatistics(Long scheduleId) {
        // 统计订单数（不同订单的数量）
        Integer totalOrders = orderItemMapper.countDistinctOrders(scheduleId);
        
        // 统计明细数（订单明细项数量）
        Integer totalItems = orderItemMapper.countItems(scheduleId);
        
        // 统计总面积（从涂布任务汇总）
        QueryWrapper<ScheduleCoating> wrapper = new QueryWrapper<>();
        wrapper.eq("schedule_id", scheduleId);
        wrapper.select("IFNULL(SUM(plan_sqm), 0) as total");
        List<Map<String, Object>> result = coatingMapper.selectMaps(wrapper);
        
        BigDecimal totalSqm = BigDecimal.ZERO;
        if (!result.isEmpty() && result.get(0).get("total") != null) {
            totalSqm = new BigDecimal(result.get(0).get("total").toString());
        }
        
        // 更新排程主记录
        ProductionSchedule schedule = new ProductionSchedule();
        schedule.setId(scheduleId);
        schedule.setTotalOrders(totalOrders != null ? totalOrders : 0);
        schedule.setTotalItems(totalItems != null ? totalItems : 0);
        schedule.setTotalSqm(totalSqm);
        
        scheduleMapper.update(schedule);
        
        System.out.println("统计数据更新完成：订单数=" + totalOrders + ", 明细数=" + totalItems + ", 总面积=" + totalSqm + "㎡");
    }
    
    private String generateScheduleNo() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
        String dateStr = sdf.format(new Date());
        String prefix = "SCH" + dateStr;
        
        // 查询当天的最大序号
        Integer maxSeq = scheduleMapper.getMaxSeqByPrefix(prefix);
        int nextSeq = (maxSeq != null ? maxSeq : 0) + 1;
        
        return prefix + String.format("%04d", nextSeq);
    }
    
    // ========== 待排程订单查询实现（新增） ==========
    
    @Override
    public IPage<com.fine.entity.PendingScheduleOrder> getPendingOrders(Map<String, Object> params) {
        Integer pageNum = getParamInt(params, "pageNum", 1);
        Integer pageSize = getParamInt(params, "pageSize", 10);
        
        Page<com.fine.entity.PendingScheduleOrder> page = new Page<>(pageNum, pageSize);
        
        // 如果指定了物料编号，则按物料查询
        String materialCode = (String) params.get("materialCode");
        List<com.fine.entity.PendingScheduleOrder> list;
        
        if (materialCode != null && !materialCode.isEmpty()) {
            list = pendingOrderMapper.selectByMaterialCode(materialCode);
        } else {
            list = pendingOrderMapper.selectAll();
        }
        
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.LocalDateTime deadline = now.plusHours(48);

        for (com.fine.entity.PendingScheduleOrder order : list) {
            if (order == null) {
                continue;
            }
            if (customerPriorityService == null) {
                order.setPriorityScore(java.math.BigDecimal.ZERO);
                continue;
            }

            Long customerId = order.getCustomerId();
            if (customerId == null && order.getCustomerCode() != null && customerMapper != null) {
                Customer customer = customerMapper.selectByCustomerCode(order.getCustomerCode());
                if (customer != null) {
                    customerId = customer.getId();
                    order.setCustomerId(customerId);
                }
            }

            java.util.Map<String, Object> detail = null;
            try {
                if (customerId != null) {
                    detail = customerPriorityService.getCustomerPriorityDetail(customerId);
                }
            } catch (Exception ignore) {
                detail = null;
            }

            if (detail != null && detail.get("totalScore") != null) {
                try {
                    order.setPriorityScore(new java.math.BigDecimal(detail.get("totalScore").toString()));
                } catch (Exception ex) {
                    order.setPriorityScore(java.math.BigDecimal.ZERO);
                }
            } else {
                order.setPriorityScore(java.math.BigDecimal.ZERO);
            }
        }

        list.sort((a, b) -> {
            java.time.LocalDateTime da = a != null ? a.getDeliveryDate() : null;
            java.time.LocalDateTime db = b != null ? b.getDeliveryDate() : null;
            boolean aWithin = da != null && !da.isAfter(deadline);
            boolean bWithin = db != null && !db.isAfter(deadline);

            if (aWithin != bWithin) {
                return aWithin ? -1 : 1; // 48h内优先
            }
            if (!aWithin && !bWithin) {
                java.math.BigDecimal pa = a != null && a.getPriorityScore() != null ? a.getPriorityScore() : java.math.BigDecimal.ZERO;
                java.math.BigDecimal pb = b != null && b.getPriorityScore() != null ? b.getPriorityScore() : java.math.BigDecimal.ZERO;
                int cmp = pb.compareTo(pa);
                if (cmp != 0) return cmp;
            }
            if (da == null && db == null) return 0;
            if (da == null) return 1;
            if (db == null) return -1;
            return da.compareTo(db);
        });

        // 手动分页
        int total = list.size();
        int start = (pageNum - 1) * pageSize;
        int end = Math.min(start + pageSize, total);

        page.setRecords(start < total ? list.subList(start, end) : new ArrayList<>());
        page.setTotal(total);
        
        return page;
    }
    
    @Override
    public List<com.fine.entity.PendingScheduleOrder> getPendingOrdersGroupByMaterial() {
        return pendingOrderMapper.groupByMaterialCode();
    }
    
    @Override
    public List<com.fine.entity.PendingScheduleOrder> getPendingOrdersByMaterial(String materialCode) {
        return pendingOrderMapper.selectByMaterialCode(materialCode);
    }
    
    @Override
    @Transactional
    public List<ScheduleCoating> autoScheduleCoating(String materialCode, Integer filmWidth, String operator) {
        System.out.println("=== 开始自动涂布排程 ===");
        System.out.println("物料编号: " + materialCode);
        System.out.println("薄膜宽度: " + filmWidth + "mm");
        
        // 1. 查询该物料的所有待排程订单
        List<com.fine.entity.PendingScheduleOrder> pendingOrders = pendingOrderMapper.selectByMaterialCode(materialCode);
        
        if (pendingOrders == null || pendingOrders.isEmpty()) {
            System.out.println("没有找到待排程订单");
            return new ArrayList<>();
        }
        
        System.out.println("找到 " + pendingOrders.size() + " 个待排程订单");
        
        // 2. 查询物料的生产配置（MOQ等）
        com.fine.entity.MaterialProductionConfig config = materialConfigMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.fine.entity.MaterialProductionConfig>()
                .eq("material_code", materialCode)
                .eq("is_active", 1)
        );
        
        // 3. 计算总需求面积
        BigDecimal totalArea = BigDecimal.ZERO;
        for (com.fine.entity.PendingScheduleOrder order : pendingOrders) {
            if (order.getPendingArea() != null) {
                totalArea = totalArea.add(order.getPendingArea());
            }
        }
        
        System.out.println("总待排程面积: " + totalArea + "㎡");
        
        // 4. 检查是否满足MOQ
        if (config != null && config.getMinProductionArea() != null) {
            if (totalArea.compareTo(config.getMinProductionArea()) < 0) {
                System.out.println("总面积 " + totalArea + "㎡ 小于最小生产面积 " + config.getMinProductionArea() + "㎡");
                // 警告但继续执行
            }
        }
        
        // 5. 创建涂布任务列表
        List<ScheduleCoating> coatingTasks = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
        String dateStr = sdf.format(new Date());
        
        for (com.fine.entity.PendingScheduleOrder order : pendingOrders) {
            ScheduleCoating coating = new ScheduleCoating();
            
            // 任务单号
            coating.setTaskNo("TB-" + dateStr.substring(Math.max(0, dateStr.length() - 6)) + "-" + String.format("%02d", coatingTasks.size() + 1));
            
            // 订单关联
            coating.setOrderItemId(order.getOrderItemId());
            coating.setOrderId(order.getOrderId());
            coating.setOrderNo(order.getOrderNo());
            
            // 产品信息
            coating.setMaterialCode(order.getMaterialCode());
            coating.setMaterialName(order.getMaterialName());
            
            // 计划面积和宽度
            coating.setPlanSqm(order.getPendingArea());
            coating.setFilmWidth(filmWidth);
            coating.setJumboWidth(filmWidth);
            
            // 状态
            coating.setStatus("pending");
            coating.setCreateBy(operator);
            coating.setCreateTime(new Date());
            
            // 计算计划时长（如果有配置）
            if (config != null && config.getSetupTime() != null && config.getUnitTime() != null) {
                int setupTime = config.getSetupTime();
                BigDecimal productionTime = order.getPendingArea().multiply(config.getUnitTime());
                coating.setPlanDuration(setupTime + productionTime.intValue());
            }
            
            coatingTasks.add(coating);
            
            // 插入数据库
            coatingMapper.insert(coating);
            System.out.println("创建涂布任务: " + coating.getTaskNo() + ", 订单: " + order.getOrderNo());
            
            // 更新订单项的已排程数量
            salesOrderItemMapper.updateScheduledQty(order.getOrderItemId(), order.getPendingQty());
        }
        
        System.out.println("=== 自动涂布排程完成，共创建 " + coatingTasks.size() + " 个任务 ===");
        return coatingTasks;
    }
    
    @Override
    @Transactional
    public List<ScheduleCoating> batchScheduleCoating(List<Long> orderItemIds, Integer filmWidth, String planDate, String operator) {
        return batchScheduleCoatingWithSchedule(orderItemIds, filmWidth, planDate, operator, null);
    }

    private List<ScheduleCoating> batchScheduleCoatingWithSchedule(List<Long> orderItemIds, Integer filmWidth, String planDate, String operator, Long scheduleId) {
        System.out.println("=== 开始批量涂布排程 ===");
        System.out.println("订单数量: " + orderItemIds.size());
        System.out.println("薄膜宽度: " + filmWidth + "mm");
        System.out.println("计划日期: " + planDate);
        
        List<ScheduleCoating> coatingTasks = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
        String dateStr = sdf.format(new Date());
        
        // 计划日期与默认时间窗（08:00开始）
        Date planDateObj;
        try {
            planDateObj = (planDate != null && !planDate.isEmpty())
                    ? new SimpleDateFormat("yyyy-MM-dd").parse(planDate)
                    : new Date();
        } catch (Exception ex) {
            planDateObj = new Date();
        }
        Calendar startCal = Calendar.getInstance();
        startCal.setTime(planDateObj);
        startCal.set(Calendar.HOUR_OF_DAY, 8);
        startCal.set(Calendar.MINUTE, 0);
        startCal.set(Calendar.SECOND, 0);
        startCal.set(Calendar.MILLISECOND, 0);
        Date currentStart = startCal.getTime();

        for (Long orderItemId : orderItemIds) {
            // 查询订单明细
            com.fine.modle.SalesOrderItem orderItem = salesOrderItemMapper.selectById(orderItemId);
            
            if (orderItem == null) {
                System.out.println("订单明细不存在: " + orderItemId);
                continue;
            }
            
            // 计算待排程数量（rolls - scheduledQty）
            int pendingQty = orderItem.getRolls() - (orderItem.getScheduledQty() != null ? orderItem.getScheduledQty() : 0);
            
            if (pendingQty <= 0) {
                System.out.println("订单已全部排程: " + orderItemId);
                continue;
            }
            
            // 计算面积
            BigDecimal area = BigDecimal.valueOf(pendingQty)
                .multiply(orderItem.getWidth())
                .multiply(orderItem.getLength())
                .divide(BigDecimal.valueOf(1000000), 2, BigDecimal.ROUND_HALF_UP);
            
            ScheduleCoating coating = new ScheduleCoating();
            if (scheduleId != null) {
                coating.setScheduleId(scheduleId);
            }
            String taskNo;
            try {
                // 使用数据库生成的任务号，避免唯一键冲突
                taskNo = coatingMapper.generateTaskNo(planDateObj);
            } catch (Exception ex) {
                // 兜底：日期+时间戳，仍确保唯一
                String shortDate = dateStr == null ? new SimpleDateFormat("yyMMdd").format(new Date()) : dateStr.substring(Math.max(0, dateStr.length() - 6));
                taskNo = "TB-" + shortDate + "-" + (System.currentTimeMillis() % 100);
            }
            coating.setTaskNo(taskNo);
            coating.setOrderItemId(orderItemId);
            coating.setOrderId(orderItem.getOrderId());
            coating.setMaterialCode(orderItem.getMaterialCode());
            coating.setMaterialName(orderItem.getMaterialName());
            coating.setPlanSqm(area);
            coating.setPlanDate(planDateObj);
            coating.setPlanStartTime(currentStart);

            // 计算预计时长：面积 / (速度 * 宽度)
            BigDecimal speed = coating.getCoatingSpeed() != null ? coating.getCoatingSpeed() : new BigDecimal(40);
            double widthM = filmWidth != null && filmWidth > 0 ? filmWidth / 1000.0 : 1.0;
            int durationMin = (int) Math.ceil(area.doubleValue() / (speed.doubleValue() * widthM));
            durationMin = Math.max(durationMin, 10); // 至少10分钟
            coating.setPlanDuration(durationMin);

            Date endTime = new Date(currentStart.getTime() + durationMin * 60L * 1000L);
            coating.setPlanEndTime(endTime);
            coating.setFilmWidth(filmWidth);
            coating.setJumboWidth(filmWidth);
            coating.setCoatingSpeed(new BigDecimal(40));
            coating.setStatus("pending");
            coating.setCreateBy(operator);
            coating.setCreateTime(new Date());
            
            // 插入任务
            coatingMapper.insert(coating);
            coatingTasks.add(coating);
            
            // 更新已排程数量
            salesOrderItemMapper.updateScheduledQty(orderItemId, pendingQty);
            
            System.out.println("创建涂布任务: " + coating.getTaskNo());

            // 顺延下一个任务：加上本次耗时 + 准备时间10分钟
            Calendar nextStart = Calendar.getInstance();
            nextStart.setTime(currentStart);
            nextStart.add(Calendar.MINUTE, durationMin + 10);
            currentStart = nextStart.getTime();
        }
        
        System.out.println("=== 批量涂布排程完成，共创建 " + coatingTasks.size() + " 个任务 ===");
        return coatingTasks;
    }
    
    @Override
    public Map<String, Object> getMaterialInfoByCode(String materialCode) {
        // 从销售订单明细表中查询该料号的最新记录
        QueryWrapper<com.fine.modle.SalesOrderItem> wrapper = new QueryWrapper<>();
        wrapper.eq("material_code", materialCode)
               .orderByDesc("created_at")
               .last("LIMIT 1");
        
        com.fine.modle.SalesOrderItem item = salesOrderItemMapper.selectOne(wrapper);
        
        Map<String, Object> result = new HashMap<>();
        if (item != null) {
            result.put("colorCode", item.getColorCode());
            result.put("thickness", item.getThickness());
            result.put("materialName", item.getMaterialName());
        }
        
        return result;
    }
    
    // ========== 缺失方法实现 ==========
    
    @Override
    public int publishSchedule(Long id, String operator) {
        ProductionSchedule schedule = new ProductionSchedule();
        schedule.setId(id);
        schedule.setStatus("published");
        schedule.setUpdateBy(operator);
        return scheduleMapper.update(schedule);
    }
    
    @Override
    public int completeSchedule(Long id, String operator) {
        ProductionSchedule schedule = new ProductionSchedule();
        schedule.setId(id);
        schedule.setStatus("completed");
        schedule.setUpdateBy(operator);
        return scheduleMapper.update(schedule);
    }
    
    @Override
    public Map<String, Object> getScheduleStats() {
        return getScheduleStatistics();
    }
    
    @Override
    public List<ScheduleCoating> getCoatingTasksByScheduleId(Long scheduleId) {
        return coatingMapper.selectByScheduleId(scheduleId);
    }
    
    @Override
    public int deleteCoatingTask(Long id) {
        return coatingMapper.deleteById(id);
    }
    
    @Override
    public int addScheduleOrderItem(ScheduleOrderItem item) {
        return orderItemMapper.insert(item);
    }
    
    @Override
    public int updateScheduleOrderItem(ScheduleOrderItem item) {
        return orderItemMapper.updateById(item);
    }
    
    @Override
    public int deleteScheduleOrderItem(Long id) {
        return orderItemMapper.deleteById(id);
    }
    
    // ========== 涂布排程看板接口实现 ==========
    
    @Override
    public List<Map<String, Object>> getCoatingQueue(String planDate) {
        // 查询指定日期的涂布任务，按稳定顺序排序；若为空则回退到“最近任务”
        QueryWrapper<ScheduleCoating> wrapper = new QueryWrapper<>();
        Date parsedStartDay = null;
        Date parsedEndExclusive = null;
        String parsedStartStr = null;
        String parsedEndStr = null;

        if (planDate != null && !planDate.isEmpty()) {
            // 支持“开始~结束”区间或单日
            String[] parts = planDate.split("~|～|至|,|，|-|－|–|—");
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                Date startDay = sdf.parse(parts[0].trim());
                Date endDay;
                if (parts.length >= 2 && !parts[1].trim().isEmpty()) {
                    endDay = sdf.parse(parts[1].trim());
                } else {
                    endDay = startDay;
                }
                if (endDay.before(startDay)) {
                    Date tmp = startDay;
                    startDay = endDay;
                    endDay = tmp;
                }
                Calendar c = Calendar.getInstance();
                c.setTime(endDay);
                c.add(Calendar.DAY_OF_MONTH, 1);
                Date endExclusive = c.getTime();

                String startStr = sdf.format(startDay);
                String endStr = sdf.format(endDay);

                parsedStartDay = startDay;
                parsedEndExclusive = endExclusive;
                parsedStartStr = startStr;
                parsedEndStr = endStr;

                // 区间内匹配开始/结束时间或计划日期
                Date finalStartDay = startDay;
                wrapper.and(w -> w.between("plan_start_time", finalStartDay, endExclusive)
                    .or().between("plan_end_time", finalStartDay, endExclusive)
                    .or().between("plan_date", startStr, endStr));
            } catch (Exception e) {
                // 回退旧逻辑，确保不会因解析失败丢数据
                wrapper.and(w -> w.likeLeft("plan_start_time", planDate)
                    .or().likeLeft("plan_date", planDate)
                    .or().likeLeft("create_time", planDate));
            }
        } else {
            // 未选日期时，显示“已排程但未完成”的任务
            wrapper.ne("status", "completed").ne("status", "cancelled");
        }
        // 固定按设备+ID正序，确保同一设备的时间线稳定
        wrapper.orderByAsc("equipment_id").orderByAsc("id");

        List<ScheduleCoating> coatings = coatingMapper.selectList(wrapper);

        // 再次在内存中过滤，确保严格落在所选区间，避免数据库兼容性或格式解析导致的漏判
        if (parsedStartDay != null && parsedEndExclusive != null && coatings != null) {
            final Date filterStart = parsedStartDay;
            final Date filterEndExclusive = parsedEndExclusive;
            final String finalParsedStartStr = parsedStartStr;
            final String finalParsedEndStr = parsedEndStr;
            coatings.removeIf(coating -> {
                Date ps = coating.getPlanStartTime();
                Date pe = coating.getPlanEndTime();
                Date pd = coating.getPlanDate();

                boolean overlapByTime = false;
                if (ps != null && pe != null) {
                    // 区间重叠判定：[ps, pe) 与 [filterStart, filterEndExclusive) 重叠
                    overlapByTime = ps.before(filterEndExclusive) && pe.after(filterStart);
                } else if (ps != null) {
                    overlapByTime = ps.after(filterStart) && ps.before(filterEndExclusive);
                } else if (pe != null) {
                    overlapByTime = pe.after(filterStart) && pe.before(filterEndExclusive);
                }

                boolean matchByDate = false;
                if (pd != null && finalParsedStartStr != null && finalParsedEndStr != null) {
                    try {
                        String dateStr = new SimpleDateFormat("yyyy-MM-dd").format(pd);
                        matchByDate = dateStr.compareTo(finalParsedStartStr) >= 0 && dateStr.compareTo(finalParsedEndStr) <= 0;
                    } catch (Exception ignore) { }
                }

                return !(overlapByTime || matchByDate);
            });
        }

        // 若按日期无数据，回退：不带日期条件，返回最新的若干条（按create_time/plan_start_time排序）
        if ((coatings == null || coatings.isEmpty()) && (planDate == null || planDate.isEmpty())) {
            QueryWrapper<ScheduleCoating> fallback = new QueryWrapper<>();
            fallback.orderByAsc("id");
            coatings = coatingMapper.selectList(fallback);
        }

        // 按设备分组后，组内按计划开始时间（空值靠后）再按ID排序，确保每台机台独立时间线且尊重已有时间
        if (coatings == null) {
            coatings = new ArrayList<>();
        }
        Map<Long, List<ScheduleCoating>> grouped = coatings.stream().collect(Collectors.groupingBy(c -> {
            Long eid = c.getEquipmentId();
            return eid != null ? eid : -1L;
        }));
        List<Long> equipmentOrder = new ArrayList<>(grouped.keySet());
        Collections.sort(equipmentOrder);

        List<ScheduleCoating> ordered = new ArrayList<>();
        for (Long eid : equipmentOrder) {
            List<ScheduleCoating> list = grouped.getOrDefault(eid, new ArrayList<>());
            list.sort((a, b) -> {
                Date sa = a.getPlanStartTime();
                Date sb = b.getPlanStartTime();
                if (sa != null && sb != null) {
                    int cmp = sa.compareTo(sb);
                    if (cmp != 0) return cmp;
                } else if (sa != null) {
                    return -1;
                } else if (sb != null) {
                    return 1;
                }
                Long ida = a.getId();
                Long idb = b.getId();
                if (ida != null && idb != null) {
                    return ida.compareTo(idb);
                }
                if (ida == null && idb == null) return 0;
                return ida == null ? 1 : -1;
            });
            ordered.addAll(list);
        }
        coatings = ordered;

        List<Map<String, Object>> result = new ArrayList<>();
        // 为每台设备构造独立时间线：上一单结束 + 准备间隔 = 下一单开始
        final int GAP_MIN = 30; // 默认准备时间 30 分钟
        Map<Long, Date> cursorMap = new HashMap<>();

        // 先按设备分段计算时间并回写数据库，再拼接返回
        for (ScheduleCoating coating : coatings) {
            Long equipId = coating.getEquipmentId();
            if (equipId == null) {
                // 未指定设备的任务仍按“无设备”时间线单独顺推
                equipId = -1L;
            }

            Date cursor = cursorMap.get(equipId);
            if (cursor == null) {
                if (planDate != null && !planDate.isEmpty()) {
                    try {
                        cursor = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").parse(planDate + " 08:00");
                    } catch (Exception e) {
                        cursor = todayAtEight();
                    }
                } else {
                    cursor = coating.getPlanStartTime() != null ? coating.getPlanStartTime() : todayAtEight();
                }
            }

            // 估算时长（分钟）优先使用 planDuration，否则按面积/速度/宽度估算
            Integer durationMinutes = coating.getPlanDuration();
            if (durationMinutes == null || durationMinutes <= 0) {
                durationMinutes = estimateDurationMinutes(coating);
            }
            durationMinutes = Math.max(durationMinutes, 10);

            // 按机台游标排时间，若已有开始时间仅用于首条初始化，其余按序推算
            Date start;
            if (!cursorMap.containsKey(equipId) && coating.getPlanStartTime() != null) {
                start = coating.getPlanStartTime();
            } else {
                start = cursor;
            }
            Date end = new Date(start.getTime() + durationMinutes * 60L * 1000L);

            // 回写时间计划并持久化
            coating.setPlanStartTime(start);
            coating.setPlanEndTime(end);
            coating.setPlanDuration(durationMinutes);
            try {
                coating.setPlanDate(new SimpleDateFormat("yyyy-MM-dd").parse(new SimpleDateFormat("yyyy-MM-dd").format(start)));
            } catch (Exception ignore) { }
            coatingMapper.updateById(coating);

            // 涂布速度默认 40 m/min（若未设置）
            java.math.BigDecimal speed = coating.getCoatingSpeed() != null ? coating.getCoatingSpeed() : new java.math.BigDecimal("40");
            // 涂布宽度（mm）：优先母卷宽度，其次膜宽字段
            Integer width = coating.getJumboWidth() != null ? coating.getJumboWidth() : coating.getFilmWidth();

            Map<String, Object> item = new HashMap<>();
            item.put("id", coating.getId()); // 前端调整时间/涂布量需要任务ID
            item.put("taskNo", coating.getTaskNo());
            item.put("materialCode", coating.getMaterialCode());
            item.put("materialName", coating.getMaterialName());
            item.put("coatingWidth", width);
            item.put("coatingQuantity", coating.getPlanSqm());
            item.put("equipmentId", coating.getEquipmentId());
            item.put("equipmentCode", coating.getEquipmentCode());
            item.put("mergedOrderCount", 1);
            item.put("coatingSpeed", speed);
            item.put("orderDuration", durationMinutes);
            item.put("planStartTime", start);
            item.put("planEndTime", end);
            item.put("taskStatus", coating.getStatus());
            result.add(item);

            // 游标顺延：结束时间 + 准备时间
            cursor = new Date(end.getTime() + GAP_MIN * 60L * 1000L);
            cursorMap.put(equipId, cursor);
        }
        return result;
    }
    
    @Override
    public List<Map<String, Object>> getCoatingTimeline(String planDate) {
        // 查询时间轴数据（同queue，按时间组织）
        return getCoatingQueue(planDate);
    }
    
    @Deprecated
    @Override
    public List<Map<String, Object>> getCoatingMergeRecords(String planDate) {
        // 查询涂布任务并返回“合并信息”；若按日期无数据则回退到最新记录
        QueryWrapper<ScheduleCoating> wrapper = new QueryWrapper<>();
        if (planDate != null && !planDate.isEmpty()) {
            wrapper.and(w -> w.likeLeft("plan_start_time", planDate)
                .or().likeLeft("plan_date", planDate)
                .or().likeLeft("create_time", planDate));
        }
        wrapper.orderByDesc("create_time");

        List<ScheduleCoating> coatings = coatingMapper.selectList(wrapper);
        if (coatings == null || coatings.isEmpty()) {
            QueryWrapper<ScheduleCoating> fallback = new QueryWrapper<>();
            fallback.orderByDesc("create_time");
            coatings = coatingMapper.selectList(fallback);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (ScheduleCoating coating : coatings) {
            int orderCount = 1;
            if (coating.getScheduleId() != null) {
                QueryWrapper<ScheduleOrderItem> orderWrapper = new QueryWrapper<>();
                orderWrapper.eq("schedule_id", coating.getScheduleId());
                orderCount = Math.max(1, orderItemMapper.selectCount(orderWrapper).intValue());
            }

            Map<String, Object> record = new HashMap<>();
            record.put("taskNo", coating.getTaskNo());
            record.put("materialCode", coating.getMaterialCode());
            record.put("mergedOrderCount", orderCount);
            record.put("createTime", coating.getCreateTime());
            result.add(record);
        }
        return result;
    }
    
    @Override
    public Map<String, Object> getCoatingStats() {
        Map<String, Object> stats = new HashMap<>();

        // 统计涂布任务状态
        long pending = coatingMapper.selectCount(new QueryWrapper<ScheduleCoating>().eq("status", "pending"));
        long inProgress = coatingMapper.selectCount(new QueryWrapper<ScheduleCoating>().eq("status", "in_progress"));
        long completed = coatingMapper.selectCount(new QueryWrapper<ScheduleCoating>().eq("status", "completed"));
        long overtime = coatingMapper.selectCount(new QueryWrapper<ScheduleCoating>().eq("status", "overtime"));

        stats.put("pending", pending);
        stats.put("inProgress", inProgress);
        stats.put("completed", completed);
        stats.put("overtime", overtime);

        return stats;
    }
    
    @Deprecated
    @Override
    public List<Map<String, Object>> getCoatingMaterialLocks() {
        // 查询当前锁定的薄膜原材料
        List<Map<String, Object>> locks = new ArrayList<>();
        
        // 从 schedule_material_lock 或 coating_material_lock 查询
        // 这里简化处理，返回示例数据结构
        // 实际应查询具体的物料锁定表
        
        return locks;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean adjustCoatingTaskTime(Long taskId, Map<String, Object> data) {
        ScheduleCoating coating = coatingMapper.selectById(taskId);
        if (coating == null) {
            return false;
        }

        int gapMin = 30;
        Object gapObj = data.get("gapMinutes");
        if (gapObj == null) {
            gapObj = data.get("prepareMinutes");
        }
        if (gapObj == null) {
            gapObj = data.get("prepareTime");
        }
        if (gapObj instanceof Number) {
            gapMin = Math.max(((Number) gapObj).intValue(), 0);
        } else if (gapObj instanceof String) {
            try {
                gapMin = Math.max(Integer.parseInt(gapObj.toString()), 0);
            } catch (Exception ignore) { }
        }

        // 更新计划开始时间（支持多种格式）
        if (data.containsKey("planStartTime")) {
            Object startTimeObj = data.get("planStartTime");
            Date parsed = parseDateFlexible(startTimeObj);
            if (parsed != null) {
                coating.setPlanStartTime(parsed);
                try {
                    coating.setPlanDate(new SimpleDateFormat("yyyy-MM-dd").parse(new SimpleDateFormat("yyyy-MM-dd").format(parsed)));
                } catch (Exception ignore) { }
            }
        }

        if (coating.getPlanStartTime() == null) {
            Date fallbackStart;
            if (coating.getPlanDate() != null) {
                fallbackStart = planDateAtEight(new SimpleDateFormat("yyyy-MM-dd").format(coating.getPlanDate()));
            } else {
                fallbackStart = todayAtEight();
            }
            coating.setPlanStartTime(fallbackStart);
            try {
                coating.setPlanDate(new SimpleDateFormat("yyyy-MM-dd").parse(new SimpleDateFormat("yyyy-MM-dd").format(fallbackStart)));
            } catch (Exception ignore) { }
        } else if (coating.getPlanDate() == null) {
            try {
                coating.setPlanDate(new SimpleDateFormat("yyyy-MM-dd").parse(new SimpleDateFormat("yyyy-MM-dd").format(coating.getPlanStartTime())));
            } catch (Exception ignore) { }
        }

        // 重新计算耗时并更新当前任务
        Integer durationMin = Math.max(estimateDurationMinutes(coating), 10);
        Date endTime = new Date(coating.getPlanStartTime().getTime() + durationMin * 60L * 1000L);
        coating.setPlanEndTime(endTime);
        coating.setPlanDuration(durationMin);

        int rows = coatingMapper.updateById(coating);

        // 从当前任务起顺推本机台后续任务（前序保持不变）
        try {
            QueryWrapper<ScheduleCoating> wrapper = new QueryWrapper<>();
            if (coating.getEquipmentId() != null) {
                wrapper.eq("equipment_id", coating.getEquipmentId());
            } else {
                wrapper.isNull("equipment_id");
            }
            if (coating.getPlanDate() != null) {
                wrapper.eq("plan_date", coating.getPlanDate());
            }
            wrapper.orderByAsc("plan_start_time").orderByAsc("id");
            List<ScheduleCoating> tasks = coatingMapper.selectList(wrapper);
            if (tasks == null) {
                tasks = new ArrayList<>();
            }

            boolean containsCurrent = tasks.stream().anyMatch(t -> Objects.equals(t.getId(), coating.getId()));
            if (!containsCurrent) {
                tasks.add(coating);
            }

            // 按计划开始时间（空值靠后）+ ID 排序
            tasks.sort((a, b) -> {
                Date sa = a.getPlanStartTime();
                Date sb = b.getPlanStartTime();
                if (sa != null && sb != null) {
                    int cmp = sa.compareTo(sb);
                    if (cmp != 0) return cmp;
                } else if (sa != null) {
                    return -1;
                } else if (sb != null) {
                    return 1;
                }
                Long ida = a.getId();
                Long idb = b.getId();
                if (ida != null && idb != null) {
                    return ida.compareTo(idb);
                }
                if (ida == null && idb == null) return 0;
                return ida == null ? 1 : -1;
            });

            // 定位锚点任务
            int anchorIdx = -1;
            for (int i = 0; i < tasks.size(); i++) {
                if (Objects.equals(tasks.get(i).getId(), coating.getId())) {
                    anchorIdx = i;
                    break;
                }
            }

            if (anchorIdx < 0) {
                // 找不到锚点则全量顺推
                Date firstStart = tasks.stream()
                        .map(ScheduleCoating::getPlanStartTime)
                        .filter(Objects::nonNull)
                        .sorted(Date::compareTo)
                        .findFirst()
                        .orElse(null);
                Date cursor = firstStart != null ? firstStart : todayAtEight();
                for (ScheduleCoating t : tasks) {
                    int dur = Math.max(estimateDurationMinutes(t), 10);
                    Date start = cursor;
                    Date end = new Date(start.getTime() + dur * 60L * 1000L);
                    t.setPlanStartTime(start);
                    t.setPlanEndTime(end);
                    t.setPlanDuration(dur);
                    try {
                        t.setPlanDate(new SimpleDateFormat("yyyy-MM-dd").parse(new SimpleDateFormat("yyyy-MM-dd").format(start)));
                    } catch (Exception ignore) { }
                    coatingMapper.updateById(t);
                    cursor = new Date(end.getTime() + gapMin * 60L * 1000L);
                }
            } else {
                // 从锚点开始顺推，前序任务保持不变
                Date cursor = null;
                if (anchorIdx > 0) {
                    ScheduleCoating prev = tasks.get(anchorIdx - 1);
                    Date prevEnd = prev.getPlanEndTime();
                    if (prevEnd == null) {
                        int d = Math.max(estimateDurationMinutes(prev), 10);
                        Date prevStart = prev.getPlanStartTime() != null ? prev.getPlanStartTime() : todayAtEight();
                        prevEnd = new Date(prevStart.getTime() + d * 60L * 1000L);
                    }
                    cursor = new Date(prevEnd.getTime() + gapMin * 60L * 1000L);
                }

                for (int i = anchorIdx; i < tasks.size(); i++) {
                    ScheduleCoating t = tasks.get(i);
                    int dur = Math.max(estimateDurationMinutes(t), 10);

                    Date start;
                    if (i == anchorIdx) {
                        start = cursor != null ? cursor : (t.getPlanStartTime() != null ? t.getPlanStartTime() : todayAtEight());
                    } else {
                        start = cursor != null ? cursor : todayAtEight();
                    }

                    Date end = new Date(start.getTime() + dur * 60L * 1000L);
                    t.setPlanStartTime(start);
                    t.setPlanEndTime(end);
                    t.setPlanDuration(dur);
                    try {
                        t.setPlanDate(new SimpleDateFormat("yyyy-MM-dd").parse(new SimpleDateFormat("yyyy-MM-dd").format(start)));
                    } catch (Exception ignore) { }
                    coatingMapper.updateById(t);

                    cursor = new Date(end.getTime() + gapMin * 60L * 1000L);
                }
            }
        } catch (Exception ignore) { }

        return rows > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean adjustCoatingTaskQuantity(Long taskId, Map<String, Object> data) {
        ScheduleCoating coating = coatingMapper.selectById(taskId);
        if (coating == null) {
            return false;
        }

        // 支持多种字段名：planSqm 或 coatingQuantity
        Object qtyObj = data.get("planSqm");
        if (qtyObj == null) {
            qtyObj = data.get("coatingQuantity");
        }

        java.math.BigDecimal newSqm = null;
        if (qtyObj instanceof java.math.BigDecimal) {
            newSqm = (java.math.BigDecimal) qtyObj;
        } else if (qtyObj instanceof Number) {
            newSqm = java.math.BigDecimal.valueOf(((Number) qtyObj).doubleValue());
        } else if (qtyObj instanceof String) {
            try {
                newSqm = new java.math.BigDecimal(qtyObj.toString());
            } catch (Exception ignore) { }
        }

        if (newSqm == null || newSqm.compareTo(java.math.BigDecimal.ZERO) <= 0) {
            return false;
        }

        // 更新计划面积并重算耗时与结束时间
        coating.setPlanSqm(newSqm);
        int durationMin = Math.max(estimateDurationMinutes(coating), 10);
        Date startTime = coating.getPlanStartTime() != null ? coating.getPlanStartTime() : todayAtEight();
        Date endTime = new Date(startTime.getTime() + durationMin * 60L * 1000L);
        coating.setPlanEndTime(endTime);
        coating.setPlanDuration(durationMin);

        int rows = coatingMapper.updateById(coating);
        return rows > 0;
    }

    @Override
    public boolean updateCoatingEquipment(Long taskId, Long equipmentId) {
        if (taskId == null || equipmentId == null) {
            return false;
        }
        ScheduleCoating coating = coatingMapper.selectById(taskId);
        if (coating == null) {
            return false;
        }
        Equipment equipment = equipmentMapper.selectById(equipmentId);
        if (equipment == null) {
            return false;
        }
        coating.setEquipmentId(equipment.getId());
        coating.setEquipmentCode(equipment.getEquipmentCode());
        return coatingMapper.updateById(coating) > 0;
    }

    @Override
    public boolean updateRewindingEquipment(Long taskId, Long equipmentId) {
        if (taskId == null || equipmentId == null) {
            return false;
        }
        ScheduleRewinding rewinding = rewindingMapper.selectById(taskId);
        if (rewinding == null) {
            return false;
        }
        Equipment equipment = equipmentMapper.selectById(equipmentId);
        if (equipment == null) {
            return false;
        }

        rewinding.setEquipmentId(equipment.getId());
        rewinding.setEquipmentCode(equipment.getEquipmentCode());
        int rows = rewindingMapper.updateById(rewinding);

        // 重新计算该设备的排程：从当前任务起顺推，前序不动
        try {
            if (rewinding.getId() != null) {
                recalcRewindingPlanFromTask(equipment.getId(), rewinding.getId(), 10);
            } else {
                recalcRewindingPlanForEquipment(null, equipment.getId(), 10);
            }
        } catch (Exception ignore) { }

        return rows > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean adjustRewindingTaskTime(Long taskId, Map<String, Object> data) {
        ScheduleRewinding rewinding = rewindingMapper.selectById(taskId);
        if (rewinding == null) {
            return false;
        }

        int gapMin = 10;
        Object gapObj = data.get("gapMinutes");
        if (gapObj == null) {
            gapObj = data.get("prepareMinutes");
        }
        if (gapObj instanceof Number) {
            gapMin = Math.max(((Number) gapObj).intValue(), 0);
        } else if (gapObj instanceof String) {
            try {
                gapMin = Math.max(Integer.parseInt(gapObj.toString()), 0);
            } catch (Exception ignore) { }
        }

        if (data.containsKey("planStartTime")) {
            Object startTimeObj = data.get("planStartTime");
            Date parsed = parseDateFlexible(startTimeObj);
            if (parsed != null) {
                rewinding.setPlanStartTime(parsed);
                try {
                    rewinding.setPlanDate(new SimpleDateFormat("yyyy-MM-dd").parse(new SimpleDateFormat("yyyy-MM-dd").format(parsed)));
                } catch (Exception ignore) { }
            }
        }

        if (rewinding.getPlanStartTime() == null) {
            Date fallbackStart;
            if (rewinding.getPlanDate() != null) {
                fallbackStart = planDateAtEight(new SimpleDateFormat("yyyy-MM-dd").format(rewinding.getPlanDate()));
            } else {
                fallbackStart = todayAtEight();
            }
            rewinding.setPlanStartTime(fallbackStart);
            try {
                rewinding.setPlanDate(new SimpleDateFormat("yyyy-MM-dd").parse(new SimpleDateFormat("yyyy-MM-dd").format(fallbackStart)));
            } catch (Exception ignore) { }
        } else if (rewinding.getPlanDate() == null) {
            try {
                rewinding.setPlanDate(new SimpleDateFormat("yyyy-MM-dd").parse(new SimpleDateFormat("yyyy-MM-dd").format(rewinding.getPlanStartTime())));
            } catch (Exception ignore) { }
        }

        int durationMin = Math.max(estimateRewindingDurationMinutes(rewinding), 1);
        Date endTime = new Date(rewinding.getPlanStartTime().getTime() + durationMin * 60L * 1000L);
        rewinding.setPlanEndTime(endTime);
        rewinding.setPlanDuration(durationMin);
        rewindingMapper.updateById(rewinding);

        // 从当前任务起顺推本机台后续任务（前序保持不变）
        try {
            recalcRewindingPlanFromTask(rewinding.getEquipmentId(), rewinding.getId(), gapMin);
        } catch (Exception ignore) { }

        return true;
    }

    private void recalcRewindingPlanForEquipment(String planDate, Long equipmentId, Integer gapMinutes) {
        if (equipmentId == null) {
            return;
        }
        int gapMin = gapMinutes != null ? gapMinutes : 10;
        Date freezeUntil = new Date();

        QueryWrapper<ScheduleRewinding> wrapper = new QueryWrapper<>();
        wrapper.eq("equipment_id", equipmentId);
        wrapper.orderByAsc("plan_start_time").orderByAsc("id");
        List<ScheduleRewinding> tasks = rewindingMapper.selectList(wrapper);
        if (tasks == null || tasks.isEmpty()) {
            // 设备ID可能未写入，回退按设备编号
            QueryWrapper<ScheduleRewinding> fallback = new QueryWrapper<>();
            if (rewindingMapper != null) { // safe
                // 获取该设备编号
                Equipment eq = equipmentMapper.selectById(equipmentId);
                if (eq != null && eq.getEquipmentCode() != null) {
                    fallback.eq("equipment_code", eq.getEquipmentCode());
                    fallback.orderByAsc("plan_start_time").orderByAsc("id");
                    tasks = rewindingMapper.selectList(fallback);
                    if (tasks == null || tasks.isEmpty()) {
                        return;
                    }
                } else {
                    return;
                }
            } else {
                return;
            }
        }

        tasks.sort(this::compareRewindingOrder);

        Date maxFrozenEnd = null;
        for (ScheduleRewinding t : tasks) {
            if (isFrozenTask(t.getPlanStartTime(), t.getStatus(), freezeUntil)) {
                Date end = t.getPlanEndTime();
                if (end == null && t.getPlanStartTime() != null) {
                    int d = estimateRewindingDurationMinutes(t);
                    end = new Date(t.getPlanStartTime().getTime() + d * 60L * 1000L);
                }
                if (end != null && (maxFrozenEnd == null || end.after(maxFrozenEnd))) {
                    maxFrozenEnd = end;
                }
            }
        }
        Date firstStart = tasks.stream()
                .map(ScheduleRewinding::getPlanStartTime)
                .filter(Objects::nonNull)
                .sorted(Date::compareTo)
                .findFirst()
                .orElse(null);
        Date cursor = maxFrozenEnd != null
                ? new Date(maxFrozenEnd.getTime() + gapMin * 60L * 1000L)
                : (firstStart != null ? firstStart : todayAtEight());
        for (ScheduleRewinding t : tasks) {
            if (isFrozenTask(t.getPlanStartTime(), t.getStatus(), freezeUntil)) {
                continue;
            }
            int duration = estimateRewindingDurationMinutes(t);
            Date start = cursor;
            Date end = new Date(start.getTime() + duration * 60L * 1000L);
            t.setPlanStartTime(start);
            t.setPlanEndTime(end);
            t.setPlanDuration(duration);
            try {
                t.setPlanDate(new SimpleDateFormat("yyyy-MM-dd").parse(new SimpleDateFormat("yyyy-MM-dd").format(start)));
            } catch (Exception ignore) { }
            rewindingMapper.updateById(t);

            cursor = new Date(end.getTime() + gapMin * 60L * 1000L);
        }
    }

    /**
     * 从指定任务开始，顺推本机台后续任务，前序任务保持原有时间。
     */
    private void recalcRewindingPlanFromTask(Long equipmentId, Long anchorTaskId, Integer gapMinutes) {
        if (equipmentId == null || anchorTaskId == null) {
            return;
        }
        int gapMin = gapMinutes != null ? gapMinutes : 10;
        Date freezeUntil = new Date();

        QueryWrapper<ScheduleRewinding> wrapper = new QueryWrapper<>();
        wrapper.eq("equipment_id", equipmentId);
        wrapper.orderByAsc("plan_start_time").orderByAsc("id");
        List<ScheduleRewinding> tasks = rewindingMapper.selectList(wrapper);
        if (tasks == null || tasks.isEmpty()) {
            return;
        }

        tasks.sort(this::compareRewindingOrder);

        int anchorIdx = -1;
        for (int i = 0; i < tasks.size(); i++) {
            if (Objects.equals(tasks.get(i).getId(), anchorTaskId)) {
                anchorIdx = i;
                break;
            }
        }
        if (anchorIdx < 0) {
            // 找不到则全量重排
            recalcRewindingPlanForEquipment(null, equipmentId, gapMin);
            return;
        }

        ScheduleRewinding anchor = tasks.get(anchorIdx);
        if (isFrozenTask(anchor.getPlanStartTime(), anchor.getStatus(), freezeUntil)) {
            return;
        }

        // 光标从前一任务结束+gap开始；若无前一任务，则用锚点原始开始或08:00
        Date cursor = null;
        if (anchorIdx > 0) {
            ScheduleRewinding prev = tasks.get(anchorIdx - 1);
            Date prevEnd = prev.getPlanEndTime();
            if (prevEnd == null) {
                int d = estimateRewindingDurationMinutes(prev);
                Date prevStart = prev.getPlanStartTime() != null ? prev.getPlanStartTime() : todayAtEight();
                prevEnd = new Date(prevStart.getTime() + d * 60L * 1000L);
            }
            cursor = new Date(prevEnd.getTime() + gapMin * 60L * 1000L);
        }

        for (int i = anchorIdx; i < tasks.size(); i++) {
            ScheduleRewinding t = tasks.get(i);
            if (isFrozenTask(t.getPlanStartTime(), t.getStatus(), freezeUntil)) {
                Date end = t.getPlanEndTime();
                if (end == null && t.getPlanStartTime() != null) {
                    int d = estimateRewindingDurationMinutes(t);
                    end = new Date(t.getPlanStartTime().getTime() + d * 60L * 1000L);
                }
                if (end != null) {
                    cursor = new Date(end.getTime() + gapMin * 60L * 1000L);
                }
                continue;
            }
            int duration = estimateRewindingDurationMinutes(t);

            Date start;
            if (i == anchorIdx) {
                // 锚点任务必须对齐到前序结束+gap；无前序则用自身开始或08:00
                start = cursor != null ? cursor : (t.getPlanStartTime() != null ? t.getPlanStartTime() : todayAtEight());
            } else {
                start = cursor != null ? cursor : todayAtEight();
            }

            Date end = new Date(start.getTime() + duration * 60L * 1000L);
            t.setPlanStartTime(start);
            t.setPlanEndTime(end);
            t.setPlanDuration(duration);
            try {
                t.setPlanDate(new SimpleDateFormat("yyyy-MM-dd").parse(new SimpleDateFormat("yyyy-MM-dd").format(start)));
            } catch (Exception ignore) { }
            rewindingMapper.updateById(t);

            cursor = new Date(end.getTime() + gapMin * 60L * 1000L);
        }
    }

    /**
     * 复卷排序：优先按任务单号后缀数字（RW-YYYYMMDD-XXX）升序，其次按开始时间，其次ID。
     */
    private int compareRewindingOrder(ScheduleRewinding a, ScheduleRewinding b) {
        String ta = a.getTaskNo();
        String tb = b.getTaskNo();
        Integer na = parseTaskSuffix(ta);
        Integer nb = parseTaskSuffix(tb);
        if (na != null && nb != null && !na.equals(nb)) {
            return Integer.compare(na, nb);
        }
        Date sa = a.getPlanStartTime();
        Date sb = b.getPlanStartTime();
        if (sa != null && sb != null) {
            int cmp = sa.compareTo(sb);
            if (cmp != 0) return cmp;
        } else if (sa != null) {
            return -1;
        } else if (sb != null) {
            return 1;
        }
        return Long.compare(a.getId() != null ? a.getId() : 0L, b.getId() != null ? b.getId() : 0L);
    }

    private boolean shouldRecalcRewinding(List<ScheduleRewinding> records) {
        if (records == null || records.size() < 2) {
            return false;
        }
        Map<Long, List<ScheduleRewinding>> byEq = new HashMap<>();
        for (ScheduleRewinding r : records) {
            if (r == null || r.getEquipmentId() == null) {
                continue;
            }
            byEq.computeIfAbsent(r.getEquipmentId(), k -> new ArrayList<>()).add(r);
        }
        for (List<ScheduleRewinding> list : byEq.values()) {
            if (list.size() < 2) {
                continue;
            }
            list.sort((a, b) -> {
                Date sa = a.getPlanStartTime();
                Date sb = b.getPlanStartTime();
                if (sa == null && sb == null) return compareRewindingOrder(a, b);
                if (sa == null) return 1;
                if (sb == null) return -1;
                int cmp = sa.compareTo(sb);
                return cmp != 0 ? cmp : compareRewindingOrder(a, b);
            });
            Date prevEnd = null;
            for (ScheduleRewinding t : list) {
                Date start = t.getPlanStartTime();
                if (start == null) {
                    return true;
                }
                if (prevEnd != null && !start.after(prevEnd)) {
                    return true;
                }
                Date end = t.getPlanEndTime();
                if (end == null) {
                    int d = estimateRewindingDurationMinutes(t);
                    end = new Date(start.getTime() + d * 60L * 1000L);
                }
                prevEnd = end;
            }
        }
        return false;
    }

    private void recalcRewindingFromRecords(List<ScheduleRewinding> records, String planDate) {
        if (records == null || records.isEmpty()) {
            return;
        }
        Set<Long> equipmentIds = new HashSet<>();
        for (ScheduleRewinding r : records) {
            if (r != null && r.getEquipmentId() != null) {
                equipmentIds.add(r.getEquipmentId());
            }
        }
        for (Long eqId : equipmentIds) {
            try {
                recalcRewindingPlanForEquipment(planDate, eqId, 10);
            } catch (Exception ignore) { }
        }
    }

    private Integer parseTaskSuffix(String taskNo) {
        if (taskNo == null) return null;
        int idx = taskNo.lastIndexOf('-');
        if (idx >= 0 && idx + 1 < taskNo.length()) {
            try {
                return Integer.parseInt(taskNo.substring(idx + 1));
            } catch (Exception ignore) { }
        }
        return null;
    }

    private Date parseDateFlexible(Object val) {
        if (val == null) {
            return null;
        }
        if (val instanceof Date) {
            return (Date) val;
        }
        if (val instanceof String) {
            String s = val.toString();
            String[] patterns = new String[] {
                    "yyyy-MM-dd HH:mm:ss",
                    "yyyy-MM-dd'T'HH:mm:ss",
                    "yyyy-MM-dd'T'HH:mm:ss.SSSX",
                    "yyyy-MM-dd"
            };
            for (String p : patterns) {
                try {
                    return new SimpleDateFormat(p).parse(s);
                } catch (Exception ignore) { }
            }
        }
        return null;
    }

    private int estimateRewindingDurationMinutes(ScheduleRewinding rewinding) {
        int rolls = rewinding.getPlanRolls() != null ? rewinding.getPlanRolls() : 0;
        int len = rewinding.getSlitLength() != null ? rewinding.getSlitLength() : 0;
        int speed = rewinding.getRewindingSpeed() != null ? rewinding.getRewindingSpeed().intValue() : DEFAULT_REWIND_SPEED;
        return computeRewindingDurationMinutes(rolls, len, speed);
    }

    /**
     * 若已有开始/结束时间为空，则用计划日08:00或今天08:00兜底。
     */
    private Date normalizeStart(ScheduleRewinding t) {
        if (t == null) return todayAtEight();
        if (t.getPlanStartTime() != null) return t.getPlanStartTime();
        if (t.getPlanDate() != null) {
            return planDateAtEight(new SimpleDateFormat("yyyy-MM-dd").format(t.getPlanDate()));
        }
        return todayAtEight();
    }

    private Date normalizeEnd(ScheduleRewinding t, Date start) {
        if (t == null) return null;
        if (t.getPlanEndTime() != null) return t.getPlanEndTime();
        Date baseStart = start != null ? start : normalizeStart(t);
        int d = Math.max(estimateRewindingDurationMinutes(t), 1);
        return new Date(baseStart.getTime() + d * 60L * 1000L);
    }

    private int computeRewindingDurationMinutes(int rolls, int lengthMeters, int speedMetersPerMin) {
        int effectiveSpeed = speedMetersPerMin > 0 ? speedMetersPerMin : DEFAULT_REWIND_SPEED;
        // 加上每卷换卷时间（默认2分钟）
        int changeover = rolls * CHANGEOVER_MINUTES_PER_ROLL;
        double process = rolls * (lengthMeters / (double) effectiveSpeed);
        int duration = (int) Math.ceil(process + changeover);
        return Math.max(duration, 1);
    }

    private void hydrateRewindingDefaults(ScheduleRewinding r) {
        if (r == null) {
            return;
        }

        // 兼容旧数据：把 remark 中的“订单:”解析为 orderNos
        if ((r.getOrderNos() == null || r.getOrderNos().isEmpty()) && r.getRemark() != null && r.getRemark().startsWith("订单:")) {
            String payload = r.getRemark().substring("订单:".length());
            String[] arr = payload.split(",");
            List<String> list = new ArrayList<>();
            for (String s : arr) {
                if (s != null && !s.trim().isEmpty()) {
                    list.add(s.trim());
                }
            }
            r.setOrderNos(list);
        }

        // 解析持久化字段 order_nos（逗号分隔）到前端使用的 orderNos 列表
        if ((r.getOrderNos() == null || r.getOrderNos().isEmpty()) && r.getOrderNosText() != null && !r.getOrderNosText().isEmpty()) {
            String[] arr = r.getOrderNosText().split(",");
            List<String> list = new ArrayList<>();
            for (String s : arr) {
                if (s != null && !s.trim().isEmpty()) {
                    list.add(s.trim());
                }
            }
            r.setOrderNos(list);
        }

        // 设置前端兼容字段
        r.setWidth(r.getJumboWidth());
        r.setLength(r.getSlitLength());
        r.setQuantity(r.getPlanRolls());

        // 解析入库的 order_nos
        if ((r.getOrderNos() == null || r.getOrderNos().isEmpty()) && r.getOrderNosText() != null && !r.getOrderNosText().isEmpty()) {
            String[] arr = r.getOrderNosText().split(",");
            List<String> list = new ArrayList<>();
            for (String s : arr) {
                if (s != null && !s.trim().isEmpty()) {
                    list.add(s.trim());
                }
            }
            r.setOrderNos(list);
        }

        // 如计划时间缺失，按计划日期 08:00 起排，基于估算时长补全
        if (r.getPlanDate() != null && (r.getPlanStartTime() == null || r.getPlanEndTime() == null)) {
            Date base = planDateAtEight(new SimpleDateFormat("yyyy-MM-dd").format(r.getPlanDate()));
            int duration = r.getPlanDuration() != null ? r.getPlanDuration() : estimateRewindingDurationMinutes(r);
            if (duration <= 0) {
                duration = 10;
            }
            r.setPlanStartTime(base);
            r.setPlanEndTime(new Date(base.getTime() + duration * 60L * 1000L));
            r.setPlanDuration(duration);
        }

        // 如长度/卷数未填，尽量从母卷数据兜底
        if (r.getSlitLength() == null && r.getJumboLength() != null) {
            r.setSlitLength(r.getJumboLength().intValue());
            r.setLength(r.getSlitLength());
        }
        if (r.getPlanRolls() == null && r.getActualRolls() != null) {
            r.setPlanRolls(r.getActualRolls());
            r.setQuantity(r.getActualRolls());
        }
    }

    private Date planDateAtEight(String planDate) {
        try {
            Date base = new SimpleDateFormat("yyyy-MM-dd").parse(planDate);
            Calendar cal = Calendar.getInstance();
            cal.setTime(base);
            cal.set(Calendar.HOUR_OF_DAY, 8);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            return cal.getTime();
        } catch (Exception e) {
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, 8);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            return cal.getTime();
        }
    }
    
    @Override
    public Map<String, Object> getCoatingTaskDetail(Long taskId) {
        ScheduleCoating coating = coatingMapper.selectById(taskId);
        if (coating == null) {
            return new HashMap<>();
        }
        
        Map<String, Object> detail = new HashMap<>();
        detail.put("taskNo", coating.getTaskNo());
        detail.put("materialCode", coating.getMaterialCode());
        detail.put("materialName", coating.getMaterialName());
        detail.put("coatingQuantity", coating.getPlanSqm());
        detail.put("planStartTime", coating.getPlanStartTime());
        detail.put("planEndTime", coating.getPlanEndTime());
        detail.put("taskStatus", coating.getStatus());
        detail.put("estimatedDuration", 
            coating.getPlanDuration() != null ? coating.getPlanDuration() / 60.0 : 0);
        
        // 查询关联订单
        QueryWrapper<ScheduleOrderItem> wrapper = new QueryWrapper<>();
        wrapper.eq("schedule_id", coating.getScheduleId());
        List<ScheduleOrderItem> items = orderItemMapper.selectList(wrapper);
        
        List<Map<String, Object>> relatedOrders = new ArrayList<>();
        for (ScheduleOrderItem item : items) {
            Map<String, Object> order = new HashMap<>();
            order.put("orderNo", item.getOrderNo());
            order.put("customerName", item.getCustomer());
            order.put("shortageQuantity", item.getScheduleQty());
            order.put("customerPriority", item.getPriority());
            relatedOrders.add(order);
        }
        detail.put("relatedOrders", relatedOrders);
        detail.put("mergedOrderCount", relatedOrders.size());
        
        return detail;
    }

    @Override
    public boolean recalculateCoatingPlan(String planDate, Integer gapMinutes) {
        int gap = (gapMinutes == null || gapMinutes < 0) ? 10 : gapMinutes;
        Date freezeUntil = new Date(System.currentTimeMillis() + 48L * 60L * 60L * 1000L);

        QueryWrapper<ScheduleCoating> wrapper = new QueryWrapper<>();
        if (StringUtils.hasText(planDate)) {
            wrapper.and(w -> w.likeLeft("plan_start_time", planDate)
                .or().likeLeft("plan_date", planDate)
                .or().likeLeft("create_time", planDate));
        }
        wrapper.orderByAsc("plan_start_time").orderByAsc("create_time");

        List<ScheduleCoating> tasks = coatingMapper.selectList(wrapper);
        if (tasks == null || tasks.isEmpty()) {
            return false;
        }

        // 起点时间：指定日期则从当日08:00开始；否则优先首条计划开始时间，否则今日08:00
        Date cursor;
        Date maxFrozenEnd = null;
        for (ScheduleCoating t : tasks) {
            if (isFrozenTask(t.getPlanStartTime(), t.getStatus(), freezeUntil)) {
                Date end = t.getPlanEndTime();
                if (end == null && t.getPlanStartTime() != null) {
                    int d = Math.max(estimateDurationMinutes(t), 10);
                    end = new Date(t.getPlanStartTime().getTime() + d * 60L * 1000L);
                }
                if (end != null && (maxFrozenEnd == null || end.after(maxFrozenEnd))) {
                    maxFrozenEnd = end;
                }
            }
        }
        if (maxFrozenEnd != null) {
            cursor = new Date(maxFrozenEnd.getTime() + gap * 60L * 1000L);
        } else if (StringUtils.hasText(planDate)) {
            try {
                cursor = new SimpleDateFormat("yyyy-MM-dd HH:mm").parse(planDate + " 08:00");
            } catch (Exception e) {
                cursor = todayAtEight();
            }
        } else {
            if (tasks.get(0).getPlanStartTime() != null) {
                cursor = tasks.get(0).getPlanStartTime();
            } else {
                cursor = todayAtEight();
            }
        }

        for (ScheduleCoating task : tasks) {
            if (isFrozenTask(task.getPlanStartTime(), task.getStatus(), freezeUntil)) {
                continue;
            }
            // 始终按面积/速度/宽度重算时长，避免历史异常值放大计划时间
            int durationMin = Math.max(estimateDurationMinutes(task), 10);

            task.setPlanStartTime(cursor);
            Date endTime = new Date(cursor.getTime() + durationMin * 60L * 1000L);
            task.setPlanEndTime(endTime);
            task.setPlanDuration(durationMin);

            coatingMapper.updateById(task);

            cursor = new Date(endTime.getTime() + gap * 60L * 1000L);
        }

        return true;
    }

    private boolean isFrozenTask(Date planStart, String status, Date freezeUntil) {
        if ("in_progress".equalsIgnoreCase(status) || "completed".equalsIgnoreCase(status)) {
            return true;
        }
        if (planStart == null || freezeUntil == null) {
            return false;
        }
        return !planStart.after(freezeUntil);
    }

    private Date todayAtEight() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 8);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTime();
    }

    private int estimateDurationMinutes(ScheduleCoating coating) {
        BigDecimal area = coating.getPlanSqm() != null ? coating.getPlanSqm() : BigDecimal.ZERO;
        BigDecimal speed = coating.getCoatingSpeed() != null ? coating.getCoatingSpeed() : new BigDecimal(40);
        int width = coating.getJumboWidth() != null ? coating.getJumboWidth()
                : (coating.getFilmWidth() != null ? coating.getFilmWidth() : 1000);
        double widthM = width > 0 ? width / 1000.0 : 1.0;
        double denom = speed.doubleValue() * widthM;
        if (denom <= 0) {
            return 10;
        }
        int est = (int) Math.ceil(area.doubleValue() / denom);
        return Math.max(est, 10);
    }
    
    // ========== 待涂布订单池接口实现 ==========
    
    @Override
    public Map<String, Object> getPendingCoatingPool(Map<String, Object> params) {
        int pageNum = params.get("pageNum") == null ? 1 : Integer.parseInt(params.get("pageNum").toString());
        int pageSize = params.get("pageSize") == null ? 20 : Integer.parseInt(params.get("pageSize").toString());

        QueryWrapper<com.fine.model.schedule.PendingCoatingOrderPool> wrapper = new QueryWrapper<>();
        if (params.get("materialCode") != null && !params.get("materialCode").toString().isEmpty()) {
            wrapper.eq("material_code", params.get("materialCode"));
        }
        if (params.get("orderNo") != null && !params.get("orderNo").toString().isEmpty()) {
            wrapper.like("order_no", params.get("orderNo"));
        }
        if (params.get("poolStatus") != null && !params.get("poolStatus").toString().isEmpty()) {
            wrapper.eq("pool_status", params.get("poolStatus"));
        }

        wrapper.orderByAsc("added_at");

        Page<com.fine.model.schedule.PendingCoatingOrderPool> page = new Page<>(pageNum, pageSize);
        IPage<com.fine.model.schedule.PendingCoatingOrderPool> pageResult = pendingCoatingPoolMapper.selectPage(page, wrapper);

        List<Map<String, Object>> records = new ArrayList<>();
        for (com.fine.model.schedule.PendingCoatingOrderPool item : pageResult.getRecords()) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", item.getId());
            map.put("orderNo", item.getOrderNo());
            map.put("customerName", item.getCustomerName());
            map.put("materialCode", item.getMaterialCode());
            map.put("materialName", item.getMaterialName());
            map.put("shortageQuantity", item.getShortageQty());
            map.put("customerPriority", item.getCustomerPriority());
            map.put("addedAt", item.getAddedAt());
            map.put("poolStatus", item.getPoolStatus());
            records.add(map);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("list", records);
        result.put("total", pageResult.getTotal());
        return result;
    }
    
    @Override
    public List<Map<String, Object>> getPendingCoatingByMaterial() {
        // 仅从 WAITING 订单直接聚合，避免“料号数”与分组列表不一致
        List<com.fine.model.schedule.PendingCoatingOrderPool> waitingItems = pendingCoatingPoolMapper.selectList(
            new QueryWrapper<com.fine.model.schedule.PendingCoatingOrderPool>().eq("pool_status", "WAITING"));

        Map<String, Map<String, Object>> grouped = new java.util.LinkedHashMap<>();

        for (com.fine.model.schedule.PendingCoatingOrderPool item : waitingItems) {
            Map<String, Object> group = grouped.computeIfAbsent(item.getMaterialCode(), k -> {
                Map<String, Object> g = new HashMap<>();
                g.put("materialCode", item.getMaterialCode());
                g.put("materialName", item.getMaterialName());
                g.put("orderCount", 0);
                g.put("totalShortage", 0);
                g.put("suggestedQuantity", 0);
                g.put("moq", 0);
                g.put("orders", new ArrayList<Map<String, Object>>());
                return g;
            });

            group.put("orderCount", ((Integer) group.get("orderCount")) + 1);
            int shortageQty = item.getShortageQty() == null ? 0 : item.getShortageQty();
            group.put("totalShortage", ((Integer) group.get("totalShortage")) + shortageQty);
            group.put("suggestedQuantity", group.get("totalShortage"));

            Map<String, Object> orderMap = new HashMap<>();
            orderMap.put("id", item.getId());
            orderMap.put("orderItemId", item.getOrderItemId());
            orderMap.put("orderNo", item.getOrderNo());
            orderMap.put("customerName", item.getCustomerName());
            orderMap.put("shortageQuantity", shortageQty);
            orderMap.put("customerPriority", item.getCustomerPriority());
            orderMap.put("addedAt", item.getAddedAt());
            orderMap.put("poolStatus", item.getPoolStatus());
            ((List<Map<String, Object>>) group.get("orders")).add(orderMap);
        }

        // 过滤掉缺口为 0 或负值的料号组（不需要涂布的料号不应出现在涂布汇总）
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> g : grouped.values()) {
            Object ts = g.get("totalShortage");
            int totalShortage = 0;
            if (ts instanceof Number) {
                totalShortage = ((Number) ts).intValue();
            } else if (ts instanceof String) {
                try { totalShortage = Integer.parseInt((String) ts); } catch (Exception ignore) { }
            }
            if (totalShortage > 0) {
                result.add(g);
            }
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> getRewindSummary() {
        // 使用 mapper 的联表聚合查询，来源：pending_coating_order_pool JOIN sales_order_items
        List<Map<String, Object>> rows = pendingCoatingPoolMapper.selectRewindSummary();
        if (rows == null) return new ArrayList<>();

        // 将拼接字段解析成数组/数值，兼容前端期望字段名
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> m = new HashMap<>();
            m.put("materialCode", r.get("materialCode"));
            m.put("materialName", r.get("materialName"));
            // length 可能是 BigDecimal/Number
            Object lenObj = r.get("length");
            int len = 0;
            if (lenObj instanceof Number) len = ((Number) lenObj).intValue();
            else if (lenObj != null) {
                try { len = Integer.parseInt(lenObj.toString()); } catch (Exception ignore) {}
            }
            m.put("length", len == 0 ? null : len);

            // width
            Object widObj = r.get("width");
            int width = 0;
            if (widObj instanceof Number) width = ((Number) widObj).intValue();
            else if (widObj != null) {
                try { width = Integer.parseInt(widObj.toString()); } catch (Exception ignore) {}
            }
            m.put("width", width == 0 ? null : width);

            // thickness
            Object thObj = r.get("thickness");
            int thickness = 0;
            if (thObj instanceof Number) thickness = ((Number) thObj).intValue();
            else if (thObj != null) {
                try { thickness = Integer.parseInt(thObj.toString()); } catch (Exception ignore) {}
            }
            m.put("thickness", thickness == 0 ? null : thickness);

            // rolls
            Object rollsObj = r.get("rolls");
            int rolls = 0;
            if (rollsObj instanceof Number) rolls = ((Number) rollsObj).intValue();
            else if (rollsObj != null) {
                try { rolls = Integer.parseInt(rollsObj.toString()); } catch (Exception ignore) {}
            }
            m.put("rolls", rolls == 0 ? null : rolls);

            // totalArea 可能为 BigDecimal/Number
            Object ta = r.get("totalArea");
            double totalArea = 0.0;
            if (ta instanceof Number) totalArea = ((Number) ta).doubleValue();
            else if (ta != null) {
                try { totalArea = Double.parseDouble(ta.toString()); } catch (Exception ignore) {}
            }
            m.put("totalArea", totalArea);

            m.put("defaultWidth", r.getOrDefault("defaultWidth", 500));

            Object ts = r.get("totalShortage");
            int totalShortage = 0;
            if (ts instanceof Number) totalShortage = ((Number) ts).intValue();
            else if (ts != null) { try { totalShortage = Integer.parseInt(ts.toString()); } catch (Exception ignore) {} }
            m.put("totalShortage", totalShortage);

            m.put("orderCount", r.getOrDefault("orderCount", 0));

            // orderNosConcat -> orderNos array
            Object on = r.get("orderNosConcat");
            List<String> orderNos = new ArrayList<>();
            if (on != null) {
                String s = on.toString();
                for (String t : s.split(",")) { if (!t.trim().isEmpty()) orderNos.add(t.trim()); }
            }
            m.put("orderNos", orderNos);

            // poolIdsConcat -> poolIds array
            Object pid = r.get("poolIdsConcat");
            List<Long> poolIds = new ArrayList<>();
            if (pid != null) {
                String s = pid.toString();
                for (String t : s.split(",")) {
                    try { poolIds.add(Long.valueOf(t.trim())); } catch (Exception ignore) {}
                }
            }
            m.put("poolIds", poolIds);

            out.add(m);
        }
        return out;
    }
    
    @Override
    public void addToPendingCoatingPool(Map<String, Object> data) {
        // 此功能可以作为标记订单进入待涂布池的占位实现
        // 实际可能需要在订单表中添加状态字段
        log.info("添加订单到待涂布池: {}", data);
    }
    
    @Override
    public void removeFromPendingCoatingPool(Long poolId, String operator) {
        if (poolId == null) {
            throw new RuntimeException("缺少池ID");
        }

        // 直接删除池记录，避免在未筛选 pool_status 时仍出现在列表
        int rows = pendingCoatingPoolMapper.deleteById(poolId);
        if (rows == 0) {
            throw new RuntimeException("待涂布池记录不存在或已移除");
        }

        log.info("已从待涂布池移除订单: poolId={}, operator={}", poolId, operator);
    }
    
    @Override
    public Map<String, Object> generateCoatingTasks(Map<String, Object> data) {
        // 调用现有的批量排程方法
        List<Long> orderItemIds = (List<Long>) data.get("orderItemIds");
        Integer filmWidth = (Integer) data.get("filmWidth");
        String planDate = (String) data.get("planDate");
        String operator = (String) data.getOrDefault("operator", "admin");
        Long scheduleId = data.get("scheduleId") == null ? null : Long.valueOf(data.get("scheduleId").toString());

        // 兼容前端只传 materialCode 的简化调用：自动收集待涂布池的订单明细
        if ((orderItemIds == null || orderItemIds.isEmpty()) && data.get("materialCode") != null) {
            String materialCode = data.get("materialCode").toString();
            List<com.fine.model.schedule.PendingCoatingOrderPool> waitingItems = pendingCoatingPoolMapper.selectWaitingByMaterialCode(materialCode);

            // 如未指定计划日期，优先用池中记录的加入日期；仍无则默认今天
            if (planDate == null || planDate.isEmpty()) {
                Date addedAt = waitingItems.get(0).getAddedAt();
                if (addedAt != null) {
                    planDate = new SimpleDateFormat("yyyy-MM-dd").format(addedAt);
                } else {
                    planDate = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
                }
            }
            // 如未指定薄膜宽度，给出安全默认值 1000mm（可后续在前端/配置中完善）
            if (filmWidth == null || filmWidth <= 0) {
                filmWidth = 1000;
            }

            // 如果待涂布池有数据，优先按料号汇总缺口面积生成单个涂布任务，计划量=建议涂布量
            if (waitingItems != null && !waitingItems.isEmpty()) {
                BigDecimal totalArea = waitingItems.stream()
                        .map(item -> item.getShortageArea() != null ? item.getShortageArea() : BigDecimal.ZERO)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                // 若池中没有面积字段，退化为用缺口数量累加（按㎡理解）
                if (totalArea.compareTo(BigDecimal.ZERO) <= 0) {
                    totalArea = waitingItems.stream()
                            .map(item -> item.getShortageQty() == null ? BigDecimal.ZERO : new BigDecimal(item.getShortageQty()))
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                }

                // 创建排程主单（如尚未创建）
                if (scheduleId == null) {
                    ProductionSchedule schedule = new ProductionSchedule();
                    schedule.setScheduleNo(scheduleMapper.generateScheduleNo());
                    schedule.setScheduleType("COATING");
                    schedule.setStatus("draft");
                    // 汇总池内订单/明细数量与面积，便于主表统计
                    int orderCount = (int) waitingItems.stream()
                            .map(com.fine.model.schedule.PendingCoatingOrderPool::getOrderId)
                            .filter(Objects::nonNull)
                            .distinct()
                            .count();
                    int itemCount = waitingItems.size();
                    schedule.setTotalOrders(orderCount > 0 ? orderCount : itemCount);
                    schedule.setTotalItems(itemCount);
                    schedule.setTotalSqm(totalArea);
                    try {
                        schedule.setScheduleDate(new SimpleDateFormat("yyyy-MM-dd").parse(planDate));
                    } catch (Exception e) {
                        schedule.setScheduleDate(new Date());
                    }
                    schedule.setCreateBy(operator);
                    schedule.setUpdateBy(operator);
                    scheduleMapper.insert(schedule);
                    scheduleId = schedule.getId();
                }

                // 计划开始时间 08:00，默认1小时占位，后续可调整
                Date planDateObj;
                try {
                    planDateObj = new SimpleDateFormat("yyyy-MM-dd").parse(planDate);
                } catch (Exception e) {
                    planDateObj = new Date();
                }
                Calendar startCal = Calendar.getInstance();
                startCal.setTime(planDateObj);
                startCal.set(Calendar.HOUR_OF_DAY, 8);
                startCal.set(Calendar.MINUTE, 0);
                startCal.set(Calendar.SECOND, 0);
                startCal.set(Calendar.MILLISECOND, 0);
                Date startTime = startCal.getTime();

                // 估算时长：面积 / (速度 * 宽度)
                BigDecimal speed = new BigDecimal(40); // 默认40m/min
                int durationMin = 60;
                if (filmWidth != null && filmWidth > 0) {
                    double widthM = filmWidth / 1000.0;
                    int est = (int) Math.ceil(totalArea.doubleValue() / (speed.doubleValue() * widthM));
                    durationMin = Math.max(est, 10);
                }

                String taskNo;
                try {
                    taskNo = coatingMapper.generateTaskNo(planDateObj);
                } catch (Exception ex) {
                    taskNo = "TB-" + new SimpleDateFormat("yyMMdd").format(new Date()) + "-" + (System.currentTimeMillis() % 100);
                }

                ScheduleCoating coating = new ScheduleCoating();
                coating.setScheduleId(scheduleId);
                coating.setTaskNo(taskNo);
                coating.setMaterialCode(materialCode);
                coating.setMaterialName(waitingItems.get(0).getMaterialName());
                coating.setPlanSqm(totalArea);
                coating.setPlanDate(planDateObj);
                coating.setPlanStartTime(startTime);
                Date endTime = new Date(startTime.getTime() + durationMin * 60L * 1000L);
                coating.setPlanEndTime(endTime);
                coating.setPlanDuration(durationMin);
                coating.setFilmWidth(filmWidth);
                coating.setJumboWidth(filmWidth);
                coating.setStatus("pending");
                coating.setCreateBy(operator);
                coating.setCreateTime(new Date());
                coatingMapper.insert(coating);

                // 更新待涂布池记录：绑定任务并标记状态
                for (com.fine.model.schedule.PendingCoatingOrderPool item : waitingItems) {
                    item.setPoolStatus("COATING");
                    item.setCoatingTaskId(coating.getId());
                    pendingCoatingPoolMapper.updateById(item);
                }

                // 保险：回填并校验主表统计，避免total_*出现空值
                try {
                    updateScheduleStatistics(scheduleId);
                } catch (Exception ignore) { }

                Map<String, Object> result = new HashMap<>();
                result.put("taskCount", 1);
                result.put("tasks", Collections.singletonList(coating));
                result.put("message", "成功生成 1 个涂布任务，计划量=" + totalArea + "㎡");
                return result;
            }

            // 若池为空但 materialCode 传入，继续走订单明细流程
            orderItemIds = waitingItems.stream()
                    .map(com.fine.model.schedule.PendingCoatingOrderPool::getOrderItemId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());
        }

        if (orderItemIds == null || orderItemIds.isEmpty()) {
            throw new RuntimeException("未选择订单");
        }
        if (filmWidth == null || filmWidth <= 0) {
            throw new RuntimeException("薄膜宽度无效");
        }

        // 如果未指定 scheduleId，创建一个自动涂布排程主单，以满足 schedule_coating 表的非空约束
        if (scheduleId == null) {
            ProductionSchedule schedule = new ProductionSchedule();
            schedule.setScheduleNo(scheduleMapper.generateScheduleNo());
            schedule.setScheduleType("COATING");
            schedule.setStatus("draft");
            try {
                if (planDate != null && !planDate.isEmpty()) {
                    schedule.setScheduleDate(new SimpleDateFormat("yyyy-MM-dd").parse(planDate));
                } else {
                    schedule.setScheduleDate(new Date());
                }
            } catch (Exception e) {
                schedule.setScheduleDate(new Date());
            }
            schedule.setCreateBy(operator);
            schedule.setUpdateBy(operator);
            scheduleMapper.insert(schedule);
            scheduleId = schedule.getId();
        }

        List<ScheduleCoating> tasks = batchScheduleCoatingWithSchedule(orderItemIds, filmWidth, planDate, operator, scheduleId);

        // 回填主表统计：订单数、明细数、面积
        if (scheduleId != null && tasks != null) {
            int taskItems = tasks.size();
            int orderCount = (int) tasks.stream()
                .map(ScheduleCoating::getOrderId)
                .filter(Objects::nonNull)
                .distinct()
                .count();
            BigDecimal totalArea = tasks.stream()
                .map(t -> t.getPlanSqm() == null ? BigDecimal.ZERO : t.getPlanSqm())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            ProductionSchedule upd = new ProductionSchedule();
            upd.setId(scheduleId);
            upd.setTotalOrders(orderCount > 0 ? orderCount : taskItems);
            upd.setTotalItems(taskItems);
            upd.setTotalSqm(totalArea);
            upd.setUpdateBy(operator);
            scheduleMapper.update(upd);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("taskCount", tasks.size());
        result.put("tasks", tasks);
        result.put("message", "成功生成 " + tasks.size() + " 个涂布任务");

        return result;
    }
    
    // 辅助方法：转换 PendingScheduleOrder 为 Map
    private List<Map<String, Object>> convertPendingOrdersToMap(List<com.fine.entity.PendingScheduleOrder> orders) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (com.fine.entity.PendingScheduleOrder order : orders) {
            Map<String, Object> map = new HashMap<>();
            map.put("orderNo", order.getOrderNo());
            map.put("materialCode", order.getMaterialCode());
            map.put("materialName", order.getMaterialName());
            map.put("customerName", order.getCustomer());
            map.put("pendingQty", order.getPendingQty());
            map.put("deliveryDate", order.getDeliveryDate());
            map.put("priority", "MEDIUM");
            result.add(map);
        }
        return result;
    }
}

