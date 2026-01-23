package com.fine.service.rd;

import com.fine.Utils.ResponseResult;
import com.fine.modle.rd.TapeSpec;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.math.BigDecimal;

/**
 * 胶带规格服务接口
 */
public interface TapeSpecService {

    /**
     * 分页查询规格列表
     */
    ResponseResult<?> getList(int page, int size, String materialCode, String productName, 
                              String colorCode, String baseMaterial, Integer status);

    /**
     * 根据ID查询详情
     */
    ResponseResult<?> getById(Long id);

    /**
     * 根据料号查询
     */
    ResponseResult<?> getByMaterialCode(String materialCode);

    /**
     * 新增规格
     */
    ResponseResult<?> create(TapeSpec spec, String operator);

    /**
     * 更新规格
     */
    ResponseResult<?> update(TapeSpec spec, String operator);

    /**
     * 删除规格
     */
    ResponseResult<?> delete(Long id);

    /**
     * 获取所有启用的规格（下拉选择用）
     */
    ResponseResult<?> getAllEnabled();

    /**
     * 获取颜色字典
     */
    ResponseResult<?> getColorDict();

    /**
     * 获取基材材质字典
     */
    ResponseResult<?> getBaseMaterialDict();

    /**
     * 获取胶水材质字典
     */
    ResponseResult<?> getGlueMaterialDict();

    /**
     * 导出Excel
     */
    void exportExcel(HttpServletResponse response, String materialCode, String productName,
                     String colorCode, String baseMaterial);

    /**
     * 导入Excel
     */
    ResponseResult<?> importExcel(MultipartFile file, String operator);

    /**
     * 下载导入模板
     */
    void downloadTemplate(HttpServletResponse response);

    /**
     * 品质校验：检查测量值是否在规格范围内
     * @param materialCode 料号
     * @param paramName 参数名（totalThickness/peelStrength/unwindForce/heatResistance/initialTack）
     * @param measuredValue 测量值
     * @return 校验结果
     */
    ResponseResult<?> checkQuality(String materialCode, String paramName, BigDecimal measuredValue);
}
