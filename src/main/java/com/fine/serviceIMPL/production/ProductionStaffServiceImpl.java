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
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * 生产人员Service实现类
 */
@Service
public class ProductionStaffServiceImpl extends ServiceImpl<ProductionStaffMapper, ProductionStaff> 
        implements ProductionStaffService {

    private static final DataFormatter CELL_FORMATTER = new DataFormatter();
    private static final String[] DATE_PATTERNS = {
            "yyyy-MM-dd",
            "yyyy/M/d",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy/M/d HH:mm:ss"
    };
        private static final DateTimeFormatter ID_CARD_BIRTH_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Autowired
    private ProductionStaffMapper staffMapper;

    @Autowired
    private StaffSkillMapper skillMapper;

    @Override
    public Map<String, Object> getStaffList(String staffCode, String staffName, Long teamId,
                            Long workshopId, String status, String department,
                            String positionName, Integer page, Integer size) {
        IPage<ProductionStaff> pageRequest = new Page<>(page, size);
        IPage<ProductionStaff> pageResult = staffMapper.selectStaffPageList(pageRequest, staffCode, staffName,
            teamId, workshopId, status, department, positionName);

        Map<String, Object> result = new HashMap<>();
        result.put("list", pageResult.getRecords());
        result.put("total", pageResult.getTotal());
        result.put("pageNum", pageResult.getCurrent());
        result.put("pageSize", pageResult.getSize());
        return result;
    }

    @Override
    public IPage<ProductionStaff> getStaffPage(String staffCode, String staffName, Long teamId,
                                               Long workshopId, String status, String department,
                                               String positionName, Integer page, Integer size) {
        IPage<ProductionStaff> pageRequest = new Page<>(page, size);
        IPage<ProductionStaff> pageResult = staffMapper.selectStaffPageList(pageRequest, staffCode, staffName,
                teamId, workshopId, status, department, positionName);
        return pageResult;
    }

    @Override
    public ProductionStaff getStaffDetail(Long id) {
        ProductionStaff staff = staffMapper.selectStaffById(id);
        if (staff != null) {
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

        fillGenderAndAgeByIdCard(staff);
        
        staff.setCreateTime(new Date());
        staff.setUpdateTime(new Date());
        staff.setIsDeleted(0);
        int rows;
        try {
            rows = staffMapper.insert(staff);
        } catch (DataIntegrityViolationException e) {
            throw new RuntimeException("工号已存在：" + staff.getStaffCode());
        }
        
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

        fillGenderAndAgeByIdCard(staff);
        
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
        if (id == null) {
            return false;
        }
        // 使用 MyBatis-Plus 逻辑删除能力（@TableLogic）
        return staffMapper.deleteById(id) > 0;
    }

    @Override
    @Transactional
    public boolean batchDeleteStaff(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return false;
        }
        return staffMapper.deleteBatchIds(ids) > 0;
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
    public List<ProductionStaff> getStaffListForExport(Long teamId, Long workshopId, String status,
                                                       String department, String positionName) {
        return staffMapper.selectStaffList(null, null, teamId, workshopId, status, department, positionName);
    }

    @Override
    @Transactional
    public Map<String, Object> importStaff(MultipartFile file) throws Exception {
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
                    staff.setDepartment(getCellStringValue(row.getCell(2)));
                    staff.setPositionName(getCellStringValue(row.getCell(3)));
                    staff.setEducation(getCellStringValue(row.getCell(4)));
                    staff.setNativePlace(getCellStringValue(row.getCell(5)));
                    staff.setPhone(getCellStringValue(row.getCell(6)));
                    staff.setIdCardNo(getCellStringValue(row.getCell(7)));
                    staff.setHouseholdAddress(getCellStringValue(row.getCell(8)));
                    staff.setCurrentAddress(getCellStringValue(row.getCell(9)));
                    staff.setEmergencyContact(getCellStringValue(row.getCell(10)));
                    staff.setEmergencyRelation(getCellStringValue(row.getCell(11)));
                    staff.setContractSignDate(getCellDateValue(row.getCell(12)));
                    staff.setMedicalExamDate(getCellDateValue(row.getCell(13)));
                    staff.setMedicalExamResult(getCellStringValue(row.getCell(14)));
                    staff.setTeamId(getCellLongValue(row.getCell(15)));
                    staff.setWorkshopId(getCellLongValue(row.getCell(16)));

                    String skillLevel = getCellStringValue(row.getCell(17));
                    staff.setSkillLevel(skillLevel != null && !skillLevel.isEmpty() ? skillLevel : "junior");
                    staff.setEntryDate(getCellDateValue(row.getCell(18)));
                    String statusVal = getCellStringValue(row.getCell(19));
                    staff.setStatus(statusVal != null && !statusVal.isEmpty() ? statusVal : "active");
                    staff.setRemark(getCellStringValue(row.getCell(20)));

                    fillGenderAndAgeByIdCard(staff);

                    staff.setCreateTime(new Date());
                    staff.setUpdateTime(new Date());
                    staff.setIsDeleted(0);

                    try {
                        staffMapper.insert(staff);
                    } catch (DataIntegrityViolationException e) {
                        errorMsg.append("第").append(i + 1).append("行：工号已存在（").append(staff.getStaffCode()).append("）\n");
                        failCount++;
                        continue;
                    }
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
    @Transactional
    public Map<String, Object> backfillGenderAgeByIdCard() {
      Map<String, Object> result = new HashMap<>();
      int total = 0;
      int updated = 0;
      int skipped = 0;

      LambdaQueryWrapper<ProductionStaff> wrapper = new LambdaQueryWrapper<>();
      wrapper.eq(ProductionStaff::getIsDeleted, 0);
      List<ProductionStaff> list = staffMapper.selectList(wrapper);
      if (list == null) {
          list = Collections.emptyList();
      }
      total = list.size();

      for (ProductionStaff item : list) {
          if (item == null) continue;
          String idCard = item.getIdCardNo();
          if (idCard == null || idCard.trim().isEmpty()) {
              skipped++;
              continue;
          }

          String oldGender = item.getGender();
          Integer oldAge = item.getAge();
          fillGenderAndAgeByIdCard(item);

          if (Objects.equals(oldGender, item.getGender()) && Objects.equals(oldAge, item.getAge())) {
              skipped++;
              continue;
          }

          ProductionStaff patch = new ProductionStaff();
          patch.setId(item.getId());
          patch.setGender(item.getGender());
          patch.setAge(item.getAge());
          patch.setIdCardNo(item.getIdCardNo());
          patch.setUpdateTime(new Date());
          if (staffMapper.updateById(patch) > 0) {
              updated++;
          }
      }

      result.put("total", total);
      result.put("updated", updated);
      result.put("skipped", skipped);
      return result;
    }

    // 辅助方法：获取单元格字符串值
    private String getCellStringValue(Cell cell) {
        if (cell == null) return null;
        try {
            String text = CELL_FORMATTER.formatCellValue(cell);
            if (text == null) {
                return null;
            }
            text = text.trim();
            return text.isEmpty() ? null : text;
        } catch (Exception e) {
            return null;
        }
    }

    // 辅助方法：获取单元格Long值
    private Long getCellLongValue(Cell cell) {
        if (cell == null) return null;
        try {
            if (cell.getCellType() == CellType.NUMERIC) {
                return (long) cell.getNumericCellValue();
            }

            String val = getCellStringValue(cell);
            if (val == null || val.isEmpty()) {
                return null;
            }
            val = val.replace(",", "");
            return new BigDecimal(val).longValue();
        } catch (Exception e) {
            return null;
        }
    }

    // 辅助方法：获取单元格日期值
    private Date getCellDateValue(Cell cell) {
        if (cell == null) return null;
        try {
            if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                return cell.getDateCellValue();
            }

            String val = getCellStringValue(cell);
            if (val == null || val.isEmpty()) {
                return null;
            }

            for (String pattern : DATE_PATTERNS) {
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat(pattern);
                    sdf.setLenient(false);
                    return sdf.parse(val);
                } catch (Exception ignored) {
                }
            }

            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private void fillGenderAndAgeByIdCard(ProductionStaff staff) {
        if (staff == null) {
            return;
        }
        String idCardNo = staff.getIdCardNo();
        if (idCardNo == null) {
            return;
        }
        String normalized = idCardNo.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("^\\d{17}[0-9X]$")) {
            return;
        }

        try {
            LocalDate birthDate = LocalDate.parse(normalized.substring(6, 14), ID_CARD_BIRTH_FORMATTER);
            LocalDate now = LocalDate.now();
            int age = Math.max(0, Period.between(birthDate, now).getYears());

            int genderBit = Character.digit(normalized.charAt(16), 10);
            if (genderBit < 0) {
                return;
            }
            staff.setGender((genderBit % 2 == 1) ? "M" : "F");
            staff.setAge(age);
            staff.setIdCardNo(normalized);
        } catch (Exception ignored) {
        }
    }
}
