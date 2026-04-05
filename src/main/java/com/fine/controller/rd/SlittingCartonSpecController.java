package com.fine.controller.rd;

import com.fine.Utils.ResponseResult;
import com.fine.modle.rd.SlittingCartonSpec;
import com.fine.service.rd.SlittingCartonSpecService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rd/carton-spec")
public class SlittingCartonSpecController {

    @Autowired
    private SlittingCartonSpecService slittingCartonSpecService;

    @GetMapping("/list")
    @PreAuthorize("isAuthenticated()")
    public ResponseResult<?> getList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String materialCode,
            @RequestParam(required = false) String specName,
            @RequestParam(required = false) Integer status
    ) {
        return slittingCartonSpecService.getList(page, size, materialCode, specName, status);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseResult<?> getById(@PathVariable Long id) {
        return slittingCartonSpecService.getById(id);
    }

    @GetMapping("/by-material/{materialCode}")
    @PreAuthorize("isAuthenticated()")
    public ResponseResult<?> getByMaterialCode(@PathVariable String materialCode,
                                               @RequestParam(required = false) Integer status) {
        return slittingCartonSpecService.getByMaterialCode(materialCode, status);
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('admin','rd')")
    public ResponseResult<?> create(@RequestBody SlittingCartonSpec spec) {
        return slittingCartonSpecService.create(spec, "admin");
    }

    @PutMapping
    @PreAuthorize("hasAnyAuthority('admin','rd')")
    public ResponseResult<?> update(@RequestBody SlittingCartonSpec spec) {
        return slittingCartonSpecService.update(spec, "admin");
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('admin','rd')")
    public ResponseResult<?> delete(@PathVariable Long id) {
        return slittingCartonSpecService.delete(id);
    }
}
