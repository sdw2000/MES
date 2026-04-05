package com.fine.controller.production;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fine.model.production.ProductionStaff;
import com.fine.model.production.ProductionTeam;
import com.fine.model.production.ProductionLeaveRecord;
import com.fine.model.production.ProductionOvertimeRecord;
import com.fine.model.production.StaffSkill;
import com.fine.service.production.ProductionLeaveRecordService;
import com.fine.service.production.ProductionOvertimeRecordService;
import com.fine.service.production.ProductionStaffService;
import com.fine.service.production.ProductionTeamService;
import com.fine.Dao.production.ShiftDefinitionMapper;
import com.fine.model.production.ShiftDefinition;
import com.fine.Utils.ResponseResult;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.format.annotation.DateTimeFormat;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 人员管理Controller
 */
@Api(tags = "人员管理")
@RestController
@RequestMapping("/production/staff")
public class ProductionStaffController {

    @Autowired
    private ProductionStaffService staffService;

    @Autowired
    private ProductionTeamService teamService;

    @Autowired
    private ShiftDefinitionMapper shiftMapper;

    @Autowired
    private ProductionLeaveRecordService leaveRecordService;

    @Autowired
    private ProductionOvertimeRecordService overtimeRecordService;

    // ==================== 人员管理 ====================

    @ApiOperation("分页查询人员列表")
    @GetMapping("/list")
    public ResponseResult<Map<String, Object>> getStaffList(
            @RequestParam(required = false) String staffCode,
            @RequestParam(required = false) String staffName,
            @RequestParam(required = false) Long teamId,
            @RequestParam(required = false) Long workshopId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String positionName,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        IPage<ProductionStaff> pageResult = staffService.getStaffPage(staffCode, staffName, teamId, workshopId,
                status, department, positionName, page, size);
        
        Map<String, Object> result = new HashMap<>();
        result.put("list", pageResult.getRecords());
        result.put("total", pageResult.getTotal());
        result.put("page", pageResult.getCurrent());
        result.put("size", pageResult.getSize());
        result.put("pages", pageResult.getPages());
        result.put("hasNextPage", pageResult.getCurrent() < pageResult.getPages());
        
        return ResponseResult.success(result);
    }

    @ApiOperation("获取人员详情")
    @GetMapping("/{id}")
    public ResponseResult<ProductionStaff> getStaffDetail(@PathVariable Long id) {
        ProductionStaff staff = staffService.getStaffDetail(id);
        if (staff == null) {
            return ResponseResult.fail("人员不存在");
        }
        return ResponseResult.success(staff);
    }

    @ApiOperation("新增人员")
    @PostMapping
    public ResponseResult<Void> addStaff(@RequestBody ProductionStaff staff) {
        try {
            boolean success = staffService.addStaff(staff);
            return success ? ResponseResult.success() : ResponseResult.fail("新增失败");
        } catch (RuntimeException e) {
            return ResponseResult.fail(e.getMessage());
        }
    }

    @ApiOperation("更新人员")
    @PutMapping("/{id}")
    public ResponseResult<Void> updateStaff(@PathVariable Long id, @RequestBody ProductionStaff staff) {
        try {
            staff.setId(id);
            boolean success = staffService.updateStaff(staff);
            return success ? ResponseResult.success() : ResponseResult.fail("更新失败");
        } catch (RuntimeException e) {
            return ResponseResult.fail(e.getMessage());
        }
    }

    @ApiOperation("删除人员")
    @DeleteMapping("/{id}")
    public ResponseResult<Void> deleteStaff(@PathVariable Long id) {
        boolean success = staffService.deleteStaff(id);
        return success ? ResponseResult.success() : ResponseResult.fail("删除失败");
    }

    @ApiOperation("批量删除人员")
    @DeleteMapping("/batch")
    public ResponseResult<Void> batchDeleteStaff(@RequestBody List<Long> ids) {
        boolean success = staffService.batchDeleteStaff(ids);
        return success ? ResponseResult.success() : ResponseResult.fail("批量删除失败");
    }

    @ApiOperation("获取人员技能列表")
    @GetMapping("/{id}/skills")
    public ResponseResult<List<StaffSkill>> getStaffSkills(@PathVariable Long id) {
        List<StaffSkill> skills = staffService.getStaffSkills(id);
        return ResponseResult.success(skills);
    }

    @ApiOperation("保存人员技能")
    @PostMapping("/{id}/skills")
    public ResponseResult<Void> saveSkills(@PathVariable Long id, @RequestBody List<StaffSkill> skills) {
        boolean success = staffService.saveSkills(id, skills);
        return success ? ResponseResult.success() : ResponseResult.fail("保存技能失败");
    }

    @ApiOperation("根据设备类型查询可操作人员")
    @GetMapping("/byEquipmentType/{equipmentType}")
    public ResponseResult<List<ProductionStaff>> getStaffByEquipmentType(@PathVariable String equipmentType) {
        List<ProductionStaff> list = staffService.getStaffByEquipmentType(equipmentType);
        return ResponseResult.success(list);
    }

    @ApiOperation("查询所有在职人员")
    @GetMapping("/active")
    public ResponseResult<List<ProductionStaff>> getAllActiveStaff() {
        List<ProductionStaff> list = staffService.getAllActiveStaff();
        return ResponseResult.success(list);
    }

    // ==================== 班组管理 ====================    @ApiOperation("分页查询班组列表")
    @GetMapping("/team/list")
    public ResponseResult<Map<String, Object>> getTeamList(
            @RequestParam(required = false) String teamCode,
            @RequestParam(required = false) String teamName,
            @RequestParam(required = false) Long workshopId,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        IPage<ProductionTeam> pageResult = teamService.getTeamPage(teamCode, teamName, workshopId, status, page, size);
        
        Map<String, Object> result = new HashMap<>();
        result.put("list", pageResult.getRecords());
        result.put("total", pageResult.getTotal());
        result.put("page", pageResult.getCurrent());
        result.put("size", pageResult.getSize());
        result.put("pages", pageResult.getPages());
        result.put("hasNextPage", pageResult.getCurrent() < pageResult.getPages());
        
        return ResponseResult.success(result);
    }

    @ApiOperation("获取班组详情")
    @GetMapping("/team/{id}")
    public ResponseResult<ProductionTeam> getTeamDetail(@PathVariable Long id) {
        ProductionTeam team = teamService.getTeamDetail(id);
        if (team == null) {
            return ResponseResult.fail("班组不存在");
        }
        return ResponseResult.success(team);
    }

    @ApiOperation("新增班组")
    @PostMapping("/team")
    public ResponseResult<Void> addTeam(@RequestBody ProductionTeam team) {
        try {
            boolean success = teamService.addTeam(team);
            return success ? ResponseResult.success() : ResponseResult.fail("新增失败");
        } catch (RuntimeException e) {
            return ResponseResult.fail(e.getMessage());
        }
    }

    @ApiOperation("更新班组")
    @PutMapping("/team/{id}")
    public ResponseResult<Void> updateTeam(@PathVariable Long id, @RequestBody ProductionTeam team) {
        try {
            team.setId(id);
            boolean success = teamService.updateTeam(team);
            return success ? ResponseResult.success() : ResponseResult.fail("更新失败");
        } catch (RuntimeException e) {
            return ResponseResult.fail(e.getMessage());
        }
    }

    @ApiOperation("删除班组")
    @DeleteMapping("/team/{id}")
    public ResponseResult<Void> deleteTeam(@PathVariable Long id) {
        try {
            boolean success = teamService.deleteTeam(id);
            return success ? ResponseResult.success() : ResponseResult.fail("删除失败");
        } catch (RuntimeException e) {
            return ResponseResult.fail(e.getMessage());
        }
    }

    @ApiOperation("根据车间查询班组")
    @GetMapping("/team/byWorkshop/{workshopId}")
    public ResponseResult<List<ProductionTeam>> getTeamsByWorkshop(@PathVariable Long workshopId) {
        List<ProductionTeam> list = teamService.getTeamsByWorkshop(workshopId);
        return ResponseResult.success(list);
    }

    @ApiOperation("查询所有启用的班组")
    @GetMapping("/team/active")
    public ResponseResult<List<ProductionTeam>> getAllActiveTeams() {
        List<ProductionTeam> list = teamService.getAllActiveTeams();
        return ResponseResult.success(list);
    }

    // ==================== 请假管理 ====================

    @ApiOperation("查询请假记录")
    @GetMapping("/leave/list")
    public ResponseResult<List<ProductionLeaveRecord>> getLeaveList(
            @RequestParam(required = false) Long staffId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date endDate) {
        List<ProductionLeaveRecord> list = leaveRecordService.getLeaveList(staffId, status, startDate, endDate);
        return ResponseResult.success(list);
    }

    @ApiOperation("新增请假记录")
    @PostMapping("/leave")
    public ResponseResult<Void> addLeaveRecord(@RequestBody ProductionLeaveRecord record) {
        boolean success = leaveRecordService.addLeaveRecord(record);
        return success ? ResponseResult.success() : ResponseResult.fail("新增请假记录失败");
    }

    @ApiOperation("更新请假记录")
    @PutMapping("/leave/{id}")
    public ResponseResult<Void> updateLeaveRecord(@PathVariable Long id, @RequestBody ProductionLeaveRecord record) {
        record.setId(id);
        boolean success = leaveRecordService.updateLeaveRecord(record);
        return success ? ResponseResult.success() : ResponseResult.fail("更新请假记录失败");
    }

    @ApiOperation("审批请假记录")
    @PutMapping("/leave/{id}/approve")
    public ResponseResult<Void> approveLeaveRecord(@PathVariable Long id, @RequestParam String status) {
        if (!"approved".equals(status) && !"rejected".equals(status)) {
            return ResponseResult.fail("审批状态仅支持approved/rejected");
        }
        boolean success = leaveRecordService.approveLeaveRecord(id, status);
        return success ? ResponseResult.success() : ResponseResult.fail("审批请假记录失败");
    }

    @ApiOperation("删除请假记录")
    @DeleteMapping("/leave/{id}")
    public ResponseResult<Void> deleteLeaveRecord(@PathVariable Long id) {
        boolean success = leaveRecordService.deleteLeaveRecord(id);
        return success ? ResponseResult.success() : ResponseResult.fail("删除请假记录失败");
    }

    // ==================== 加班管理 ====================

    @ApiOperation("查询加班记录")
    @GetMapping("/overtime/list")
    public ResponseResult<List<ProductionOvertimeRecord>> getOvertimeList(
            @RequestParam(required = false) Long staffId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date endDate) {
        List<ProductionOvertimeRecord> list = overtimeRecordService.getOvertimeList(staffId, status, startDate, endDate);
        return ResponseResult.success(list);
    }

    @ApiOperation("新增加班记录")
    @PostMapping("/overtime")
    public ResponseResult<Void> addOvertimeRecord(@RequestBody ProductionOvertimeRecord record) {
        boolean success = overtimeRecordService.addOvertimeRecord(record);
        return success ? ResponseResult.success() : ResponseResult.fail("新增加班记录失败");
    }

    @ApiOperation("更新加班记录")
    @PutMapping("/overtime/{id}")
    public ResponseResult<Void> updateOvertimeRecord(@PathVariable Long id, @RequestBody ProductionOvertimeRecord record) {
        record.setId(id);
        boolean success = overtimeRecordService.updateOvertimeRecord(record);
        return success ? ResponseResult.success() : ResponseResult.fail("更新加班记录失败");
    }

    @ApiOperation("审批加班记录")
    @PutMapping("/overtime/{id}/approve")
    public ResponseResult<Void> approveOvertimeRecord(@PathVariable Long id, @RequestParam String status) {
        if (!"approved".equals(status) && !"rejected".equals(status)) {
            return ResponseResult.fail("审批状态仅支持approved/rejected");
        }
        boolean success = overtimeRecordService.approveOvertimeRecord(id, status);
        return success ? ResponseResult.success() : ResponseResult.fail("审批加班记录失败");
    }

    @ApiOperation("删除加班记录")
    @DeleteMapping("/overtime/{id}")
    public ResponseResult<Void> deleteOvertimeRecord(@PathVariable Long id) {
        boolean success = overtimeRecordService.deleteOvertimeRecord(id);
        return success ? ResponseResult.success() : ResponseResult.fail("删除加班记录失败");
    }

    // ==================== 班次 ====================

    @ApiOperation("查询所有班次")
    @GetMapping("/shift/list")
    public ResponseResult<List<ShiftDefinition>> getAllShifts() {
        List<ShiftDefinition> list = shiftMapper.selectAllActive();
        return ResponseResult.success(list);
    }

    // ==================== 导入导出 ====================

    /**
     * 下载人员导入模板
     * GET /production/staff/template
     */
    @ApiOperation("下载人员导入模板")
    @GetMapping("/template")
    public void downloadTemplate(HttpServletResponse response) throws IOException {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("人员导入模板");
        
        // 表头
        Row header = sheet.createRow(0);
        String[] headers = {"工号", "姓名", "部门", "岗位", "学历", "籍贯", "联系电话",
            "身份证号码", "户口所在地", "现居住址", "紧急联系人", "紧急联系人关系", "签约日期", "体检日期", "体检情况",
            "班组ID", "车间ID", "技能等级(junior/middle/senior)", "入职日期", "状态(active/leave/resigned)", "备注"};
        for (int i = 0; i < headers.length; i++) {
            header.createCell(i).setCellValue(headers[i]);
        }
        
        // 示例数据
        Row row = sheet.createRow(1);
        row.createCell(0).setCellValue("S001");
        row.createCell(1).setCellValue("张三");
        row.createCell(2).setCellValue("生产部");
        row.createCell(3).setCellValue("生产员工");
        row.createCell(4).setCellValue("高中");
        row.createCell(5).setCellValue("广东省");
        row.createCell(6).setCellValue("13800138000");
        row.createCell(7).setCellValue("440101199001011234");
        row.createCell(8).setCellValue("广州市天河区");
        row.createCell(9).setCellValue("广州市白云区XX路");
        row.createCell(10).setCellValue("李四");
        row.createCell(11).setCellValue("配偶");
        row.createCell(12).setCellValue("2026-01-01");
        row.createCell(13).setCellValue("2026-01-05");
        row.createCell(14).setCellValue("合格");
        row.createCell(15).setCellValue(1);
        row.createCell(16).setCellValue(1);
        row.createCell(17).setCellValue("middle");
        row.createCell(18).setCellValue("2024-01-15");
        row.createCell(19).setCellValue("active");
        row.createCell(20).setCellValue("");
        
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment;filename=" + 
                URLEncoder.encode("人员导入模板.xlsx", "UTF-8"));
        workbook.write(response.getOutputStream());
        workbook.close();
    }

    /**
     * 导出人员数据
     * GET /production/staff/export
     */
    @ApiOperation("导出人员数据")
    @GetMapping("/export")
    public void exportStaff(
            @RequestParam(required = false) Long teamId,
            @RequestParam(required = false) Long workshopId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String positionName,
            HttpServletResponse response) throws IOException {
        List<ProductionStaff> list = staffService.getStaffListForExport(teamId, workshopId, status, department, positionName);
        
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("人员数据");
        
        // 表头
        Row header = sheet.createRow(0);
        String[] headers = {"工号", "姓名", "性别", "部门", "岗位", "学历", "年龄", "籍贯", "联系电话", "身份证号码",
            "户口所在地", "现居住址", "紧急联系人", "紧急联系人关系", "签约日期", "体检日期", "体检情况",
            "班组名称", "车间名称", "技能等级", "入职日期", "状态", "备注"};
        for (int i = 0; i < headers.length; i++) {
            header.createCell(i).setCellValue(headers[i]);
        }
        
        // 数据
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        for (int i = 0; i < list.size(); i++) {
            ProductionStaff staff = list.get(i);
            Row row = sheet.createRow(i + 1);
            row.createCell(0).setCellValue(staff.getStaffCode() != null ? staff.getStaffCode() : "");
            row.createCell(1).setCellValue(staff.getStaffName() != null ? staff.getStaffName() : "");
            row.createCell(2).setCellValue(staff.getGender() != null ? staff.getGender() : "");
            row.createCell(3).setCellValue(staff.getDepartment() != null ? staff.getDepartment() : "");
            row.createCell(4).setCellValue(staff.getPositionName() != null ? staff.getPositionName() : "");
            row.createCell(5).setCellValue(staff.getEducation() != null ? staff.getEducation() : "");
            row.createCell(6).setCellValue(staff.getAge() != null ? staff.getAge() : 0);
            row.createCell(7).setCellValue(staff.getNativePlace() != null ? staff.getNativePlace() : "");
            row.createCell(8).setCellValue(staff.getPhone() != null ? staff.getPhone() : "");
            row.createCell(9).setCellValue(staff.getIdCardNo() != null ? staff.getIdCardNo() : "");
            row.createCell(10).setCellValue(staff.getHouseholdAddress() != null ? staff.getHouseholdAddress() : "");
            row.createCell(11).setCellValue(staff.getCurrentAddress() != null ? staff.getCurrentAddress() : "");
            row.createCell(12).setCellValue(staff.getEmergencyContact() != null ? staff.getEmergencyContact() : "");
            row.createCell(13).setCellValue(staff.getEmergencyRelation() != null ? staff.getEmergencyRelation() : "");
            row.createCell(14).setCellValue(staff.getContractSignDate() != null ? sdf.format(staff.getContractSignDate()) : "");
            row.createCell(15).setCellValue(staff.getMedicalExamDate() != null ? sdf.format(staff.getMedicalExamDate()) : "");
            row.createCell(16).setCellValue(staff.getMedicalExamResult() != null ? staff.getMedicalExamResult() : "");
            row.createCell(17).setCellValue(staff.getTeamName() != null ? staff.getTeamName() : "");
            row.createCell(18).setCellValue(staff.getWorkshopName() != null ? staff.getWorkshopName() : "");
            row.createCell(19).setCellValue(staff.getSkillLevel() != null ? staff.getSkillLevel() : "");
            row.createCell(20).setCellValue(staff.getEntryDate() != null ? sdf.format(staff.getEntryDate()) : "");
            row.createCell(21).setCellValue(staff.getStatus() != null ? staff.getStatus() : "");
            row.createCell(22).setCellValue(staff.getRemark() != null ? staff.getRemark() : "");
        }
        
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment;filename=" + 
                URLEncoder.encode("人员数据.xlsx", "UTF-8"));
        workbook.write(response.getOutputStream());
        workbook.close();
    }

    /**
     * 导入人员数据
     * POST /production/staff/import
     */
    @ApiOperation("导入人员数据")
    @PostMapping("/import")
    public ResponseResult<?> importStaff(@RequestParam("file") MultipartFile file) {
        try {
            Map<String, Object> result = staffService.importStaff(file);
            if ((boolean) result.get("success")) {
                return ResponseResult.success(result);
            } else {
                return ResponseResult.fail((String) result.get("message"));
            }
        } catch (Exception e) {
            return ResponseResult.fail("导入失败: " + e.getMessage());
        }
    }

    /**
     * 一次性回填老数据：根据身份证重算性别和年龄
     * POST /production/staff/backfill/gender-age
     */
    @ApiOperation("一次性回填性别年龄")
    @PostMapping("/backfill/gender-age")
    public ResponseResult<Map<String, Object>> backfillGenderAge() {
        Map<String, Object> result = staffService.backfillGenderAgeByIdCard();
        return ResponseResult.success(result);
    }
}
