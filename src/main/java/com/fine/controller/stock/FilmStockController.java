package com.fine.controller.stock;

import com.fine.Utils.ResponseResult;
import com.fine.model.stock.FilmStock;
import com.fine.model.stock.FilmStockDetail;
import com.fine.model.stock.FilmStockOut;
import com.fine.service.stock.FilmStockService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 薄膜库存管理Controller
 * @author Fine
 * @date 2026-01-15
 */
@PreAuthorize("hasAuthority('admin')")
@RestController
@RequestMapping("/api/stock/film")
@CrossOrigin
public class FilmStockController {
    
    @Autowired
    private FilmStockService filmStockService;
    
    /**
     * 查询所有薄膜库存
     */
    @GetMapping("/list")
    public ResponseResult<List<FilmStock>> getFilmStockList() {
        List<FilmStock> list = filmStockService.getAllFilmStock();
        return new ResponseResult<>(20000, "查询成功", list);
    }
    
    /**
     * 按规格查询薄膜库存
     */
    @GetMapping("/spec")
    public ResponseResult<List<FilmStock>> getBySpec(
            @RequestParam(required = false) Integer thickness,
            @RequestParam(required = false) Integer width
    ) {
        List<FilmStock> list = filmStockService.getBySpec(thickness, width);
        return new ResponseResult<>(20000, "查询成功", list);
    }
    
    /**
     * 根据ID查询薄膜库存
     */
    @GetMapping("/{id}")
    public ResponseResult<FilmStock> getById(@PathVariable Long id) {
        FilmStock filmStock = filmStockService.getById(id);
        if (filmStock == null) {
            return new ResponseResult<>(40004, "薄膜库存不存在", null);
        }
        return new ResponseResult<>(20000, "查询成功", filmStock);
    }
    
    /**
     * 查询薄膜库存明细
     */
    @GetMapping("/{id}/details")
    public ResponseResult<List<FilmStockDetail>> getDetails(@PathVariable Long id) {
        List<FilmStockDetail> details = filmStockService.getDetailsByFilmStockId(id);
        return new ResponseResult<>(20000, "查询成功", details);
    }
    
    /**
     * 查询可用的薄膜明细
     */
    @GetMapping("/{id}/available")
    public ResponseResult<List<FilmStockDetail>> getAvailableDetails(@PathVariable Long id) {
        List<FilmStockDetail> details = filmStockService.getAvailableDetails(id);
        return new ResponseResult<>(20000, "查询成功", details);
    }
    
    /**
     * 查询薄膜出库记录
     */
    @GetMapping("/outbound/schedule/{scheduleId}")
    public ResponseResult<List<FilmStockOut>> getOutboundBySchedule(@PathVariable Long scheduleId) {
        List<FilmStockOut> records = filmStockService.getOutboundByScheduleId(scheduleId);
        return new ResponseResult<>(20000, "查询成功", records);
    }
}
