package com.fine.model.stock;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
@TableName("chemical_purchase_request")
public class ChemicalPurchaseRequest {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String requestNo;

    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date planDate;

    private Long scheduleId;

    private String orderNo;

    private String finishedMaterialCode;

    /** DRAFT/SUBMITTED/CLOSED */
    private String status;

    private String purchaseOrderNo;

    private String source;

    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;

    private String createBy;

    private String updateBy;

    private Integer isDeleted;

    /** 前端显示用：状态中文文案 */
    private transient String statusText;

    private transient List<ChemicalPurchaseRequestItem> items;

    private transient List<ChemicalRequisitionLog> logs;
}
