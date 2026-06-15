package com.fine.modle.stock;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 实体仓库
 */
@Data
@TableName("warehouse")
public class Warehouse {
    @TableId(type = IdType.AUTO)
    private Integer id;
    
    /** 仓库编码 */
    private String warehouseCode;
    
    /** 仓库名称 */
    private String warehouseName;
    
    /** 仓库类型：原料仓, 成品仓, 半成品仓等 */
    private String warehouseType;
    
    /** 地址 */
    private String address;
    
    /** 管理员 */
    private String manager;
    
    /** 联系电话 */
    private String contactPhone;
    
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
}
