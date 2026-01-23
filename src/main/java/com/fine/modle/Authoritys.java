package com.fine.modle;

import java.io.Serializable;


import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;


@TableName("authorities")
@Data
public class Authoritys implements Serializable{
    /**
     *
     */
    private static final long serialVersionUID = 5771915676216380245L;
    private int id;
    private int userid;
    private String authority;
}