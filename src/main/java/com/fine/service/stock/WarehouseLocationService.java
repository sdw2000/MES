package com.fine.service.stock;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.fine.modle.stock.WarehouseLocation;

public interface WarehouseLocationService extends IService<WarehouseLocation> {
    IPage<WarehouseLocation> listLocations(Page<WarehouseLocation> page, Integer warehouseId, String keyword);
}
