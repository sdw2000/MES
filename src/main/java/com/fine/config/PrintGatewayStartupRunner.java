package com.fine.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Component
public class PrintGatewayStartupRunner {

    private static final Logger log = LoggerFactory.getLogger(PrintGatewayStartupRunner.class);

    @Value("${mes.print.gateway.enabled:true}")
    private boolean enabled;

    @Value("${mes.print.gateway.force-restart-on-start:true}")
    private boolean forceRestartOnStart;

    @Value("${mes.print.gateway.script-path:E:/vue/ERP/tools/bartender/start-gateway.ps1}")
    private String scriptPath;

    @Value("${mes.print.gateway.health-url:http://127.0.0.1:9123/health}")
    private String healthUrl;

    @Value("${mes.print.gateway.startup-timeout-seconds:20}")
    private int startupTimeoutSeconds;

    @Value("${mes.print.gateway.stop-on-shutdown:true}")
    private boolean stopOnShutdown;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!enabled) {
            log.info("Print gateway auto-start is disabled.");
            return;
        }

        if (!isWindows()) {
            log.info("Print gateway auto-start skipped: current OS is not Windows.");
            return;
        }

        Path gatewayScript = Paths.get(scriptPath);
        if (!Files.exists(gatewayScript)) {
            log.warn("Print gateway script not found, skip auto-start: {}", gatewayScript);
            return;
        }

        Thread bootstrapThread = new Thread(() -> restartGateway(gatewayScript), "print-gateway-bootstrap");
        bootstrapThread.setDaemon(true);
        bootstrapThread.start();
    }

    @EventListener(ContextClosedEvent.class)
    public void onContextClosed() {
        if (!enabled || !stopOnShutdown) {
            return;
        }

        if (!isWindows()) {
            return;
        }

        Path gatewayScript = Paths.get(scriptPath);
        if (!Files.exists(gatewayScript)) {
            return;
        }

        Thread shutdownThread = new Thread(() -> stopGateway(gatewayScript), "print-gateway-shutdown");
        shutdownThread.setDaemon(true);
        shutdownThread.start();
    }

    private void restartGateway(Path gatewayScript) {
        try {
            boolean healthy = isGatewayHealthy();
            if (healthy && !forceRestartOnStart) {
                log.info("Print gateway is already healthy, skip restart: {}", healthUrl);
                return;
            }

            if (healthy) {
                log.info("Print gateway is healthy but force-restart is enabled, restarting once.");
            } else {
                log.info("Print gateway is offline, starting gateway script.");
            }

            stopExistingGatewayProcess(gatewayScript);
            startGatewayScript(gatewayScript);

            if (waitForGatewayHealthy()) {
                log.info("Print gateway restarted successfully: {}", healthUrl);
            } else {
                log.warn("Print gateway startup timed out after {} seconds: {}", startupTimeoutSeconds, healthUrl);
            }
        } catch (Exception ex) {
            log.error("Failed to auto start/restart print gateway", ex);
        }
    }

    private boolean waitForGatewayHealthy() {
        int attempts = Math.max(1, startupTimeoutSeconds);
        for (int i = 0; i < attempts; i++) {
            if (isGatewayHealthy()) {
                return true;
            }
            try {
                TimeUnit.SECONDS.sleep(1);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private boolean isGatewayHealthy() {
        HttpURLConnection connection = null;
        try {
            URI uri = URI.create(healthUrl);
            connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setConnectTimeout(2000);
            connection.setReadTimeout(2000);
            connection.setRequestMethod("GET");
            int statusCode = connection.getResponseCode();
            return statusCode >= 200 && statusCode < 300;
        } catch (Exception ex) {
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void startGatewayScript(Path gatewayScript) throws IOException, InterruptedException {
        String script = gatewayScript.toAbsolutePath().toString().replace("'", "''");
        String launchCommand = "Start-Process powershell.exe "
                + "-WindowStyle Hidden "
                + "-ArgumentList @('-NoProfile','-ExecutionPolicy','Bypass','-File','" + script + "')";

        ProcessBuilder processBuilder = new ProcessBuilder(
                "powershell.exe",
                "-NoProfile",
                "-ExecutionPolicy",
                "Bypass",
                "-Command",
                launchCommand
        );
        processBuilder.inheritIO();
        Process process = processBuilder.start();
        boolean finished = process.waitFor(10, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException("Print gateway bootstrap script timed out");
        }
        if (process.exitValue() != 0) {
            throw new IllegalStateException("Print gateway bootstrap launcher exited with code " + process.exitValue());
        }
    }

    private void stopExistingGatewayProcess(Path gatewayScript) {
        String command = "$ErrorActionPreference='SilentlyContinue'; "
                + "Get-CimInstance Win32_Process "
            + "| Where-Object { $_.CommandLine -and $_.CommandLine -like '*start-gateway.ps1*' } "
                + "| ForEach-Object { Stop-Process -Id $_.ProcessId -Force }";

        ProcessBuilder processBuilder = new ProcessBuilder(
                "powershell.exe",
                "-NoProfile",
                "-ExecutionPolicy",
                "Bypass",
                "-Command",
                command
        );
        processBuilder.inheritIO();
        try {
            Process process = processBuilder.start();
            process.waitFor(20, TimeUnit.SECONDS);
        } catch (Exception ex) {
            log.warn("Failed to stop existing print gateway process before restart: {}", ex.getMessage());
        }
    }

    private void stopGateway(Path gatewayScript) {
        try {
            stopExistingGatewayProcess(gatewayScript);
            log.info("Print gateway stop on shutdown finished.");
        } catch (Exception ex) {
            log.warn("Failed to stop print gateway on shutdown: {}", ex.getMessage());
        }
    }

    private boolean isWindows() {
        String osName = System.getProperty("os.name", "");
        return osName.toLowerCase(Locale.ROOT).contains("win");
    }
}