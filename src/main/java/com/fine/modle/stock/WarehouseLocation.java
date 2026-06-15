package com.fine.modle.stock;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 仓库库位/卡板位
 */
@Data
@TableName("warehouse_location")
public class WarehouseLocation {
    @TableId(type = IdType.AUTO)
    private Integer id;
    
    /** 仓库ID */
    private Integer warehouseId;
    
    /** 库位编码 */
    private String locationCode;
    
    /** 库位名称 */
    private String locationName;
    
    /** 库位类型：卡板位, 货架, 区域等 */
    private String locationType;
    
    /** 状态 1:启用 0:禁用 */
    private Integer status;
    
    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    
    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    
    /** 逻辑删除 */
    @TableLogic
    private Integer deleted;
    
    /** 仓库名称 (非数据库字段，用于展示) */
    @TableField(exist = false)
    private String warehouseName;
}
