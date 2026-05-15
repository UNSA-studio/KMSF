package com.unsa.kmsf;

import javax.servlet.http.*;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;

public class BrowserCheckHandler {
    private static final String TOKEN_COOKIE = "kmsf_token";
    private final String secret;
    private final long tokenValiditySeconds;
    private final boolean strictMode;
    private final Map<String, Boolean> checks;

    public BrowserCheckHandler(Map<String, Object> config) {
        Map<String, Object> bc = (Map<String, Object>) config.get("browser_check");
        this.secret = (String) bc.get("challenge_secret");
        this.tokenValiditySeconds = ((Double) bc.get("token_validity_seconds")).longValue();
        this.strictMode = (boolean) bc.get("strict_mode");
        this.checks = (Map<String, Boolean>) bc.get("checks");
    }

    public boolean isVerified(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie c : cookies) {
                if (TOKEN_COOKIE.equals(c.getName())) {
                    String token = c.getValue();
                    String expected = generateToken(request.getRemoteAddr());
                    return expected.equals(token);
                }
            }
        }
        return false;
    }

    public void issueChallenge(HttpServletResponse response, String ip, String userAgent) throws IOException {
        String nonce = UUID.randomUUID().toString().substring(0, 8);
        response.setContentType("text/html;charset=UTF-8");
        PrintWriter out = response.getWriter();
        out.println("<!DOCTYPE html><html><head><meta charset='UTF-8'></head><body>");
        out.println("<script>");
        out.println("var nonce = '" + nonce + "';");
        out.println("var secret = '" + secret + "';");
        out.println("var ip = '" + ip + "';");
        out.println("var strict = " + strictMode + ";");
        // 前端检测函数
        out.println("function sha256(s) {");
        out.println("  var msgBuffer = new TextEncoder('utf-8').encode(s);");
        out.println("  return crypto.subtle.digest('SHA-256', msgBuffer).then(function(hash) {");
        out.println("    var hex = '';");
        out.println("    var view = new DataView(hash);");
        out.println("    for (var i = 0; i < hash.byteLength; i++) {");
        out.println("      var b = view.getUint8(i).toString(16).padStart(2,'0');");
        out.println("      hex += b;");
        out.println("    }");
        out.println("    return hex;");
        out.println("  });");
        out.println("}");
        out.println("async function run() {");
        out.println("  var token = await sha256(ip + secret + nonce);");
        out.println("  if (strict) {");
        // 严格模式额外检测
        if (checks.getOrDefault("webdriver", true)) {
            out.println("    if (navigator.webdriver) { token = 'blocked_webdriver'; }");
        }
        if (checks.getOrDefault("headless", true)) {
            out.println("    if (navigator.userAgent.includes('Headless') || navigator.userAgent.includes('headless')) { token = 'blocked_headless'; }");
        }
        if (checks.getOrDefault("plugins", true)) {
            out.println("    if (!navigator.plugins || navigator.plugins.length === 0) { token = 'blocked_plugins'; }");
        }
        if (checks.getOrDefault("languages", true)) {
            out.println("    if (!navigator.languages || navigator.languages.length === 0) { token = 'blocked_languages'; }");
        }
        if (checks.getOrDefault("timezone", true)) {
            out.println("    try { if (Intl.DateTimeFormat().resolvedOptions().timeZone === '') token = 'blocked_tz'; } catch(e){}");
        }
        if (checks.getOrDefault("canvas", true)) {
            out.println("    var canvas = document.createElement('canvas');");
            out.println("    var gl = canvas.getContext('webgl') || canvas.getContext('experimental-webgl');");
            out.println("    if (!gl) { token = 'blocked_webgl'; }");
        }
        out.println("  }");
        out.println("  document.cookie = '" + TOKEN_COOKIE + "=' + token + ';path=/;max-age=" + tokenValiditySeconds + "';");
        out.println("  location.reload();");
        out.println("}");
        out.println("run();");
        out.println("</script>");
        out.println("<p>正在验证浏览器环境，请稍候……</p>");
        out.println("</body></html>");
        out.close();
    }

    private String generateToken(String ip) {
        return sha256(ip + secret + "static_token");
    }

    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes("UTF-8"));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
