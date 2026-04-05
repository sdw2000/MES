package com.fine.controller.production;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fine.Utils.ResponseResult;
import com.fine.model.production.RewindingProcessParams;
import com.fine.service.production.RewindingProcessParamsService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Api(tags = "复卷工艺参数管理")
@RestController
@RequestMapping("/production/rewinding-params")
public class RewindingProcessParamsController {

    private final RewindingProcessParamsService service;

    public RewindingProcessParamsController(RewindingProcessParamsService service) {
        this.service = service;
    }

    @ApiOperation("分页查询复卷工艺参数")
    @GetMapping("/list")
    public ResponseResult<Map<String, Object>> list(@RequestParam(required = false) String materialCode,
                                                    @RequestParam(required = false) String equipmentCode,
                                                    @RequestParam(defaultValue = "1") Integer page,
                                                    @RequestParam(defaultValue = "10") Integer size) {
        IPage<RewindingProcessParams> pageResult = service.getPage(materialCode, equipmentCode, page, size);
        Map<String, Object> result = new HashMap<>();
        result.put("list", pageResult.getRecords());
        result.put("total", pageResult.getTotal());
        result.put("page", pageResult.getCurrent());
        result.put("size", pageResult.getSize());
        result.put("pages", pageResult.getPages());
        return ResponseResult.success(result);
    }

    @ApiOperation("按料号和设备编码查询复卷工艺参数")
    @GetMapping("/get")
    public ResponseResult<RewindingProcessParams> get(@RequestParam String materialCode,
                                                      @RequestParam(required = false) String equipmentCode) {
        return ResponseResult.success(service.getByMaterialAndEquipment(materialCode, equipmentCode));
    }

    @ApiOperation("获取复卷工艺参数详情")
    @GetMapping("/{id}")
    public ResponseResult<RewindingProcessParams> getById(@PathVariable Long id) {
        return ResponseResult.success(service.getById(id));
    }

    @ApiOperation("新增复卷工艺参数")
    @PostMapping
    public ResponseResult<Void> add(@RequestBody RewindingProcessParams params) {
        try {
            return service.addParams(params) ? ResponseResult.success() : ResponseResult.fail("新增失败");
        } catch (RuntimeException e) {
            return ResponseResult.fail(e.getMessage());
        }
    }

    @ApiOperation("更新复卷工艺参数")
    @PutMapping("/{id}")
    public ResponseResult<Void> update(@PathVariable Long id, @RequestBody RewindingProcessParams params) {
        try {
            params.setId(id);
            return service.updateParams(params) ? ResponseResult.success() : ResponseResult.fail("更新失败");
        } catch (RuntimeException e) {
            return ResponseResult.fail(e.getMessage());
        }
    }

    @ApiOperation("删除复卷工艺参数")
    @DeleteMapping("/{id}")
    public ResponseResult<Void> delete(@PathVariable Long id) {
        return service.deleteParams(id) ? ResponseResult.success() : ResponseResult.fail("删除失败");
    }
}
