package com.unsa.kmsf;

import javax.servlet.*;
import javax.servlet.http.*;
import java.io.*;
import java.util.*;

public class KmsfFilter implements Filter {
    private RateLimiter rateLimiter;
    private BrowserCheckHandler browserCheck;
    private Map<String, Object> config;
    private List<String> protectedPaths;
    private List<String> rootAccessIPs;
    private boolean enabled;
    private String stisFilePath;
    private Map<String, String> customHeaders;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        stisFilePath = filterConfig.getInitParameter("stisFilePath");
        if (stisFilePath == null) {
            stisFilePath = System.getProperty("kmsf.stis.path", "/etc/kmsf/default.stis");
        }
        try {
            ConfigLoader.loadConfig(stisFilePath);
            reloadConfig();
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
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        // 添加自定义安全头
        for (Map.Entry<String, String> e : customHeaders.entrySet()) {
            response.setHeader(e.getKey(), e.getValue());
        }

        if (!enabled) {
            chain.doFilter(req, res);
            return;
        }

        String ip = getClientIP(request);

        // 1. IP 黑名单检查
        List<String> blacklist = (List<String>) config.get("ip_blacklist");
        if (blacklist != null && blacklist.contains(ip)) {
            sendBlocked(response, "IP permanently blocked");
            return;
        }

        // 2. 速率限制
        if (!rateLimiter.isAllowed(ip)) {
            sendBlocked(response, "Rate limit exceeded or blocked");
            return;
        }

        // 3. 浏览器挑战
        if ((boolean) ((Map) config.get("browser_check")).get("enabled")) {
            if (!browserCheck.isVerified(request)) {
                // 检查本次请求是否带有 blocked_android_root 的 cookie
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
                    // 永久封禁此 IP
                    try {
                        Map<String, Object> currentCfg = ConfigLoader.getConfig();
                        List<String> list = (List<String>) currentCfg.get("ip_blacklist");
                        if (list == null) list = new ArrayList<>();
                        if (!list.contains(ip)) {
                            list.add(ip);
                            currentCfg.put("ip_blacklist", list);
                            ConfigLoader.updateConfig(currentCfg);
                            reloadConfig(); // 立即生效
                        }
                    } catch (Exception e) {
                        // 日志可忽略
                    }
                    sendBlocked(response, "Android root detected - IP permanently blocked");
                    return;
                }
                // 普通验证未通过，发送挑战页面
                browserCheck.issueChallenge(response, ip, request.getHeader("User-Agent"));
                return;
            }
        }

        // 4. 路径保护
        String requestURI = request.getRequestURI();
        if (isProtectedPath(requestURI)) {
            boolean hasAccess = rootAccessIPs.contains(ip);
            if (!hasAccess) {
                sendBlocked(response, "Access to protected path denied");
                return;
            }
        }

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
