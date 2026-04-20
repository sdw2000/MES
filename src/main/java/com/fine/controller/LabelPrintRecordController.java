package com.fine.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fine.Dao.LabelPrintRecordMapper;
import com.fine.Utils.ResponseResult;
import com.fine.entity.LabelPrintRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/production/label-print-record")
@PreAuthorize("hasAnyAuthority('admin','sales','finance','production','packaging','plan','warehouse','quality','rd')")
public class LabelPrintRecordController {

    @Autowired
    private LabelPrintRecordMapper labelPrintRecordMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @PostMapping("/save")
    public ResponseResult<?> save(@RequestBody Map<String, Object> body) {
        try {
            LabelPrintRecord entity = new LabelPrintRecord();
            entity.setSceneName(text(body, "sceneName"));
            entity.setBizType(text(body, "bizType"));
            entity.setTemplateKey(text(body, "templateKey"));
            entity.setJobName(text(body, "jobName"));
            entity.setCopies(number(body, "copies"));
            entity.setCustomerCode(text(body, "customerCode"));
            entity.setCustomerOrderNo(text(body, "customerOrderNo"));
            entity.setOrderNo(text(body, "orderNo"));
            entity.setMaterialCode(text(body, "materialCode"));
            entity.setMaterialName(text(body, "materialName"));
            entity.setBatchNo(text(body, "batchNo"));
            entity.setPrinterName(text(body, "printerName"));
            entity.setPrintStatus(text(body, "printStatus"));
            entity.setResultMessage(text(body, "resultMessage"));

            entity.setPrintDataJson(toJson(body.get("printData")));
            entity.setPrintPayloadJson(toJson(body.get("printPayload")));
            entity.setPrintResultJson(toJson(body.get("printResult")));

            LocalDateTime now = LocalDateTime.now();
            entity.setPrintTime(now);
            entity.setCreateTime(now);
            entity.setOperator(resolveOperator());

            labelPrintRecordMapper.insert(entity);
            return ResponseResult.success(entity.getId());
        } catch (Exception e) {
            e.printStackTrace();
            String msg = e.getMessage() == null ? "" : e.getMessage();
            if (msg.contains("label_print_record") && msg.contains("doesn't exist")) {
                return ResponseResult.success("标签记录表不存在，已跳过记录，不影响实际打印", null);
            }
            return ResponseResult.error("保存标签打印记录失败: " + e.getMessage());
        }
    }

    @GetMapping("/page")
    public ResponseResult<?> page(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) String customerCode,
            @RequestParam(required = false) String customerOrderNo,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String materialCode,
            @RequestParam(required = false) String batchNo,
            @RequestParam(required = false) String bizType,
            @RequestParam(required = false) String templateKey,
            @RequestParam(required = false) String printStatus
    ) {
        try {
            QueryWrapper<LabelPrintRecord> qw = new QueryWrapper<>();
            if (notBlank(customerCode)) qw.eq("customer_code", customerCode.trim());
            if (notBlank(customerOrderNo)) qw.eq("customer_order_no", customerOrderNo.trim());
            if (notBlank(orderNo)) qw.eq("order_no", orderNo.trim());
            if (notBlank(materialCode)) qw.eq("material_code", materialCode.trim());
            if (notBlank(batchNo)) qw.eq("batch_no", batchNo.trim());
            if (notBlank(bizType)) qw.eq("biz_type", bizType.trim());
            if (notBlank(templateKey)) qw.eq("template_key", templateKey.trim());
            if (notBlank(printStatus)) qw.eq("print_status", printStatus.trim().toUpperCase());
            qw.orderByDesc("id");

            Page<LabelPrintRecord> page = new Page<>(pageNum, pageSize);
            Page<LabelPrintRecord> result = labelPrintRecordMapper.selectPage(page, qw);
            return ResponseResult.success(result);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseResult.error("查询标签打印记录失败: " + e.getMessage());
        }
    }

    private String resolveOperator() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || "anonymousUser".equalsIgnoreCase(authentication.getName())) {
            return "system";
        }
        return authentication.getName();
    }

    private String toJson(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }

    private String text(Map<String, Object> body, String key) {
        if (body == null || key == null) return null;
        Object value = body.get(key);
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private Integer number(Map<String, Object> body, String key) {
        if (body == null || key == null) return null;
        Object value = body.get(key);
        if (value == null) return null;
        try {
            int n = Integer.parseInt(String.valueOf(value).trim());
            return n > 0 ? n : null;
        } catch (Exception e) {
            return null;
        }
    }

    private boolean notBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
