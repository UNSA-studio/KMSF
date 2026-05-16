package com.unsa.kmsf;

import javax.servlet.*;
import javax.servlet.http.*;
import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.text.SimpleDateFormat;

public class KmsfFilter implements Filter {
    private RateLimiter rateLimiter;
    private BrowserCheckHandler browserCheck;
    private Map<String, Object> config;
    private List<String> protectedPaths;
    private List<String> rootAccessIPs;
    private boolean enabled;
    private String stisFilePath;
    private Map<String, String> customHeaders;
    private String logPath;
    private boolean logEnabled;
    private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        stisFilePath = filterConfig.getInitParameter("stisFilePath");
        if (stisFilePath == null) {
            stisFilePath = System.getProperty("kmsf.stis.path", "Settings/.stis");
        }
        try {
            ConfigLoader.loadConfig(stisFilePath);
            reloadConfig();
            // 每次启动时清空日志，避免新旧堆叠
            if (logEnabled && logPath != null) {
                Path logFile = Paths.get(logPath);
                if (logFile.getParent() != null) {
                    Files.createDirectories(logFile.getParent());
                }
                // 覆盖写入空内容，相当于清空
                Files.write(logFile, "".getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                log("KMSF filter initialized, log cleared.");
            }
        } catch (IOException e) {
            throw new ServletException("KMSF 配置加载失败", e);
        }
    }

    private void reloadConfig() {
        config = ConfigLoader.getConfig();
        enabled = (boolean) config.getOrDefault("enabled", true);
        protectedPaths = (List<String>) config.get("file_not_accessible");
        rootAccessIPs = (List<String>) config.get("root_access");
        rateLimiter = new RateLimiter(config);
        browserCheck = new BrowserCheckHandler(config);
        Map<String, Object> hdrs = (Map<String, Object>) config.get("custom_headers");
        customHeaders = new HashMap<>();
        if (hdrs != null) {
            hdrs.forEach((k, v) -> customHeaders.put(k, String.valueOf(v)));
        }
        logPath = (String) config.getOrDefault("log_path", "Settings/kmsf.log");
        String logLevel = (String) config.getOrDefault("log_level", "info");
        logEnabled = logLevel.equals("debug") || logLevel.equals("info");
    }

    private void log(String message) {
        if (!logEnabled || logPath == null) return;
        try {
            Path path = Paths.get(logPath);
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            String line = sdf.format(new Date()) + " [" + Thread.currentThread().getName() + "] " + message + "\n";
            Files.write(path, line.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        for (Map.Entry<String, String> e : customHeaders.entrySet()) {
            response.setHeader(e.getKey(), e.getValue());
        }

        if (!enabled) {
            log("KMSF disabled, forwarding request: " + request.getRequestURI());
            chain.doFilter(req, res);
            return;
        }

        String ip = getClientIP(request);
        String requestURI = request.getRequestURI();
        log("Request from " + ip + " to " + requestURI);

        // 1. IP 黑名单检查
        List<String> blacklist = (List<String>) config.get("ip_blacklist");
        if (blacklist != null && blacklist.contains(ip)) {
            log("BLOCKED: IP " + ip + " in blacklist");
            sendBlocked(response, "IP permanently blocked");
            return;
        }

        // 2. 速率限制（含动态黑名单）
        if (!rateLimiter.isAllowed(ip)) {
            log("BLOCKED: Rate limit or dynamic blacklist triggered for " + ip);
            sendBlocked(response, "Rate limit exceeded or blocked");
            return;
        }

        // 3. 浏览器挑战
        if ((boolean) ((Map) config.get("browser_check")).get("enabled")) {
            if (!browserCheck.isVerified(request)) {
                Cookie[] cookies = request.getCookies();
                String token = null;
                if (cookies != null) {
                    for (Cookie c : cookies) {
                        if ("kmsf_token".equals(c.getName())) {
                            token = c.getValue();
                            break;
                        }
                    }
                }
                if ("blocked_android_root".equals(token)) {
                    log("BLOCKED: Android root detected for IP " + ip + " - permanently blocking");
                    try {
                        Map<String, Object> currentCfg = ConfigLoader.getConfig();
                        List<String> list = (List<String>) currentCfg.get("ip_blacklist");
                        if (list == null) list = new ArrayList<>();
                        if (!list.contains(ip)) {
                            list.add(ip);
                            currentCfg.put("ip_blacklist", list);
                            ConfigLoader.updateConfig(currentCfg);
                            reloadConfig();
                        }
                    } catch (Exception e) {}
                    sendBlocked(response, "Android root detected - IP permanently blocked");
                    return;
                }
                // 未验证，发送挑战页面
                log("Browser challenge issued for " + ip);
                browserCheck.issueChallenge(response, ip, request.getHeader("User-Agent"));
                return;
            }
        }

        // 4. 路径保护
        if (isProtectedPath(requestURI)) {
            boolean hasAccess = rootAccessIPs.contains(ip);
            if (!hasAccess) {
                log("BLOCKED: Protected path " + requestURI + " accessed by " + ip + " without root access");
                sendBlocked(response, "Access to protected path denied");
                return;
            }
        }

        log("ALLOWED: " + ip + " -> " + requestURI);
        chain.doFilter(req, res);
    }

    private boolean isProtectedPath(String uri) {
        if (protectedPaths == null) return false;
        String lowerUri = uri.toLowerCase();
        if (protectedPaths.contains("root") && (uri.equals("/") || uri.isEmpty())) {
            return true;
        }
        for (String rule : protectedPaths) {
            rule = rule.toLowerCase();
            if (rule.equals("settings.folder") && lowerUri.equals("/settings")) return true;
            if (rule.equals("settings.folder/*") && lowerUri.startsWith("/settings/")) return true;
            if (rule.endsWith(".folder") && !rule.endsWith(".folder/*")) {
                String folderName = rule.substring(0, rule.length() - 7);
                if (lowerUri.equals("/" + folderName)) return true;
            }
            if (rule.endsWith(".folder/*")) {
                String folderName = rule.substring(0, rule.length() - 9);
                if (lowerUri.startsWith("/" + folderName + "/")) return true;
            }
        }
        return false;
    }

    private void sendBlocked(HttpServletResponse response, String msg) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("text/plain;charset=UTF-8");
        PrintWriter out = response.getWriter();
        out.println("KMSF: Access Denied - " + msg);
        out.close();
    }

    private String getClientIP(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    @Override
    public void destroy() {}
}
