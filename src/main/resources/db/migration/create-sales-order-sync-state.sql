-- 销售订单历史初始化/增量同步状态表
CREATE TABLE IF NOT EXISTS sales_order_sync_state (
  id BIGINT NOT NULL PRIMARY KEY COMMENT '单例主键(固定为1)',
  initialized TINYINT NOT NULL DEFAULT 0 COMMENT '是否已完成历史初始化:0否1是',
  initialized_at DATETIME NULL COMMENT '历史初始化完成时间',
  initialized_by VARCHAR(64) NULL COMMENT '历史初始化执行人',
  last_sync_at DATETIME NULL COMMENT '最近增量同步时间',
  last_sync_by VARCHAR(64) NULL COMMENT '最近增量同步执行人',
  last_import_file VARCHAR(255) NULL COMMENT '最近导入文件名',
  total_orders INT NOT NULL DEFAULT 0 COMMENT '累计成功导入订单数',
  total_items INT NOT NULL DEFAULT 0 COMMENT '累计成功导入明细数',
  remark VARCHAR(255) NULL COMMENT '备注',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='销售订单同步状态';
