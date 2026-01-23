package com.fine.model.stock;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 薄膜出库记录表
 */
@Data
@TableName("film_stock_out")
public class FilmStockOut {
    
    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /** 关联film_stock表的ID */
    private Long filmStockId;
    
    /** 关联film_stock_detail表的ID（多个以逗号分隔） */
    private String filmDetailIds;
    
    /** 出库单号 */
    private String outboundNo;
    
    /** 批次号 */
    private String batchNo;
    
    /** 出库面积(㎡) */
    private BigDecimal outArea;
    
    /** 出库卷数 */
    private Integer outRolls;
    
    /** 关联排程ID */
    private Long scheduleId;
    
    /** 关联涂布任务ID */
    private Long coatingTaskId;
    
    /** 用途说明 */
    private String purpose;
    
    /** 出库人 */
    private String outboundBy;
    
    /** 出库时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date outboundTime;
    
    /** 备注 */
    private String remark;
    
    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;
}
