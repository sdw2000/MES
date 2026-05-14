package com.fine.controller.stock;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fine.Dao.stock.PackageStockDetailMapper;
import com.fine.Dao.stock.PackageStockMapper;
import com.fine.Utils.ResponseResult;
import com.fine.model.stock.PackageStock;
import com.fine.model.stock.PackageStockDetail;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 包材仓库存管理
 */
@PreAuthorize("hasAnyAuthority('admin','warehouse','production','finance','quality')")
@RestController
@RequestMapping("/api/stock/package")
@CrossOrigin
public class PackageStockController {

    @Autowired
    private PackageStockMapper packageStockMapper;

    @Autowired
    private PackageStockDetailMapper packageStockDetailMapper;

    @GetMapping("/list")
    public ResponseResult<List<PackageStock>> list() {
        LambdaQueryWrapper<PackageStock> qw = new LambdaQueryWrapper<>();
        qw.orderByAsc(PackageStock::getMaterialCode);
        return new ResponseResult<>(20000, "查询成功", packageStockMapper.selectList(qw));
    }

    @GetMapping("/list/page")
    public ResponseResult<IPage<PackageStock>> page(
            @RequestParam(defaultValue = "1") Long current,
            @RequestParam(defaultValue = "20") Long size,
            @RequestParam(required = false) String materialCode,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortOrder
    ) {
        LambdaQueryWrapper<PackageStock> qw = new LambdaQueryWrapper<>();
        String code = materialCode == null ? "" : materialCode.trim();
        if (!code.isEmpty()) {
            qw.and(w -> w.like(PackageStock::getMaterialCode, code)
                    .or().like(PackageStock::getMaterialName, code));
        }

        boolean asc = "ascending".equalsIgnoreCase(sortOrder) || "asc".equalsIgnoreCase(sortOrder);
        String field = sortField == null ? "" : sortField.trim();
        if ("materialCode".equals(field)) {
            qw.orderBy(true, asc, PackageStock::getMaterialCode);
        } else if ("materialName".equals(field)) {
            qw.orderBy(true, asc, PackageStock::getMaterialName);
        } else if ("totalQuantity".equals(field)) {
            qw.orderBy(true, asc, PackageStock::getTotalQuantity);
        } else if ("availableQuantity".equals(field)) {
            qw.orderBy(true, asc, PackageStock::getAvailableQuantity);
        } else if ("lockedQuantity".equals(field)) {
            qw.orderBy(true, asc, PackageStock::getLockedQuantity);
        } else if ("status".equals(field)) {
            qw.orderBy(true, asc, PackageStock::getStatus);
        } else {
            qw.orderByAsc(PackageStock::getMaterialCode);
        }

        Page<PackageStock> page = new Page<>(current, size);
        IPage<PackageStock> result = packageStockMapper.selectPage(page, qw);
        return new ResponseResult<>(20000, "查询成功", result);
    }

    @GetMapping("/list/statistics")
    public ResponseResult<Map<String, Object>> statistics(
            @RequestParam(required = false) String materialCode
    ) {
        LambdaQueryWrapper<PackageStock> qw = new LambdaQueryWrapper<>();
        String code = materialCode == null ? "" : materialCode.trim();
        if (!code.isEmpty()) {
            qw.and(w -> w.like(PackageStock::getMaterialCode, code)
                    .or().like(PackageStock::getMaterialName, code));
        }
        List<PackageStock> list = packageStockMapper.selectList(qw);

        long totalTypes = list.size();
        int totalQuantity = 0;
        int availableQuantity = 0;
        int lockedQuantity = 0;
        for (PackageStock s : list) {
            totalQuantity += s == null || s.getTotalQuantity() == null ? 0 : s.getTotalQuantity();
            availableQuantity += s == null || s.getAvailableQuantity() == null ? 0 : s.getAvailableQuantity();
            lockedQuantity += s == null || s.getLockedQuantity() == null ? 0 : s.getLockedQuantity();
        }

        Map<String, Object> data = new HashMap<>();
        data.put("totalTypes", totalTypes);
        data.put("totalQuantity", totalQuantity);
        data.put("availableQuantity", availableQuantity);
        data.put("lockedQuantity", lockedQuantity);
        return new ResponseResult<>(20000, "查询成功", data);
    }

    @GetMapping("/{id}/details/page")
    public ResponseResult<IPage<PackageStockDetail>> detailPage(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") Long current,
            @RequestParam(defaultValue = "20") Long size,
            @RequestParam(defaultValue = "false") Boolean includeUsed
    ) {
        LambdaQueryWrapper<PackageStockDetail> qw = new LambdaQueryWrapper<>();
        qw.eq(PackageStockDetail::getPackageStockId, id)
                .eq(PackageStockDetail::getIsDeleted, 0);
        if (!Boolean.TRUE.equals(includeUsed)) {
            qw.ne(PackageStockDetail::getStatus, "used");
        }
        qw.orderByAsc(PackageStockDetail::getInboundDate)
                .orderByAsc(PackageStockDetail::getId);

        Page<PackageStockDetail> page = new Page<>(current, size);
        IPage<PackageStockDetail> result = packageStockDetailMapper.selectPage(page, qw);
        return new ResponseResult<>(20000, "查询成功", result);
    }

    @GetMapping("/{id}/details")
    public ResponseResult<List<PackageStockDetail>> details(@PathVariable Long id) {
        LambdaQueryWrapper<PackageStockDetail> qw = new LambdaQueryWrapper<>();
        qw.eq(PackageStockDetail::getPackageStockId, id)
                .eq(PackageStockDetail::getIsDeleted, 0)
                .orderByAsc(PackageStockDetail::getInboundDate)
                .orderByAsc(PackageStockDetail::getId);
        return new ResponseResult<>(20000, "查询成功", packageStockDetailMapper.selectList(qw));
    }
}
