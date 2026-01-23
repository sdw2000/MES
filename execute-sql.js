// 执行 SQL 脚本的 Node.js 工具
const mysql = require('mysql2/promise');
const fs = require('fs');
const path = require('path');

const config = {
    host: 'ssdw8127.mysql.rds.aliyuncs.com',
    user: 'david',
    password: 'dadazhengzheng@feng',
    database: 'erp',
    multipleStatements: true,
    charset: 'utf8mb4'
};

async function main() {
    let connection;
    
    try {
        console.log('🔄 正在连接数据库...');
        connection = await mysql.createConnection(config);
        console.log('✅ 数据库连接成功！\n');

        // 1. 先检查现有表
        console.log('📋 检查现有表结构...\n');
        const [tables] = await connection.query(`
            SELECT TABLE_NAME, TABLE_COMMENT 
            FROM INFORMATION_SCHEMA.TABLES 
            WHERE TABLE_SCHEMA = 'erp' 
            AND TABLE_NAME LIKE '%schedule%' OR TABLE_NAME LIKE '%production%' 
            OR TABLE_NAME LIKE '%quality%' OR TABLE_NAME LIKE '%urgent%'
            ORDER BY TABLE_NAME
        `);
        
        if (tables.length > 0) {
            console.log('已存在的相关表:');
            tables.forEach(t => console.log(`  - ${t.TABLE_NAME}: ${t.TABLE_COMMENT || '无注释'}`));
        } else {
            console.log('  暂无相关表');
        }
        console.log('');

        // 2. 检查 sales_order_items 是否有新字段
        console.log('📋 检查 sales_order_items 表字段...');
        const [columns] = await connection.query(`
            SELECT COLUMN_NAME 
            FROM INFORMATION_SCHEMA.COLUMNS 
            WHERE TABLE_SCHEMA = 'erp' AND TABLE_NAME = 'sales_order_items'
            AND COLUMN_NAME IN ('scheduled_qty', 'produced_qty', 'delivered_qty', 'remaining_qty', 'production_status')
        `);
        console.log(`  已有字段: ${columns.map(c => c.COLUMN_NAME).join(', ') || '无'}\n`);

        // 3. 添加缺失的字段到 sales_order_items
        const requiredColumns = [
            { name: 'scheduled_qty', def: "INT DEFAULT 0 COMMENT '已排程数量(卷)'" },
            { name: 'produced_qty', def: "INT DEFAULT 0 COMMENT '已生产数量(卷)'" },
            { name: 'delivered_qty', def: "INT DEFAULT 0 COMMENT '已出货数量(卷)'" },
            { name: 'remaining_qty', def: "INT DEFAULT 0 COMMENT '未交货数量(卷)'" },
            { name: 'production_status', def: "VARCHAR(30) DEFAULT 'pending' COMMENT '生产状态'" }
        ];
        
        const existingCols = columns.map(c => c.COLUMN_NAME);
        for (const col of requiredColumns) {
            if (!existingCols.includes(col.name)) {
                try {
                    await connection.query(`ALTER TABLE sales_order_items ADD COLUMN ${col.name} ${col.def}`);
                    console.log(`✅ 添加字段 sales_order_items.${col.name}`);
                } catch (e) {
                    if (e.code === 'ER_DUP_FIELDNAME') {
                        console.log(`⏭️ 字段已存在: sales_order_items.${col.name}`);
                    } else {
                        console.log(`❌ 添加失败: ${col.name} - ${e.message}`);
                    }
                }
            } else {
                console.log(`⏭️ 字段已存在: sales_order_items.${col.name}`);
            }
        }
        console.log('');

        // 4. 创建/更新表 - 使用 CREATE TABLE IF NOT EXISTS
        const tablesToCreate = [
            {
                name: 'production_schedule',
                sql: `CREATE TABLE IF NOT EXISTS production_schedule (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                    schedule_no VARCHAR(50) NOT NULL COMMENT '排程单号',
                    schedule_date DATE NOT NULL COMMENT '排程日期',
                    schedule_type VARCHAR(20) NOT NULL COMMENT '排程类型',
                    total_orders INT DEFAULT 0 COMMENT '涉及订单数',
                    total_items INT DEFAULT 0 COMMENT '涉及订单明细数',
                    total_sqm DECIMAL(12,2) DEFAULT 0 COMMENT '总面积',
                    status VARCHAR(20) DEFAULT 'draft' COMMENT '状态',
                    approval_by VARCHAR(50) COMMENT '审批人',
                    approval_time DATETIME COMMENT '审批时间',
                    approval_remark VARCHAR(500) COMMENT '审批备注',
                    confirmed_by VARCHAR(50) COMMENT '确认人',
                    confirmed_time DATETIME COMMENT '确认时间',
                    remark VARCHAR(500) COMMENT '备注',
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    create_by VARCHAR(50),
                    update_by VARCHAR(50),
                    is_deleted TINYINT DEFAULT 0,
                    UNIQUE KEY uk_schedule_no (schedule_no),
                    INDEX idx_schedule_date (schedule_date),
                    INDEX idx_status (status)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='排程主表'`
            },
            {
                name: 'schedule_order_item',
                sql: `CREATE TABLE IF NOT EXISTS schedule_order_item (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    schedule_id BIGINT NOT NULL COMMENT '排程ID',
                    order_id BIGINT NOT NULL COMMENT '订单ID',
                    order_item_id BIGINT NOT NULL COMMENT '订单明细ID',
                    order_no VARCHAR(50) COMMENT '订单号',
                    customer VARCHAR(200) COMMENT '客户',
                    customer_level VARCHAR(20) COMMENT '客户等级',
                    material_code VARCHAR(50) NOT NULL COMMENT '产品料号',
                    material_name VARCHAR(100) COMMENT '产品名称',
                    color_code VARCHAR(20) COMMENT '颜色代码',
                    thickness DECIMAL(10,3) COMMENT '厚度',
                    width DECIMAL(10,2) COMMENT '宽度',
                    length DECIMAL(10,2) COMMENT '长度',
                    order_qty INT NOT NULL COMMENT '订单数量',
                    schedule_qty INT NOT NULL COMMENT '排程数量',
                    delivery_date DATE COMMENT '交货日期',
                    priority INT DEFAULT 0 COMMENT '优先级',
                    source_type VARCHAR(20) COMMENT '来源类型',
                    stock_id BIGINT COMMENT '库存ID',
                    status VARCHAR(20) DEFAULT 'pending',
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                    INDEX idx_schedule_id (schedule_id),
                    INDEX idx_order_item_id (order_item_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='排程订单关联表'`
            },
            {
                name: 'schedule_printing',
                sql: `CREATE TABLE IF NOT EXISTS schedule_printing (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    schedule_id BIGINT NOT NULL COMMENT '排程ID',
                    task_no VARCHAR(50) NOT NULL COMMENT '任务单号',
                    equipment_id BIGINT,
                    equipment_code VARCHAR(30),
                    staff_id BIGINT,
                    staff_name VARCHAR(50),
                    shift_code VARCHAR(20),
                    plan_date DATE,
                    material_code VARCHAR(50) NOT NULL,
                    material_name VARCHAR(100),
                    color_code VARCHAR(20),
                    color_name VARCHAR(50),
                    print_pattern VARCHAR(100),
                    base_width INT,
                    plan_length DECIMAL(12,2),
                    plan_sqm DECIMAL(12,2),
                    actual_length DECIMAL(12,2),
                    actual_sqm DECIMAL(12,2),
                    printing_speed DECIMAL(10,2),
                    ink_type VARCHAR(50),
                    drying_temp DECIMAL(6,2),
                    plan_start_time DATETIME,
                    plan_end_time DATETIME,
                    plan_duration INT,
                    actual_start_time DATETIME,
                    actual_end_time DATETIME,
                    actual_duration INT,
                    status VARCHAR(20) DEFAULT 'pending',
                    output_batch_no VARCHAR(50),
                    remark VARCHAR(500),
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    create_by VARCHAR(50),
                    update_by VARCHAR(50),
                    UNIQUE KEY uk_task_no (task_no),
                    INDEX idx_schedule_id (schedule_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='印刷计划表'`
            },
            {
                name: 'schedule_coating',
                sql: `CREATE TABLE IF NOT EXISTS schedule_coating (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    schedule_id BIGINT NOT NULL,
                    task_no VARCHAR(50) NOT NULL,
                    printing_task_id BIGINT,
                    equipment_id BIGINT,
                    equipment_code VARCHAR(30),
                    staff_id BIGINT,
                    staff_name VARCHAR(50),
                    shift_code VARCHAR(20),
                    plan_date DATE,
                    material_code VARCHAR(50) NOT NULL,
                    material_name VARCHAR(100),
                    color_code VARCHAR(20),
                    color_name VARCHAR(50),
                    thickness DECIMAL(10,3),
                    plan_length DECIMAL(12,2),
                    plan_sqm DECIMAL(12,2),
                    actual_length DECIMAL(12,2),
                    actual_sqm DECIMAL(12,2),
                    jumbo_width INT,
                    coating_speed DECIMAL(10,2),
                    oven_temp DECIMAL(6,2),
                    plan_start_time DATETIME,
                    plan_end_time DATETIME,
                    plan_duration INT,
                    actual_start_time DATETIME,
                    actual_end_time DATETIME,
                    actual_duration INT,
                    status VARCHAR(20) DEFAULT 'pending',
                    output_batch_no VARCHAR(50),
                    warehouse_in TINYINT DEFAULT 0,
                    warehouse_in_time DATETIME,
                    remark VARCHAR(500),
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    create_by VARCHAR(50),
                    update_by VARCHAR(50),
                    UNIQUE KEY uk_task_no (task_no),
                    INDEX idx_schedule_id (schedule_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='涂布计划表'`
            },
            {
                name: 'schedule_rewinding',
                sql: `CREATE TABLE IF NOT EXISTS schedule_rewinding (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    schedule_id BIGINT NOT NULL,
                    task_no VARCHAR(50) NOT NULL,
                    coating_task_id BIGINT,
                    equipment_id BIGINT,
                    equipment_code VARCHAR(30),
                    staff_id BIGINT,
                    staff_name VARCHAR(50),
                    shift_code VARCHAR(20),
                    plan_date DATE,
                    source_batch_no VARCHAR(50),
                    source_stock_id BIGINT,
                    jumbo_width INT,
                    jumbo_length DECIMAL(12,2),
                    material_code VARCHAR(50) NOT NULL,
                    material_name VARCHAR(100),
                    thickness DECIMAL(10,3),
                    slit_length INT,
                    plan_rolls INT,
                    actual_rolls INT,
                    rewinding_speed DECIMAL(10,2),
                    tension DECIMAL(10,2),
                    plan_start_time DATETIME,
                    plan_end_time DATETIME,
                    plan_duration INT,
                    actual_start_time DATETIME,
                    actual_end_time DATETIME,
                    actual_duration INT,
                    status VARCHAR(20) DEFAULT 'pending',
                    output_batch_no VARCHAR(50),
                    warehouse_in TINYINT DEFAULT 0,
                    warehouse_in_time DATETIME,
                    remark VARCHAR(500),
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    create_by VARCHAR(50),
                    update_by VARCHAR(50),
                    UNIQUE KEY uk_task_no (task_no),
                    INDEX idx_schedule_id (schedule_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='复卷计划表'`
            },
            {
                name: 'schedule_slitting',
                sql: `CREATE TABLE IF NOT EXISTS schedule_slitting (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    schedule_id BIGINT NOT NULL,
                    task_no VARCHAR(50) NOT NULL,
                    rewinding_task_id BIGINT,
                    equipment_id BIGINT,
                    equipment_code VARCHAR(30),
                    staff_id BIGINT,
                    staff_name VARCHAR(50),
                    shift_code VARCHAR(20),
                    plan_date DATE,
                    source_batch_no VARCHAR(50),
                    source_stock_id BIGINT,
                    slit_width INT,
                    slit_length INT,
                    material_code VARCHAR(50) NOT NULL,
                    material_name VARCHAR(100),
                    thickness DECIMAL(10,3),
                    target_width INT,
                    cuts_per_slit INT,
                    plan_rolls INT,
                    actual_rolls INT,
                    edge_loss INT DEFAULT 10,
                    slitting_speed DECIMAL(10,2),
                    plan_start_time DATETIME,
                    plan_end_time DATETIME,
                    plan_duration INT,
                    actual_start_time DATETIME,
                    actual_end_time DATETIME,
                    actual_duration INT,
                    status VARCHAR(20) DEFAULT 'pending',
                    output_batch_no VARCHAR(50),
                    warehouse_in TINYINT DEFAULT 0,
                    warehouse_in_time DATETIME,
                    remark VARCHAR(500),
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    create_by VARCHAR(50),
                    update_by VARCHAR(50),
                    UNIQUE KEY uk_task_no (task_no),
                    INDEX idx_schedule_id (schedule_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分切计划表'`
            },
            {
                name: 'schedule_stripping',
                sql: `CREATE TABLE IF NOT EXISTS schedule_stripping (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    schedule_id BIGINT NOT NULL,
                    task_no VARCHAR(50) NOT NULL,
                    slitting_task_id BIGINT,
                    equipment_id BIGINT,
                    equipment_code VARCHAR(30),
                    staff_id BIGINT,
                    staff_name VARCHAR(50),
                    shift_code VARCHAR(20),
                    plan_date DATE,
                    source_batch_no VARCHAR(50),
                    source_stock_id BIGINT,
                    source_width INT,
                    source_length DECIMAL(12,2),
                    material_code VARCHAR(50) NOT NULL,
                    material_name VARCHAR(100),
                    thickness DECIMAL(10,3),
                    target_width INT,
                    target_length INT,
                    cuts_width INT,
                    cuts_length INT,
                    plan_rolls INT,
                    actual_rolls INT,
                    stripping_speed DECIMAL(10,2),
                    plan_start_time DATETIME,
                    plan_end_time DATETIME,
                    plan_duration INT,
                    actual_start_time DATETIME,
                    actual_end_time DATETIME,
                    actual_duration INT,
                    status VARCHAR(20) DEFAULT 'pending',
                    output_batch_no VARCHAR(50),
                    warehouse_in TINYINT DEFAULT 0,
                    warehouse_in_time DATETIME,
                    remark VARCHAR(500),
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    create_by VARCHAR(50),
                    update_by VARCHAR(50),
                    UNIQUE KEY uk_task_no (task_no),
                    INDEX idx_schedule_id (schedule_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分条计划表'`
            },
            {
                name: 'production_report',
                sql: `CREATE TABLE IF NOT EXISTS production_report (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    report_no VARCHAR(50) NOT NULL,
                    task_type VARCHAR(20) NOT NULL,
                    task_id BIGINT NOT NULL,
                    task_no VARCHAR(50),
                    equipment_id BIGINT,
                    staff_id BIGINT NOT NULL,
                    staff_name VARCHAR(50),
                    shift_code VARCHAR(20),
                    report_date DATE,
                    output_qty INT,
                    output_length DECIMAL(12,2),
                    output_sqm DECIMAL(12,2),
                    defect_qty INT DEFAULT 0,
                    defect_reason VARCHAR(200),
                    start_time DATETIME,
                    end_time DATETIME,
                    work_minutes INT,
                    pause_minutes INT DEFAULT 0,
                    output_batch_no VARCHAR(50),
                    report_source VARCHAR(20) DEFAULT 'workshop',
                    remark VARCHAR(500),
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    UNIQUE KEY uk_report_no (report_no),
                    INDEX idx_task_id (task_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='生产报工表'`
            },
            {
                name: 'quality_inspection',
                sql: `CREATE TABLE IF NOT EXISTS quality_inspection (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    inspection_no VARCHAR(50) NOT NULL,
                    task_type VARCHAR(20),
                    task_id BIGINT,
                    task_no VARCHAR(50),
                    batch_no VARCHAR(50),
                    material_code VARCHAR(50),
                    material_name VARCHAR(100),
                    inspection_type VARCHAR(30) NOT NULL,
                    inspector_id BIGINT,
                    inspector_name VARCHAR(50),
                    inspection_time DATETIME,
                    sample_qty INT,
                    pass_qty INT,
                    fail_qty INT,
                    result VARCHAR(20) NOT NULL,
                    defect_type VARCHAR(100),
                    defect_desc TEXT,
                    disposition VARCHAR(50),
                    disposition_remark VARCHAR(500),
                    remark VARCHAR(500),
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    create_by VARCHAR(50),
                    UNIQUE KEY uk_inspection_no (inspection_no),
                    INDEX idx_task_id (task_id),
                    INDEX idx_batch_no (batch_no)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='质检记录表'`
            },
            {
                name: 'urgent_insert_order',
                sql: `CREATE TABLE IF NOT EXISTS urgent_insert_order (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    insert_no VARCHAR(50) NOT NULL,
                    order_id BIGINT NOT NULL,
                    order_no VARCHAR(50) NOT NULL,
                    order_item_id BIGINT,
                    customer VARCHAR(200),
                    customer_level VARCHAR(20),
                    insert_reason VARCHAR(500) NOT NULL,
                    priority_before INT,
                    priority_after INT,
                    required_date DATE,
                    apply_by VARCHAR(50),
                    apply_time DATETIME,
                    approve_by VARCHAR(50),
                    approve_time DATETIME,
                    approve_result VARCHAR(20),
                    approve_remark VARCHAR(500),
                    affected_schedules TEXT,
                    affected_orders TEXT,
                    delay_days INT DEFAULT 0,
                    status VARCHAR(20) DEFAULT 'pending',
                    remark VARCHAR(500),
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    UNIQUE KEY uk_insert_no (insert_no),
                    INDEX idx_order_id (order_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='紧急插单记录表'`
            },
            {
                name: 'schedule_approval_log',
                sql: `CREATE TABLE IF NOT EXISTS schedule_approval_log (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    schedule_id BIGINT NOT NULL,
                    schedule_no VARCHAR(50),
                    action VARCHAR(30) NOT NULL,
                    from_status VARCHAR(20),
                    to_status VARCHAR(20),
                    operator_id BIGINT,
                    operator_name VARCHAR(50),
                    opinion VARCHAR(500),
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                    INDEX idx_schedule_id (schedule_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='排程审批记录表'`
            }
        ];

        console.log('📦 创建/检查表...\n');
        for (const table of tablesToCreate) {
            try {
                await connection.query(table.sql);
                // 检查表是否已存在
                const [check] = await connection.query(
                    `SELECT COUNT(*) as cnt FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='erp' AND TABLE_NAME=?`,
                    [table.name]
                );
                console.log(`✅ ${table.name} - OK`);
            } catch (e) {
                console.log(`❌ ${table.name} - ${e.message}`);
            }
        }

        // 5. 最终验证
        console.log('\n📋 最终表结构验证...\n');
        const [finalTables] = await connection.query(`
            SELECT TABLE_NAME, TABLE_COMMENT 
            FROM INFORMATION_SCHEMA.TABLES 
            WHERE TABLE_SCHEMA = 'erp' 
            AND TABLE_NAME IN (
                'production_schedule', 'schedule_order_item', 'schedule_printing',
                'schedule_coating', 'schedule_rewinding', 'schedule_slitting',
                'schedule_stripping', 'production_report', 'quality_inspection',
                'urgent_insert_order', 'schedule_approval_log'
            )
            ORDER BY TABLE_NAME
        `);

        console.log('✅ 已创建的生产排程相关表:');
        finalTables.forEach(t => console.log(`   - ${t.TABLE_NAME}: ${t.TABLE_COMMENT || ''}`));

        console.log('\n========================================');
        console.log('🎉 数据库结构更新完成！');
        console.log('========================================');

    } catch (error) {
        console.error('❌ 错误:', error.message);
    } finally {
        if (connection) {
            await connection.end();
            console.log('\n🔌 数据库连接已关闭');
        }
    }
}

main();
