package com.fine.serviceIMPL.production;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fine.Dao.production.EquipmentMapper;
import com.fine.Dao.production.EquipmentTypeMapper;
import com.fine.Dao.production.WorkshopMapper;
import com.fine.Utils.ResponseResult;
import com.fine.model.production.Equipment;
import com.fine.model.production.EquipmentType;
import com.fine.model.production.Workshop;
import com.fine.service.production.EquipmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 设备管理Service实现类
 */
@Service
public class EquipmentServiceImpl extends ServiceImpl<EquipmentMapper, Equipment>
        implements EquipmentService {

    @Autowired
    private EquipmentMapper equipmentMapper;

    @Autowired
    private EquipmentTypeMapper equipmentTypeMapper;

    @Autowired
    private WorkshopMapper workshopMapper;

    @Override
    public ResponseResult<?> getEquipmentList(String equipmentType, Long workshopId, String status, String keyword, Integer pageNum, Integer pageSize) {
        try {
            Page<Equipment> page = new Page<>(pageNum != null ? pageNum : 1, pageSize != null ? pageSize : 10);
            
            IPage<Equipment> pageResult = equipmentMapper.selectEquipmentPageList(page, equipmentType, workshopId, status, keyword);
            
            return new ResponseResult<>(200, "查询成功", pageResult);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(500, "查询失败: " + e.getMessage());
        }
    }

    @Override
    public ResponseResult<?> getEquipmentById(Long id) {
        try {
            Equipment equipment = equipmentMapper.selectById(id);
            if (equipment == null || equipment.getIsDeleted() == 1) {
                return new ResponseResult<>(404, "设备不存在");
            }
            return new ResponseResult<>(200, "查询成功", equipment);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(500, "查询失败: " + e.getMessage());
        }
    }

    @Override
    public ResponseResult<?> addEquipment(Equipment equipment) {
        try {
            // 检查设备编号是否已存在
            if (equipmentMapper.checkCodeExists(equipment.getEquipmentCode(), 0L) > 0) {
                return new ResponseResult<>(400, "设备编号已存在");
            }

            equipment.setCreateTime(new Date());
            equipment.setUpdateTime(new Date());
            equipment.setIsDeleted(0);
            if (equipment.getStatus() == null || equipment.getStatus().isEmpty()) {
                equipment.setStatus("normal");
            }

            int result = equipmentMapper.insert(equipment);
            if (result > 0) {
                return new ResponseResult<>(200, "添加成功", equipment);
            }
            return new ResponseResult<>(500, "添加失败");
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(500, "添加失败: " + e.getMessage());
        }
    }

    @Override
    public ResponseResult<?> updateEquipment(Equipment equipment) {
        try {
            // 检查设备是否存在
            Equipment existing = equipmentMapper.selectById(equipment.getId());
            if (existing == null || existing.getIsDeleted() == 1) {
                return new ResponseResult<>(404, "设备不存在");
            }

            // 检查设备编号是否重复
            if (!existing.getEquipmentCode().equals(equipment.getEquipmentCode())) {
                if (equipmentMapper.checkCodeExists(equipment.getEquipmentCode(), equipment.getId()) > 0) {
                    return new ResponseResult<>(400, "设备编号已存在");
                }
            }

            equipment.setUpdateTime(new Date());
            int result = equipmentMapper.updateById(equipment);
            if (result > 0) {
                return new ResponseResult<>(200, "修改成功");
            }
            return new ResponseResult<>(500, "修改失败");
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(500, "修改失败: " + e.getMessage());
        }
    }

    @Override
    public ResponseResult<?> deleteEquipment(Long id) {
        try {
            Equipment equipment = equipmentMapper.selectById(id);
            if (equipment == null || equipment.getIsDeleted() == 1) {
                return new ResponseResult<>(404, "设备不存在");
            }

            // 逻辑删除
            equipment.setIsDeleted(1);
            equipment.setUpdateTime(new Date());
            int result = equipmentMapper.updateById(equipment);
            if (result > 0) {
                return new ResponseResult<>(200, "删除成功");
            }
            return new ResponseResult<>(500, "删除失败");
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(500, "删除失败: " + e.getMessage());
        }
    }

    @Override
    public ResponseResult<?> getEquipmentTypes() {
        try {
            List<EquipmentType> list = equipmentTypeMapper.selectAllEnabled();
            return new ResponseResult<>(200, "查询成功", list);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(500, "查询失败: " + e.getMessage());
        }
    }

    @Override
    public ResponseResult<?> getWorkshops() {
        try {
            List<Workshop> list = workshopMapper.selectAllEnabled();
            return new ResponseResult<>(200, "查询成功", list);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(500, "查询失败: " + e.getMessage());
        }
    }

    @Override
    public ResponseResult<?> getAvailableByType(String equipmentType) {
        try {
            List<Equipment> list = equipmentMapper.selectAvailableByType(equipmentType);
            return new ResponseResult<>(200, "查询成功", list);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(500, "查询失败: " + e.getMessage());
        }
    }

    @Override
    public ResponseResult<?> updateStatus(Long id, String status) {
        try {
            Equipment equipment = equipmentMapper.selectById(id);
            if (equipment == null || equipment.getIsDeleted() == 1) {
                return new ResponseResult<>(404, "设备不存在");
            }

            equipment.setStatus(status);
            equipment.setUpdateTime(new Date());
            int result = equipmentMapper.updateById(equipment);
            if (result > 0) {
                return new ResponseResult<>(200, "状态更新成功");
            }
            return new ResponseResult<>(500, "状态更新失败");
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(500, "状态更新失败: " + e.getMessage());
        }
    }

    // ==================== 车间管理 ====================

    @Override
    public IPage<Workshop> getWorkshopPage(Integer status, String keyword, Integer page, Integer size) {
        try {
            IPage<Workshop> pageRequest = new Page<>(page, size);
            LambdaQueryWrapper<Workshop> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Workshop::getIsDeleted, 0);
            if (status != null) {
                wrapper.eq(Workshop::getStatus, status);
            }
            if (keyword != null && !keyword.isEmpty()) {
                wrapper.and(w -> w.like(Workshop::getWorkshopCode, keyword)
                        .or().like(Workshop::getWorkshopName, keyword));
            }
            wrapper.orderByAsc(Workshop::getWorkshopCode);
            
            IPage<Workshop> pageResult = workshopMapper.selectPage(pageRequest, wrapper);
            return pageResult;
        } catch (Exception e) {
            e.printStackTrace();
            return new Page<>();
        }
    }

    @Override
    public ResponseResult<?> addWorkshop(Workshop workshop) {
        try {
            // 检查车间编号是否已存在
            LambdaQueryWrapper<Workshop> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Workshop::getWorkshopCode, workshop.getWorkshopCode())
                   .eq(Workshop::getIsDeleted, 0);
            if (workshopMapper.selectCount(wrapper) > 0) {
                return new ResponseResult<>(400, "车间编号已存在");
            }

            workshop.setCreateTime(new Date());
            workshop.setUpdateTime(new Date());
            workshop.setIsDeleted(0);
            if (workshop.getStatus() == null) {
                workshop.setStatus(1);
            }

            int result = workshopMapper.insert(workshop);
            if (result > 0) {
                return new ResponseResult<>(200, "添加成功", workshop);
            }
            return new ResponseResult<>(500, "添加失败");
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(500, "添加失败: " + e.getMessage());
        }
    }

    @Override
    public ResponseResult<?> updateWorkshop(Workshop workshop) {
        try {
            Workshop existing = workshopMapper.selectById(workshop.getId());
            if (existing == null || existing.getIsDeleted() == 1) {
                return new ResponseResult<>(404, "车间不存在");
            }

            // 检查车间编号是否重复
            if (!existing.getWorkshopCode().equals(workshop.getWorkshopCode())) {
                LambdaQueryWrapper<Workshop> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(Workshop::getWorkshopCode, workshop.getWorkshopCode())
                       .eq(Workshop::getIsDeleted, 0)
                       .ne(Workshop::getId, workshop.getId());
                if (workshopMapper.selectCount(wrapper) > 0) {
                    return new ResponseResult<>(400, "车间编号已存在");
                }
            }

            workshop.setUpdateTime(new Date());
            int result = workshopMapper.updateById(workshop);
            if (result > 0) {
                return new ResponseResult<>(200, "修改成功");
            }
            return new ResponseResult<>(500, "修改失败");
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(500, "修改失败: " + e.getMessage());
        }
    }

    @Override
    public ResponseResult<?> deleteWorkshop(Long id) {
        try {
            Workshop workshop = workshopMapper.selectById(id);
            if (workshop == null || workshop.getIsDeleted() == 1) {
                return new ResponseResult<>(404, "车间不存在");
            }

            // 检查车间下是否有设备
            LambdaQueryWrapper<Equipment> eWrapper = new LambdaQueryWrapper<>();
            eWrapper.eq(Equipment::getWorkshopId, id).eq(Equipment::getIsDeleted, 0);
            if (equipmentMapper.selectCount(eWrapper) > 0) {
                return new ResponseResult<>(400, "该车间下还有设备，请先删除设备");
            }

            // 逻辑删除
            workshop.setIsDeleted(1);
            workshop.setUpdateTime(new Date());
            int result = workshopMapper.updateById(workshop);
            if (result > 0) {
                return new ResponseResult<>(200, "删除成功");
            }
            return new ResponseResult<>(500, "删除失败");
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(500, "删除失败: " + e.getMessage());
        }
    }

    // ==================== 设备类型管理 ====================

    @Override
    public IPage<EquipmentType> getEquipmentTypePage(Integer status, Integer page, Integer size) {
        try {
            IPage<EquipmentType> pageRequest = new Page<>(page, size);
            LambdaQueryWrapper<EquipmentType> wrapper = new LambdaQueryWrapper<>();
            if (status != null) {
                wrapper.eq(EquipmentType::getStatus, status);
            }
            wrapper.orderByAsc(EquipmentType::getProcessOrder);
            
            IPage<EquipmentType> pageResult = equipmentTypeMapper.selectPage(pageRequest, wrapper);
            return pageResult;
        } catch (Exception e) {
            e.printStackTrace();
            return new Page<>();
        }
    }

    // ==================== 导入导出 ====================

    @Override
    public List<Equipment> getEquipmentListForExport(String equipmentType, Long workshopId, String status) {
        return equipmentMapper.selectEquipmentList(equipmentType, workshopId, status, null);
    }

    @Override
    public Map<String, Object> importEquipment(org.springframework.web.multipart.MultipartFile file) throws Exception {
        Map<String, Object> result = new HashMap<>();
        int successCount = 0;
        int failCount = 0;
        StringBuilder errorMsg = new StringBuilder();

        try (org.apache.poi.ss.usermodel.Workbook workbook = org.apache.poi.ss.usermodel.WorkbookFactory.create(file.getInputStream())) {
            org.apache.poi.ss.usermodel.Sheet sheet = workbook.getSheetAt(0);
            int lastRow = sheet.getLastRowNum();

            for (int i = 1; i <= lastRow; i++) {
                org.apache.poi.ss.usermodel.Row row = sheet.getRow(i);
                if (row == null) continue;

                try {
                    Equipment equipment = new Equipment();
                    // 设备编号
                    equipment.setEquipmentCode(getCellStringValue(row.getCell(0)));
                    if (equipment.getEquipmentCode() == null || equipment.getEquipmentCode().isEmpty()) {
                        errorMsg.append("第").append(i + 1).append("行：设备编号不能为空\n");
                        failCount++;
                        continue;
                    }

                    // 检查编号是否已存在
                    if (equipmentMapper.checkCodeExists(equipment.getEquipmentCode(), 0L) > 0) {
                        errorMsg.append("第").append(i + 1).append("行：设备编号已存在\n");
                        failCount++;
                        continue;
                    }

                    equipment.setEquipmentName(getCellStringValue(row.getCell(1)));
                    equipment.setEquipmentType(getCellStringValue(row.getCell(2)));
                    equipment.setWorkshopId(getCellLongValue(row.getCell(3)));
                    equipment.setBrand(getCellStringValue(row.getCell(4)));
                    equipment.setModel(getCellStringValue(row.getCell(5)));
                    equipment.setMaxWidth(getCellIntValue(row.getCell(6)));
                    equipment.setMaxSpeed(getCellBigDecimalValue(row.getCell(7)));
                    equipment.setDailyCapacity(getCellBigDecimalValue(row.getCell(8)));
                    equipment.setPurchaseDate(getCellDateValue(row.getCell(9)));
                    String statusVal = getCellStringValue(row.getCell(10));
                    equipment.setStatus(statusVal != null && !statusVal.isEmpty() ? statusVal : "normal");
                    equipment.setLocation(getCellStringValue(row.getCell(11)));
                    equipment.setRemark(getCellStringValue(row.getCell(12)));

                    equipment.setCreateTime(new Date());
                    equipment.setUpdateTime(new Date());
                    equipment.setIsDeleted(0);

                    equipmentMapper.insert(equipment);
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

    // 辅助方法：获取单元格字符串值
    private String getCellStringValue(org.apache.poi.ss.usermodel.Cell cell) {
        if (cell == null) return null;
        cell.setCellType(org.apache.poi.ss.usermodel.CellType.STRING);
        return cell.getStringCellValue().trim();
    }

    // 辅助方法：获取单元格整数值
    private Integer getCellIntValue(org.apache.poi.ss.usermodel.Cell cell) {
        if (cell == null) return null;
        try {
            if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.NUMERIC) {
                return (int) cell.getNumericCellValue();
            } else {
                String val = cell.getStringCellValue().trim();
                return val.isEmpty() ? null : Integer.parseInt(val);
            }
        } catch (Exception e) {
            return null;
        }
    }

    // 辅助方法：获取单元格Long值
    private Long getCellLongValue(org.apache.poi.ss.usermodel.Cell cell) {
        if (cell == null) return null;
        try {
            if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.NUMERIC) {
                return (long) cell.getNumericCellValue();
            } else {
                String val = cell.getStringCellValue().trim();
                return val.isEmpty() ? null : Long.parseLong(val);
            }
        } catch (Exception e) {
            return null;
        }
    }

    // 辅助方法：获取单元格BigDecimal值
    private java.math.BigDecimal getCellBigDecimalValue(org.apache.poi.ss.usermodel.Cell cell) {
        if (cell == null) return null;
        try {
            if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.NUMERIC) {
                return java.math.BigDecimal.valueOf(cell.getNumericCellValue());
            } else {
                String val = cell.getStringCellValue().trim();
                return val.isEmpty() ? null : new java.math.BigDecimal(val);
            }
        } catch (Exception e) {
            return null;
        }
    }

    // 辅助方法：获取单元格日期值
    private Date getCellDateValue(org.apache.poi.ss.usermodel.Cell cell) {
        if (cell == null) return null;
        try {
            if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.NUMERIC) {
                return cell.getDateCellValue();
            } else {
                String val = cell.getStringCellValue().trim();
                if (val.isEmpty()) return null;
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
                return sdf.parse(val);
            }
        } catch (Exception e) {
            return null;
        }
    }
}
