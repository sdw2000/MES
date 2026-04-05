package com.fine.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fine.Dao.UserMapper;
import com.fine.Dao.CustomerMapper;
import com.fine.Dao.production.ProductionStaffMapper;
import com.fine.modle.User;
import com.fine.Utils.ResponseResult;
import com.fine.model.production.ProductionStaff;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.servlet.http.HttpServletResponse;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.fine.modle.LoginUser;

/**
 * 用户管理Controller
 */
@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasAuthority('admin')")
public class UserController {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private CustomerMapper customerMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ProductionStaffMapper productionStaffMapper;

    /**
     * 分页查询用户列表
     */
    @GetMapping
    public ResponseResult<Map<String, Object>> list(
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "size", defaultValue = "10") Integer size,
            @RequestParam(value = "username", required = false) String username) {
        
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(username)) {
            wrapper.like(User::getUsername, username);
        }
        wrapper.orderByDesc(User::getId);

        Page<User> pageParam = new Page<>(page, size);
        // 禁用MyBatis-Plus的COUNT优化，避免生成错误的 `SELECT COUNT()` SQL
        pageParam.setOptimizeCountSql(false);
        IPage<User> result = userMapper.selectPage(pageParam, wrapper);

        // 清除密码字段
        result.getRecords().forEach(user -> user.setPassword(null));

        Map<String, Object> data = new HashMap<>();
        data.put("list", result.getRecords());
        data.put("total", result.getTotal());
        data.put("page", result.getCurrent());
        data.put("size", result.getSize());

        return ResponseResult.success(data);
    }

    /**
     * 获取所有用户简单列表（不需要admin权限，用于下拉选择）
     */
    @GetMapping("/simple")
    @PreAuthorize("isAuthenticated()")
    public ResponseResult<Map<String, Object>> getSimpleList(
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "size", defaultValue = "1000") Integer size,
            @RequestParam(value = "roleKeyword", required = false) String roleKeyword,
            @RequestParam(value = "source", required = false) String source,
            @RequestParam(value = "roleType", required = false) String roleType,
            @RequestParam(value = "ownerScope", required = false) String ownerScope) {

        Map<String, Object> data = new HashMap<>();

        if (StringUtils.hasText(roleKeyword)) {
            List<User> fullList = userMapper.selectUsersByRoleKeyword(roleKeyword.trim());
            int total = fullList.size();
            int startIndex = Math.max(0, (page - 1) * size);
            int endIndex = Math.min(total, startIndex + size);
            List<User> pageList = startIndex >= total ? new ArrayList<>() : new ArrayList<>(fullList.subList(startIndex, endIndex));

            // 清除敏感字段
            pageList.forEach(user -> {
                user.setPassword(null);
                user.setEmail(null);
            });

            data.put("list", pageList);
            data.put("records", pageList); // 兼容前端
            data.put("total", total);
            return ResponseResult.success(data);
        }

        if (StringUtils.hasText(source) && "customer".equalsIgnoreCase(source.trim())) {
            boolean selfScope = StringUtils.hasText(ownerScope) && "self".equalsIgnoreCase(ownerScope.trim());
            LoginUser loginUser = getLoginUser();
            Long currentUserId = getCurrentUserId(loginUser);
            List<Long> userIds;

            if (StringUtils.hasText(roleType) && "documentation".equalsIgnoreCase(roleType.trim())) {
                if (selfScope) {
                    if (currentUserId == null) {
                        data.put("list", new ArrayList<>());
                        data.put("records", new ArrayList<>());
                        data.put("total", 0);
                        return ResponseResult.success(data);
                    }
                    userIds = customerMapper.selectDistinctDocumentationUserIdsBySalesUser(currentUserId);
                } else {
                    userIds = customerMapper.selectDistinctDocumentationUserIds();
                }
            } else {
                if (selfScope) {
                    if (currentUserId == null) {
                        data.put("list", new ArrayList<>());
                        data.put("records", new ArrayList<>());
                        data.put("total", 0);
                        return ResponseResult.success(data);
                    }
                    userIds = customerMapper.selectDistinctSalesUserIdsByDocumentationUser(currentUserId);
                } else {
                    userIds = customerMapper.selectDistinctSalesUserIds();
                }
            }

            if (userIds == null || userIds.isEmpty()) {
                data.put("list", new ArrayList<>());
                data.put("records", new ArrayList<>());
                data.put("total", 0);
                return ResponseResult.success(data);
            }

            LambdaQueryWrapper<User> customerUserWrapper = new LambdaQueryWrapper<>();
            customerUserWrapper.in(User::getId, userIds)
                    .eq(User::getStatus, 0)
                    .eq(User::getDelFlag, 0)
                    .orderByAsc(User::getUsername);

            List<User> fullList = userMapper.selectList(customerUserWrapper);
            int total = fullList.size();
            int startIndex = Math.max(0, (page - 1) * size);
            int endIndex = Math.min(total, startIndex + size);
            List<User> pageList = startIndex >= total ? new ArrayList<>() : new ArrayList<>(fullList.subList(startIndex, endIndex));

            pageList.forEach(user -> {
                user.setPassword(null);
                user.setEmail(null);
            });

            data.put("list", pageList);
            data.put("records", pageList);
            data.put("total", total);
            return ResponseResult.success(data);
        }

        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getStatus, 0) // 只查询正常状态的用户
               .eq(User::getDelFlag, 0) // 未删除的用户
               .orderByAsc(User::getUsername);

        Page<User> pageParam = new Page<>(page, size);
        pageParam.setOptimizeCountSql(false);
        IPage<User> result = userMapper.selectPage(pageParam, wrapper);

        // 清除敏感字段
        result.getRecords().forEach(user -> {
            user.setPassword(null);
            user.setEmail(null);
        });

        data.put("list", result.getRecords());
        data.put("records", result.getRecords()); // 兼容前端
        data.put("total", result.getTotal());

        return ResponseResult.success(data);
    }

    /**
     * 获取用户详情
     */
    @GetMapping("/{id}")
    public ResponseResult<User> getById(@PathVariable Long id) {
        User user = userMapper.selectById(id);
        if (user != null) {
            user.setPassword(null);
        }
        return ResponseResult.success(user);
    }

    /**
     * 新增用户
     */
    @PostMapping
    public ResponseResult<User> create(@RequestBody User user) {
        if (user.getStaffId() == null) {
            return ResponseResult.error(400, "请先选择关联人员");
        }
        if (user.getUsername() != null) {
            user.setUsername(user.getUsername().trim());
        }
        // 检查用户名是否已存在
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, user.getUsername());
        if (userMapper.selectCount(wrapper) > 0) {
            return ResponseResult.error(400, "用户名已存在");
        }        // 加密密码
        if (StringUtils.hasText(user.getPassword())) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        } else {
            // 默认密码
            user.setPassword(passwordEncoder.encode("123456"));
        }

        if (user.getStaffId() != null) {
            ProductionStaff staff = productionStaffMapper.selectById(user.getStaffId());
            if (staff == null || "resigned".equalsIgnoreCase(staff.getStatus())) {
                return ResponseResult.error(400, "关联人员不存在或已离职");
            }
            user.setRealName(staff.getStaffName());
        }

        user.setStatus(0);  // 0表示正常状态
        user.setDelFlag(0); // 0表示未删除
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        // 如果客户端误传了 id（例如超长 snowflake id），清空以使用数据库自增
        if (user.getId() != null) {
            System.out.println("Warning: create user request contained id=" + user.getId() + ", clearing to use AUTO_INCREMENT.");
            user.setId(null);
        }

        userMapper.insert(user);
        user.setPassword(null);
        return ResponseResult.success(user);
    }

    /**
     * 更新用户
     */
    @PutMapping("/{id}")
    public ResponseResult<User> update(@PathVariable Long id, @RequestBody User user) {
        User existingUser = userMapper.selectById(id);
        if (existingUser == null) {
            return ResponseResult.error(404, "用户不存在");
        }

        // 检查用户名是否与其他用户冲突
        if (StringUtils.hasText(user.getUsername())) {
            user.setUsername(user.getUsername().trim());
            LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(User::getUsername, user.getUsername());
            wrapper.ne(User::getId, id);
            if (userMapper.selectCount(wrapper) > 0) {
                return ResponseResult.error(400, "用户名已存在");
            }
            existingUser.setUsername(user.getUsername());
        }

        if (StringUtils.hasText(user.getRealName())) {
            existingUser.setRealName(user.getRealName());
        }

        if (user.getStaffId() == null) {
            return ResponseResult.error(400, "请先选择关联人员");
        }
        ProductionStaff staff = productionStaffMapper.selectById(user.getStaffId());
        if (staff == null || "resigned".equalsIgnoreCase(staff.getStatus())) {
            return ResponseResult.error(400, "关联人员不存在或已离职");
        }
        existingUser.setStaffId(user.getStaffId());
        existingUser.setRealName(staff.getStaffName());

        // 如果提供了新密码，则更新密码
        if (StringUtils.hasText(user.getPassword())) {
            existingUser.setPassword(passwordEncoder.encode(user.getPassword()));
        }

        if (user.getStatus() != null) {
            existingUser.setStatus(user.getStatus());
        }

        existingUser.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(existingUser);

        existingUser.setPassword(null);
        return ResponseResult.success(existingUser);
    }

    /**
     * 删除用户
     */
    @DeleteMapping("/{id}")
    public ResponseResult<Void> delete(@PathVariable Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            return ResponseResult.error(404, "用户不存在");
        }

        // 不能删除admin用户
        if ("admin".equals(user.getUsername())) {
            return ResponseResult.error(400, "不能删除管理员账户");
        }

        userMapper.deleteById(id);

        return ResponseResult.success();
    }

    /**
     * 重置密码
     */
    @PostMapping("/{id}/reset-password")
    public ResponseResult<Void> resetPassword(@PathVariable Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            return ResponseResult.error(404, "用户不存在");
        }

        user.setPassword(passwordEncoder.encode("123456"));
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);

        return ResponseResult.success();
    }

    /**
     * 修改当前用户密码
     */
    @PostMapping("/change-password")
    @PreAuthorize("isAuthenticated()")
    public ResponseResult<Void> changePassword(@RequestBody ChangePasswordRequest request) {
        if (request == null || !StringUtils.hasText(request.getOldPassword()) || !StringUtils.hasText(request.getNewPassword())) {
            return ResponseResult.error(400, "参数不完整");
        }
        if (request.getNewPassword().length() < 6) {
            return ResponseResult.error(400, "新密码长度至少6位");
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser)) {
            return ResponseResult.error(401, "未登录");
        }
        LoginUser loginUser = (LoginUser) authentication.getPrincipal();
        Long userId = loginUser.getUser().getId();
        User user = userMapper.selectById(userId);
        if (user == null) {
            return ResponseResult.error(404, "用户不存在");
        }
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            return ResponseResult.error(400, "旧密码不正确");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        return ResponseResult.success();
    }

    public static class ChangePasswordRequest {
        private String oldPassword;
        private String newPassword;

        public String getOldPassword() {
            return oldPassword;
        }

        public void setOldPassword(String oldPassword) {
            this.oldPassword = oldPassword;
        }

        public String getNewPassword() {
            return newPassword;
        }

        public void setNewPassword(String newPassword) {
            this.newPassword = newPassword;
        }
    }

    /**
     * 导出用户数据
     */
    @GetMapping("/export")
    public void exportUsers(HttpServletResponse response,
                            @RequestParam(required = false) String username) {
        try {
            LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
            if (StringUtils.hasText(username)) {
                wrapper.like(User::getUsername, username);
            }
            wrapper.eq(User::getDelFlag, 0);
            wrapper.orderByDesc(User::getId);
            List<User> list = userMapper.selectList(wrapper);

            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("用户数据");

            // 表头样式
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            // 表头
            String[] headers = {"序号", "用户名", "真实姓名", "邮箱", "状态", "创建时间"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 4000);
            }

            // 数据行
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            int rowNum = 1;
            for (User user : list) {
                Row row = sheet.createRow(rowNum);
                row.createCell(0).setCellValue(rowNum);
                row.createCell(1).setCellValue(user.getUsername() != null ? user.getUsername() : "");
                row.createCell(2).setCellValue(user.getRealName() != null ? user.getRealName() : "");
                row.createCell(3).setCellValue(user.getEmail() != null ? user.getEmail() : "");
                row.createCell(4).setCellValue(user.getStatus() != null && user.getStatus() == 0 ? "正常" : "停用");
                row.createCell(5).setCellValue(user.getCreatedAt() != null ? user.getCreatedAt().format(dtf) : "");
                rowNum++;
            }

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment;filename=" +
                    URLEncoder.encode("用户数据.xlsx", "UTF-8"));

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
     * 导入用户数据
     */
    @PostMapping("/import")
    public ResponseResult<?> importUsers(@RequestParam("file") MultipartFile file) {
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
                    String username = getCellStringValue(row.getCell(1));
                    String realName = getCellStringValue(row.getCell(2));
                    String email = getCellStringValue(row.getCell(3));
                    String statusStr = getCellStringValue(row.getCell(4));

                    if (!StringUtils.hasText(username)) {
                        errors.add("第" + (i + 1) + "行：用户名不能为空");
                        failCount++;
                        continue;
                    }

                    // 检查用户名是否已存在
                    LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
                    wrapper.eq(User::getUsername, username);
                    User existingUser = userMapper.selectOne(wrapper);

                    if (existingUser != null) {
                        // 更新现有用户
                        existingUser.setRealName(realName);
                        existingUser.setEmail(email);
                        existingUser.setStatus("正常".equals(statusStr) ? 0 : 1);
                        existingUser.setUpdatedAt(LocalDateTime.now());
                        userMapper.updateById(existingUser);
                    } else {
                        // 新增用户
                        User user = new User();
                        user.setUsername(username);
                        user.setRealName(realName);
                        user.setEmail(email);
                        user.setPassword(passwordEncoder.encode("123456")); // 默认密码
                        user.setStatus("正常".equals(statusStr) ? 0 : 1);
                        user.setDelFlag(0);
                        user.setCreatedAt(LocalDateTime.now());
                        user.setUpdatedAt(LocalDateTime.now());
                        userMapper.insert(user);
                    }
                    successCount++;

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
     * 下载用户导入模板
     */
    @GetMapping("/template")
    public void downloadTemplate(HttpServletResponse response) {
        try {
            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("用户导入模板");

            // 表头样式
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            // 表头
            String[] headers = {"序号", "用户名", "真实姓名", "邮箱", "状态"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 4000);
            }

            // 示例数据
            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue(1);
            row.createCell(1).setCellValue("zhangsan");
            row.createCell(2).setCellValue("张三");
            row.createCell(3).setCellValue("zhangsan@example.com");
            row.createCell(4).setCellValue("正常");

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment;filename=" +
                    URLEncoder.encode("用户导入模板.xlsx", "UTF-8"));

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

    private LoginUser getLoginUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser) {
            return (LoginUser) authentication.getPrincipal();
        }
        return null;
    }

    private Long getCurrentUserId(LoginUser loginUser) {
        return loginUser != null && loginUser.getUser() != null ? loginUser.getUser().getId() : null;
    }
}
