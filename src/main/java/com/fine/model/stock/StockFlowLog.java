package com.fine.model.stock;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 统一库存流水实体
 */
@Data
@TableName("stock_flow_log")
public class StockFlowLog {

    /** 库存类型枚举 */
    public enum StockType {
        TAPE("TAPE", "胶带"),
        CHEMICAL("CHEMICAL", "化工原料"),
        FILM("FILM", "薄膜");

        private final String code;
        private final String desc;

        StockType(String code, String desc) {
            this.code = code;
            this.desc = desc;
        }

        public String getCode() {
            return code;
        }

        public String getDesc() {
            return desc;
        }
    }

    /** 操作类型枚举 */
    public enum OperationType {
        IN("IN", "入库"),
        OUT("OUT", "出库"),
        ADJUST("ADJUST", "调整"),
        CONSUME("CONSUME", "消耗");

        private final String code;
        private final String desc;

        OperationType(String code, String desc) {
            this.code = code;
            this.desc = desc;
        }

        public String getCode() {
            return code;
        }

        public String getDesc() {
            return desc;
        }
    }

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 库存类型：TAPE/CHEMICAL/FILM */
    private String stockType;

    /** 关联库存ID */
    private Long stockId;

    /** 生产批次号 */
    private String batchNo;

    /** 料号 */
    private String materialCode;

    /** 产品名称 */
    private String productName;

    /** 类型：IN入库/OUT出库/ADJUST调整/CONSUME消耗 */
    private String type;

    /** 变动数量（入库正数，出库负数） */
    private BigDecimal changeQuantity;

    /** 单位：卷/㎡/kg/L等 */
    private String unit;

    /** 标准单位下的变动数量 */
    private BigDecimal stdChangeQuantity;

    /** 标准单位 */
    private String stdUnit;

    /** 变动前数量 */
    private BigDecimal beforeQuantity;

    /** 变动前数量的标准单位值 */
    private BigDecimal stdBeforeQuantity;

    /** 变动后数量 */
    private BigDecimal afterQuantity;

    /** 变动后数量的标准单位值 */
    private BigDecimal stdAfterQuantity;

    /** 关联单号 */
    private String refNo;

    /** 操作人 */
    private String operator;

    /** 备注 */
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}