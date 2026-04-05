package com.fine.modle;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 客户实体类
 * @author Fine
 * @date 2026-01-06
 */
@Data
@TableName("customers")
public class Customer {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    // 基本信息
    private String customerCode;        // 客户编码（唯一）格式：前缀+序号 如ALB001
    private String customerName;        // 客户名称（公司全称）
    private String shortName;           // 客户简称
    private String customerType;        // 客户类型：企业客户、个人客户
    private String customerLevel;       // 客户等级：A级客户、B级客户、C级客户、潜在客户
    private String industry;            // 所属行业
    private String orderNoPrefix;       // 订单号前缀
    private String orderNoSuffix;       // 订单号后缀
    
    // 企业信息
    private String taxNumber;           // 纳税人识别号
    private String legalPerson;         // 法人代表
    private BigDecimal registeredCapital; // 注册资本（万元）
    private String registeredAddress;   // 注册地址
    private String businessAddress;     // 经营地址
    private String contactAddress;      // 联系地址（默认收货地址）
    private String receiveAddress;      // 收货地址（优先使用）
    private String businessScope;       // 经营范围
    private String creditCode;          // 统一社会信用代码
    
    // 联系信息
    private String companyPhone;        // 公司电话
    private String companyFax;          // 公司传真
    private String companyEmail;        // 公司邮箱
    private String website;             // 公司网站
    
    // 财务信息
    private BigDecimal creditLimit;     // 信用额度（元）
    private String paymentTerms;        // 付款条件：现款现货、货到付款、月结30天、月结60天、预付30%
    private BigDecimal taxRate;         // 税率(%)
    private String bankName;            // 开户银行
    private String bankAccount;         // 银行账号
    
    // 销售信息
    private String source;              // 客户来源：网络推广、老客户介绍、展会、电话营销、其他
    @TableField("sales")
    private Long salesUserId;           // 销售用户ID
    @TableField("documentation_person")
    private Long documentationPersonUserId; // 跟单员用户ID
    
    // 状态管理
    private String status;              // 状态：正常、冻结、黑名单
    private String remark;              // 备注
    
    // 系统字段
    private String createBy;            // 创建人
    private LocalDateTime createTime;   // 创建时间
    private String updateBy;            // 更新人
    private LocalDateTime updateTime;   // 更新时间
    private Integer isDeleted;          // 是否删除：0-否，1-是
    
    // 联系人列表（不映射到数据库）
    @TableField(exist = false)
    private List<CustomerContact> contacts;
    
    // 主联系人信息（不映射到数据库，用于列表显示）
    @TableField(exist = false)
    private String primaryContactName;
    
    @TableField(exist = false)
    private String primaryContactMobile;
    
    // 联系人数量（不映射到数据库，用于统计）
    @TableField(exist = false)
    private Integer contactCount;
    
    // 前端展示字段（不映射到数据库）
    @TableField(exist = false)
    private String salesUserName;       // 销售真实姓名
    @TableField(exist = false)
    private String documentationPersonUserName; // 跟单员真实姓名
}