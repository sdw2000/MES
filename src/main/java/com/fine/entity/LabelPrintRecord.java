package com.fine.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("label_print_record")
public class LabelPrintRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String sceneName;
    private String bizType;
    private String templateKey;
    private String jobName;
    private Integer copies;

    private String customerCode;
    private String customerOrderNo;
    private String orderNo;

    private String materialCode;
    private String materialName;
    private String batchNo;

    private String printerName;
    private String printStatus;
    private String resultMessage;

    private String printDataJson;
    private String printPayloadJson;
    private String printResultJson;

    private String operator;
    private LocalDateTime printTime;
    private LocalDateTime createTime;
}
