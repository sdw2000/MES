package com.fine.controller.production;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fine.Dao.RewindingCodingInboundMapper;
import com.fine.Utils.ResponseResult;
import com.fine.entity.RewindingCodingInbound;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/production/rewinding-coding")
@PreAuthorize("hasAnyAuthority('admin','sales','finance','production','packaging','packing','plan','warehouse','quality','rd')")
public class RewindingCodingInboundController {

    @Autowired
    private RewindingCodingInboundMapper rewindingCodingInboundMapper;

    @PostMapping("/save")
    public ResponseResult<?> save(@RequestBody Map<String, Object> body) {
        try {
            String motherRollCode = text(body, "motherRollCode");
            if (!StringUtils.hasText(motherRollCode)) {
                return ResponseResult.error("母卷号不能为空");
            }
            String materialCode = text(body, "materialCode");
            String materialName = text(body, "materialName");
            Long motherStockId = toLong(body.get("motherStockId"));
            Integer motherThickness = toInteger(body.get("motherThickness"));
            Integer motherWidthMm = toInteger(body.get("motherWidthMm"));
            BigDecimal motherLengthM = toDecimal(body.get("motherLengthM"));
            String operator = text(body, "operator");
            String visibleFields = csv(body.get("visibleFields"));

            Object rowsObj = body.get("rows");
            if (!(rowsObj instanceof List) || ((List<?>) rowsObj).isEmpty()) {
                return ResponseResult.error("请至少填写一行复卷参数");
            }

            String batchNo = "RCI" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + new Random().nextInt(1000);
            int lineNo = 1;
            int inserted = 0;
            for (Object one : (List<?>) rowsObj) {
                if (!(one instanceof Map)) continue;
                Map<?, ?> row = (Map<?, ?>) one;
                BigDecimal rewindingLengthM = toDecimal(row.get("rewindingLengthM"));
                if (rewindingLengthM == null || rewindingLengthM.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                Integer serialStart = toInteger(row.get("serialStart"));
                Integer printCount = toInteger(row.get("printCount"));
                Integer rewindingRollCount = toInteger(row.get("rewindingRollCount"));
                if (serialStart == null || serialStart < 1) serialStart = 1;
                if (printCount == null || printCount < 1) printCount = 1;
                if (rewindingRollCount == null || rewindingRollCount < 1) rewindingRollCount = 1;

                String rewindingSpec = buildSpecText(motherThickness, motherWidthMm, rewindingLengthM);

                RewindingCodingInbound entity = new RewindingCodingInbound();
                entity.setBatchNo(batchNo);
                entity.setMotherRollCode(motherRollCode);
                entity.setMotherStockId(motherStockId);
                entity.setMaterialCode(materialCode);
                entity.setMaterialName(materialName);
                entity.setMotherThickness(motherThickness);
                entity.setMotherWidthMm(motherWidthMm);
                entity.setMotherLengthM(motherLengthM);
                entity.setRewindingLengthM(rewindingLengthM);
                entity.setRewindingRollCount(rewindingRollCount);
                entity.setRewindingSpec(rewindingSpec);
                entity.setLineNo(lineNo++);
                entity.setSerialStart(serialStart);
                entity.setPrintCount(printCount);
                entity.setOperator(StringUtils.hasText(operator) ? operator : resolveOperator());
                entity.setVisibleFields(visibleFields);
                entity.setCreatedAt(LocalDateTime.now());
                entity.setUpdatedAt(LocalDateTime.now());

                rewindingCodingInboundMapper.insert(entity);
                inserted++;
            }

            if (inserted <= 0) {
                return ResponseResult.error("没有可保存的复卷行数据");
            }

            Map<String, Object> data = new HashMap<>();
            data.put("batchNo", batchNo);
            data.put("rows", inserted);
            return ResponseResult.success("保存成功", data);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseResult.error("保存复卷入库记录失败: " + e.getMessage());
        }
    }

    @GetMapping("/page")
    public ResponseResult<?> page(@RequestParam(defaultValue = "1") Integer pageNum,
                                  @RequestParam(defaultValue = "20") Integer pageSize,
                                  @RequestParam(required = false) String motherRollCode,
                                  @RequestParam(required = false) String materialCode,
                                  @RequestParam(required = false) String operator,
                                  @RequestParam(required = false) String batchNo) {
        try {
            QueryWrapper<RewindingCodingInbound> qw = new QueryWrapper<>();
            if (StringUtils.hasText(motherRollCode)) qw.like("mother_roll_code", motherRollCode.trim());
            if (StringUtils.hasText(materialCode)) qw.like("material_code", materialCode.trim());
            if (StringUtils.hasText(operator)) qw.like("operator", operator.trim());
            if (StringUtils.hasText(batchNo)) qw.eq("batch_no", batchNo.trim());
            qw.orderByDesc("id");
            Page<RewindingCodingInbound> result = rewindingCodingInboundMapper.selectPage(new Page<>(pageNum, pageSize), qw);
            return ResponseResult.success(result);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseResult.error("查询复卷入库记录失败: " + e.getMessage());
        }
    }

    private String buildSpecText(Integer thickness, Integer widthMm, BigDecimal lengthM) {
        String t = thickness == null || thickness <= 0 ? "-" : String.valueOf(thickness);
        String w = widthMm == null || widthMm <= 0 ? "-" : String.valueOf(widthMm);
        String l = lengthM == null || lengthM.compareTo(BigDecimal.ZERO) <= 0 ? "-" : lengthM.stripTrailingZeros().toPlainString();
        return t + "μm*" + w + "mm*" + l + "m";
    }

    private String text(Map<String, Object> body, String key) {
        if (body == null || key == null) return null;
        Object value = body.get(key);
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private Long toLong(Object value) {
        if (value == null) return null;
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (Exception e) {
            return null;
        }
    }

    private Integer toInteger(Object value) {
        if (value == null) return null;
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (Exception e) {
            return null;
        }
    }

    private BigDecimal toDecimal(Object value) {
        if (value == null) return null;
        try {
            return new BigDecimal(String.valueOf(value).trim());
        } catch (Exception e) {
            return null;
        }
    }

    private String csv(Object value) {
        if (!(value instanceof List)) return null;
        List<?> list = (List<?>) value;
        List<String> out = new ArrayList<>();
        for (Object one : list) {
            String t = String.valueOf(one == null ? "" : one).trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out.isEmpty() ? null : String.join(",", out);
    }

    private String resolveOperator() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || "anonymousUser".equalsIgnoreCase(authentication.getName())) {
            return "system";
        }
        return authentication.getName();
    }
}
