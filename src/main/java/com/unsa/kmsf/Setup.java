package com.unsa.kmsf;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import com.google.gson.*;

public class Setup {
    private static Scanner scanner;
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    // 语言包
    private static Map<String, Map<String, String>> lang = new LinkedHashMap<>();
    static {
        Map<String, String> cn = new LinkedHashMap<>();
        cn.put("title", "KMSF 首次运行配置向导");
        cn.put("choose_lang", "Bootstrap language: CN/US");
        cn.put("lang_prompt", "输入 CN 代表中文，US 代表英文");
        cn.put("use_default", "使用默认配置 (default.stis) 吗？(y/n)");
        cn.put("start_custom", "开始自定义配置（输入 y/n 或具体值）：");
        cn.put("enable_protection", "启用 KMSF 保护？(y/n)");
        cn.put("log_level", "日志级别 (info/debug)");
        cn.put("enable_rate_limit", "启用速率限制？(y/n)");
        cn.put("max_requests", "  时间窗口内最大请求数");
        cn.put("time_window", "  时间窗口（支持 s/m/h，如 10s 或 1m）");
        cn.put("block_duration", "  封禁时长（支持 s/m/h，最大 100 年）");
        cn.put("enable_dynamic", "启用动态黑名单？(y/n)");
        cn.put("dynamic_threshold", "  触发封禁的失败次数阈值");
        cn.put("dynamic_duration", "  动态封禁时长（支持 s/m/h）");
        cn.put("enable_browser_check", "启用浏览器检测？(y/n)");
        cn.put("challenge_secret", "  挑战密钥（留空自动生成）");
        cn.put("token_validity", "  Token 有效期（秒）");
        cn.put("strict_mode", "  严格模式？(y/n)");
        cn.put("check_webdriver", "    检测 webdriver？(y/n)");
        cn.put("check_headless", "    检测无头浏览器？(y/n)");
        cn.put("check_plugins", "    检测插件数量？(y/n)");
        cn.put("check_languages", "    检测语言列表？(y/n)");
        cn.put("check_timezone", "    检测时区？(y/n)");
        cn.put("check_canvas", "    检测 WebGL？(y/n)");
        cn.put("root_access", "设置根目录访问白名单 IP（逗号分隔，无则回车）");
        cn.put("forbidden", "设置禁止访问的文件夹/文件（逗号分隔，如 Settings.folder,root）");
        cn.put("config_saved", "配置已保存到 Settings/.stis，现在可以启动您的 Web 应用了。");
        cn.put("invalid_number", "输入无效数字，请重新输入。");
        cn.put("invalid_time", "时间格式错误或超过上限，请重新输入（如 10s、5m、1h）。");

        Map<String, String> en = new LinkedHashMap<>();
        en.put("title", "KMSF Initial Setup Wizard");
        en.put("choose_lang", "Bootstrap language: CN/US");
        en.put("lang_prompt", "Enter CN for Chinese, US for English");
        en.put("use_default", "Use default.stis configuration? (y/n)");
        en.put("start_custom", "Start custom configuration (input y/n or specific value):");
        en.put("enable_protection", "Enable KMSF protection? (y/n)");
        en.put("log_level", "Log level (info/debug)");
        en.put("enable_rate_limit", "Enable rate limiting? (y/n)");
        en.put("max_requests", "  Max requests per time window");
        en.put("time_window", "  Time window (supports s/m/h, e.g. 10s or 1m)");
        en.put("block_duration", "  Block duration (supports s/m/h, max 100 years)");
        en.put("enable_dynamic", "Enable dynamic blacklist? (y/n)");
        en.put("dynamic_threshold", "  Failure threshold for blocking");
        en.put("dynamic_duration", "  Dynamic block duration (supports s/m/h)");
        en.put("enable_browser_check", "Enable browser check? (y/n)");
        en.put("challenge_secret", "  Challenge secret (leave blank to generate)");
        en.put("token_validity", "  Token validity (seconds)");
        en.put("strict_mode", "  Strict mode? (y/n)");
        en.put("check_webdriver", "    Check webdriver? (y/n)");
        en.put("check_headless", "    Check headless? (y/n)");
        en.put("check_plugins", "    Check plugins? (y/n)");
        en.put("check_languages", "    Check languages? (y/n)");
        en.put("check_timezone", "    Check timezone? (y/n)");
        en.put("check_canvas", "    Check WebGL? (y/n)");
        en.put("root_access", "Whitelist IPs for root access (comma separated, enter to skip)");
        en.put("forbidden", "Forbidden paths (comma separated, e.g. Settings.folder,root)");
        en.put("config_saved", "Configuration saved to Settings/.stis. You may now start your web application.");
        en.put("invalid_number", "Invalid number, please try again.");
        en.put("invalid_time", "Invalid time format or exceeds limit, please re-enter (e.g. 10s, 5m, 1h).");

        lang.put("CN", cn);
        lang.put("US", en);
    }

    public static void main(String[] args) throws IOException {
        scanner = new Scanner(System.in);
        System.out.println("=================================");
        System.out.println(lang.get("CN").get("choose_lang")); // 先显示语言提示，不翻译
        System.out.print("> ");
        String langChoice = scanner.nextLine().trim().toUpperCase();
        if (!lang.containsKey(langChoice)) {
            System.out.println("Invalid choice, defaulting to EN.");
            langChoice = "US";
        }
        final String L = langChoice;
        Map<String, String> t = lang.get(L);

        System.out.println("=================================");
        System.out.println("  " + t.get("title"));
        System.out.println("=================================");
        System.out.print(t.get("use_default") + " ");
        String choice = scanner.nextLine().trim().toLowerCase();

        Map<String, Object> config;
        if (choice.equals("y") || choice.equals("yes")) {
            InputStream is = Setup.class.getClassLoader().getResourceAsStream("default.stis");
            if (is == null) {
                System.err.println("默认配置模板 default.stis 未找到");
                return;
            }
            String json = new String(is.readAllBytes());
            config = gson.fromJson(json, Map.class);
        } else {
            config = new LinkedHashMap<>();
            System.out.println("\n" + t.get("start_custom"));

            config.put("enabled", askYesNo(t.get("enable_protection")));
            config.put("log_level", askString(t.get("log_level")));

            // 速率限制
            boolean rl = askYesNo(t.get("enable_rate_limit"));
            Map<String, Object> rateLimit = new LinkedHashMap<>();
            rateLimit.put("enabled", rl);
            if (rl) {
                rateLimit.put("max_requests", askInt(t.get("max_requests")));
                rateLimit.put("time_window_seconds", askTime(t.get("time_window")));
                rateLimit.put("block_duration_seconds", askTime(t.get("block_duration")));
            }
            config.put("rate_limit", rateLimit);

            // 动态黑名单
            boolean db = askYesNo(t.get("enable_dynamic"));
            Map<String, Object> dynamic = new LinkedHashMap<>();
            dynamic.put("enabled", db);
            if (db) {
                dynamic.put("threshold", askInt(t.get("dynamic_threshold")));
                dynamic.put("duration_seconds", askTime(t.get("dynamic_duration")));
            }
            config.put("dynamic_blacklist", dynamic);

            // 浏览器检测
            boolean bc = askYesNo(t.get("enable_browser_check"));
            Map<String, Object> browserCheck = new LinkedHashMap<>();
            browserCheck.put("enabled", bc);
            if (bc) {
                String secret = askString(t.get("challenge_secret"));
                if (secret.isEmpty()) secret = UUID.randomUUID().toString();
                browserCheck.put("challenge_secret", secret);
                browserCheck.put("token_validity_seconds", askInt(t.get("token_validity")));
                boolean strict = askYesNo(t.get("strict_mode"));
                browserCheck.put("strict_mode", strict);
                Map<String, Boolean> checks = new LinkedHashMap<>();
                if (strict) {
                    checks.put("webdriver", askYesNo(t.get("check_webdriver")));
                    checks.put("headless", askYesNo(t.get("check_headless")));
                    checks.put("plugins", askYesNo(t.get("check_plugins")));
                    checks.put("languages", askYesNo(t.get("check_languages")));
                    checks.put("timezone", askYesNo(t.get("check_timezone")));
                    checks.put("canvas", askYesNo(t.get("check_canvas")));
                }
                browserCheck.put("checks", checks);
            }
            config.put("browser_check", browserCheck);

            // 路径保护
            String rootIPs = askString(t.get("root_access"));
            List<String> rootAccess = new ArrayList<>();
            if (!rootIPs.isEmpty()) {
                for (String s : rootIPs.split(",")) rootAccess.add(s.trim());
            }
            config.put("root_access", rootAccess);

            String forbidden = askString(t.get("forbidden"));
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

        String json = gson.toJson(config);
        Files.write(settingsDir.resolve(".stis"), json.getBytes());
        System.out.println("\n" + t.get("config_saved"));
    }

    // 辅助方法：询问 yes/no
    private static boolean askYesNo(String prompt) {
        System.out.print(prompt + " ");
        String input = scanner.nextLine().trim().toLowerCase();
        return input.equals("y") || input.equals("yes");
    }

    // 询问字符串
    private static String askString(String prompt) {
        System.out.print(prompt + ": ");
        return scanner.nextLine().trim();
    }

    // 询问整数，带重试
    private static int askInt(String prompt) {
        while (true) {
            System.out.print(prompt + ": ");
            String input = scanner.nextLine().trim();
            try {
                int val = Integer.parseInt(input);
                if (val < 0) throw new NumberFormatException();
                return val;
            } catch (NumberFormatException e) {
                System.out.println(lang.get("CN").get("invalid_number")); // 错误提示可用默认语言
            }
        }
    }

    // 解析时间输入：支持数字+s/m/h，纯数字视为秒，最大100年
    private static long askTime(String prompt) {
        final long MAX_SECONDS = 100L * 365 * 24 * 3600; // 100 年
        while (true) {
            System.out.print(prompt + ": ");
            String input = scanner.nextLine().trim().toLowerCase();
            try {
                if (input.isEmpty()) throw new NumberFormatException();
                long multiplier = 1;
                if (input.endsWith("s")) {
                    multiplier = 1;
                    input = input.substring(0, input.length() - 1);
                } else if (input.endsWith("m")) {
                    multiplier = 60;
                    input = input.substring(0, input.length() - 1);
                } else if (input.endsWith("h")) {
                    multiplier = 3600;
                    input = input.substring(0, input.length() - 1);
                }
                long value = Long.parseLong(input);
                if (value < 0) throw new NumberFormatException();
                long seconds = value * multiplier;
                if (seconds > MAX_SECONDS) {
                    System.out.println(lang.get("CN").get("invalid_time"));
                    continue;
                }
                return seconds;
            } catch (NumberFormatException e) {
                System.out.println(lang.get("CN").get("invalid_time"));
            }
        }
    }
}
