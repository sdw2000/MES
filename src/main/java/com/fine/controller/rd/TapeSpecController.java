package com.fine.controller.rd;

import com.fine.Utils.ResponseResult;
import com.fine.modle.LoginUser;
import com.fine.modle.rd.DictItem;
import com.fine.modle.rd.TapeSpec;
import com.fine.service.rd.TapeSpecService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.math.BigDecimal;

/**
 * 胶带规格管理控制器（研发管理）
 */
@RestController
@RequestMapping("/api/tape-spec")
@PreAuthorize("hasAnyAuthority('admin','rd','sales','production','warehouse','finance','quality','packaging','packing','purchase','coating','plan','scheduler')")
public class TapeSpecController {

    @Autowired
    private TapeSpecService tapeSpecService;

    /**
     * 分页查询规格列表
     */
    @GetMapping("/list")
    public ResponseResult<?> getList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String materialCode,
            @RequestParam(required = false) String productName,
            @RequestParam(required = false) String colorCode,
            @RequestParam(required = false) String baseMaterial,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder) {
        return tapeSpecService.getList(page, size, materialCode, productName, colorCode, baseMaterial, status, sortBy, sortOrder);
    }

    /**
     * 未生产平方统计（后端分页 + 排序）
     */
    @GetMapping("/unproduced/page")
    public ResponseResult<?> getUnproducedStatsPage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String materialCode,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder) {
        return tapeSpecService.getUnproducedStatsPage(page, size, materialCode, sortBy, sortOrder);
    }

    /**
     * 根据ID查询详情
     */
    @GetMapping("/{id}")
    public ResponseResult<?> getById(@PathVariable Long id) {
        return tapeSpecService.getById(id);
    }

    /**
     * 根据料号查询
     */
    @GetMapping("/by-code/{materialCode}")
    public ResponseResult<?> getByMaterialCode(@PathVariable String materialCode) {
        return tapeSpecService.getByMaterialCode(materialCode);
    }

    /**
     * 料号建议（前缀匹配，最多5条）
     */
    @GetMapping("/suggest")
    public ResponseResult<?> suggestByMaterialCode(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "5") Integer limit) {
        return tapeSpecService.suggestByMaterialCode(keyword, limit);
    }

    /**
     * 新增规格
     */
    @PostMapping
    @PreAuthorize("hasAnyAuthority('admin','rd')")
    public ResponseResult<?> create(@RequestBody TapeSpec spec) {
        return tapeSpecService.create(spec, getCurrentUsername());
    }

    /**
     * 更新规格
     */
    @PutMapping
    @PreAuthorize("hasAnyAuthority('admin','rd')")
    public ResponseResult<?> update(@RequestBody TapeSpec spec) {
        return tapeSpecService.update(spec, getCurrentUsername());
    }

    private String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser) {
            return ((LoginUser) authentication.getPrincipal()).getUsername();
        }
        return "system";
    }

    /**
     * 删除规格
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('admin','rd')")
    public ResponseResult<?> delete(@PathVariable Long id) {
        return tapeSpecService.delete(id);
    }

    /**
     * 获取所有启用的规格（下拉选择用）
     */
    @GetMapping("/enabled")
    public ResponseResult<?> getAllEnabled() {
        return tapeSpecService.getAllEnabled();
    }

    /**
     * 获取颜色字典
     */
    @GetMapping("/dict/color")
    public ResponseResult<?> getColorDict() {
        return tapeSpecService.getColorDict();
    }

    /**
     * 颜色字典管理列表
     */
    @GetMapping("/dict/color/list")
    @PreAuthorize("hasAnyAuthority('admin','rd')")
    public ResponseResult<?> getColorDictList(@RequestParam(required = false) String keyword,
                                              @RequestParam(required = false) Integer status,
                                              @RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return tapeSpecService.getColorDictList(keyword, status, page, size);
    }

    /**
     * 新增颜色字典
     */
    @PostMapping("/dict/color")
    @PreAuthorize("hasAnyAuthority('admin','rd')")
    public ResponseResult<?> createColorDict(@RequestBody DictItem item) {
        return tapeSpecService.createColorDict(item, "admin");
    }

    /**
     * 更新颜色字典
     */
    @PutMapping("/dict/color")
    @PreAuthorize("hasAnyAuthority('admin','rd')")
    public ResponseResult<?> updateColorDict(@RequestBody DictItem item) {
        return tapeSpecService.updateColorDict(item, "admin");
    }

    /**
     * 删除颜色字典
     */
    @DeleteMapping("/dict/color/{id}")
    @PreAuthorize("hasAnyAuthority('admin','rd')")
    public ResponseResult<?> deleteColorDict(@PathVariable Long id) {
        return tapeSpecService.deleteColorDict(id);
    }

    /**
     * 获取基材材质字典
     */
    @GetMapping("/dict/base-material")
    public ResponseResult<?> getBaseMaterialDict() {
        return tapeSpecService.getBaseMaterialDict();
    }

    /**
     * 获取胶水材质字典
     */
    @GetMapping("/dict/glue-material")
    public ResponseResult<?> getGlueMaterialDict() {
        return tapeSpecService.getGlueMaterialDict();
    }

    /**
     * 导出Excel
     */
    @GetMapping("/export")
    public void exportExcel(HttpServletResponse response,
                            @RequestParam(required = false) String materialCode,
                            @RequestParam(required = false) String productName,
                            @RequestParam(required = false) String colorCode,
                            @RequestParam(required = false) String baseMaterial) {
        tapeSpecService.exportExcel(response, materialCode, productName, colorCode, baseMaterial);
    }

    /**
     * 导入Excel
     */
    @PostMapping("/import")
    public ResponseResult<?> importExcel(@RequestParam("file") MultipartFile file) {
        return tapeSpecService.importExcel(file, "admin");
    }

    /**
     * 下载导入模板
     */
    @GetMapping("/template")
    public void downloadTemplate(HttpServletResponse response) {
        tapeSpecService.downloadTemplate(response);
    }

    /**
     * 品质校验接口
     * @param materialCode 料号
        * @param paramName 参数名（totalThickness/peelStrength/unwindForce/heatResistance/initialTack）
        * @param value 测量值
     */
    @GetMapping("/check-quality")
    public ResponseResult<?> checkQuality(
            @RequestParam String materialCode,
            @RequestParam String paramName,
            @RequestParam BigDecimal value) {
        return tapeSpecService.checkQuality(materialCode, paramName, value);
    }
}
