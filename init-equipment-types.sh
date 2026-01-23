#!/bin/bash
# 直接在数据库中初始化必要的数据

mysql -h ssdw8127.mysql.rds.aliyuncs.com \
  -u david \
  -pdadazhengzheng@feng \
  -D erp \
  -e "INSERT IGNORE INTO equipment_type (type_code, type_name, process_order, description, status) VALUES 
      ('PRINTING', '印刷机', 1, '印刷工序（可选）', 1),
      ('COATING', '涂布机', 2, '涂布工序', 1),
      ('REWINDING', '复卷机', 3, '复卷工序（切长度）', 1),
      ('SLITTING', '分切机', 4, '分切工序（切宽度）', 1),
      ('STRIPPING', '分条机', 5, '分条机（母卷直接切成小卷）', 1);"

echo "Equipment types initialized successfully!"
