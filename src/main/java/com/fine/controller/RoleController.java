package com.fine.controller;

import com.fine.Utils.ResponseResult;
import com.fine.modle.Role;
import com.fine.service.RoleService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 角色管理Controller
 */
@RestController
@RequestMapping("/api/roles")
@PreAuthorize("hasAuthority('admin')")
public class RoleController {

    @Autowired
    private RoleService roleService;

    /**
     * 获取所有角色列表
     */
    @GetMapping
    public ResponseResult<?> getAllRoles(
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "keyword", required = false) String keyword) {
        if (page != null || size != null || StringUtils.hasText(keyword)) {
            return roleService.getRolePage(page, size, keyword);
        }
        return roleService.getAllRoles();
    }

    /**
     * 根据ID获取角色
     */
    @GetMapping("/{id}")
    public ResponseResult<?> getRoleById(@PathVariable Long id) {
        return roleService.getRoleById(id);
    }

    /**
     * 创建角色
     */
    @PostMapping
    public ResponseResult<?> createRole(@RequestBody Role role) {
        return roleService.createRole(role);
    }

    /**
     * 更新角色
     */
    @PutMapping("/{id}")
    public ResponseResult<?> updateRole(@PathVariable Long id, @RequestBody Role role) {
        role.setId(id);
        return roleService.updateRole(role);
    }

    /**
     * 删除角色
     */
    @DeleteMapping("/{id}")
    public ResponseResult<?> deleteRole(@PathVariable Long id) {
        return roleService.deleteRole(id);
    }

    /**
     * 为用户分配角色
     */
    @PostMapping("/assign/{userId}")
    public ResponseResult<?> assignRolesToUser(@PathVariable Long userId, @RequestBody Map<String, List<Long>> body) {
        List<Long> roleIds = body.get("roleIds");
        return roleService.assignRolesToUser(userId, roleIds);
    }

    /**
     * 获取用户的角色ID列表
     */
    @GetMapping("/user/{userId}")
    public ResponseResult<?> getUserRoleIds(@PathVariable Long userId) {
        return roleService.getUserRoleIds(userId);
    }

    /**
     * 导出角色数据
     */
    @GetMapping("/export")
    public void exportRoles(HttpServletResponse response) {
        try {
            ResponseResult<?> result = roleService.getAllRoles();
            @SuppressWarnings("unchecked")
            List<Role> list = (List<Role>) result.getData();

            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("角色数据");

            // 表头样式
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            // 表头
            String[] headers = {"序号", "角色标识", "显示名称", "描述", "状态", "创建时间"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 5000);
            }

            // 数据行
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            int rowNum = 1;
            for (Role role : list) {
                Row row = sheet.createRow(rowNum);
                row.createCell(0).setCellValue(rowNum);
                row.createCell(1).setCellValue(role.getName() != null ? role.getName() : "");
                row.createCell(2).setCellValue(role.getDisplayName() != null ? role.getDisplayName() : "");
                row.createCell(3).setCellValue(role.getDescription() != null ? role.getDescription() : "");
                row.createCell(4).setCellValue(role.getStatus() != null && role.getStatus() == 1 ? "启用" : "禁用");
                row.createCell(5).setCellValue(role.getCreatedAt() != null ? role.getCreatedAt().format(dtf) : "");
                rowNum++;
            }

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment;filename=" +
                    URLEncoder.encode("角色数据.xlsx", "UTF-8"));

            OutputStream out = response.getOutputStream();
            workbook.write(out);
            out.flush();
            out.close();
            workbook.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 导入角色数据
     */
    @PostMapping("/import")
    public ResponseResult<?> importRoles(@RequestParam("file") MultipartFile file) {
        List<String> errors = new ArrayList<>();
        int successCount = 0;
        int failCount = 0;

        try {
            Workbook workbook = WorkbookFactory.create(file.getInputStream());
            Sheet sheet = workbook.getSheetAt(0);

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                try {
                    String name = getCellStringValue(row.getCell(1));
                    String displayName = getCellStringValue(row.getCell(2));
                    String description = getCellStringValue(row.getCell(3));
                    String statusStr = getCellStringValue(row.getCell(4));

                    if (!StringUtils.hasText(name)) {
                        errors.add("第" + (i + 1) + "行：角色标识不能为空");
                        failCount++;
                        continue;
                    }

                    Role role = new Role();
                    role.setName(name);
                    role.setDisplayName(displayName);
                    role.setDescription(description);
                    role.setStatus("启用".equals(statusStr) ? 1 : 0);                    // 调用service创建或更新角色
                    ResponseResult<?> result = roleService.createOrUpdateRole(role);
                    if (result.getCode() == 20000) {
                        successCount++;
                    } else {
                        errors.add("第" + (i + 1) + "行：" + result.getMsg());
                        failCount++;
                    }

                } catch (Exception e) {
                    errors.add("第" + (i + 1) + "行：" + e.getMessage());
                    failCount++;
                }
            }
            workbook.close();

        } catch (Exception e) {
            return ResponseResult.error(500, "导入失败: " + e.getMessage());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("successCount", successCount);
        result.put("failCount", failCount);
        result.put("errors", errors);

        if (failCount > 0) {
            return new ResponseResult<>(20000, "导入完成，成功" + successCount + "条，失败" + failCount + "条", result);
        }
        return new ResponseResult<>(20000, "导入成功，共" + successCount + "条", result);
    }

    /**
     * 下载角色导入模板
     */
    @GetMapping("/template")
    public void downloadTemplate(HttpServletResponse response) {
        try {
            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("角色导入模板");

            // 表头样式
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            // 表头
            String[] headers = {"序号", "角色标识", "显示名称", "描述", "状态"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 5000);
            }

            // 示例数据
            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue(1);
            row.createCell(1).setCellValue("viewer");
            row.createCell(2).setCellValue("访客");
            row.createCell(3).setCellValue("只有查看权限");
            row.createCell(4).setCellValue("启用");

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment;filename=" +
                    URLEncoder.encode("角色导入模板.xlsx", "UTF-8"));

            OutputStream out = response.getOutputStream();
            workbook.write(out);
            out.flush();
            out.close();
            workbook.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 获取单元格字符串值
     */
    private String getCellStringValue(Cell cell) {
        if (cell == null) return null;
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                return String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            default:
                return null;
        }
    }
}
