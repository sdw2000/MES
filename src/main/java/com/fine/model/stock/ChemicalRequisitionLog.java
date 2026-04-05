package com.fine.model.stock;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("chemical_requisition_log")
public class ChemicalRequisitionLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String requestNo;

    private Long requestId;

    private String actionType;

    private String operator;

    private String content;

    private Date createTime;
}
