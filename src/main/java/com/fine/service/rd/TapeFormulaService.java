package com.fine.service.rd;

import com.fine.Utils.ResponseResult;
import com.fine.modle.rd.TapeFormula;
import com.fine.modle.rd.TapeRawMaterial;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;

/**
 * 配胶标准单服务接口
 */
public interface TapeFormulaService {

    /**
     * 分页查询配方列表
     */
    ResponseResult<?> getList(int page, int size, String materialCode, String productName, 
                              String glueModel, Integer status);

    /**
     * 根据ID查询详情（包含原料明细）
     */
    ResponseResult<?> getById(Long id);

    /**
     * 根据产品料号查询配方
     */
    ResponseResult<?> getByMaterialCode(String materialCode);

    /**
     * 新增配方（包含原料明细）
     */
    ResponseResult<?> create(TapeFormula formula, String operator);

    /**
     * 更新配方（包含原料明细）
     */
    ResponseResult<?> update(TapeFormula formula, String operator);

    /**
     * 删除配方
     */
    ResponseResult<?> delete(Long id);

    /**
     * 获取原料字典列表
     */
    ResponseResult<?> getRawMaterialList();

    /**
     * 分页查询原材料列表（支持筛选）
     */
    ResponseResult<?> getRawMaterialPage(int page, int size, String materialCode, String materialName,
                                         String materialType, Integer status);

    /**
     * 查询原材料详情
     */
    ResponseResult<?> getRawMaterialById(Long id);

    /**
     * 新增原料
     */
    ResponseResult<?> createRawMaterial(TapeRawMaterial material);

    /**
     * 更新原料
     */
    ResponseResult<?> updateRawMaterial(TapeRawMaterial material);

    /**
     * 删除原料
     */
    ResponseResult<?> deleteRawMaterial(Long id);    /**
     * 导出配方为PDF/Excel
     */
    void exportFormula(HttpServletResponse response, Long id);

    /**
     * 打印配胶标准单
     */
    ResponseResult<?> getPrintData(Long id);

    /**
     * 下载导入模板
     */
    void downloadTemplate(HttpServletResponse response);

    /**
     * 导入配方Excel
     */
    ResponseResult<?> importFormula(org.springframework.web.multipart.MultipartFile file, String operator);

    /**
     * 批量导出所有配方
     */
    void exportAllFormula(HttpServletResponse response);

    /**
     * 导出原材料
     */
    void exportRawMaterials(HttpServletResponse response, String materialCode, String materialName,
                            String materialType, Integer status);

    /**
     * 导入原材料
     */
    ResponseResult<?> importRawMaterials(MultipartFile file);

    /**
     * 下载原材料导入模板
     */
    void downloadRawMaterialTemplate(HttpServletResponse response);
}
