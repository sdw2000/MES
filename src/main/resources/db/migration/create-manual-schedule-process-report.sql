-- 工序报工记录表（支持涂布/复卷/分切多次报工）
CREATE TABLE IF NOT EXISTS manual_schedule_process_report (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  schedule_id BIGINT NOT NULL COMMENT '手动排程ID',
  process_type VARCHAR(20) NOT NULL COMMENT '工序类型: COATING/REWINDING/SLITTING',
  start_time DATETIME NOT NULL COMMENT '开始时间',
  end_time DATETIME NOT NULL COMMENT '结束时间',
  produced_qty DECIMAL(12,2) NOT NULL COMMENT '本次报工产量(卷)',
  operator_name VARCHAR(64) DEFAULT NULL COMMENT '操作人',
  remark VARCHAR(255) DEFAULT NULL COMMENT '备注',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除:0否1是',
  PRIMARY KEY (id),
  KEY idx_schedule_process (schedule_id, process_type, is_deleted),
  KEY idx_start_time (start_time),
  KEY idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='手动排程工序报工记录';
