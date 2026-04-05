package com.fine.modle;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 销售订单历史初始化/增量同步状态
 */
@Data
@TableName("sales_order_sync_state")
public class SalesOrderSyncState {

    @TableId(type = IdType.INPUT)
    private Long id;

    /** 是否已完成历史初始化 */
    private Integer initialized;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date initializedAt;

    private String initializedBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date lastSyncAt;

    private String lastSyncBy;

    private String lastImportFile;

    private Integer totalOrders;

    private Integer totalItems;

    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updatedAt;
}
