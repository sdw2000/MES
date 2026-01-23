package com.fine.controller.rd;

import com.fine.Utils.ResponseResult;
import com.fine.modle.rd.TapeFormula;
import com.fine.modle.rd.TapeRawMaterial;
import com.fine.service.rd.TapeFormulaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;

/**
 * 配胶标准单控制器
 */
@RestController
@RequestMapping("/api/tape-formula")
@PreAuthorize("hasAnyAuthority('admin','rd','production','warehouse')")
public class TapeFormulaController {

    @Autowired
    private TapeFormulaService tapeFormulaService;

    /**
     * 分页查询配方列表
     */
    @GetMapping("/list")
    public ResponseResult<?> getList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String materialCode,
            @RequestParam(required = false) String productName,
            @RequestParam(required = false) String glueModel,
            @RequestParam(required = false) Integer status) {
        return tapeFormulaService.getList(page, size, materialCode, productName, glueModel, status);
    }

    /**
     * 根据ID查询详情（包含原料明细）
     */
    @GetMapping("/{id}")
    public ResponseResult<?> getById(@PathVariable Long id) {
        return tapeFormulaService.getById(id);
    }

    /**
     * 根据产品料号查询配方
     */
    @GetMapping("/by-code/{materialCode}")
    public ResponseResult<?> getByMaterialCode(@PathVariable String materialCode) {
        return tapeFormulaService.getByMaterialCode(materialCode);
    }

    /**
     * 新增配方
     */
    @PostMapping
    @PreAuthorize("hasAnyAuthority('admin','rd')")
    public ResponseResult<?> create(@RequestBody TapeFormula formula) {
        return tapeFormulaService.create(formula, "admin");
    }

    /**
     * 更新配方
     */
    @PutMapping
    @PreAuthorize("hasAnyAuthority('admin','rd')")
    public ResponseResult<?> update(@RequestBody TapeFormula formula) {
        return tapeFormulaService.update(formula, "admin");
    }

    /**
     * 删除配方
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('admin','rd')")
    public ResponseResult<?> delete(@PathVariable Long id) {
        return tapeFormulaService.delete(id);
    }

    /**
     * 导出配方Excel
     */
    @GetMapping("/export/{id}")
    public void exportFormula(HttpServletResponse response, @PathVariable Long id) {
        tapeFormulaService.exportFormula(response, id);
    }

    /**
     * 下载导入模板
     */
    @GetMapping("/template")
    public void downloadTemplate(HttpServletResponse response) {
        tapeFormulaService.downloadTemplate(response);
    }

    /**
     * 导入配方Excel
     */
    @PostMapping("/import")
    @PreAuthorize("hasAnyAuthority('admin','rd')")
    public ResponseResult<?> importFormula(@RequestParam("file") MultipartFile file) {
        return tapeFormulaService.importFormula(file, "admin");
    }

    /**
     * 批量导出所有配方
     */
    @GetMapping("/export-all")
    public void exportAllFormula(HttpServletResponse response) {
        tapeFormulaService.exportAllFormula(response);
    }

    /**
     * 获取打印数据
     */
    @GetMapping("/print/{id}")
    public ResponseResult<?> getPrintData(@PathVariable Long id) {
        return tapeFormulaService.getPrintData(id);
    }

    // =============== 原料字典管理 ===============

    /**
     * 获取原料字典列表
     */
    @GetMapping("/raw-materials")
    public ResponseResult<?> getRawMaterialList() {
        return tapeFormulaService.getRawMaterialList();
    }

    /**
     * 新增原料
     */
    @PostMapping("/raw-material")
    public ResponseResult<?> createRawMaterial(@RequestBody TapeRawMaterial material) {
        return tapeFormulaService.createRawMaterial(material);
    }

    /**
     * 更新原料
     */
    @PutMapping("/raw-material")
    public ResponseResult<?> updateRawMaterial(@RequestBody TapeRawMaterial material) {
        return tapeFormulaService.updateRawMaterial(material);
    }

    /**
     * 删除原料
     */
    @DeleteMapping("/raw-material/{id}")
    public ResponseResult<?> deleteRawMaterial(@PathVariable Long id) {
        return tapeFormulaService.deleteRawMaterial(id);
    }
}
