package com.fine.serviceIMPL.stock;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fine.Dao.stock.WarehouseLocationMapper;
import com.fine.modle.stock.WarehouseLocation;
import com.fine.service.stock.WarehouseLocationService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class WarehouseLocationServiceImpl extends ServiceImpl<WarehouseLocationMapper, WarehouseLocation> implements WarehouseLocationService {

    @Override
    public IPage<WarehouseLocation> listLocations(Page<WarehouseLocation> page, Integer warehouseId, String keyword) {
        LambdaQueryWrapper<WarehouseLocation> wrapper = new LambdaQueryWrapper<>();
        if (warehouseId != null) {
            wrapper.eq(WarehouseLocation::getWarehouseId, warehouseId);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(WarehouseLocation::getLocationCode, keyword)
                    .or().like(WarehouseLocation::getLocationName, keyword));
        }
        wrapper.orderByAsc(WarehouseLocation::getLocationCode);
        return baseMapper.selectLocationPage(page, wrapper);
    }
}
