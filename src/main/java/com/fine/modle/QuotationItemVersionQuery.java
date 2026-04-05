package com.fine.modle;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class QuotationItemVersionQuery {

    private String customer;

    private String materialCode;

    private String specification;

    private String model;

    private String colorCode;

    private BigDecimal length;

    private BigDecimal width;

    private BigDecimal thickness;
}