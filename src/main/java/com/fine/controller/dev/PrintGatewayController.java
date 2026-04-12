package com.fine.controller.dev;

import com.fine.Utils.ResponseResult;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/print-gateway")
@PreAuthorize("isAuthenticated()")
public class PrintGatewayController {

    @GetMapping("/health")
    public ResponseResult<?> health(@RequestParam(defaultValue = "127.0.0.1") String host,
                                    @RequestParam(defaultValue = "9123") int port,
                                    @RequestParam(defaultValue = "3") int retries,
                                    @RequestParam(defaultValue = "500") int timeoutMs) {
        int safeRetries = Math.max(1, Math.min(retries, 10));
        int safeTimeout = Math.max(100, Math.min(timeoutMs, 5000));
        String lastError = null;

        for (int attempt = 1; attempt <= safeRetries; attempt++) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), safeTimeout);
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("host", host);
                data.put("port", port);
                data.put("online", true);
                data.put("attempts", attempt);
                data.put("timeoutMs", safeTimeout);
                data.put("message", "打印服务可用");
                return ResponseResult.success("打印服务可用", data);
            } catch (Exception ex) {
                lastError = ex.getMessage();
                if (attempt < safeRetries) {
                    try {
                        Thread.sleep(200L);
                    } catch (InterruptedException interruptedException) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("host", host);
        data.put("port", port);
        data.put("online", false);
        data.put("attempts", safeRetries);
        data.put("timeoutMs", safeTimeout);
        data.put("message", "打印服务不可达");
        data.put("error", lastError == null ? "CONNECTION_REFUSED" : lastError);
        return new ResponseResult<>(503, "打印服务不可达", data);
    }
}
