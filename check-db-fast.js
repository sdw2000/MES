// 快速数据库检查脚本 - 带详细进度输出
const mysql = require('mysql2/promise');

// 计时函数
function timer() {
    const start = Date.now();
    return () => ((Date.now() - start) / 1000).toFixed(2) + 's';
}

async function main() {
    const totalTimer = timer();
    
    console.log('========================================');
    console.log('🔧 数据库检查工具 (快速版)');
    console.log('========================================\n');

    let conn;
    
    try {
        // 步骤1: 连接数据库
        console.log('[1/5] 🔄 连接数据库...');
        const connTimer = timer();
        
        conn = await mysql.createConnection({
            host: 'ssdw8127.mysql.rds.aliyuncs.com',
            user: 'david',
            password: 'dadazhengzheng@feng',
            database: 'erp',
            connectTimeout: 10000,  // 10秒超时
            timeout: 30000
        });
        
        console.log(`[1/5] ✅ 连接成功! (${connTimer()})\n`);

        // 步骤2: 检查表是否存在
        console.log('[2/5] 📋 获取现有表列表...');
        const tableTimer = timer();
        
        const [allTables] = await conn.query("SHOW TABLES");
        const tableNames = allTables.map(t => Object.values(t)[0]);
        
        console.log(`[2/5] ✅ 共 ${tableNames.length} 个表 (${tableTimer()})\n`);

        // 步骤3: 检查必需的表
        console.log('[3/5] 🔍 检查生产排程相关表...');
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

        const existing = [];
        const missing = [];
        
        for (const table of requiredTables) {
            if (tableNames.includes(table)) {
                existing.push(table);
                console.log(`      ✅ ${table}`);
            } else {
                missing.push(table);
                console.log(`      ❌ ${table} (缺失)`);
            }
        }
        
        console.log(`\n      统计: ${existing.length} 个已存在, ${missing.length} 个缺失\n`);

        // 步骤4: 创建缺失的表
        if (missing.length > 0) {
            console.log('[4/5] 🔨 创建缺失的表...');
            
            for (const table of missing) {
                const createTimer = timer();
                console.log(`      创建 ${table}...`);
                
                try {
                    const sql = getCreateTableSQL(table);
                    if (sql) {
                        await conn.query(sql);
                        console.log(`      ✅ ${table} 创建成功 (${createTimer()})`);
                    } else {
                        console.log(`      ⚠️ ${table} 无创建语句`);
                    }
                } catch (e) {
                    console.log(`      ❌ ${table} 失败: ${e.message}`);
                }
            }
            console.log('');
        } else {
            console.log('[4/5] ⏭️ 无需创建表\n');
        }

        // 步骤5: 检查字段
        console.log('[5/5] 🔍 检查 sales_order_items 字段...');
        const colTimer = timer();
        
        const [cols] = await conn.query(`
            SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS 
            WHERE TABLE_SCHEMA='erp' AND TABLE_NAME='sales_order_items'
        `);
        const colNames = cols.map(c => c.COLUMN_NAME);
        
        const needCols = [
            { name: 'scheduled_qty', type: 'INT DEFAULT 0' },
            { name: 'produced_qty', type: 'INT DEFAULT 0' },
            { name: 'delivered_qty', type: 'INT DEFAULT 0' },
            { name: 'remaining_qty', type: 'INT DEFAULT 0' },
            { name: 'production_status', type: "VARCHAR(30) DEFAULT 'pending'" }
        ];
        
        let addedCols = 0;
        for (const col of needCols) {
            if (colNames.includes(col.name)) {
                console.log(`      ✅ ${col.name}`);
            } else {
                console.log(`      ➕ ${col.name} (添加中...)`);
                try {
                    await conn.query(`ALTER TABLE sales_order_items ADD COLUMN ${col.name} ${col.type}`);
                    console.log(`         ✅ 添加成功`);
                    addedCols++;
                } catch (e) {
                    if (e.code === 'ER_DUP_FIELDNAME') {
                        console.log(`         ⏭️ 已存在`);
                    } else {
                        console.log(`         ❌ 失败: ${e.message}`);
                    }
                }
            }
        }
        
        console.log(`\n      字段检查完成 (${colTimer()}), 新增 ${addedCols} 个字段\n`);

        // 完成
        console.log('========================================');
        console.log(`🎉 全部完成! 总耗时: ${totalTimer()}`);
        console.log('========================================');

    } catch (error) {
        console.error('\n❌ 发生错误:', error.message);
        console.error('错误代码:', error.code);
        if (error.code === 'ETIMEDOUT' || error.code === 'ECONNREFUSED') {
            console.error('\n💡 提示: 网络连接问题，请检查:');
            console.error('   1. 网络是否正常');
            console.error('   2. 数据库服务器是否可访问');
            console.error('   3. 防火墙是否允许3306端口');
        }
    } finally {
        if (conn) {
            await conn.end();
            console.log('\n🔌 数据库连接已关闭');
        }
    }
}

// 获取创建表的SQL
function getCreateTableSQL(tableName) {
    const sqls = {
        'schedule_printing': `CREATE TABLE IF NOT EXISTS schedule_printing (
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
        
        'production_report': `CREATE TABLE IF NOT EXISTS production_report (
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
        
        'quality_inspection': `CREATE TABLE IF NOT EXISTS quality_inspection (
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
            result VARCHAR(20) NOT NULL DEFAULT 'pending',
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
        
        'urgent_insert_order': `CREATE TABLE IF NOT EXISTS urgent_insert_order (
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
        
        'schedule_approval_log': `CREATE TABLE IF NOT EXISTS schedule_approval_log (
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
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='排程审批记录表'`,

        'production_schedule': `CREATE TABLE IF NOT EXISTS production_schedule (
            id BIGINT PRIMARY KEY AUTO_INCREMENT,
            schedule_no VARCHAR(50) NOT NULL,
            schedule_date DATE NOT NULL,
            schedule_type VARCHAR(20) NOT NULL,
            total_orders INT DEFAULT 0,
            total_items INT DEFAULT 0,
            total_sqm DECIMAL(12,2) DEFAULT 0,
            status VARCHAR(20) DEFAULT 'draft',
            approval_by VARCHAR(50),
            approval_time DATETIME,
            approval_remark VARCHAR(500),
            confirmed_by VARCHAR(50),
            confirmed_time DATETIME,
            remark VARCHAR(500),
            create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
            update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
            create_by VARCHAR(50),
            update_by VARCHAR(50),
            is_deleted TINYINT DEFAULT 0,
            UNIQUE KEY uk_schedule_no (schedule_no),
            INDEX idx_schedule_date (schedule_date),
            INDEX idx_status (status)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='排程主表'`,

        'schedule_order_item': `CREATE TABLE IF NOT EXISTS schedule_order_item (
            id BIGINT PRIMARY KEY AUTO_INCREMENT,
            schedule_id BIGINT NOT NULL,
            order_id BIGINT NOT NULL,
            order_item_id BIGINT NOT NULL,
            order_no VARCHAR(50),
            customer VARCHAR(200),
            customer_level VARCHAR(20),
            material_code VARCHAR(50) NOT NULL,
            material_name VARCHAR(100),
            color_code VARCHAR(20),
            thickness DECIMAL(10,3),
            width DECIMAL(10,2),
            length DECIMAL(10,2),
            order_qty INT NOT NULL,
            schedule_qty INT NOT NULL,
            delivery_date DATE,
            priority INT DEFAULT 0,
            source_type VARCHAR(20),
            stock_id BIGINT,
            status VARCHAR(20) DEFAULT 'pending',
            create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
            INDEX idx_schedule_id (schedule_id),
            INDEX idx_order_item_id (order_item_id)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='排程订单关联表'`,

        'schedule_coating': `CREATE TABLE IF NOT EXISTS schedule_coating (
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
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='涂布计划表'`,

        'schedule_rewinding': `CREATE TABLE IF NOT EXISTS schedule_rewinding (
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
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='复卷计划表'`,

        'schedule_slitting': `CREATE TABLE IF NOT EXISTS schedule_slitting (
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
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分切计划表'`,

        'schedule_stripping': `CREATE TABLE IF NOT EXISTS schedule_stripping (
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
    };
    
    return sqls[tableName] || null;
}

// 执行
main();
