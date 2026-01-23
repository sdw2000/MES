package com.fine.modle;

import com.alibaba.excel.annotation.ExcelProperty;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("tapes")
public class Tape {
	
	 @TableId(type = IdType.AUTO)
	private int id;

    @ExcelProperty("名称")
    private String materialName;

    @ExcelProperty("胶带料号")
    private String partNumber;

    @ExcelProperty("颜色")
    private String color;

    @ExcelProperty("基材厚度")
    private Double substrateThickness;

    @ExcelProperty("基材材质")
    private String substrateMaterial;

    @ExcelProperty("胶水材质")
    private String adhesiveMaterial;

    @ExcelProperty("胶水厚度")
    private Double adhesiveThickness;

    @ExcelProperty("初粘下限")
    private Double initialTackMin;

    @ExcelProperty("初粘上限")
    private Double initialTackMax;

    @ExcelProperty("总厚度")
    private Double totalThickness;

    @ExcelProperty("厚度波动")
    private Double thicknessVariation;

    @ExcelProperty("剥离力下限")
    private Double peelStrengthMin;

    @ExcelProperty("剥离力上限")
    private Double peelStrengthMax;

    @ExcelProperty("解卷力下限")
    private Double unwindForceMin;

    @ExcelProperty("解卷力上限")
    private Double unwindForceMax;

    @ExcelProperty("耐温")
    private Double temperatureResistance;

    // Getters and Setters
}
