package com.fine.serviceIMPL.stock;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fine.Dao.stock.WarehouseMapper;
import com.fine.modle.stock.Warehouse;
import com.fine.service.stock.WarehouseService;
import org.springframework.stereotype.Service;

@Service
public class WarehouseServiceImpl extends ServiceImpl<WarehouseMapper, Warehouse> implements WarehouseService {
}
