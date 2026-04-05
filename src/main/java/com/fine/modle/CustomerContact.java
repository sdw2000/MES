package com.fine.modle;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 客户联系人实体类
 * @author Fine
 * @date 2026-01-06
 */
@Data
@TableName("customer_contacts")
public class CustomerContact {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long customerId;            // 客户ID（外键）
    
    // 基本信息
    private String contactName;         // 联系人姓名
    private String contactGender;       // 性别：男、女
    private String contactPosition;     // 职位
    private String contactDepartment;   // 所属部门
    
    // 联系方式
    private String contactPhone;        // 联系电话（固定电话或手机）
    private String contactMobile;       // 手机号码
    private String contactEmail;        // 邮箱
    private String contactWechat;       // 微信号
    private String contactQq;           // QQ号
    private String contactAddress;      // 联系地址
    
    // 标记信息
    private Integer isPrimary;          // 是否主联系人：0-否，1-是
    private Integer isDecisionMaker;    // 是否决策人：0-否，1-是
    private Integer isReceiver;         // 是否收货人：0-否，1-是
    
    // 个人信息
    private LocalDate birthday;         // 生日
    private String hobby;               // 爱好
    
    // 其他
    private String remark;              // 备注
    private Integer sortOrder;          // 排序（数字越小越靠前）
    
    // 系统字段
    private LocalDateTime createTime;   // 创建时间
    private LocalDateTime updateTime;   // 更新时间
}
