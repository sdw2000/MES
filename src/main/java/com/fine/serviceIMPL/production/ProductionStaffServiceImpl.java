package com.fine.serviceIMPL.production;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fine.Dao.production.ProductionStaffMapper;
import com.fine.Dao.production.StaffSkillMapper;
import com.fine.model.production.ProductionStaff;
import com.fine.model.production.StaffSkill;
import com.fine.service.production.ProductionStaffService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 生产人员Service实现类
 */
@Service
public class ProductionStaffServiceImpl extends ServiceImpl<ProductionStaffMapper, ProductionStaff> 
        implements ProductionStaffService {

    @Autowired
    private ProductionStaffMapper staffMapper;

    @Autowired
    private StaffSkillMapper skillMapper;

    @Override
    public Map<String, Object> getStaffList(String staffCode, String staffName, Long teamId,
                                            Long workshopId, String status, Integer page, Integer size) {
        IPage<ProductionStaff> pageRequest = new Page<>(page, size);
        IPage<ProductionStaff> pageResult = staffMapper.selectStaffPageList(pageRequest, staffCode, staffName, teamId, workshopId, status);

        Map<String, Object> result = new HashMap<>();
        result.put("list", pageResult.getRecords());
        result.put("total", pageResult.getTotal());
        result.put("pageNum", pageResult.getCurrent());
        result.put("pageSize", pageResult.getSize());
        return result;
    }

    @Override
    public IPage<ProductionStaff> getStaffPage(String staffCode, String staffName, Long teamId,
                                               Long workshopId, String status, Integer page, Integer size) {
        IPage<ProductionStaff> pageRequest = new Page<>(page, size);
        IPage<ProductionStaff> pageResult = staffMapper.selectStaffPageList(pageRequest, staffCode, staffName, teamId, workshopId, status);
        return pageResult;
    }

    @Override
    public ProductionStaff getStaffDetail(Long id) {
        ProductionStaff staff = staffMapper.selectStaffById(id);
        if (staff != null) {
            // 查询技能列表
            List<StaffSkill> skills = skillMapper.selectByStaffId(id);
            staff.setSkills(skills);
        }
        return staff;
    }

    @Override
    @Transactional
    public boolean addStaff(ProductionStaff staff) {
        // 检查工号是否重复
        int count = staffMapper.checkStaffCodeExists(staff.getStaffCode(), 0L);
        if (count > 0) {
            throw new RuntimeException("工号已存在：" + staff.getStaffCode());
        }
        
        staff.setCreateTime(new Date());
        staff.setUpdateTime(new Date());
        staff.setIsDeleted(0);
        int rows = staffMapper.insert(staff);
        
        // 保存技能
        if (rows > 0 && staff.getSkills() != null && !staff.getSkills().isEmpty()) {
            for (StaffSkill skill : staff.getSkills()) {
                skill.setStaffId(staff.getId());
                skill.setCreateTime(new Date());
            }
            skillMapper.batchInsert(staff.getSkills());
        }
        
        return rows > 0;
    }

    @Override
    @Transactional
    public boolean updateStaff(ProductionStaff staff) {
        // 检查工号是否重复
        int count = staffMapper.checkStaffCodeExists(staff.getStaffCode(), staff.getId());
        if (count > 0) {
            throw new RuntimeException("工号已存在：" + staff.getStaffCode());
        }
        
        staff.setUpdateTime(new Date());
        int rows = staffMapper.updateById(staff);
        
        // 更新技能：先删除再新增
        if (rows > 0 && staff.getSkills() != null) {
            skillMapper.deleteByStaffId(staff.getId());
            if (!staff.getSkills().isEmpty()) {
                for (StaffSkill skill : staff.getSkills()) {
                    skill.setStaffId(staff.getId());
                    skill.setCreateTime(new Date());
                }
                skillMapper.batchInsert(staff.getSkills());
            }
        }
        
        return rows > 0;
    }

    @Override
    @Transactional
    public boolean deleteStaff(Long id) {
        // 逻辑删除
        ProductionStaff staff = new ProductionStaff();
        staff.setId(id);
        staff.setIsDeleted(1);
        staff.setUpdateTime(new Date());
        return staffMapper.updateById(staff) > 0;
    }

    @Override
    @Transactional
    public boolean batchDeleteStaff(List<Long> ids) {
        for (Long id : ids) {
            deleteStaff(id);
        }
        return true;
    }

    @Override
    @Transactional
    public boolean saveSkills(Long staffId, List<StaffSkill> skills) {
        // 先删除原有技能
        skillMapper.deleteByStaffId(staffId);
        
        // 新增技能
        if (skills != null && !skills.isEmpty()) {
            for (StaffSkill skill : skills) {
                skill.setStaffId(staffId);
                skill.setCreateTime(new Date());
            }
            return skillMapper.batchInsert(skills) > 0;
        }
        return true;
    }

    @Override
    public List<StaffSkill> getStaffSkills(Long staffId) {
        return skillMapper.selectByStaffId(staffId);
    }

    @Override
    public List<ProductionStaff> getStaffByEquipmentType(String equipmentType) {
        return staffMapper.selectByEquipmentType(equipmentType);
    }

    @Override
    public List<ProductionStaff> getStaffByTeam(Long teamId) {
        return staffMapper.selectByTeamId(teamId);
    }

    @Override
    public List<ProductionStaff> getAllActiveStaff() {
        LambdaQueryWrapper<ProductionStaff> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProductionStaff::getStatus, "active")
               .eq(ProductionStaff::getIsDeleted, 0)
               .orderByAsc(ProductionStaff::getStaffCode);
        return staffMapper.selectList(wrapper);
    }

    // ==================== 导入导出 ====================

    @Override
    public List<ProductionStaff> getStaffListForExport(Long teamId, Long workshopId, String status) {
        return staffMapper.selectStaffList(null, null, teamId, workshopId, status);
    }

    @Override
    @Transactional
    public Map<String, Object> importStaff(org.springframework.web.multipart.MultipartFile file) throws Exception {
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
                    ProductionStaff staff = new ProductionStaff();
                    // 工号
                    staff.setStaffCode(getCellStringValue(row.getCell(0)));
                    if (staff.getStaffCode() == null || staff.getStaffCode().isEmpty()) {
                        errorMsg.append("第").append(i + 1).append("行：工号不能为空\n");
                        failCount++;
                        continue;
                    }

                    // 检查工号是否已存在
                    if (staffMapper.checkStaffCodeExists(staff.getStaffCode(), 0L) > 0) {
                        errorMsg.append("第").append(i + 1).append("行：工号已存在\n");
                        failCount++;
                        continue;
                    }

                    staff.setStaffName(getCellStringValue(row.getCell(1)));
                    staff.setGender(getCellStringValue(row.getCell(2)));
                    staff.setPhone(getCellStringValue(row.getCell(3)));
                    staff.setTeamId(getCellLongValue(row.getCell(4)));
                    staff.setWorkshopId(getCellLongValue(row.getCell(5)));
                    String skillLevel = getCellStringValue(row.getCell(6));
                    staff.setSkillLevel(skillLevel != null && !skillLevel.isEmpty() ? skillLevel : "junior");
                    staff.setEntryDate(getCellDateValue(row.getCell(7)));
                    String statusVal = getCellStringValue(row.getCell(8));
                    staff.setStatus(statusVal != null && !statusVal.isEmpty() ? statusVal : "active");
                    staff.setRemark(getCellStringValue(row.getCell(9)));

                    staff.setCreateTime(new Date());
                    staff.setUpdateTime(new Date());
                    staff.setIsDeleted(0);

                    staffMapper.insert(staff);
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
        // cell.setCellType(org.apache.poi.ss.usermodel.CellType.STRING); // Deprecated
        return cell.getStringCellValue().trim();
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
