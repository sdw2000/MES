package com.fine.model.production;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;

/**
 * 设备类型字典实体类
 */
@Data
@TableName("equipment_type")
public class EquipmentType implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 类型编码 */
    private String typeCode;

    /** 类型名称 */
    private String typeName;

    /** 工序顺序 */
    private Integer processOrder;

    /** 描述 */
    private String description;

    /** 状态：0-停用，1-启用 */
    private Integer status;
}
