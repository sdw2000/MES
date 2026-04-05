package com.fine.controller.production;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fine.model.production.ProcessParams;
import com.fine.service.production.ProcessParamsService;
import com.fine.Utils.ResponseResult;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工艺参数Controller
 */
@Api(tags = "工艺参数管理")
@RestController
@RequestMapping("/production/process-params")
public class ProcessParamsController {

    @Autowired
    private ProcessParamsService paramsService;

    @ApiOperation("分页查询工艺参数列表")
    @GetMapping("/list")
    public ResponseResult<Map<String, Object>> getList(
            @RequestParam(required = false) String materialCode,
            @RequestParam(required = false) String processType,
            @RequestParam(required = false) String equipmentCode,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        
        // 使用MyBatis-Plus分页方式
        IPage<ProcessParams> pageResult = paramsService.getProcessParamsPage(materialCode, processType, equipmentCode, page, size);
        
        Map<String, Object> result = new HashMap<>();
        result.put("list", pageResult.getRecords());
        result.put("total", pageResult.getTotal());
        result.put("page", pageResult.getCurrent());
        result.put("size", pageResult.getSize());
        result.put("pages", pageResult.getPages());
        result.put("hasNextPage", pageResult.getCurrent() < pageResult.getPages());
        
        return ResponseResult.success(result);
    }

    @ApiOperation("根据料号和工序类型查询参数")
    @GetMapping("/get")
    public ResponseResult<ProcessParams> getByMaterialAndProcess(
            @RequestParam String materialCode,
            @RequestParam String processType,
            @RequestParam(required = false) String equipmentCode) {
        ProcessParams params = paramsService.getByMaterialAndProcess(materialCode, processType, equipmentCode);
        return ResponseResult.success(params);
    }

    @ApiOperation("根据料号查询所有工序参数")
    @GetMapping("/byMaterial/{materialCode}")
    public ResponseResult<List<ProcessParams>> getByMaterialCode(@PathVariable String materialCode) {
        List<ProcessParams> list = paramsService.getByMaterialCode(materialCode);
        return ResponseResult.success(list);
    }

    @ApiOperation("获取工艺参数详情")
    @GetMapping("/{id}")
    public ResponseResult<ProcessParams> getById(@PathVariable Long id) {
        ProcessParams params = paramsService.getById(id);
        return ResponseResult.success(params);
    }

    @ApiOperation("新增工艺参数")
    @PostMapping
    public ResponseResult<Void> add(@RequestBody ProcessParams params) {
        try {
            boolean success = paramsService.addParams(params);
            return success ? ResponseResult.success() : ResponseResult.fail("新增失败");
        } catch (RuntimeException e) {
            return ResponseResult.fail(e.getMessage());
        }
    }

    @ApiOperation("更新工艺参数")
    @PutMapping("/{id}")
    public ResponseResult<Void> update(@PathVariable Long id, @RequestBody ProcessParams params) {
        try {
            params.setId(id);
            boolean success = paramsService.updateParams(params);
            return success ? ResponseResult.success() : ResponseResult.fail("更新失败");
        } catch (RuntimeException e) {
            return ResponseResult.fail(e.getMessage());
        }
    }

    @ApiOperation("删除工艺参数")
    @DeleteMapping("/{id}")
    public ResponseResult<Void> delete(@PathVariable Long id) {
        boolean success = paramsService.deleteParams(id);
        return success ? ResponseResult.success() : ResponseResult.fail("删除失败");
    }

    @ApiOperation("批量保存料号的工艺参数")
    @PostMapping("/batch/{materialCode}")
    public ResponseResult<Void> batchSave(@PathVariable String materialCode, @RequestBody List<ProcessParams> paramsList) {
        try {
            boolean success = paramsService.batchSaveParams(materialCode, paramsList);
            return success ? ResponseResult.success() : ResponseResult.fail("保存失败");
        } catch (RuntimeException e) {
            return ResponseResult.fail(e.getMessage());
        }
    }

    // ==================== 导入导出 ====================

    /**
     * 下载工艺参数导入模板
     * GET /production/process-params/template
     */
    @ApiOperation("下载工艺参数导入模板")
    @GetMapping("/template")
    public void downloadTemplate(HttpServletResponse response) throws IOException {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("工艺参数导入模板");
        
        // 表头
        Row header = sheet.createRow(0);
        String[] headers = {"产品料号", "工序类型(COATING/REWINDING/SLITTING/STRIPPING)", 
                           "涂布速度(m/min)", "烘箱温度(℃)", "涂布厚度(μm)", "换色清洗时间(min)", "换厚度调机时间(min)",
                           "复卷速度(m/min)", "张力设定", "换卷时间(min)",
                           "分切速度(m/min)", "刀片类型", "换刀时间(min)", "最小分切宽度(mm)", "最大刀数", "首尾损耗(mm)",
                           "分条速度(m/min)", "首检时间(min)", "末检时间(min)", "准备时间(min)", "备注"};
        for (int i = 0; i < headers.length; i++) {
            header.createCell(i).setCellValue(headers[i]);
        }
        
        // 示例数据
        Row row = sheet.createRow(1);
        row.createCell(0).setCellValue("1011-R02-2307-G03-0350");
        row.createCell(1).setCellValue("COATING");
        row.createCell(2).setCellValue(30);
        row.createCell(3).setCellValue(120);
        row.createCell(4).setCellValue(30);
        row.createCell(5).setCellValue(15);
        row.createCell(6).setCellValue(10);
        // 其他字段留空
        
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment;filename=" + 
                URLEncoder.encode("工艺参数导入模板.xlsx", "UTF-8"));
        workbook.write(response.getOutputStream());
        workbook.close();
    }

    /**
     * 导出工艺参数数据
     * GET /production/process-params/export
     */
    @ApiOperation("导出工艺参数数据")
    @GetMapping("/export")
    public void exportParams(
            @RequestParam(required = false) String materialCode,
            @RequestParam(required = false) String processType,
            @RequestParam(required = false) String equipmentCode,
            HttpServletResponse response) throws IOException {
        List<ProcessParams> list = paramsService.getParamsListForExport(materialCode, processType, equipmentCode);
        
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("工艺参数数据");
        
        // 表头
        Row header = sheet.createRow(0);
        String[] headers = {"产品料号", "产品名称", "工序类型", 
                           "涂布速度(m/min)", "烘箱温度(℃)", "涂布厚度(μm)", "换色清洗时间(min)", "换厚度调机时间(min)",
                           "复卷速度(m/min)", "张力设定", "换卷时间(min)",
                           "分切速度(m/min)", "刀片类型", "换刀时间(min)", "最小分切宽度(mm)", "最大刀数", "首尾损耗(mm)",
                           "分条速度(m/min)", "首检时间(min)", "末检时间(min)", "准备时间(min)", "备注"};
        for (int i = 0; i < headers.length; i++) {
            header.createCell(i).setCellValue(headers[i]);
        }
        
        // 数据
        for (int i = 0; i < list.size(); i++) {
            ProcessParams params = list.get(i);
            Row row = sheet.createRow(i + 1);
            row.createCell(0).setCellValue(params.getMaterialCode() != null ? params.getMaterialCode() : "");
            row.createCell(1).setCellValue(params.getMaterialName() != null ? params.getMaterialName() : "");
            row.createCell(2).setCellValue(params.getProcessType() != null ? params.getProcessType() : "");
            row.createCell(3).setCellValue(params.getCoatingSpeed() != null ? params.getCoatingSpeed().doubleValue() : 0);
            row.createCell(4).setCellValue(params.getOvenTemp() != null ? params.getOvenTemp().doubleValue() : 0);
            row.createCell(5).setCellValue(params.getCoatingThickness() != null ? params.getCoatingThickness().doubleValue() : 0);
            row.createCell(6).setCellValue(params.getColorChangeTime() != null ? params.getColorChangeTime() : 0);
            row.createCell(7).setCellValue(params.getThicknessChangeTime() != null ? params.getThicknessChangeTime() : 0);
            row.createCell(8).setCellValue(params.getRewindingSpeed() != null ? params.getRewindingSpeed().doubleValue() : 0);
            row.createCell(9).setCellValue(params.getTensionSetting() != null ? params.getTensionSetting().doubleValue() : 0);
            row.createCell(10).setCellValue(params.getRollChangeTime() != null ? params.getRollChangeTime() : 0);
            row.createCell(11).setCellValue(params.getSlittingSpeed() != null ? params.getSlittingSpeed().doubleValue() : 0);
            row.createCell(12).setCellValue(params.getBladeType() != null ? params.getBladeType() : "");
            row.createCell(13).setCellValue(params.getBladeChangeTime() != null ? params.getBladeChangeTime() : 0);
            row.createCell(14).setCellValue(params.getMinSlitWidth() != null ? params.getMinSlitWidth() : 0);
            row.createCell(15).setCellValue(params.getMaxBlades() != null ? params.getMaxBlades() : 0);
            row.createCell(16).setCellValue(params.getEdgeLoss() != null ? params.getEdgeLoss() : 0);
            row.createCell(17).setCellValue(params.getStrippingSpeed() != null ? params.getStrippingSpeed().doubleValue() : 0);
            row.createCell(18).setCellValue(params.getFirstCheckTime() != null ? params.getFirstCheckTime() : 0);
            row.createCell(19).setCellValue(params.getLastCheckTime() != null ? params.getLastCheckTime() : 0);
            row.createCell(20).setCellValue(params.getSetupTime() != null ? params.getSetupTime() : 0);
            row.createCell(21).setCellValue(params.getRemark() != null ? params.getRemark() : "");
        }
        
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment;filename=" + 
                URLEncoder.encode("工艺参数数据.xlsx", "UTF-8"));
        workbook.write(response.getOutputStream());
        workbook.close();
    }

    /**
     * 导入工艺参数数据
     * POST /production/process-params/import
     */
    @ApiOperation("导入工艺参数数据")
    @PostMapping("/import")
    public ResponseResult<?> importParams(@RequestParam("file") MultipartFile file) {
        try {
            Map<String, Object> result = paramsService.importParams(file);
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
