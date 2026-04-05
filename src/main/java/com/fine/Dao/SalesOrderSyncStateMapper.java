package com.fine.Dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fine.modle.SalesOrderSyncState;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SalesOrderSyncStateMapper extends BaseMapper<SalesOrderSyncState> {

    @Select("SELECT * FROM sales_order_sync_state WHERE id = 1 LIMIT 1")
    SalesOrderSyncState selectSingleton();

    @Insert("INSERT INTO sales_order_sync_state(" +
            "id, initialized, initialized_at, initialized_by, last_sync_at, last_sync_by, last_import_file, " +
            "total_orders, total_items, remark, created_at, updated_at" +
            ") VALUES (" +
            "#{id}, #{initialized}, #{initializedAt}, #{initializedBy}, #{lastSyncAt}, #{lastSyncBy}, #{lastImportFile}, " +
            "#{totalOrders}, #{totalItems}, #{remark}, #{createdAt}, #{updatedAt}" +
            ") ON DUPLICATE KEY UPDATE " +
            "initialized = VALUES(initialized), " +
            "initialized_at = VALUES(initialized_at), " +
            "initialized_by = VALUES(initialized_by), " +
            "last_sync_at = VALUES(last_sync_at), " +
            "last_sync_by = VALUES(last_sync_by), " +
            "last_import_file = VALUES(last_import_file), " +
            "total_orders = VALUES(total_orders), " +
            "total_items = VALUES(total_items), " +
            "remark = VALUES(remark), " +
            "updated_at = VALUES(updated_at)")
    int upsert(SalesOrderSyncState state);

            @Update("CREATE TABLE IF NOT EXISTS sales_order_sync_state (" +
                "id BIGINT NOT NULL PRIMARY KEY, " +
                "initialized TINYINT NOT NULL DEFAULT 0, " +
                "initialized_at DATETIME NULL, " +
                "initialized_by VARCHAR(64) NULL, " +
                "last_sync_at DATETIME NULL, " +
                "last_sync_by VARCHAR(64) NULL, " +
                "last_import_file VARCHAR(255) NULL, " +
                "total_orders INT NOT NULL DEFAULT 0, " +
                "total_items INT NOT NULL DEFAULT 0, " +
                "remark VARCHAR(255) NULL, " +
                "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4")
            int ensureTableExists();

    @Delete("DELETE FROM sales_order_sync_state WHERE id = 1")
    int deleteSingleton();
}
