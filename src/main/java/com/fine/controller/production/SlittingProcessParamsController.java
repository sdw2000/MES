package com.fine.controller.production;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fine.Utils.ResponseResult;
import com.fine.model.production.SlittingProcessParams;
import com.fine.service.production.SlittingProcessParamsService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Api(tags = "分切工艺参数管理")
@RestController
@RequestMapping("/production/slitting-params")
public class SlittingProcessParamsController {

    private final SlittingProcessParamsService service;

    public SlittingProcessParamsController(SlittingProcessParamsService service) {
        this.service = service;
    }

    @ApiOperation("分页查询分切工艺参数")
    @GetMapping("/list")
    public ResponseResult<Map<String, Object>> list(@RequestParam(required = false) BigDecimal totalThickness,
                                                    @RequestParam(required = false) BigDecimal processLength,
                                                    @RequestParam(required = false) BigDecimal processWidth,
                                                    @RequestParam(required = false) String equipmentCode,
                                                    @RequestParam(defaultValue = "1") Integer page,
                                                    @RequestParam(defaultValue = "10") Integer size) {
        IPage<SlittingProcessParams> pageResult = service.getPage(totalThickness, processLength, processWidth, equipmentCode, page, size);
        Map<String, Object> result = new HashMap<>();
        result.put("list", pageResult.getRecords());
        result.put("total", pageResult.getTotal());
        result.put("page", pageResult.getCurrent());
        result.put("size", pageResult.getSize());
        result.put("pages", pageResult.getPages());
        return ResponseResult.success(result);
    }

    @ApiOperation("按厚度/长度/宽度和设备编码查询分切工艺参数")
    @GetMapping("/get")
    public ResponseResult<SlittingProcessParams> get(@RequestParam BigDecimal totalThickness,
                                                     @RequestParam BigDecimal processLength,
                                                     @RequestParam BigDecimal processWidth,
                                                     @RequestParam(required = false) String equipmentCode) {
        return ResponseResult.success(service.getByDimensions(totalThickness, processLength, processWidth, equipmentCode));
    }

    @ApiOperation("获取分切工艺参数详情")
    @GetMapping("/{id}")
    public ResponseResult<SlittingProcessParams> getById(@PathVariable Long id) {
        return ResponseResult.success(service.getById(id));
    }

    @ApiOperation("新增分切工艺参数")
    @PostMapping
    public ResponseResult<Void> add(@RequestBody SlittingProcessParams params) {
        try {
            return service.addParams(params) ? ResponseResult.success() : ResponseResult.fail("新增失败");
        } catch (RuntimeException e) {
            return ResponseResult.fail(e.getMessage());
        }
    }

    @ApiOperation("更新分切工艺参数")
    @PutMapping("/{id}")
    public ResponseResult<Void> update(@PathVariable Long id, @RequestBody SlittingProcessParams params) {
        try {
            params.setId(id);
            return service.updateParams(params) ? ResponseResult.success() : ResponseResult.fail("更新失败");
        } catch (RuntimeException e) {
            return ResponseResult.fail(e.getMessage());
        }
    }

    @ApiOperation("删除分切工艺参数")
    @DeleteMapping("/{id}")
    public ResponseResult<Void> delete(@PathVariable Long id) {
        return service.deleteParams(id) ? ResponseResult.success() : ResponseResult.fail("删除失败");
    }
}
