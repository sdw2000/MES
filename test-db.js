// 简单测试数据库连接
const mysql = require('mysql2/promise');

async function test() {
    console.log('开始连接...');
    try {
        const conn = await mysql.createConnection({
            host: 'ssdw8127.mysql.rds.aliyuncs.com',
            user: 'david',
            password: 'dadazhengzheng@feng',
            database: 'erp',
            connectTimeout: 10000
        });
        console.log('连接成功!');
        
        const [rows] = await conn.query('SELECT 1 as test');
        console.log('查询成功:', rows);
        
        const [tables] = await conn.query("SHOW TABLES LIKE '%schedule%'");
        console.log('Schedule相关表:', tables);
        
        await conn.end();
        console.log('完成');
    } catch (e) {
        console.error('错误:', e.message);
    }
}

test();
