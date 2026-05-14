package com.fine.service.stock;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fine.model.stock.FilmStock;
import com.fine.model.stock.FilmStockDetail;
import com.fine.model.stock.FilmStockOut;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 薄膜库存服务接口
 * @author Fine
 * @date 2026-01-15
 */
public interface FilmStockService {
    
    /**
     * 按规格查询薄膜库存
     * @param thickness 厚度(μm)
     * @param width 宽度(mm)
     * @return 薄膜库存列表
     */
    List<FilmStock> getBySpec(Integer thickness, Integer width);
    
    /**
     * 查询所有薄膜库存
     * @return 薄膜库存列表
     */
    List<FilmStock> getAllFilmStock();

    /**
     * 分页查询薄膜库存
     * @param current 当前页（从1开始）
     * @param size 每页大小
     * @param thickness 厚度筛选（可选）
        * @param materialCode 料号筛选（可选，支持模糊匹配）
        * @param sortField 排序字段（可选，前端字段名）
        * @param sortOrder 排序方向（ascending/descending）
     * @return 分页结果
     */
        IPage<FilmStock> getFilmStockPage(long current,
                                   long size,
                                   Integer thickness,
                                   String materialCode,
                                   String sortField,
                                   String sortOrder);

      /**
       * 薄膜库存统计（全量聚合，非当前页）
       * @param thickness 厚度筛选（可选）
       * @param materialCode 料号筛选（可选）
       * @return 统计信息
       */
      Map<String, Object> getFilmStockStatistics(Integer thickness, String materialCode);
    
    /**
     * 根据ID查询薄膜库存
     * @param id 库存ID
     * @return 薄膜库存
     */
    FilmStock getById(Long id);
    
    /**
     * 查询薄膜库存明细
     * @param filmStockId 薄膜库存ID
     * @return 明细列表
     */
    List<FilmStockDetail> getDetailsByFilmStockId(Long filmStockId);
    
    /**
     * 查询可用的薄膜明细
     * @param filmStockId 薄膜库存ID
     * @return 可用明细列表
     */
    List<FilmStockDetail> getAvailableDetails(Long filmStockId);

    /**
     * 新增薄膜库存明细
     * @param filmStockId 库存ID
     * @param detail 明细
     * @return 新增后的明细
     */
    FilmStockDetail createDetail(Long filmStockId, FilmStockDetail detail);

    /**
     * 更新薄膜库存明细
     * @param filmStockId 库存ID
     * @param detailId 明细ID
     * @param detail 明细
     * @return 更新后的明细
     */
    FilmStockDetail updateDetail(Long filmStockId, Long detailId, FilmStockDetail detail);

    /**
     * 删除薄膜库存明细（逻辑删除）
     * @param filmStockId 库存ID
     * @param detailId 明细ID
     * @return 是否成功
     */
    boolean deleteDetail(Long filmStockId, Long detailId);
    
    /**
     * 锁定薄膜库存
     * @param filmStockId 薄膜库存ID
     * @param lockArea 锁定面积
     * @param lockRolls 锁定卷数
     * @param detailIds 明细ID列表
     * @return 是否成功
     */
    boolean lockStock(Long filmStockId, BigDecimal lockArea, Integer lockRolls, List<Long> detailIds);
    
    /**
     * 解锁薄膜库存
     * @param filmStockId 薄膜库存ID
     * @param unlockArea 解锁面积
     * @param unlockRolls 解锁卷数
     * @param detailIds 明细ID列表
     * @return 是否成功
     */
    boolean unlockStock(Long filmStockId, BigDecimal unlockArea, Integer unlockRolls, List<Long> detailIds);
    
    /**
     * 薄膜出库
     * @param filmStockOut 出库记录
     * @param detailIds 明细ID列表
     * @return 是否成功
     */
    boolean outbound(FilmStockOut filmStockOut, List<Long> detailIds);
    
    /**
     * 查询薄膜出库记录
     * @param scheduleId 排程ID
     * @return 出库记录列表
     */
    List<FilmStockOut> getOutboundByScheduleId(Long scheduleId);
    
    /**
     * 获取所有可用的薄膜宽度列表（用于排程选择）
     * @param thickness 厚度筛选（可选）
     * @return 宽度列表，包含库存信息
     */
    List<Map<String, Object>> getAvailableWidths(Integer thickness);
    
    /**
     * 根据宽度和厚度获取库存详情
     * @param width 宽度(mm)
     * @param thickness 厚度(μm)，可选
     * @return 库存详情
     */
    Map<String, Object> getStockDetailBySpec(Integer width, Integer thickness);
    
    /**
     * 检查库存是否足够
     * @param width 宽度(mm)
     * @param thickness 厚度(μm)，可选
     * @param requiredArea 需求面积(㎡)
     * @return 是否有足够库存
     */
    boolean checkStockAvailability(Integer width, Integer thickness, Double requiredArea);

    /**
     * 导入薄膜库存汇总Excel
     * @param file Excel文件
     * @return 导入结果
     */
    Map<String, Object> importExcel(MultipartFile file);

    /**
     * 导入薄膜库存Excel（可选：导入前先清空）
     * @param file Excel文件
     * @param clearBeforeImport 是否导入前先清空薄膜库存
     * @return 导入结果
     */
    Map<String, Object> importExcel(MultipartFile file, boolean clearBeforeImport);

    /**
     * 清空薄膜库存数据（用于重新盘点后全量重导）
     * @param clearOutboundRecords 是否清空出库记录
     * @return 清空结果统计
     */
    Map<String, Object> clearForReimport(boolean clearOutboundRecords);
}
