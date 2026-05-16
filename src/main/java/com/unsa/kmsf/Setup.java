package com.unsa.kmsf;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import com.google.gson.*;

public class Setup {
    private static Scanner scanner;
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private static Map<String, Map<String, String>> lang = new LinkedHashMap<>();
    static {
        Map<String, String> cn = new LinkedHashMap<>();
        cn.put("title", "KMSF 首次运行配置向导");
        cn.put("choose_lang", "Bootstrap language: CN/US");
        cn.put("use_default", "使用默认配置 (default.stis) 吗？");
        cn.put("start_custom", "开始自定义配置（输入 y/n 或具体值）：");
        cn.put("enable_protection", "启用 KMSF 保护？");
        cn.put("log_level", "日志级别 (必须为 info 或 debug)");
        cn.put("enable_rate_limit", "启用速率限制？");
        cn.put("max_requests", "  时间窗口内最大请求数");
        cn.put("time_window", "  时间窗口（支持 s/m/h/w/mo/y，如 10s、1m、2w、6mo、1y）");
        cn.put("block_duration", "  封禁时长（支持 s/m/h/w/mo/y，最大 100 年）");
        cn.put("enable_dynamic", "启用动态黑名单？");
        cn.put("dynamic_threshold", "  触发封禁的失败次数阈值");
        cn.put("dynamic_duration", "  动态封禁时长（支持 s/m/h/w/mo/y）");
        cn.put("enable_browser_check", "启用浏览器检测？");
        cn.put("challenge_secret", "  挑战密钥（留空自动生成）");
        cn.put("token_validity", "  Token 有效期（秒）");
        cn.put("strict_mode", "  严格模式？");
        cn.put("check_webdriver", "    检测 webdriver？");
        cn.put("check_headless", "    检测无头浏览器？");
        cn.put("check_plugins", "    检测插件数量？");
        cn.put("check_languages", "    检测语言列表？");
        cn.put("check_timezone", "    检测时区？");
        cn.put("check_canvas", "    检测 WebGL？");
        cn.put("root_access", "设置根目录访问白名单 IP（逗号分隔，无则回车）");
        cn.put("forbidden_note", "强制保护：Settings.folder, Settings.folder/*, root 已自动启用，不可修改。");
        cn.put("config_saved", "配置已保存到 Settings/.stis，现在可以启动您的 Web 应用了。");
        cn.put("invalid_number", "输入无效数字，请重新输入。");
        cn.put("invalid_time", "时间格式错误或超过上限，请重新输入（如 10s、5m、1h、2w、6mo、1y）。");
        cn.put("invalid_yes_no", "无效输入，请输入 y/yes 或 n/no。");
        cn.put("invalid_log_level", "无效日志级别，请输入 info 或 debug。");

        Map<String, String> en = new LinkedHashMap<>();
        en.put("title", "KMSF Initial Setup Wizard");
        en.put("choose_lang", "Bootstrap language: CN/US");
        en.put("use_default", "Use default.stis configuration?");
        en.put("start_custom", "Start custom configuration (input y/n or specific value):");
        en.put("enable_protection", "Enable KMSF protection?");
        en.put("log_level", "Log level (must be info or debug)");
        en.put("enable_rate_limit", "Enable rate limiting?");
        en.put("max_requests", "  Max requests per time window");
        en.put("time_window", "  Time window (supports s/m/h/w/mo/y, e.g. 10s, 1m, 2w, 6mo, 1y)");
        en.put("block_duration", "  Block duration (supports s/m/h/w/mo/y, max 100 years)");
        en.put("enable_dynamic", "Enable dynamic blacklist?");
        en.put("dynamic_threshold", "  Failure threshold for blocking");
        en.put("dynamic_duration", "  Dynamic block duration (supports s/m/h/w/mo/y)");
        en.put("enable_browser_check", "Enable browser check?");
        en.put("challenge_secret", "  Challenge secret (leave blank to generate)");
        en.put("token_validity", "  Token validity (seconds)");
        en.put("strict_mode", "  Strict mode?");
        en.put("check_webdriver", "    Check webdriver?");
        en.put("check_headless", "    Check headless?");
        en.put("check_plugins", "    Check plugins?");
        en.put("check_languages", "    Check languages?");
        en.put("check_timezone", "    Check timezone?");
        en.put("check_canvas", "    Check WebGL?");
        en.put("root_access", "Whitelist IPs for root access (comma separated, enter to skip)");
        en.put("forbidden_note", "Mandatory protection: Settings.folder, Settings.folder/*, root are auto-enabled and locked.");
        en.put("config_saved", "Configuration saved to Settings/.stis. You may now start your web application.");
        en.put("invalid_number", "Invalid number, please try again.");
        en.put("invalid_time", "Invalid time format or exceeds limit, please re-enter (e.g. 10s, 5m, 1h, 2w, 6mo, 1y).");
        en.put("invalid_yes_no", "Invalid input, please enter y/yes or n/no.");
        en.put("invalid_log_level", "Invalid log level, please enter info or debug.");

        lang.put("CN", cn);
        lang.put("US", en);
    }

    public static void main(String[] args) throws IOException {
        scanner = new Scanner(System.in);
        System.out.println("=================================");
        System.out.print(lang.get("CN").get("choose_lang") + " > ");
        String langChoice = scanner.nextLine().trim().toUpperCase();
        if (!lang.containsKey(langChoice)) {
            System.out.println("Invalid choice, defaulting to US.");
            langChoice = "US";
        }
        final Map<String, String> t = lang.get(langChoice);

        System.out.println("=================================");
        System.out.println("  " + t.get("title"));
        System.out.println("=================================");
        System.out.println(t.get("forbidden_note"));

        // 严格询问是否使用默认配置
        boolean useDefault = askYesNo(t.get("use_default"), t);

        Map<String, Object> config;
        if (useDefault) {
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

            config.put("enabled", askYesNo(t.get("enable_protection"), t));
            config.put("log_level", askLogLevel(t.get("log_level"), t));

            // 速率限制
            boolean rl = askYesNo(t.get("enable_rate_limit"), t);
            Map<String, Object> rateLimit = new LinkedHashMap<>();
            rateLimit.put("enabled", rl);
            if (rl) {
                rateLimit.put("max_requests", askInt(t.get("max_requests"), t));
                rateLimit.put("time_window_seconds", askTime(t.get("time_window"), t));
                rateLimit.put("block_duration_seconds", askTime(t.get("block_duration"), t));
            }
            config.put("rate_limit", rateLimit);

            // 动态黑名单
            boolean db = askYesNo(t.get("enable_dynamic"), t);
            Map<String, Object> dynamic = new LinkedHashMap<>();
            dynamic.put("enabled", db);
            if (db) {
                dynamic.put("threshold", askInt(t.get("dynamic_threshold"), t));
                dynamic.put("duration_seconds", askTime(t.get("dynamic_duration"), t));
            }
            config.put("dynamic_blacklist", dynamic);

            // 浏览器检测
            boolean bc = askYesNo(t.get("enable_browser_check"), t);
            Map<String, Object> browserCheck = new LinkedHashMap<>();
            browserCheck.put("enabled", bc);
            if (bc) {
                String secret = askString(t.get("challenge_secret"));
                if (secret.isEmpty()) secret = UUID.randomUUID().toString();
                browserCheck.put("challenge_secret", secret);
                browserCheck.put("token_validity_seconds", askInt(t.get("token_validity"), t));
                boolean strict = askYesNo(t.get("strict_mode"), t);
                browserCheck.put("strict_mode", strict);
                Map<String, Boolean> checks = new LinkedHashMap<>();
                if (strict) {
                    checks.put("webdriver", askYesNo(t.get("check_webdriver"), t));
                    checks.put("headless", askYesNo(t.get("check_headless"), t));
                    checks.put("plugins", askYesNo(t.get("check_plugins"), t));
                    checks.put("languages", askYesNo(t.get("check_languages"), t));
                    checks.put("timezone", askYesNo(t.get("check_timezone"), t));
                    checks.put("canvas", askYesNo(t.get("check_canvas"), t));
                }
                browserCheck.put("checks", checks);
            }
            config.put("browser_check", browserCheck);

            // 根目录白名单
            String rootIPs = askString(t.get("root_access"));
            List<String> rootAccess = new ArrayList<>();
            if (!rootIPs.isEmpty()) {
                for (String s : rootIPs.split(",")) rootAccess.add(s.trim());
            }
            config.put("root_access", rootAccess);

            // 强制硬编码保护路径
            List<String> forcedList = new ArrayList<>();
            forcedList.add("Settings.folder");
            forcedList.add("Settings.folder/*");
            forcedList.add("root");
            config.put("file_not_accessible", forcedList);

            config.put("ip_blacklist", new ArrayList<>());
            config.put("response_action", "block");
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("X-Frame-Options", "DENY");
            headers.put("X-Content-Type-Options", "nosniff");
            config.put("custom_headers", headers);
        }

        Path settingsDir = Paths.get("Settings");
        if (!Files.exists(settingsDir)) {
            Files.createDirectory(settingsDir);
        }

        String json = gson.toJson(config);
        Files.write(settingsDir.resolve(".stis"), json.getBytes());
        System.out.println("\n" + t.get("config_saved"));
    }

    // 严格校验 y/n
    private static boolean askYesNo(String prompt, Map<String, String> t) {
        while (true) {
            System.out.print(prompt + " (y/n): ");
            String input = scanner.nextLine().trim().toLowerCase();
            if (input.equals("y") || input.equals("yes")) {
                return true;
            } else if (input.equals("n") || input.equals("no")) {
                return false;
            } else {
                System.out.println(t.get("invalid_yes_no"));
            }
        }
    }

    // 严格校验日志级别
    private static String askLogLevel(String prompt, Map<String, String> t) {
        while (true) {
            System.out.print(prompt + ": ");
            String input = scanner.nextLine().trim().toLowerCase();
            if (input.equals("info") || input.equals("debug")) {
                return input;
            } else {
                System.out.println(t.get("invalid_log_level"));
            }
        }
    }

    private static String askString(String prompt) {
        System.out.print(prompt + ": ");
        return scanner.nextLine().trim();
    }

    private static int askInt(String prompt, Map<String, String> t) {
        while (true) {
            System.out.print(prompt + ": ");
            String input = scanner.nextLine().trim();
            try {
                int val = Integer.parseInt(input);
                if (val < 0) throw new NumberFormatException();
                return val;
            } catch (NumberFormatException e) {
                System.out.println(t.get("invalid_number"));
            }
        }
    }

    private static long askTime(String prompt, Map<String, String> t) {
        final long MAX_SECONDS = 100L * 365 * 24 * 3600;
        while (true) {
            System.out.print(prompt + ": ");
            String input = scanner.nextLine().trim().toLowerCase();
            if (input.isEmpty()) {
                System.out.println(t.get("invalid_time"));
                continue;
            }
            try {
                long multiplier = 1;
                if (input.endsWith("mo")) {
                    multiplier = 30L * 24 * 3600;
                    input = input.substring(0, input.length() - 2);
                } else if (input.endsWith("y")) {
                    multiplier = 365L * 24 * 3600;
                    input = input.substring(0, input.length() - 1);
                } else if (input.endsWith("w")) {
                    multiplier = 7L * 24 * 3600;
                    input = input.substring(0, input.length() - 1);
                } else if (input.endsWith("h")) {
                    multiplier = 3600;
                    input = input.substring(0, input.length() - 1);
                } else if (input.endsWith("m")) {
                    multiplier = 60;
                    input = input.substring(0, input.length() - 1);
                } else if (input.endsWith("s")) {
                    multiplier = 1;
                    input = input.substring(0, input.length() - 1);
                }
                long value = Long.parseLong(input);
                if (value < 0) throw new NumberFormatException();
                long seconds = value * multiplier;
                if (seconds > MAX_SECONDS) {
                    System.out.println(t.get("invalid_time"));
                    continue;
                }
                return seconds;
            } catch (NumberFormatException e) {
                System.out.println(t.get("invalid_time"));
            }
        }
    }
}
