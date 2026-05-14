package com.fine.tools;

import com.fine.MesApplication;
import com.fine.service.stock.TapeStockService;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Map;

/**
 * 一次性执行“采购原材料误入胶带仓”纠偏迁移。
 * 用法：
 *   mvn -DskipTests spring-boot:run -Dspring-boot.run.main-class=com.fine.tools.MisroutedPurchaseMigrationRunner -Dspring-boot.run.arguments=--auditor=system
 */
public class MisroutedPurchaseMigrationRunner {

    public static void main(String[] args) {
        String auditor = "system";
        if (args != null) {
            for (String arg : args) {
                if (arg != null && arg.startsWith("--auditor=")) {
                    String v = arg.substring("--auditor=".length()).trim();
                    if (!v.isEmpty()) {
                        auditor = v;
                    }
                }
            }
        }

        ConfigurableApplicationContext ctx = null;
        try {
            ctx = SpringApplication.run(MesApplication.class, args);
            TapeStockService tapeStockService = ctx.getBean(TapeStockService.class);
            Map<String, Object> result = tapeStockService.migrateMisroutedPurchaseInboundToRawWarehouse(auditor);
            System.out.println("[MISROUTED_MIGRATION_RESULT] " + result);
        } catch (Exception ex) {
            System.err.println("[MISROUTED_MIGRATION_ERROR] " + ex.getMessage());
            ex.printStackTrace(System.err);
            System.exit(1);
        } finally {
            if (ctx != null) {
                ctx.close();
            }
        }
    }
}
