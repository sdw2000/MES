package com.fine.controller.production;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fine.Utils.ResponseResult;
import com.fine.model.production.Equipment;
import com.fine.model.production.EquipmentType;
import com.fine.model.production.Workshop;
import com.fine.service.production.EquipmentService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Map;

/**
 * 设备管理Controller
 */
@RestController
@RequestMapping("/production/equipment")
public class EquipmentController {

    @Autowired
    private EquipmentService equipmentService;

    /**
     * 查询设备列表
     * GET /production/equipment/list
     */
    @GetMapping("/list")
    public ResponseResult<?> getList(
            @RequestParam(required = false) String equipmentType,
            @RequestParam(required = false) Long workshopId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return equipmentService.getEquipmentList(equipmentType, workshopId, status, keyword, pageNum, pageSize);
    }

    /**
     * 根据ID查询设备
     * GET /production/equipment/{id}
     */
    @GetMapping("/{id}")
    public ResponseResult<?> getById(@PathVariable Long id) {
        return equipmentService.getEquipmentById(id);
    }

    /**
     * 新增设备
     * POST /production/equipment
     */
    @PostMapping
    public ResponseResult<?> add(@RequestBody Equipment equipment) {
        return equipmentService.addEquipment(equipment);
    }

    /**
     * 修改设备
     * PUT /production/equipment
     */
    @PutMapping
    public ResponseResult<?> update(@RequestBody Equipment equipment) {
        return equipmentService.updateEquipment(equipment);
    }

    /**
     * 删除设备
     * DELETE /production/equipment/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseResult<?> delete(@PathVariable Long id) {
        return equipmentService.deleteEquipment(id);
    }

    /**
     * 获取设备类型列表
     * GET /production/equipment/types
     */
    @GetMapping("/types")
    public ResponseResult<?> getTypes() {
        return equipmentService.getEquipmentTypes();
    }

    /**
     * 获取车间列表
     * GET /production/equipment/workshops
     */
    @GetMapping("/workshops")
    public ResponseResult<?> getWorkshops() {
        return equipmentService.getWorkshops();
    }

    /**
     * 根据设备类型查询可用设备
     * GET /production/equipment/available/{type}
     */
    @GetMapping("/available/{type}")
    public ResponseResult<?> getAvailableByType(@PathVariable("type") String equipmentType) {
        return equipmentService.getAvailableByType(equipmentType);
    }

    /**
     * 更新设备状态
     * PUT /production/equipment/{id}/status
     */
    @PutMapping("/{id}/status")
    public ResponseResult<?> updateStatus(@PathVariable Long id, @RequestParam String status) {
        return equipmentService.updateStatus(id, status);
    }

    // ==================== 车间管理 ====================

    /**
     * 查询车间列表（分页）
     * GET /production/equipment/workshop/list
     */
    @GetMapping("/workshop/list")
    public ResponseResult<?> getWorkshopList(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        IPage<Workshop> pageResult = equipmentService.getWorkshopPage(status, keyword, page, size);
        return new ResponseResult<>(200, "查询成功", pageResult);
    }

    /**
     * 新增车间
     * POST /production/equipment/workshop
     */
    @PostMapping("/workshop")
    public ResponseResult<?> addWorkshop(@RequestBody com.fine.model.production.Workshop workshop) {
        return equipmentService.addWorkshop(workshop);
    }

    /**
     * 修改车间
     * PUT /production/equipment/workshop
     */
    @PutMapping("/workshop")
    public ResponseResult<?> updateWorkshop(@RequestBody com.fine.model.production.Workshop workshop) {
        return equipmentService.updateWorkshop(workshop);
    }

    /**
     * 删除车间
     * DELETE /production/equipment/workshop/{id}
     */
    @DeleteMapping("/workshop/{id}")
    public ResponseResult<?> deleteWorkshop(@PathVariable Long id) {
        return equipmentService.deleteWorkshop(id);
    }

    // ==================== 设备类型管理 ====================

    /**
     * 查询设备类型列表（分页）
     * GET /production/equipment/type/list
     */
    @GetMapping("/type/list")
    public ResponseResult<?> getEquipmentTypeList(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        IPage<EquipmentType> pageResult = equipmentService.getEquipmentTypePage(status, page, size);
        return new ResponseResult<>(200, "查询成功", pageResult);
    }

    // ==================== 导入导出 ====================

    /**
     * 下载设备导入模板
     * GET /production/equipment/template
     */
    @GetMapping("/template")
    public void downloadTemplate(HttpServletResponse response) throws IOException {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("设备导入模板");
        
        // 表头
        Row header = sheet.createRow(0);
        String[] headers = {"设备编号", "设备名称", "设备类型", "所属车间ID", "品牌", "型号", 
                           "最大加工宽度(mm)", "最大速度(m/min)", "日产能(㎡)", "购买日期", "状态", "设备位置", "备注"};
        for (int i = 0; i < headers.length; i++) {
            header.createCell(i).setCellValue(headers[i]);
        }
        
        // 示例数据
        Row row = sheet.createRow(1);
        row.createCell(0).setCellValue("EQ001");
        row.createCell(1).setCellValue("涂布机1号");
        row.createCell(2).setCellValue("COATING");
        row.createCell(3).setCellValue(1);
        row.createCell(4).setCellValue("西门子");
        row.createCell(5).setCellValue("CB-1200");
        row.createCell(6).setCellValue(1200);
        row.createCell(7).setCellValue(50);
        row.createCell(8).setCellValue(5000);
        row.createCell(9).setCellValue("2024-01-15");
        row.createCell(10).setCellValue("normal");
        row.createCell(11).setCellValue("A区1号位");
        row.createCell(12).setCellValue("");
        
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment;filename=" + 
                URLEncoder.encode("设备导入模板.xlsx", "UTF-8"));
        workbook.write(response.getOutputStream());
        workbook.close();
    }

    /**
     * 导出设备数据
     * GET /production/equipment/export
     */
    @GetMapping("/export")
    public void exportEquipment(
            @RequestParam(required = false) String equipmentType,
            @RequestParam(required = false) Long workshopId,
            @RequestParam(required = false) String status,
            HttpServletResponse response) throws IOException {
        List<Equipment> list = equipmentService.getEquipmentListForExport(equipmentType, workshopId, status);
        
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("设备数据");
        
        // 表头
        Row header = sheet.createRow(0);
        String[] headers = {"设备编号", "设备名称", "设备类型", "设备类型名称", "所属车间", "品牌", "型号",
                           "最大加工宽度(mm)", "最大速度(m/min)", "日产能(㎡)", "购买日期", "状态", "设备位置", "备注"};
        for (int i = 0; i < headers.length; i++) {
            header.createCell(i).setCellValue(headers[i]);
        }
        
        // 数据
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        for (int i = 0; i < list.size(); i++) {
            Equipment eq = list.get(i);
            Row row = sheet.createRow(i + 1);
            row.createCell(0).setCellValue(eq.getEquipmentCode() != null ? eq.getEquipmentCode() : "");
            row.createCell(1).setCellValue(eq.getEquipmentName() != null ? eq.getEquipmentName() : "");
            row.createCell(2).setCellValue(eq.getEquipmentType() != null ? eq.getEquipmentType() : "");
            row.createCell(3).setCellValue(eq.getEquipmentTypeName() != null ? eq.getEquipmentTypeName() : "");
            row.createCell(4).setCellValue(eq.getWorkshopName() != null ? eq.getWorkshopName() : "");
            row.createCell(5).setCellValue(eq.getBrand() != null ? eq.getBrand() : "");
            row.createCell(6).setCellValue(eq.getModel() != null ? eq.getModel() : "");
            row.createCell(7).setCellValue(eq.getMaxWidth() != null ? eq.getMaxWidth() : 0);
            row.createCell(8).setCellValue(eq.getMaxSpeed() != null ? eq.getMaxSpeed().doubleValue() : 0);
            row.createCell(9).setCellValue(eq.getDailyCapacity() != null ? eq.getDailyCapacity().doubleValue() : 0);
            row.createCell(10).setCellValue(eq.getPurchaseDate() != null ? sdf.format(eq.getPurchaseDate()) : "");
            row.createCell(11).setCellValue(eq.getStatus() != null ? eq.getStatus() : "");
            row.createCell(12).setCellValue(eq.getLocation() != null ? eq.getLocation() : "");
            row.createCell(13).setCellValue(eq.getRemark() != null ? eq.getRemark() : "");
        }
        
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment;filename=" + 
                URLEncoder.encode("设备数据.xlsx", "UTF-8"));
        workbook.write(response.getOutputStream());
        workbook.close();
    }

    /**
     * 导入设备数据
     * POST /production/equipment/import
     */
    @PostMapping("/import")
    public ResponseResult<?> importEquipment(@RequestParam("file") MultipartFile file) {
        try {
            Map<String, Object> result = equipmentService.importEquipment(file);
            if ((boolean) result.get("success")) {
                return ResponseResult.success(result);
            } else {
                return ResponseResult.fail((String) result.get("message"));
            }
        } catch (Exception e) {
            return ResponseResult.fail("导入失败: " + e.getMessage());
        }
    }
}
