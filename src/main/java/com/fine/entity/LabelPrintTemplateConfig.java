package com.fine.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("label_print_template_config")
public class LabelPrintTemplateConfig {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String bizType;

    private String sceneName;

    private String templateKey;

    private String customerCode;

    private Integer sortNo;

    private Integer isActive;

    private String remark;

    private String createBy;

    private LocalDateTime createTime;

    private String updateBy;

    private LocalDateTime updateTime;
}
