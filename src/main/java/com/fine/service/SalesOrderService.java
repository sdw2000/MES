package com.fine.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fine.Utils.ResponseResult;
import com.fine.modle.SalesOrder;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;

public interface SalesOrderService extends IService<SalesOrder> {
    
    /**
     * 获取所有订单列表
     */
    ResponseResult<?> getAllOrders(Integer pageNum, Integer pageSize, String orderNo, String customer, String completionStatus,
                                   Boolean showCompleted, String startDate, String endDate, String sortProp, String sortOrder);
    
    /**
     * 创建订单
     */
    ResponseResult<?> createOrder(SalesOrder salesOrder);
    
    /**
     * 更新订单
     */
    ResponseResult<?> updateOrder(SalesOrder salesOrder);
    
    /**
     * 删除订单（逻辑删除）
     */
    ResponseResult<?> deleteOrder(String orderNo);

    /**
     * 取消订单（必须填写取消原因）
     */
    ResponseResult<?> cancelOrder(String orderNo, String cancelReason);
    
    /**
     * 根据订单号获取订单详情
     */
    ResponseResult<?> getOrderByOrderNo(String orderNo);
    
    /**
     * 搜索订单（用于发货通知选择）
     */
    ResponseResult<?> searchOrders(String keyword, String status, String customer);
    
    /**
     * 生成订单号
     */
    ResponseResult<?> generateOrderNo(String customerCode, java.time.LocalDate orderDate);

    /**
     * 查询客户在指定料号下的历史下单规格（厚度/宽度/长度）
     */
    ResponseResult<?> getCustomerMaterialHistorySpecs(String customerCode, String materialCode);

    /**
     * 查询客户历史订单备注（用于订单表头备注快速选择）
     */
    ResponseResult<?> getCustomerOrderRemarkHistory(String customerCode, Integer limit);

    /**
     * 下载导入模板
     */
    void downloadTemplate(HttpServletResponse response);

    /**
     * 导入订单Excel
     */
    ResponseResult<?> importOrders(MultipartFile file, String operator);

    /**
     * 历史初始化状态
     */
    ResponseResult<?> getHistoryInitStatus();

    /**
     * 历史订单初始化导入（一次性）
     */
    ResponseResult<?> importHistoryInit(MultipartFile file, String operator);

    /**
     * 初始化后增量同步订单
     */
    ResponseResult<?> syncIncrementalOrders(MultipartFile file, String operator);

    /**
     * 清空订单与明细并重置历史初始化状态
     */
    ResponseResult<?> resetHistoryInitialization(String operator);

    /**
     * 基于现有订单数据重建历史初始化状态（不删除订单）
     */
    ResponseResult<?> rebuildHistoryInitializationState(String operator);

    /**
     * 导出所有订单
     */
    void exportOrders(HttpServletResponse response);
}
