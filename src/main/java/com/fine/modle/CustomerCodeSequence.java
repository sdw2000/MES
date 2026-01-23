package com.fine.modle;

import lombok.Data;

/**
 * 客户编号序列实体类
 * @author Fine
 * @date 2026-01-06
 */
@Data
public class CustomerCodeSequence {
    private Long id;
    private String prefix;          // 客户编号前缀（如ALB、TX等）
    private Integer currentNumber;  // 当前序号
}
