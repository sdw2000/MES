const mysql = require('mysql2/promise');
const fs = require('fs');
const path = require('path');

(async () => {
  const sqlPath = path.join(__dirname, 'create-equipment-schedule-config.sql');
  const sql = fs.readFileSync(sqlPath, 'utf8');

  const conn = await mysql.createConnection({
    host: 'ssdw8127.mysql.rds.aliyuncs.com',
    user: 'david',
    password: 'dadazhengzheng@feng',
    database: 'erp',
    multipleStatements: true,
    connectTimeout: 30000
  });

  try {
    await conn.query(sql);
    const [rows] = await conn.query("SHOW TABLES LIKE 'equipment_schedule_config'");
    console.log('table_exists=', rows.length > 0);
  } finally {
    await conn.end();
  }
})().catch((err) => {
  console.error(err && err.message ? err.message : err);
  process.exit(1);
});
