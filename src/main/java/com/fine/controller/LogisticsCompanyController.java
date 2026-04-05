package com.fine.controller;

import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fine.Utils.ResponseResult;
import com.fine.modle.LogisticsCompany;
import com.fine.service.LogisticsCompanyService;

@RestController
@RequestMapping("/logistics-company")
@PreAuthorize("hasAnyAuthority('admin','sales','warehouse')")
public class LogisticsCompanyController {

    @Autowired
    private LogisticsCompanyService logisticsCompanyService;

    @GetMapping("/list")
    public ResponseResult<?> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) String keyword) {
        Page<LogisticsCompany> p = new Page<>(page, size);
        QueryWrapper<LogisticsCompany> wrapper = new QueryWrapper<>();
        if (keyword != null && !keyword.trim().isEmpty()) {
            wrapper.and(w -> w.like("company_name", keyword)
                              .or()
                              .like("contact_phone", keyword));
        }
        wrapper.orderByDesc("created_at");
        return ResponseResult.success(logisticsCompanyService.page(p, wrapper));
    }

    @PostMapping("/create")
    public ResponseResult<?> create(@RequestBody LogisticsCompany company) {
        company.setId(null);
        if (company.getStatus() == null || company.getStatus().isEmpty()) {
            company.setStatus("active");
        }
        company.setCreatedAt(new Date());
        company.setUpdatedAt(new Date());
        company.setIsDeleted(0);
        boolean ok = logisticsCompanyService.save(company);
        return ok ? ResponseResult.success(company) : ResponseResult.error(500, "创建失败");
    }

    @PostMapping("/update")
    public ResponseResult<?> update(@RequestBody LogisticsCompany company) {
        if (company.getId() == null) {
            return ResponseResult.error(400, "缺少ID");
        }
        company.setUpdatedAt(new Date());
        boolean ok = logisticsCompanyService.updateById(company);
        return ok ? ResponseResult.success("更新成功", company) : ResponseResult.error(500, "更新失败");
    }

    @PostMapping("/delete/{id}")
    public ResponseResult<?> delete(@PathVariable Long id) {
        boolean ok = logisticsCompanyService.removeById(id);
        return ok ? ResponseResult.success("删除成功", null) : ResponseResult.error(500, "删除失败");
    }
}
