package com.unsa.kmsf;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class Setup {
    public static void main(String[] args) throws IOException {
        Scanner scanner = new Scanner(System.in);
        System.out.println("=================================");
        System.out.println("  KMSF 首次运行配置向导");
        System.out.println("=================================");
        System.out.println("Use default.stis or custom.stis configuration? (y/n)");
        System.out.print("> ");
        String choice = scanner.nextLine().trim().toLowerCase();

        Map<String, Object> config;
        if (choice.equals("y") || choice.equals("yes")) {
            // 加载默认配置并保存到 Settings/.stis
            InputStream is = Setup.class.getClassLoader().getResourceAsStream("default.stis");
            if (is == null) {
                System.err.println("未找到默认配置模板 default.stis");
                return;
            }
            String json = new String(is.readAllBytes());
            config = new com.google.gson.Gson().fromJson(json, Map.class);
        } else {
            // 自定义配置
            config = new LinkedHashMap<>();
            System.out.println("\n开始自定义配置（输入 y/n 或具体值）：");

            System.out.print("启用 KMSF 保护？(y/n): ");
            config.put("enabled", scanner.nextLine().trim().equalsIgnoreCase("y"));

            System.out.print("日志级别 (info/debug): ");
            config.put("log_level", scanner.nextLine().trim().toLowerCase());

            // 速率限制
            System.out.print("启用速率限制？(y/n): ");
            boolean rl = scanner.nextLine().trim().equalsIgnoreCase("y");
            Map<String, Object> rateLimit = new LinkedHashMap<>();
            rateLimit.put("enabled", rl);
            if (rl) {
                System.out.print("  时间窗口内最大请求数: ");
                rateLimit.put("max_requests", Integer.parseInt(scanner.nextLine().trim()));
                System.out.print("  时间窗口（秒）: ");
                rateLimit.put("time_window_seconds", Integer.parseInt(scanner.nextLine().trim()));
                System.out.print("  封禁时长（秒）: ");
                rateLimit.put("block_duration_seconds", Integer.parseInt(scanner.nextLine().trim()));
            }
            config.put("rate_limit", rateLimit);

            // 动态黑名单
            System.out.print("启用动态黑名单？(y/n): ");
            boolean db = scanner.nextLine().trim().equalsIgnoreCase("y");
            Map<String, Object> dynamic = new LinkedHashMap<>();
            dynamic.put("enabled", db);
            if (db) {
                System.out.print("  触发封禁的失败次数阈值: ");
                dynamic.put("threshold", Integer.parseInt(scanner.nextLine().trim()));
                System.out.print("  动态封禁时长（秒）: ");
                dynamic.put("duration_seconds", Integer.parseInt(scanner.nextLine().trim()));
            }
            config.put("dynamic_blacklist", dynamic);

            // 浏览器检测
            System.out.print("启用浏览器检测？(y/n): ");
            boolean bc = scanner.nextLine().trim().equalsIgnoreCase("y");
            Map<String, Object> browserCheck = new LinkedHashMap<>();
            browserCheck.put("enabled", bc);
            if (bc) {
                System.out.print("  挑战密钥（留空自动生成）: ");
                String secret = scanner.nextLine().trim();
                if (secret.isEmpty()) secret = UUID.randomUUID().toString();
                browserCheck.put("challenge_secret", secret);
                System.out.print("  Token 有效期（秒）: ");
                browserCheck.put("token_validity_seconds", Long.parseLong(scanner.nextLine().trim()));
                System.out.print("  严格模式？(y/n): ");
                boolean strict = scanner.nextLine().trim().equalsIgnoreCase("y");
                browserCheck.put("strict_mode", strict);
                Map<String, Boolean> checks = new LinkedHashMap<>();
                if (strict) {
                    System.out.print("    检测 webdriver？(y/n): ");
                    checks.put("webdriver", scanner.nextLine().trim().equalsIgnoreCase("y"));
                    System.out.print("    检测无头浏览器？(y/n): ");
                    checks.put("headless", scanner.nextLine().trim().equalsIgnoreCase("y"));
                    System.out.print("    检测插件数量？(y/n): ");
                    checks.put("plugins", scanner.nextLine().trim().equalsIgnoreCase("y"));
                    System.out.print("    检测语言列表？(y/n): ");
                    checks.put("languages", scanner.nextLine().trim().equalsIgnoreCase("y"));
                    System.out.print("    检测时区？(y/n): ");
                    checks.put("timezone", scanner.nextLine().trim().equalsIgnoreCase("y"));
                    System.out.print("    检测 WebGL？(y/n): ");
                    checks.put("canvas", scanner.nextLine().trim().equalsIgnoreCase("y"));
                }
                browserCheck.put("checks", checks);
            }
            config.put("browser_check", browserCheck);

            // 路径保护
            System.out.print("设置根目录访问白名单 IP（逗号分隔，无则回车）: ");
            String rootIPs = scanner.nextLine().trim();
            List<String> rootAccess = new ArrayList<>();
            if (!rootIPs.isEmpty()) {
                for (String s : rootIPs.split(",")) rootAccess.add(s.trim());
            }
            config.put("root_access", rootAccess);

            System.out.print("设置禁止访问的文件夹/文件（逗号分隔，如 Settings.folder,root）：");
            String forbidden = scanner.nextLine().trim();
            List<String> forbiddenList = new ArrayList<>();
            if (!forbidden.isEmpty()) {
                for (String s : forbidden.split(",")) forbiddenList.add(s.trim());
            }
            config.put("file_not_accessible", forbiddenList);

            config.put("ip_blacklist", new ArrayList<>());
            config.put("response_action", "block");
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("X-Frame-Options", "DENY");
            headers.put("X-Content-Type-Options", "nosniff");
            config.put("custom_headers", headers);
        }

        // 确保 Settings 目录存在
        Path settingsDir = Paths.get("Settings");
        if (!Files.exists(settingsDir)) {
            Files.createDirectory(settingsDir);
        }

        // 写入 .stis 文件
        String json = new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(config);
        Files.write(settingsDir.resolve(".stis"), json.getBytes());
        System.out.println("\n配置已保存到 Settings/.stis");
        System.out.println("现在可以启动您的 Web 应用了。");
    }
}
