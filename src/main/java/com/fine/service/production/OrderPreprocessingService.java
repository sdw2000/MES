package com.fine.service.production;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.fine.model.production.OrderPreprocessing;
import com.fine.model.production.OrderMaterialLock;

import java.math.BigDecimal;
import java.util.List;

/**
 * 订单预处理Service接口
 */
public interface OrderPreprocessingService extends IService<OrderPreprocessing> {

    /**
     * 分页查询预处理订单
     */
    IPage<OrderPreprocessing> queryPreprocessingPage(Page<OrderPreprocessing> page, String status, String orderNo, String materialCode);

    /**
     * 根据订单ID查询预处理记录
     */
    List<OrderPreprocessing> getByOrderId(Long orderId);

    /**
     * 根据订单明细ID查询预处理记录
     */
    OrderPreprocessing getByOrderItemId(Long orderItemId);

    /**
     * 创建订单预处理记录（含展示字段以满足非空约束）
     */
    OrderPreprocessing createPreprocessing(Long orderId,
                                           Long orderItemId,
                                           String orderNo,
                                           String orderItemCode,
                                           String materialCode,
                                           String materialName,
                                           String specDesc,
                                           BigDecimal requiredQty);

    /**
     * 查询可锁定的物料列表 (FIFO排序)
     */
    List<AvailableMaterialDTO> getAvailableMaterials(String materialCode, Integer limit, Long orderItemId,
                                                     Integer requiredRolls, java.math.BigDecimal requiredArea);

    /**
     * 锁定物料
     */
    void lockMaterials(Long orderItemId, Long orderId, List<OrderMaterialLock> locks) throws Exception;

    /**
     * 解除锁定，释放库存
     */
    void releaseLocks(Long orderItemId) throws Exception;

    /**
     * 提交预处理订单
     */
    void submitPreprocessing(Long orderItemId) throws Exception;

    /**
     * 获取预处理的已锁定面积
     */
    BigDecimal getLockedArea(Long orderItemId);

    /**
     * 判断排程类型
     */
    String determineScheduleType(Long orderItemId);

    /** 取消订单明细：释放锁定、清理待排池、标记状态 */
    void cancelOrderItem(Long orderItemId) throws Exception;
}
