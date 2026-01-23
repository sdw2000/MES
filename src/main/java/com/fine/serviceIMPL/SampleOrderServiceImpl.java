package com.fine.serviceIMPL;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fine.Dao.SampleOrderMapper;
import com.fine.Dao.SampleItemMapper;
import com.fine.Utils.ResponseResult;
import com.fine.modle.*;
import com.fine.service.SampleOrderService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.DateUtil;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 送样订单Service实现类
 * @author AI Assistant
 * @date 2026-01-05
 */
@Service
public class SampleOrderServiceImpl implements SampleOrderService {
      @Autowired
    private SampleOrderMapper sampleOrderMapper;
    
    @Autowired
    private SampleItemMapper sampleItemMapper;
    
    @Override
    public Page<SampleOrderDTO> list(int current, int size, String customerName, String status, String trackingNumber) {
        Page<SampleOrder> page = new Page<>(current, size);
        page.setOptimizeCountSql(false);
        
        LambdaQueryWrapper<SampleOrder> wrapper = new LambdaQueryWrapper<>();
        
        // 客户名称模糊查询
        if (StringUtils.hasText(customerName)) {
            wrapper.like(SampleOrder::getCustomerName, customerName);
        }
        
        // 状态筛选
        if (StringUtils.hasText(status)) {
            wrapper.eq(SampleOrder::getStatus, status);
        }
        
        // 快递单号查询
        if (StringUtils.hasText(trackingNumber)) {
            wrapper.like(SampleOrder::getTrackingNumber, trackingNumber);
        }
        
        // 按创建时间倒序
        wrapper.orderByDesc(SampleOrder::getCreateTime);
        
        Page<SampleOrder> resultPage = sampleOrderMapper.selectPage(page, wrapper);
        
        // 转换为DTO
        Page<SampleOrderDTO> dtoPage = new Page<>(resultPage.getCurrent(), resultPage.getSize(), resultPage.getTotal());
        List<SampleOrderDTO> dtoList = resultPage.getRecords().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        
        dtoPage.setRecords(dtoList);
        
        return dtoPage;
    }
    
    @Override
    public SampleOrderDTO getDetailBySampleNo(String sampleNo) {
        LambdaQueryWrapper<SampleOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SampleOrder::getSampleNo, sampleNo);
        
        SampleOrder order = sampleOrderMapper.selectOne(wrapper);
        if (order == null) {
            return null;
        }
        
        // 查询明细
        List<SampleItem> items = sampleItemMapper.selectBySampleNo(sampleNo);
        order.setItems(items);
        
        // 计算总数量
        int totalQty = items.stream().mapToInt(SampleItem::getQuantity).sum();
        order.setTotalQuantity(totalQty);
        
        return convertToDTO(order);
    }    @Override
    @Transactional(rollbackFor = Exception.class)
    public String create(SampleOrderDTO dto) {
        // 生成送样编号
        String sampleNo = generateSampleNo();
        
        SampleOrder order = new SampleOrder();
        if (dto != null) {
            BeanUtils.copyProperties(dto, order);
            order.setSampleNo(sampleNo);
            
            // 设置默认状态
            if (!StringUtils.hasText(order.getStatus())) {
                order.setStatus("待发货");
            }
            
            // 保存主表
            sampleOrderMapper.insert(order);
            
            // 保存明细
            if (dto.getItems() != null && !dto.getItems().isEmpty()) {
                for (SampleItem item : dto.getItems()) {
                    item.setSampleNo(sampleNo);
                    sampleItemMapper.insert(item);
                }
            }
        }
        
        return sampleNo;
    }
      @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(SampleOrderDTO dto) {
        if (dto == null) {
            return false;
        }
        
        LambdaQueryWrapper<SampleOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SampleOrder::getSampleNo, dto.getSampleNo());
        SampleOrder order = sampleOrderMapper.selectOne(wrapper);
        if (order == null) {
            return false;
        }
        
        // 更新主表
        BeanUtils.copyProperties(dto, order, "id", "sampleNo", "createTime", "createBy");
        sampleOrderMapper.updateById(order);
        
        // 删除旧明细
        sampleItemMapper.deleteBySampleNo(dto.getSampleNo());
        
        // 插入新明细
        if (dto.getItems() != null && !dto.getItems().isEmpty()) {
            for (SampleItem item : dto.getItems()) {
                item.setId(null); // 确保是新记录
                item.setSampleNo(dto.getSampleNo());
                sampleItemMapper.insert(item);
            }
        }
        
        return true;
    }
    
    @Override
    public boolean delete(String sampleNo) {
        LambdaQueryWrapper<SampleOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SampleOrder::getSampleNo, sampleNo);
        
        SampleOrder order = sampleOrderMapper.selectOne(wrapper);
        if (order == null) {
            return false;
        }
        
        // 逻辑删除
        order.setIsDeleted(true);
        return sampleOrderMapper.updateById(order) > 0;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateLogistics(LogisticsUpdateDTO dto) {
        LambdaQueryWrapper<SampleOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SampleOrder::getSampleNo, dto.getSampleNo());
        
        SampleOrder order = sampleOrderMapper.selectOne(wrapper);
        if (order == null) {
            return false;
        }
        
        // 更新物流信息
        order.setExpressCompany(dto.getExpressCompany());
        order.setTrackingNumber(dto.getTrackingNumber());
        
        if (StringUtils.hasText(dto.getShipDate())) {
            order.setShipDate(LocalDate.parse(dto.getShipDate()));
        }
        
        if (StringUtils.hasText(dto.getDeliveryDate())) {
            order.setDeliveryDate(LocalDate.parse(dto.getDeliveryDate()));
        }
        
        // 如果填写了快递单号，自动更新状态为"已发货"
        if (StringUtils.hasText(dto.getTrackingNumber()) && "待发货".equals(order.getStatus())) {
            order.setStatus("已发货");
            order.setShipDate(LocalDate.now());
        }
        
        // 尝试查询物流信息
        if (StringUtils.hasText(dto.getTrackingNumber())) {
            try {
                queryAndUpdateLogistics(order);
            } catch (Exception e) {
                // 查询失败不影响主流程
                System.err.println("物流查询失败: " + e.getMessage());
            }
        }
        
        return sampleOrderMapper.updateById(order) > 0;
    }
    
    @Override
    public boolean updateStatus(String sampleNo, String newStatus, String reason) {
        LambdaQueryWrapper<SampleOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SampleOrder::getSampleNo, sampleNo);
        
        SampleOrder order = sampleOrderMapper.selectOne(wrapper);
        if (order == null) {
            return false;
        }
        
        order.setStatus(newStatus);
        return sampleOrderMapper.updateById(order) > 0;
    }
    
    @Override
    public Map<String, Object> queryLogistics(String sampleNo) {
        LambdaQueryWrapper<SampleOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SampleOrder::getSampleNo, sampleNo);
        
        SampleOrder order = sampleOrderMapper.selectOne(wrapper);
        if (order == null || !StringUtils.hasText(order.getTrackingNumber())) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "未找到快递单号");
            return result;
        }
        
        return queryAndUpdateLogistics(order);
    }
      /**
     * 查询并更新物流信息
     * 这里需要调用快递100 API或其他物流查询API
     */
    private Map<String, Object> queryAndUpdateLogistics(SampleOrder order) {
        Map<String, Object> result = new HashMap<>();
        
        // Note: 快递100 API集成 - 需要申请API key
        // 这里需要申请快递100的API key
        // API文档: https://www.kuaidi100.com/openapi/
        
        /*
         * 快递100 API调用示例：
         * String url = "https://poll.kuaidi100.com/poll/query.do";
         * Map<String, String> params = new HashMap<>();
         * params.put("customer", "您的授权KEY");
         * params.put("sign", "签名");
         * params.put("param", JSON.toJSONString(queryParam));
         * 
         * // 发送HTTP请求...
         */
        
        // 模拟物流查询结果
        result.put("success", true);
        result.put("message", "查询成功");
        result.put("status", "运输中");
        result.put("lastUpdate", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        
        List<Map<String, String>> traces = new ArrayList<>();
        Map<String, String> trace1 = new HashMap<>();
        trace1.put("time", "2026-01-05 10:00:00");
        trace1.put("context", "[深圳市]已收件");
        traces.add(trace1);
        
        Map<String, String> trace2 = new HashMap<>();
        trace2.put("time", "2026-01-05 14:30:00");
        trace2.put("context", "[深圳市]运输中");
        traces.add(trace2);
        
        result.put("traces", traces);
        
        // 更新物流状态
        order.setLogisticsStatus("运输中");
        order.setLastLogisticsQueryTime(LocalDateTime.now());
        
        // 根据物流状态自动更新订单状态
        if ("已签收".equals(result.get("status"))) {
            order.setStatus("已签收");
            order.setDeliveryDate(LocalDate.now());
        } else if ("运输中".equals(result.get("status")) || "派件中".equals(result.get("status"))) {
            if (!"已签收".equals(order.getStatus())) {
                order.setStatus("运输中");
            }
        }
        
        sampleOrderMapper.updateById(order);
        
        return result;
    }
      @Override
    @Transactional(rollbackFor = Exception.class)
    public String convertToOrder(String sampleNo) {
        // Future enhancement: 实现完整的转订单逻辑
        // 1. 查询送样订单
        // 2. 创建销售订单
        // 3. 复制明细信息
        // 4. 更新送样订单状态
        
        SampleOrderDTO sample = getDetailBySampleNo(sampleNo);
        if (sample == null) {
            throw new RuntimeException("送样订单不存在");
        }
        
        if (Boolean.TRUE.equals(sample.getConvertedToOrder())) {
            throw new RuntimeException("该送样订单已转为订单");
        }
        
        // 生成订单号（这里简化处理）
        String orderNo = "SO" + System.currentTimeMillis();
        
        // 更新送样订单
        LambdaQueryWrapper<SampleOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SampleOrder::getSampleNo, sampleNo);
        SampleOrder order = sampleOrderMapper.selectOne(wrapper);
        order.setConvertedToOrder(true);
        order.setOrderNo(orderNo);
        sampleOrderMapper.updateById(order);
        
        return orderNo;
    }
    
    @Override
    public String generateSampleNo() {
        String sampleNo = sampleOrderMapper.generateSampleNo();
        if (sampleNo == null || sampleNo.isEmpty()) {
            // 如果数据库函数失败，使用Java生成
            String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            return "SP" + dateStr + "001";
        }
        return sampleNo;
    }    /**
     * 转换为DTO
     */
    private SampleOrderDTO convertToDTO(SampleOrder order) {
        SampleOrderDTO dto = new SampleOrderDTO();
        if (order != null) {
            BeanUtils.copyProperties(order, dto);
            
            // 格式化时间
            if (order.getCreateTime() != null) {
                dto.setCreateTime(order.getCreateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            }
            if (order.getUpdateTime() != null) {
                dto.setUpdateTime(order.getUpdateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            }
        }
        
        return dto;
    }
    
    @Override
    public Map<String, Object> importFromExcel(MultipartFile file) throws Exception {
        Map<String, Object> result = new HashMap<>();
        int successCount = 0;
        int failCount = 0;
        StringBuilder errorMsg = new StringBuilder();

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            int lastRow = sheet.getLastRowNum();

            for (int i = 1; i <= lastRow; i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                try {
                    SampleOrder order = new SampleOrder();
                    
                    // 送样编号（如果为空则自动生成）
                    String sampleNo = getCellStringValue(row.getCell(0));
                    if (sampleNo == null || sampleNo.isEmpty()) {
                        sampleNo = generateSampleNo();
                    } else {
                        // 检查编号是否已存在
                        LambdaQueryWrapper<SampleOrder> wrapper = new LambdaQueryWrapper<>();
                        wrapper.eq(SampleOrder::getSampleNo, sampleNo);
                        if (sampleOrderMapper.selectCount(wrapper) > 0) {
                            errorMsg.append("第").append(i + 1).append("行：送样编号已存在\n");
                            failCount++;
                            continue;
                        }
                    }
                    order.setSampleNo(sampleNo);
                    
                    // 客户名称（必填）
                    String customerName = getCellStringValue(row.getCell(1));
                    if (customerName == null || customerName.isEmpty()) {
                        errorMsg.append("第").append(i + 1).append("行：客户名称不能为空\n");
                        failCount++;
                        continue;
                    }
                    order.setCustomerName(customerName);
                    
                    order.setContactName(getCellStringValue(row.getCell(2)));
                    order.setContactPhone(getCellStringValue(row.getCell(3)));                    order.setContactAddress(getCellStringValue(row.getCell(4)));
                    String sendDateStr = getCellStringValue(row.getCell(5));
                    if (sendDateStr != null && !sendDateStr.isEmpty()) {
                        order.setSendDate(LocalDate.parse(sendDateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd")));
                    }
                    order.setExpressCompany(getCellStringValue(row.getCell(6)));
                    order.setTrackingNumber(getCellStringValue(row.getCell(7)));
                    
                    String status = getCellStringValue(row.getCell(8));
                    order.setStatus(status != null && !status.isEmpty() ? status : "待发货");
                    
                    order.setRemark(getCellStringValue(row.getCell(9)));
                    order.setCreateTime(LocalDateTime.now());
                    order.setUpdateTime(LocalDateTime.now());

                    sampleOrderMapper.insert(order);
                    successCount++;
                } catch (Exception e) {
                    errorMsg.append("第").append(i + 1).append("行：").append(e.getMessage()).append("\n");
                    failCount++;
                }
            }
        }

        result.put("success", failCount == 0);
        result.put("successCount", successCount);
        result.put("failCount", failCount);
        result.put("message", failCount > 0 ? errorMsg.toString() : "导入成功");
        return result;
    }
    
    @Override
    public ResponseResult<?> exportToExcel(String customerName, String status) {
        LambdaQueryWrapper<SampleOrder> wrapper = new LambdaQueryWrapper<>();
        
        if (StringUtils.hasText(customerName)) {
            wrapper.like(SampleOrder::getCustomerName, customerName);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(SampleOrder::getStatus, status);
        }
        wrapper.orderByDesc(SampleOrder::getCreateTime);
        
        List<SampleOrder> list = sampleOrderMapper.selectList(wrapper);
        
        List<Map<String, Object>> exportData = list.stream().map(order -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("sampleNo", order.getSampleNo());
            map.put("customerName", order.getCustomerName());
            map.put("contactName", order.getContactName());
            map.put("contactPhone", order.getContactPhone());
            map.put("contactAddress", order.getContactAddress());
            map.put("sendDate", order.getSendDate());
            map.put("expressCompany", order.getExpressCompany());
            map.put("trackingNumber", order.getTrackingNumber());
            map.put("status", order.getStatus());
            map.put("remark", order.getRemark());
            return map;
        }).collect(Collectors.toList());
        
        return new ResponseResult<>(20000, "导出成功", exportData);
    }
      // 辅助方法：获取单元格字符串值
    private String getCellStringValue(Cell cell) {
        if (cell == null) return null;
        try {
            switch (cell.getCellType()) {
                case STRING:
                    return cell.getStringCellValue().trim();
                case NUMERIC:
                    if (DateUtil.isCellDateFormatted(cell)) {
                        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
                        return sdf.format(cell.getDateCellValue());
                    }
                    return String.valueOf((long) cell.getNumericCellValue());
                case BOOLEAN:
                    return String.valueOf(cell.getBooleanCellValue());
                case FORMULA:
                    return cell.getCellFormula();
                default:
                    return null;
            }
        } catch (Exception e) {
            return null;
        }
    }
}
