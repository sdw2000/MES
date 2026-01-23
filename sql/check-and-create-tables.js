// 检查并创建缺失的表

const mysql = require('mysql2/promise');

const config = {
    host: 'ssdw8127.mysql.rds.aliyuncs.com',
    user: 'david',
    password: 'dadazhengzheng@feng',
    database: 'erp',
    multipleStatements: true,
    connectTimeout: 30000
};

// 需要检查的表
const requiredTables = [
    'production_schedule',
    'schedule_order_item',
    'schedule_printing',
    'schedule_coating',
    'schedule_rewinding',
    'schedule_slitting',
    'schedule_stripping',
    'production_report',
    'quality_inspection',
    'order_delivery_record',
    'production_exception',
    'urgent_insert_order',
    'slitting_combination',
    'schedule_approval_log'
];

// 创建表的 SQL（不包含 DELIMITER 的版本）
const createTableSQLs = {
    schedule_printing: `
        CREATE TABLE IF NOT EXISTS schedule_printing (
            id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
            schedule_id BIGINT NOT NULL COMMENT '排程ID',
            task_no VARCHAR(50) NOT NULL COMMENT '任务单号（PR-YYYYMMDD-XXX）',
            equipment_id BIGINT COMMENT '设备ID',
            equipment_code VARCHAR(30) COMMENT '设备编号',
            staff_id BIGINT COMMENT '操作人员ID',
            staff_name VARCHAR(50) COMMENT '操作人员',
            shift_code VARCHAR(20) COMMENT '班次',
            plan_date DATE COMMENT '计划日期',
            material_code VARCHAR(50) NOT NULL COMMENT '产品料号',
            material_name VARCHAR(100) COMMENT '产品名称',
            color_code VARCHAR(20) COMMENT '颜色代码',
            color_name VARCHAR(50) COMMENT '颜色名称',
            print_pattern VARCHAR(100) COMMENT '印刷图案',
            base_width INT COMMENT '基材宽度(mm)',
            plan_length DECIMAL(12,2) COMMENT '计划印刷长度(米)',
            plan_sqm DECIMAL(12,2) COMMENT '计划面积(平方米)',
            actual_length DECIMAL(12,2) COMMENT '实际印刷长度(米)',
            actual_sqm DECIMAL(12,2) COMMENT '实际面积(平方米)',
            printing_speed DECIMAL(10,2) COMMENT '印刷速度(米/分钟)',
            ink_type VARCHAR(50) COMMENT '油墨类型',
            drying_temp DECIMAL(6,2) COMMENT '干燥温度(℃)',
            plan_start_time DATETIME COMMENT '计划开始时间',
            plan_end_time DATETIME COMMENT '计划结束时间',
            plan_duration INT COMMENT '计划时长(分钟)',
            actual_start_time DATETIME COMMENT '实际开始时间',
            actual_end_time DATETIME COMMENT '实际结束时间',
            actual_duration INT COMMENT '实际时长(分钟)',
            status VARCHAR(20) DEFAULT 'pending' COMMENT '状态',
            output_batch_no VARCHAR(50) COMMENT '产出批次号',
            remark VARCHAR(500) COMMENT '备注',
            create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
            update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
            create_by VARCHAR(50),
            update_by VARCHAR(50),
            UNIQUE KEY uk_task_no (task_no),
            INDEX idx_schedule_id (schedule_id),
            INDEX idx_plan_date (plan_date),
            INDEX idx_status (status)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='印刷计划表'
    `,
    
    quality_inspection: `
        CREATE TABLE IF NOT EXISTS quality_inspection (
            id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
            inspection_no VARCHAR(50) NOT NULL COMMENT '检验单号',
            task_type VARCHAR(20) COMMENT '任务类型',
            task_id BIGINT COMMENT '任务ID',
            task_no VARCHAR(50) COMMENT '任务单号',
            batch_no VARCHAR(50) COMMENT '批次号',
            material_code VARCHAR(50) COMMENT '产品料号',
            material_name VARCHAR(100) COMMENT '产品名称',
            inspection_type VARCHAR(30) NOT NULL COMMENT '检验类型',
            inspector_id BIGINT COMMENT '检验员ID',
            inspector_name VARCHAR(50) COMMENT '检验员',
            inspection_time DATETIME COMMENT '检验时间',
            sample_qty INT COMMENT '抽检数量',
            pass_qty INT COMMENT '合格数量',
            fail_qty INT COMMENT '不合格数量',
            result VARCHAR(20) NOT NULL COMMENT '检验结果',
            defect_type VARCHAR(100) COMMENT '缺陷类型',
            defect_desc TEXT COMMENT '缺陷描述',
            disposition VARCHAR(50) COMMENT '处置方式',
            disposition_remark VARCHAR(500) COMMENT '处置说明',
            remark VARCHAR(500) COMMENT '备注',
            create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
            update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
            create_by VARCHAR(50),
            UNIQUE KEY uk_inspection_no (inspection_no),
            INDEX idx_task_id (task_id),
            INDEX idx_batch_no (batch_no),
            INDEX idx_result (result)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='质检记录表'
    `,
    
    urgent_insert_order: `
        CREATE TABLE IF NOT EXISTS urgent_insert_order (
            id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
            insert_no VARCHAR(50) NOT NULL COMMENT '插单单号',
            order_id BIGINT NOT NULL COMMENT '订单ID',
            order_no VARCHAR(50) NOT NULL COMMENT '订单号',
            order_item_id BIGINT COMMENT '订单明细ID',
            customer VARCHAR(200) COMMENT '客户',
            customer_level VARCHAR(20) COMMENT '客户等级',
            insert_reason VARCHAR(500) NOT NULL COMMENT '插单原因',
            priority_before INT COMMENT '原优先级',
            priority_after INT COMMENT '新优先级',
            required_date DATE COMMENT '要求交期',
            apply_by VARCHAR(50) COMMENT '申请人',
            apply_time DATETIME COMMENT '申请时间',
            approve_by VARCHAR(50) COMMENT '审批人',
            approve_time DATETIME COMMENT '审批时间',
            approve_result VARCHAR(20) COMMENT '审批结果',
            approve_remark VARCHAR(500) COMMENT '审批意见',
            affected_schedules TEXT COMMENT '受影响排程ID',
            affected_orders TEXT COMMENT '受影响订单号',
            delay_days INT DEFAULT 0 COMMENT '预计延期天数',
            status VARCHAR(20) DEFAULT 'pending' COMMENT '状态',
            remark VARCHAR(500) COMMENT '备注',
            create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
            update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
            UNIQUE KEY uk_insert_no (insert_no),
            INDEX idx_order_id (order_id),
            INDEX idx_status (status)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='紧急插单记录表'
    `,
    
    schedule_approval_log: `
        CREATE TABLE IF NOT EXISTS schedule_approval_log (
            id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
            schedule_id BIGINT NOT NULL COMMENT '排程ID',
            schedule_no VARCHAR(50) COMMENT '排程单号',
            action VARCHAR(30) NOT NULL COMMENT '操作',
            from_status VARCHAR(20) COMMENT '原状态',
            to_status VARCHAR(20) COMMENT '新状态',
            operator_id BIGINT COMMENT '操作人ID',
            operator_name VARCHAR(50) COMMENT '操作人',
            opinion VARCHAR(500) COMMENT '意见',
            create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
            INDEX idx_schedule_id (schedule_id),
            INDEX idx_create_time (create_time)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='排程审批记录表'
    `
};

// 需要添加的列
const columnsToAdd = [
    { table: 'sales_order_items', column: 'scheduled_qty', definition: 'INT DEFAULT 0 COMMENT \'已排程数量(卷)\'' },
    { table: 'sales_order_items', column: 'produced_qty', definition: 'INT DEFAULT 0 COMMENT \'已生产数量(卷)\'' },
    { table: 'sales_order_items', column: 'delivered_qty', definition: 'INT DEFAULT 0 COMMENT \'已出货数量(卷)\'' },
    { table: 'sales_order_items', column: 'remaining_qty', definition: 'INT DEFAULT 0 COMMENT \'未交货数量(卷)\'' },
    { table: 'sales_order_items', column: 'production_status', definition: 'VARCHAR(30) DEFAULT \'pending\' COMMENT \'生产状态\'' },
    { table: 'tape_stock', column: 'stock_type', definition: 'VARCHAR(20) DEFAULT \'finished\' COMMENT \'库存类型\'' },
    { table: 'tape_stock', column: 'parent_batch_no', definition: 'VARCHAR(50) COMMENT \'来源批次号\'' },
    { table: 'tape_stock', column: 'source_schedule_id', definition: 'BIGINT COMMENT \'来源排程ID\'' },
    { table: 'production_schedule', column: 'approval_by', definition: 'VARCHAR(50) COMMENT \'审批人\'' },
    { table: 'production_schedule', column: 'approval_time', definition: 'DATETIME COMMENT \'审批时间\'' },
    { table: 'production_schedule', column: 'approval_remark', definition: 'VARCHAR(500) COMMENT \'审批备注\'' },
    { table: 'schedule_coating', column: 'printing_task_id', definition: 'BIGINT COMMENT \'印刷任务ID\'' },
    { table: 'schedule_coating', column: 'warehouse_in', definition: 'TINYINT DEFAULT 0 COMMENT \'是否已入库\'' },
    { table: 'schedule_coating', column: 'warehouse_in_time', definition: 'DATETIME COMMENT \'入库时间\'' },
    { table: 'schedule_rewinding', column: 'coating_task_id', definition: 'BIGINT COMMENT \'涂布任务ID\'' },
    { table: 'schedule_rewinding', column: 'warehouse_in', definition: 'TINYINT DEFAULT 0 COMMENT \'是否已入库\'' },
    { table: 'schedule_rewinding', column: 'warehouse_in_time', definition: 'DATETIME COMMENT \'入库时间\'' },
    { table: 'schedule_slitting', column: 'rewinding_task_id', definition: 'BIGINT COMMENT \'复卷任务ID\'' },
    { table: 'schedule_slitting', column: 'warehouse_in', definition: 'TINYINT DEFAULT 0 COMMENT \'是否已入库\'' },
    { table: 'schedule_slitting', column: 'warehouse_in_time', definition: 'DATETIME COMMENT \'入库时间\'' },
    { table: 'schedule_stripping', column: 'slitting_task_id', definition: 'BIGINT COMMENT \'分切任务ID\'' },
    { table: 'schedule_stripping', column: 'warehouse_in', definition: 'TINYINT DEFAULT 0 COMMENT \'是否已入库\'' },
    { table: 'schedule_stripping', column: 'warehouse_in_time', definition: 'DATETIME COMMENT \'入库时间\'' },
    { table: 'production_report', column: 'report_source', definition: 'VARCHAR(20) DEFAULT \'workshop\' COMMENT \'反馈来源\'' }
];

async function main() {
    let connection;
    try {
        console.log('🔗 正在连接数据库...');
        connection = await mysql.createConnection(config);
        console.log('✅ 数据库连接成功！\n');

        // 1. 检查现有表
        console.log('📊 检查现有表...');
        const [tables] = await connection.query(`
            SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES 
            WHERE TABLE_SCHEMA = DATABASE()
        `);
        const existingTables = new Set(tables.map(t => t.TABLE_NAME));
        
        // 检查缺失的表
        const missingTables = requiredTables.filter(t => !existingTables.has(t));
        console.log(`\n现有相关表: ${requiredTables.filter(t => existingTables.has(t)).join(', ')}`);
        console.log(`缺失的表: ${missingTables.length > 0 ? missingTables.join(', ') : '无'}`);

        // 2. 创建缺失的表
        for (const tableName of missingTables) {
            if (createTableSQLs[tableName]) {
                console.log(`\n📝 创建表: ${tableName}`);
                try {
                    await connection.query(createTableSQLs[tableName]);
                    console.log(`   ✅ ${tableName} 创建成功`);
                } catch (err) {
                    console.log(`   ❌ ${tableName} 创建失败: ${err.message}`);
                }
            }
        }

        // 3. 检查并添加缺失的列
        console.log('\n📊 检查并添加缺失的列...');
        for (const col of columnsToAdd) {
            try {
                const [columns] = await connection.query(`
                    SELECT COUNT(*) as cnt FROM INFORMATION_SCHEMA.COLUMNS 
                    WHERE TABLE_SCHEMA = DATABASE() 
                    AND TABLE_NAME = ? AND COLUMN_NAME = ?
                `, [col.table, col.column]);
                
                if (columns[0].cnt === 0) {
                    console.log(`   添加 ${col.table}.${col.column}...`);
                    await connection.query(`ALTER TABLE ${col.table} ADD COLUMN ${col.column} ${col.definition}`);
                    console.log(`   ✅ 添加成功`);
                }
            } catch (err) {
                // 忽略错误（可能表不存在或列已存在）
            }
        }

        // 4. 最终检查
        console.log('\n\n========================================');
        console.log('📊 最终表结构检查：');
        console.log('========================================\n');
        
        const [finalTables] = await connection.query(`
            SELECT TABLE_NAME, TABLE_COMMENT 
            FROM INFORMATION_SCHEMA.TABLES 
            WHERE TABLE_SCHEMA = DATABASE() 
            AND TABLE_NAME IN (?)
            ORDER BY TABLE_NAME
        `, [requiredTables]);
        
        console.log('✅ 已创建的排程相关表：');
        finalTables.forEach(t => {
            console.log(`   - ${t.TABLE_NAME}: ${t.TABLE_COMMENT || ''}`);
        });

        const stillMissing = requiredTables.filter(t => !finalTables.some(ft => ft.TABLE_NAME === t));
        if (stillMissing.length > 0) {
            console.log(`\n⚠️ 仍然缺失的表: ${stillMissing.join(', ')}`);
        } else {
            console.log('\n🎉 所有排程相关表已就绪！');
        }

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
