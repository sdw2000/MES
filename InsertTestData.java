import java.sql.*;

public class InsertTestData {
    public static void main(String[] args) {
        String url = "jdbc:mysql://ssdw8127.mysql.rds.aliyuncs.com/erp?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC";
        String user = "david";
        String password = "dadazhengzheng@feng";
        
        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            conn.setAutoCommit(false);
            
            // 插入销售订单
            String sql1 = "INSERT INTO sales_orders (order_no, customer, customer_order_no, total_amount, total_area, order_date, delivery_date, delivery_address, status, remark, created_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            PreparedStatement ps1 = conn.prepareStatement(sql1, Statement.RETURN_GENERATED_KEYS);
            
            // 订单1
            ps1.setString(1, "SO-20260110-001");
            ps1.setString(2, "深圳华为技术有限公司");
            ps1.setString(3, "HW-PO-2026-0088");
            ps1.setDouble(4, 25800.00);
            ps1.setDouble(5, 1200.50);
            ps1.setString(6, "2026-01-10");
            ps1.setString(7, "2026-01-20");
            ps1.setString(8, "广东省深圳市龙岗区坂田街道华为基地F区");
            ps1.setString(9, "processing");
            ps1.setString(10, "优先发货");
            ps1.setString(11, "admin");
            ps1.executeUpdate();
            ResultSet rs1 = ps1.getGeneratedKeys();
            rs1.next();
            long order1Id = rs1.getLong(1);
            
            // 订单2
            ps1.setString(1, "SO-20260111-002");
            ps1.setString(2, "东莞市比亚迪电子有限公司");
            ps1.setString(3, "BYD-2026-00156");
            ps1.setDouble(4, 18500.00);
            ps1.setDouble(5, 850.00);
            ps1.setString(6, "2026-01-11");
            ps1.setString(7, "2026-01-25");
            ps1.setString(8, "广东省东莞市大朗镇比亚迪工业园A栋");
            ps1.setString(9, "pending");
            ps1.setString(10, null);
            ps1.setString(11, "admin");
            ps1.executeUpdate();
            ResultSet rs2 = ps1.getGeneratedKeys();
            rs2.next();
            long order2Id = rs2.getLong(1);
            
            // 订单3
            ps1.setString(1, "SO-20260112-003");
            ps1.setString(2, "广州小鹏汽车科技有限公司");
            ps1.setString(3, "XP-2026-0099");
            ps1.setDouble(4, 32000.00);
            ps1.setDouble(5, 1500.00);
            ps1.setString(6, "2026-01-12");
            ps1.setString(7, "2026-01-28");
            ps1.setString(8, "广东省广州市番禺区化龙镇小鹏汽车生产基地");
            ps1.setString(9, "completed");
            ps1.setString(10, "已完成发货");
            ps1.setString(11, "admin");
            ps1.executeUpdate();
            ResultSet rs3 = ps1.getGeneratedKeys();
            rs3.next();
            long order3Id = rs3.getLong(1);
            
            System.out.println("订单ID: " + order1Id + ", " + order2Id + ", " + order3Id);
            
            // 插入订单明细
            String sql2 = "INSERT INTO sales_order_items (order_id, material_code, material_name, length, width, thickness, rolls, sqm, unit_price, amount, remark) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            PreparedStatement ps2 = conn.prepareStatement(sql2, Statement.RETURN_GENERATED_KEYS);
            
            // 订单1明细
            long[] item1Ids = new long[3];
            ps2.setLong(1, order1Id); ps2.setString(2, "FR-4725-A"); ps2.setString(3, "阻燃聚酯薄膜(黑色)");
            ps2.setInt(4, 100); ps2.setInt(5, 1000); ps2.setDouble(6, 0.075); ps2.setInt(7, 20);
            ps2.setDouble(8, 400.00); ps2.setDouble(9, 32.00); ps2.setDouble(10, 12800.00); ps2.setString(11, "常规品");
            ps2.executeUpdate(); ResultSet rsItem = ps2.getGeneratedKeys(); rsItem.next(); item1Ids[0] = rsItem.getLong(1);
            
            ps2.setLong(1, order1Id); ps2.setString(2, "FR-4725-B"); ps2.setString(3, "阻燃聚酯薄膜(白色)");
            ps2.setInt(4, 100); ps2.setInt(5, 1000); ps2.setDouble(6, 0.050); ps2.setInt(7, 15);
            ps2.setDouble(8, 300.00); ps2.setDouble(9, 28.00); ps2.setDouble(10, 8400.00); ps2.setString(11, "加急");
            ps2.executeUpdate(); rsItem = ps2.getGeneratedKeys(); rsItem.next(); item1Ids[1] = rsItem.getLong(1);
            
            ps2.setLong(1, order1Id); ps2.setString(2, "FR-4730-C"); ps2.setString(3, "阻燃PET保护膜");
            ps2.setInt(4, 50); ps2.setInt(5, 800); ps2.setDouble(6, 0.100); ps2.setInt(7, 10);
            ps2.setDouble(8, 160.00); ps2.setDouble(9, 35.00); ps2.setDouble(10, 5600.00); ps2.setString(11, null);
            ps2.executeUpdate(); rsItem = ps2.getGeneratedKeys(); rsItem.next(); item1Ids[2] = rsItem.getLong(1);
            
            // 订单2明细
            long[] item2Ids = new long[2];
            ps2.setLong(1, order2Id); ps2.setString(2, "PET-5020-A"); ps2.setString(3, "透明PET薄膜");
            ps2.setInt(4, 80); ps2.setInt(5, 900); ps2.setDouble(6, 0.080); ps2.setInt(7, 12);
            ps2.setDouble(8, 345.60); ps2.setDouble(9, 26.50); ps2.setDouble(10, 9158.40); ps2.setString(11, "耐高温");
            ps2.executeUpdate(); rsItem = ps2.getGeneratedKeys(); rsItem.next(); item2Ids[0] = rsItem.getLong(1);
            
            ps2.setLong(1, order2Id); ps2.setString(2, "PET-5020-B"); ps2.setString(3, "磨砂PET薄膜");
            ps2.setInt(4, 80); ps2.setInt(5, 900); ps2.setDouble(6, 0.100); ps2.setInt(7, 8);
            ps2.setDouble(8, 230.40); ps2.setDouble(9, 29.00); ps2.setDouble(10, 6681.60); ps2.setString(11, null);
            ps2.executeUpdate(); rsItem = ps2.getGeneratedKeys(); rsItem.next(); item2Ids[1] = rsItem.getLong(1);
            
            // 订单3明细
            long[] item3Ids = new long[2];
            ps2.setLong(1, order3Id); ps2.setString(2, "AL-3015-A"); ps2.setString(3, "铝箔复合膜");
            ps2.setInt(4, 120); ps2.setInt(5, 1200); ps2.setDouble(6, 0.090); ps2.setInt(7, 18);
            ps2.setDouble(8, 777.60); ps2.setDouble(9, 38.00); ps2.setDouble(10, 29548.80); ps2.setString(11, "防静电");
            ps2.executeUpdate(); rsItem = ps2.getGeneratedKeys(); rsItem.next(); item3Ids[0] = rsItem.getLong(1);
            
            ps2.setLong(1, order3Id); ps2.setString(2, "AL-3015-B"); ps2.setString(3, "铝箔屏蔽膜");
            ps2.setInt(4, 100); ps2.setInt(5, 1100); ps2.setDouble(6, 0.085); ps2.setInt(7, 12);
            ps2.setDouble(8, 529.20); ps2.setDouble(9, 42.00); ps2.setDouble(10, 22226.40); ps2.setString(11, null);
            ps2.executeUpdate(); rsItem = ps2.getGeneratedKeys(); rsItem.next(); item3Ids[1] = rsItem.getLong(1);
            
            // 插入发货通知单
            String sql3 = "INSERT INTO delivery_notices (notice_no, order_id, order_no, customer, customer_order_no, delivery_date, delivery_address, contact_person, contact_phone, carrier_name, carrier_phone, status, remarks, created_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            PreparedStatement ps3 = conn.prepareStatement(sql3, Statement.RETURN_GENERATED_KEYS);
            
            ps3.setString(1, "DN-2026-0001"); ps3.setLong(2, order1Id); ps3.setString(3, "SO-20260110-001");
            ps3.setString(4, "深圳华为技术有限公司"); ps3.setString(5, "HW-PO-2026-0088"); ps3.setString(6, "2026-01-15");
            ps3.setString(7, "广东省深圳市龙岗区坂田街道华为基地F区"); ps3.setString(8, "张工"); ps3.setString(9, "13800138001");
            ps3.setString(10, "顺丰速运"); ps3.setString(11, "400-811-1111"); ps3.setString(12, "pending");
            ps3.setString(13, "优先配送，请注意防潮"); ps3.setString(14, "admin");
            ps3.executeUpdate(); ResultSet rsNotice = ps3.getGeneratedKeys(); rsNotice.next(); long notice1Id = rsNotice.getLong(1);
            
            ps3.setString(1, "DN-2026-0002"); ps3.setLong(2, order2Id); ps3.setString(3, "SO-20260111-002");
            ps3.setString(4, "东莞市比亚迪电子有限公司"); ps3.setString(5, "BYD-2026-00156"); ps3.setString(6, "2026-01-16");
            ps3.setString(7, "广东省东莞市大朗镇比亚迪工业园A栋"); ps3.setString(8, "李主管"); ps3.setString(9, "13900139002");
            ps3.setString(10, "德邦物流"); ps3.setString(11, "95353"); ps3.setString(12, "shipped");
            ps3.setString(13, "已发货，运单号：DB2026011600123"); ps3.setString(14, "admin");
            ps3.executeUpdate(); rsNotice = ps3.getGeneratedKeys(); rsNotice.next(); long notice2Id = rsNotice.getLong(1);
            
            ps3.setString(1, "DN-2026-0003"); ps3.setLong(2, order3Id); ps3.setString(3, "SO-20260112-003");
            ps3.setString(4, "广州小鹏汽车科技有限公司"); ps3.setString(5, "XP-2026-0099"); ps3.setString(6, "2026-01-13");
            ps3.setString(7, "广东省广州市番禺区化龙镇小鹏汽车生产基地"); ps3.setString(8, "王经理"); ps3.setString(9, "13700137003");
            ps3.setString(10, "中通快运"); ps3.setString(11, "95311"); ps3.setString(12, "shipped");
            ps3.setString(13, "客户已签收，满意度高"); ps3.setString(14, "admin");
            ps3.executeUpdate(); rsNotice = ps3.getGeneratedKeys(); rsNotice.next(); long notice3Id = rsNotice.getLong(1);
            
            // 插入发货明细
            String sql4 = "INSERT INTO delivery_notice_items (notice_id, order_item_id, material_code, material_name, spec, quantity, area_size, box_count, gross_weight, total_weight, detail_remarks, created_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            PreparedStatement ps4 = conn.prepareStatement(sql4);
            
            ps4.setLong(1, notice1Id); ps4.setLong(2, item1Ids[0]); ps4.setString(3, "FR-4725-A"); ps4.setString(4, "阻燃聚酯薄膜(黑色)");
            ps4.setString(5, "1000*100*0.075mm"); ps4.setInt(6, 20); ps4.setDouble(7, 400.00); ps4.setInt(8, 4);
            ps4.setDouble(9, 55.0); ps4.setDouble(10, 220.0); ps4.setString(11, "第一批次"); ps4.setString(12, "admin");
            ps4.executeUpdate();
            
            ps4.setLong(1, notice1Id); ps4.setLong(2, item1Ids[1]); ps4.setString(3, "FR-4725-B"); ps4.setString(4, "阻燃聚酯薄膜(白色)");
            ps4.setString(5, "1000*100*0.050mm"); ps4.setInt(6, 15); ps4.setDouble(7, 300.00); ps4.setInt(8, 3);
            ps4.setDouble(9, 48.0); ps4.setDouble(10, 144.0); ps4.setString(11, "加急发货"); ps4.setString(12, "admin");
            ps4.executeUpdate();
            
            ps4.setLong(1, notice1Id); ps4.setLong(2, item1Ids[2]); ps4.setString(3, "FR-4730-C"); ps4.setString(4, "阻燃PET保护膜");
            ps4.setString(5, "800*50*0.100mm"); ps4.setInt(6, 10); ps4.setDouble(7, 160.00); ps4.setInt(8, 2);
            ps4.setDouble(9, 42.0); ps4.setDouble(10, 84.0); ps4.setString(11, null); ps4.setString(12, "admin");
            ps4.executeUpdate();
            
            ps4.setLong(1, notice2Id); ps4.setLong(2, item2Ids[0]); ps4.setString(3, "PET-5020-A"); ps4.setString(4, "透明PET薄膜");
            ps4.setString(5, "900*80*0.080mm"); ps4.setInt(6, 12); ps4.setDouble(7, 345.60); ps4.setInt(8, 3);
            ps4.setDouble(9, 62.5); ps4.setDouble(10, 187.5); ps4.setString(11, "耐高温材料"); ps4.setString(12, "admin");
            ps4.executeUpdate();
            
            ps4.setLong(1, notice2Id); ps4.setLong(2, item2Ids[1]); ps4.setString(3, "PET-5020-B"); ps4.setString(4, "磨砂PET薄膜");
            ps4.setString(5, "900*80*0.100mm"); ps4.setInt(6, 8); ps4.setDouble(7, 230.40); ps4.setInt(8, 2);
            ps4.setDouble(9, 58.0); ps4.setDouble(10, 116.0); ps4.setString(11, null); ps4.setString(12, "admin");
            ps4.executeUpdate();
            
            ps4.setLong(1, notice3Id); ps4.setLong(2, item3Ids[0]); ps4.setString(3, "AL-3015-A"); ps4.setString(4, "铝箔复合膜");
            ps4.setString(5, "1200*120*0.090mm"); ps4.setInt(6, 18); ps4.setDouble(7, 777.60); ps4.setInt(8, 5);
            ps4.setDouble(9, 88.0); ps4.setDouble(10, 440.0); ps4.setString(11, "防静电包装"); ps4.setString(12, "admin");
            ps4.executeUpdate();
            
            ps4.setLong(1, notice3Id); ps4.setLong(2, item3Ids[1]); ps4.setString(3, "AL-3015-B"); ps4.setString(4, "铝箔屏蔽膜");
            ps4.setString(5, "1100*100*0.085mm"); ps4.setInt(6, 12); ps4.setDouble(7, 529.20); ps4.setInt(8, 4);
            ps4.setDouble(9, 75.0); ps4.setDouble(10, 300.0); ps4.setString(11, null); ps4.setString(12, "admin");
            ps4.executeUpdate();
            
            conn.commit();
            System.out.println("✅ 测试数据插入成功！");
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
