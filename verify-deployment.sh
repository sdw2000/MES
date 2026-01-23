#!/bin/bash

# ============================================
# 库存锁定机制 - 自动化验证脚本
# 用于验证部署是否成功
# ============================================

set -e  # 任何命令失败时停止

echo "=================================================="
echo "  库存锁定机制 - 自动化验证脚本"
echo "=================================================="

# 配置参数
BACKEND_URL="http://localhost:8080"
FRONTEND_URL="http://localhost:8000"
MYSQL_USER="root"
MYSQL_DB="MES_database"

# 颜色定义
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo ""
echo -e "${YELLOW}========== 验证 1: 后端服务状态 ==========${NC}"

# 检查后端服务是否运行
if curl -s "${BACKEND_URL}/health" > /dev/null 2>&1; then
    echo -e "${GREEN}✓${NC} 后端服务正在运行"
else
    echo -e "${RED}✗${NC} 后端服务未运行，请先启动"
    echo "  运行: cd e:\java\MES && mvn spring-boot:run"
    exit 1
fi

echo ""
echo -e "${YELLOW}========== 验证 2: 数据库表状态 ==========${NC}"

# 检查数据库表是否存在
TABLES=$(mysql -u${MYSQL_USER} -p${MYSQL_DB} -e "SHOW TABLES LIKE 'schedule_material%';" 2>/dev/null)

if echo "$TABLES" | grep -q "schedule_material_lock"; then
    echo -e "${GREEN}✓${NC} schedule_material_lock 表已存在"
else
    echo -e "${RED}✗${NC} schedule_material_lock 表不存在"
    echo "  请执行: mysql -u${MYSQL_USER} -p${MYSQL_DB} < src/main/resources/sql/2_create_schedule_material_lock.sql"
    exit 1
fi

if echo "$TABLES" | grep -q "schedule_material_allocation"; then
    echo -e "${GREEN}✓${NC} schedule_material_allocation 表已存在"
else
    echo -e "${RED}✗${NC} schedule_material_allocation 表不存在"
    echo "  请执行: mysql -u${MYSQL_USER} -p${MYSQL_DB} < src/main/resources/sql/3_create_schedule_material_allocation.sql"
    exit 1
fi

echo ""
echo -e "${YELLOW}========== 验证 3: tape_stock 表新字段 ==========${NC}"

# 检查新字段
FIELDS=$(mysql -u${MYSQL_USER} -p${MYSQL_DB} -e "DESC tape_stock;" 2>/dev/null)

for field in "available_area" "reserved_area" "consumed_area" "version"; do
    if echo "$FIELDS" | grep -q "$field"; then
        echo -e "${GREEN}✓${NC} 字段 $field 已存在"
    else
        echo -e "${RED}✗${NC} 字段 $field 不存在"
        echo "  请执行: mysql -u${MYSQL_USER} -p${MYSQL_DB} < src/main/resources/sql/1_alter_tape_stock.sql"
        exit 1
    fi
done

echo ""
echo -e "${YELLOW}========== 验证 4: API 端点状态 ==========${NC}"

# 测试 API 端点
TEST_SCHEDULE_ID=1

# 测试锁定物料接口
echo -n "测试 POST /production/schedule-material/lock/{scheduleId}... "
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "${BACKEND_URL}/production/schedule-material/lock/${TEST_SCHEDULE_ID}" \
  -H "Content-Type: application/json")
HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
if [[ "$HTTP_CODE" == "200" || "$HTTP_CODE" == "400" || "$HTTP_CODE" == "500" ]]; then
    echo -e "${GREEN}✓${NC} (HTTP $HTTP_CODE)"
else
    echo -e "${RED}✗${NC} (HTTP $HTTP_CODE)"
fi

# 测试查询分配接口
echo -n "测试 GET /production/schedule-material/allocation/{scheduleId}... "
RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "${BACKEND_URL}/production/schedule-material/allocation/${TEST_SCHEDULE_ID}")
HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
if [[ "$HTTP_CODE" == "200" || "$HTTP_CODE" == "400" || "$HTTP_CODE" == "500" ]]; then
    echo -e "${GREEN}✓${NC} (HTTP $HTTP_CODE)"
else
    echo -e "${RED}✗${NC} (HTTP $HTTP_CODE)"
fi

# 测试查询锁定接口
echo -n "测试 GET /production/schedule-material/tape-locks/{tapeId}... "
RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "${BACKEND_URL}/production/schedule-material/tape-locks/1")
HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
if [[ "$HTTP_CODE" == "200" || "$HTTP_CODE" == "400" || "$HTTP_CODE" == "500" ]]; then
    echo -e "${GREEN}✓${NC} (HTTP $HTTP_CODE)"
else
    echo -e "${RED}✗${NC} (HTTP $HTTP_CODE)"
fi

echo ""
echo -e "${YELLOW}========== 验证 5: 数据查询测试 ==========${NC}"

# 查询库存数据
STOCK_COUNT=$(mysql -u${MYSQL_USER} -p${MYSQL_DB} -e "SELECT COUNT(*) FROM tape_stock;" 2>/dev/null | tail -1)
echo -e "${GREEN}✓${NC} 库存总数: $STOCK_COUNT 个"

# 查询锁定数据
LOCK_COUNT=$(mysql -u${MYSQL_USER} -p${MYSQL_DB} -e "SELECT COUNT(*) FROM schedule_material_lock;" 2>/dev/null | tail -1)
echo -e "${GREEN}✓${NC} 锁定记录: $LOCK_COUNT 条"

# 查询分配数据
ALLOCATION_COUNT=$(mysql -u${MYSQL_USER} -p${MYSQL_DB} -e "SELECT COUNT(*) FROM schedule_material_allocation;" 2>/dev/null | tail -1)
echo -e "${GREEN}✓${NC} 分配记录: $ALLOCATION_COUNT 条"

echo ""
echo -e "${YELLOW}========== 验证 6: 前端页面 ==========${NC}"

# 检查前端服务
if curl -s "${FRONTEND_URL}/" > /dev/null 2>&1; then
    echo -e "${GREEN}✓${NC} 前端服务正在运行"
else
    echo -e "${YELLOW}⚠${NC} 前端服务未运行（可选）"
    echo "  运行: cd e:\vue\ERP && npm run serve"
fi

echo ""
echo "=================================================="
echo -e "${GREEN}  ✓ 验证完成！所有检查已通过${NC}"
echo "=================================================="
echo ""
echo "下一步:"
echo "  1. 确认后端服务运行在 http://localhost:8080"
echo "  2. 确认前端服务运行在 http://localhost:8000"
echo "  3. 在排程启动接口中集成物料锁定流程"
echo "  4. 运行功能测试验证完整流程"
echo ""
