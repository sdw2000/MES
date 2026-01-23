// 检查并创建缺失的表
const mysql = require('mysql2/promise');

async function main() {
    console.log('🔄 连接数据库...');
    const conn = await mysql.createConnection({
        host: 'ssdw8127.mysql.rds.aliyuncs.com',
        user: 'david',
        password: 'dadazhengzheng@feng',
        database: 'erp',
        connectTimeout: 30000
    });
    console.log('✅ 连接成功!\n');

    // 检查所有需要的表
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
        'urgent_insert_order',
        'schedule_approval_log'
    ];

    console.log('📋 检查表是否存在...\n');
    const [allTables] = await conn.query("SHOW TABLES");
    const tableNames = allTables.map(t => Object.values(t)[0]);
    
    const missing = [];
    for (const table of requiredTables) {
        if (tableNames.includes(table)) {
            console.log(`  ✅ ${table} - 已存在`);
        } else {
            console.log(`  ❌ ${table} - 缺失`);
            missing.push(table);
        }
    }

    if (missing.length === 0) {
        console.log('\n🎉 所有表都已存在！');
    } else {
        console.log(`\n⚠️ 缺失 ${missing.length} 个表，正在创建...\n`);
        
        // 创建缺失的表
        const createSqls = {
            'schedule_printing': `CREATE TABLE schedule_printing (
                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                schedule_id BIGINT NOT NULL,
                task_no VARCHAR(50) NOT NULL,
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
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='印刷计划表'`,
            
            'production_report': `CREATE TABLE production_report (
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
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='生产报工表'`,
            
            'quality_inspection': `CREATE TABLE quality_inspection (
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
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='质检记录表'`,
            
            'urgent_insert_order': `CREATE TABLE urgent_insert_order (
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
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='紧急插单记录表'`,
            
            'schedule_approval_log': `CREATE TABLE schedule_approval_log (
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
        };
        
        for (const table of missing) {
            if (createSqls[table]) {
                try {
                    await conn.query(createSqls[table]);
                    console.log(`  ✅ 创建 ${table} 成功`);
                } catch (e) {
                    console.log(`  ❌ 创建 ${table} 失败: ${e.message}`);
                }
            } else {
                console.log(`  ⚠️ ${table} 没有对应的创建语句`);
            }
        }
    }

    // 检查 sales_order_items 字段
    console.log('\n📋 检查 sales_order_items 字段...');
    const [cols] = await conn.query(`
        SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS 
        WHERE TABLE_SCHEMA='erp' AND TABLE_NAME='sales_order_items'
    `);
    const colNames = cols.map(c => c.COLUMN_NAME);
    
    const needCols = ['scheduled_qty', 'produced_qty', 'delivered_qty', 'remaining_qty', 'production_status'];
    for (const col of needCols) {
        if (colNames.includes(col)) {
            console.log(`  ✅ ${col} - 已存在`);
        } else {
            console.log(`  ❌ ${col} - 缺失，添加中...`);
            try {
                if (col === 'production_status') {
                    await conn.query(`ALTER TABLE sales_order_items ADD COLUMN ${col} VARCHAR(30) DEFAULT 'pending'`);
                } else {
                    await conn.query(`ALTER TABLE sales_order_items ADD COLUMN ${col} INT DEFAULT 0`);
                }
                console.log(`     ✅ 添加成功`);
            } catch (e) {
                console.log(`     ❌ 添加失败: ${e.message}`);
            }
        }
    }

    await conn.end();
    console.log('\n========================================');
    console.log('🎉 数据库检查/更新完成！');
    console.log('========================================');
}

main().catch(e => console.error('错误:', e.message));
