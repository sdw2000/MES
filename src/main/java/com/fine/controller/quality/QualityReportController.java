package com.fine.controller.quality;

import com.fine.Utils.ResponseResult;
import com.fine.service.quality.QualityReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/quality/report")
public class QualityReportController {

    @Autowired
    private QualityReportService reportService;

    @GetMapping("/summary")
    public ResponseResult<?> summary() {
        return ResponseResult.success(reportService.summary());
    }
}
