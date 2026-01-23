import java.sql.*;

public class AddRemarkField {
    public static void main(String[] args) {
        String url = "jdbc:mysql://ssdw8127.mysql.rds.aliyuncs.com/erp?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC";
        String username = "mesuser";
        String password = "u7qH^$Y9eo";

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("MySQL Driver not found");
            return;
        }

        try (Connection conn = DriverManager.getConnection(url, username, password);
             Statement stmt = conn.createStatement()) {

            String checkSql = "SELECT COUNT(*) as cnt FROM information_schema.COLUMNS " +
                            "WHERE TABLE_SCHEMA = 'erp' " +
                            "AND TABLE_NAME = 'delivery_notice_items' " +
                            "AND COLUMN_NAME = 'remark'";
            
            ResultSet rs = stmt.executeQuery(checkSql);
            rs.next();
            int count = rs.getInt("cnt");
            
            if (count == 0) {
                String alterSql = "ALTER TABLE delivery_notice_items " +
                                "ADD COLUMN remark VARCHAR(500) DEFAULT '' COMMENT 'remarks'";
                stmt.executeUpdate(alterSql);
                System.out.println("SUCCESS: Added remark field to delivery_notice_items");
            } else {
                System.out.println("INFO: remark field already exists");
            }
            
            String verifySql = "SHOW COLUMNS FROM delivery_notice_items LIKE 'remark'";
            ResultSet rs2 = stmt.executeQuery(verifySql);
            if (rs2.next()) {
                System.out.println("Field: " + rs2.getString("Field"));
                System.out.println("Type: " + rs2.getString("Type"));
                System.out.println("Default: " + rs2.getString("Default"));
            }
            
        } catch (SQLException e) {
            System.err.println("ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
