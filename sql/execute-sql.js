// 用于执行 SQL 脚本的 Node.js 工具

const mysql = require('mysql2/promise');
const fs = require('fs');
const path = require('path');

const config = {
    host: 'ssdw8127.mysql.rds.aliyuncs.com',
    user: 'david',
    password: 'dadazhengzheng@feng',
    database: 'erp',
    multipleStatements: true,
    connectTimeout: 30000
};

async function executeSql() {
    let connection;
    try {
        console.log('🔗 正在连接数据库...');
        connection = await mysql.createConnection(config);
        console.log('✅ 数据库连接成功！');

        // 读取 SQL 文件
        const sqlFile = path.join(__dirname, 'production_schedule_safe.sql');
        console.log(`📄 读取 SQL 文件: ${sqlFile}`);
        
        let sqlContent = fs.readFileSync(sqlFile, 'utf8');
        
        // 移除 DELIMITER 语句和存储过程定义（因为 mysql2 不支持 DELIMITER）
        // 我们需要分别处理
        
        // 先执行简单的测试查询
        console.log('\n📊 测试连接，查询当前数据库表...');
        const [tables] = await connection.query(`
            SELECT TABLE_NAME, TABLE_COMMENT 
            FROM INFORMATION_SCHEMA.TABLES 
            WHERE TABLE_SCHEMA = DATABASE() 
            ORDER BY TABLE_NAME
        `);
        
        console.log(`\n当前数据库有 ${tables.length} 个表：`);
        tables.forEach(t => {
            console.log(`  - ${t.TABLE_NAME}: ${t.TABLE_COMMENT || '无注释'}`);
        });

        // 检查 production_schedule 表是否存在
        const scheduleExists = tables.some(t => t.TABLE_NAME === 'production_schedule');
        console.log(`\n🔍 production_schedule 表${scheduleExists ? '已存在' : '不存在'}`);

        console.log('\n✅ 数据库连接测试完成！');
        console.log('\n💡 提示：由于 SQL 脚本包含存储过程（DELIMITER），');
        console.log('   建议使用 MySQL Workbench 或 Navicat 执行完整脚本。');
        console.log(`   脚本路径: ${sqlFile}`);
        
    } catch (error) {
        console.error('❌ 错误:', error.message);
        if (error.code === 'ECONNREFUSED') {
            console.log('   数据库连接被拒绝，请检查网络或防火墙设置');
        } else if (error.code === 'ER_ACCESS_DENIED_ERROR') {
            console.log('   用户名或密码错误');
        }
    } finally {
        if (connection) {
            await connection.end();
            console.log('\n🔌 数据库连接已关闭');
        }
    }
}

executeSql();
