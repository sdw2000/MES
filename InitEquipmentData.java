import java.sql.*;

public class InitEquipmentData {
    public static void main(String[] args) {
        String url = "jdbc:mysql://139.224.132.208:3306/MES?useUnicode=true&characterEncoding=utf8mb4&serverTimezone=Asia/Shanghai";
        String user = "MES";
        String password = "MES@2024";
        
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            Connection conn = DriverManager.getConnection(url, user, password);
            Statement stmt = conn.createStatement();
            
            // 读取SQL文件
            String sqlFile = "e:\\java\\MES\\sql\\init-equipment-data.sql";
            String sql = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(sqlFile)), "UTF-8");
            
            // 分割SQL语句
            String[] statements = sql.split(";");
            
            for (String statement : statements) {
                String trimmed = statement.trim();
                if (trimmed.length() > 0 && !trimmed.startsWith("--")) {
                    try {
                        System.out.println("Executing: " + trimmed.substring(0, Math.min(80, trimmed.length())) + "...");
                        if (trimmed.toUpperCase().startsWith("SELECT")) {
                            ResultSet rs = stmt.executeQuery(trimmed);
                            System.out.println("Query executed successfully.");
                            int count = 0;
                            while (rs.next() && count < 10) {
                                System.out.println(rs.getObject(1));
                                count++;
                            }
                            rs.close();
                        } else {
                            int result = stmt.executeUpdate(trimmed);
                            System.out.println("Rows affected: " + result);
                        }
                    } catch (Exception e) {
                        System.err.println("Error executing: " + e.getMessage());
                    }
                }
            }
            
            stmt.close();
            conn.close();
            System.out.println("✓ Database initialization completed successfully!");
            
        } catch (Exception e) {
            System.err.println("✗ Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
