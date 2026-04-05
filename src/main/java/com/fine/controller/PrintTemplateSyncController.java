package com.fine.controller;

import com.fine.Utils.ResponseResult;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.FileInputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/print-template")
@PreAuthorize("isAuthenticated()")
public class PrintTemplateSyncController {

    @GetMapping("/manifest")
    public ResponseResult<List<Map<String, Object>>> manifest(HttpServletRequest request) {
        try {
            File root = resolveTemplateRoot();
            if (!root.exists() || !root.isDirectory()) {
                return ResponseResult.success(new ArrayList<>());
            }

            File[] files = root.listFiles((dir, name) -> {
                String n = String.valueOf(name).toLowerCase(Locale.ROOT);
                return n.endsWith(".btw");
            });
            if (files == null || files.length == 0) {
                return ResponseResult.success(new ArrayList<>());
            }

            List<Map<String, Object>> rows = new ArrayList<>();
            String base = buildBaseUrl(request);

            for (File f : files) {
                if (f == null || !f.isFile()) continue;
                String fileName = f.getName();
                String stem = fileName;
                int dot = fileName.lastIndexOf('.');
                if (dot > 0) stem = fileName.substring(0, dot);

                Map<String, Object> row = new HashMap<>();
                row.put("templateKey", stem.toUpperCase(Locale.ROOT));
                row.put("version", String.valueOf(f.lastModified()));
                row.put("sha256", sha256(f));
                row.put("fileName", fileName);
                row.put("downloadUrl", base + "/api/print-template/file/" + URLEncoder.encode(fileName, StandardCharsets.UTF_8.toString()));
                row.put("updatedAt", new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date(f.lastModified())));
                rows.add(row);
            }

            rows.sort(Comparator.comparing(x -> String.valueOf(x.get("templateKey"))));
            return ResponseResult.success(rows);
        } catch (Exception e) {
            return ResponseResult.error("读取模板清单失败: " + e.getMessage());
        }
    }

    @GetMapping("/file/{fileName:.+}")
    public ResponseEntity<Resource> download(@PathVariable String fileName) {
        try {
            if (fileName == null || fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
                return ResponseEntity.badRequest().build();
            }
            String name = fileName.trim();
            if (!name.toLowerCase(Locale.ROOT).endsWith(".btw")) {
                return ResponseEntity.badRequest().build();
            }

            File root = resolveTemplateRoot();
            File target = new File(root, name);
            if (!target.exists() || !target.isFile()) {
                return ResponseEntity.notFound().build();
            }

            Resource resource = new FileSystemResource(target);
            String encoded = URLEncoder.encode(name, StandardCharsets.UTF_8.toString()).replaceAll("\\+", "%20");
                return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                    .header(HttpHeaders.CONTENT_TYPE, "application/octet-stream")
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    private File resolveTemplateRoot() {
        Set<String> candidates = new LinkedHashSet<>();

        String fromEnv = System.getenv("MES_TEMPLATE_ROOT");
        if (fromEnv != null && !fromEnv.trim().isEmpty()) {
            candidates.add(fromEnv.trim());
        }

        String fromEnvList = System.getenv("MES_TEMPLATE_ROOTS");
        if (fromEnvList != null && !fromEnvList.trim().isEmpty()) {
            String[] arr = fromEnvList.split("[;,]");
            for (String x : arr) {
                if (x != null && !x.trim().isEmpty()) candidates.add(x.trim());
            }
        }

        String fromProp = System.getProperty("mes.template.root");
        if (fromProp != null && !fromProp.trim().isEmpty()) {
            candidates.add(fromProp.trim());
        }

        candidates.add(new File(System.getProperty("user.dir"), "print-templates").getAbsolutePath());
        candidates.add("D:/MES/BarTender/Templates");
        candidates.add("E:/MES/BarTender/Templates");
        candidates.add("D:/BarTender/Templates");
        candidates.add("E:/BarTender/Templates");

        for (String p : candidates) {
            try {
                File f = new File(p);
                if (f.exists() && f.isDirectory()) {
                    File[] files = f.listFiles((dir, name) -> String.valueOf(name).toLowerCase(Locale.ROOT).endsWith(".btw"));
                    if (files != null && files.length > 0) {
                        return f;
                    }
                }
            } catch (Exception ignore) {
            }
        }

        // 兜底返回默认目录（即便不存在），保持历史行为
        return new File(System.getProperty("user.dir"), "print-templates");
    }

    private String buildBaseUrl(HttpServletRequest request) {
        String scheme = request.getScheme();
        String host = request.getServerName();
        int port = request.getServerPort();
        String context = request.getContextPath();

        boolean standard = ("http".equalsIgnoreCase(scheme) && port == 80)
                || ("https".equalsIgnoreCase(scheme) && port == 443);
        return scheme + "://" + host + (standard ? "" : ":" + port) + (context == null ? "" : context);
    }

    private String sha256(File file) {
        FileInputStream fis = null;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            fis = new FileInputStream(file);
            byte[] buf = new byte[8192];
            int n;
            while ((n = fis.read(buf)) > 0) {
                md.update(buf, 0, n);
            }
            byte[] hash = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        } finally {
            if (fis != null) {
                try { fis.close(); } catch (Exception ignore) {}
            }
        }
    }
}
