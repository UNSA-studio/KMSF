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
                    // 如果 token 包含特殊前缀（blocked_），视为未通过
                    if (token.startsWith("blocked_")) return false;
                    String expected = generateToken(request.getRemoteAddr());
                    return expected.equals(token);
                }
            }
        }
        return false;
    }

    public void issueChallenge(HttpServletResponse response, String ip, String userAgent) throws IOException {
        String nonce = UUID.randomUUID().toString().substring(0, 8);
        boolean isAndroid = userAgent != null && userAgent.contains("Android");
        response.setContentType("text/html;charset=UTF-8");
        PrintWriter out = response.getWriter();
        out.println("<!DOCTYPE html>");
        out.println("<html lang=\"en\">");
        out.println("<head>");
        out.println("<meta charset=\"UTF-8\">");
        out.println("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
        out.println("<title>KMSF Verification</title>");
        out.println("<style>");
        out.println("body { font-family: Arial, sans-serif; background: #f4f4f4; display: flex; justify-content: center; align-items: center; height: 100vh; margin: 0; }");
        out.println(".container { text-align: center; background: white; padding: 40px; border-radius: 10px; box-shadow: 0 4px 12px rgba(0,0,0,0.1); }");
        out.println("h1 { color: #333; }");
        out.println("p { color: #666; }");
        out.println("</style>");
        out.println("</head>");
        out.println("<body>");
        out.println("<div class=\"container\">");
        out.println("<h1>Please wait.</h1>");
        out.println("<p>This site is protected with <strong>KMSF</strong></p>");
        out.println("<p>KMSF is verifying that you are a real browser, not an automated script...</p>");
        out.println("</div>");
        out.println("<script>");
        out.println("var nonce = '" + nonce + "';");
        out.println("var secret = '" + secret + "';");
        out.println("var ip = '" + ip + "';");
        out.println("var strict = " + strictMode + ";");
        out.println("var isAndroid = " + isAndroid + ";");
        // SHA256 函数
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
        // 收集硬件指纹
        out.println("function collectFingerprint() {");
        out.println("  var fp = '';");
        out.println("  try { fp += 'cpus:' + (navigator.hardwareConcurrency || '0') + ';'; } catch(e){}");
        out.println("  try { fp += 'mem:' + (navigator.deviceMemory || '0') + ';'; } catch(e){}");
        out.println("  try {");
        out.println("    var canvas = document.createElement('canvas');");
        out.println("    var gl = canvas.getContext('webgl') || canvas.getContext('experimental-webgl');");
        out.println("    if (gl) {");
        out.println("      var debugInfo = gl.getExtension('WEBGL_debug_renderer_info');");
        out.println("      if (debugInfo) {");
        out.println("        fp += 'gpu:' + gl.getParameter(debugInfo.UNMASKED_RENDERER_WEBGL) + ';';");
        out.println("      }");
        out.println("    }");
        out.println("  } catch(e){}");
        out.println("  try { fp += 'tz:' + Intl.DateTimeFormat().resolvedOptions().timeZone + ';'; } catch(e){}");
        out.println("  return fp;");
        out.println("}");
        out.println("async function run() {");
        out.println("  var fingerprint = collectFingerprint();");
        out.println("  var token = await sha256(ip + secret + nonce + fingerprint);");
        out.println("  if (strict) {");
        // 各项检测
        if (checks.getOrDefault("webdriver", true)) {
            out.println("    if (navigator.webdriver) {");
            out.println("      token = (isAndroid ? 'blocked_android_root' : 'blocked_webdriver');");
            out.println("    }");
        }
        if (checks.getOrDefault("headless", true)) {
            out.println("    if (navigator.userAgent.includes('Headless') || navigator.userAgent.includes('headless')) {");
            out.println("      token = (isAndroid ? 'blocked_android_root' : 'blocked_headless');");
            out.println("    }");
        }
        if (checks.getOrDefault("plugins", true)) {
            out.println("    if (!navigator.plugins || navigator.plugins.length === 0) {");
            out.println("      token = (isAndroid ? 'blocked_android_root' : 'blocked_plugins');");
            out.println("    }");
        }
        if (checks.getOrDefault("languages", true)) {
            out.println("    if (!navigator.languages || navigator.languages.length === 0) {");
            out.println("      token = (isAndroid ? 'blocked_android_root' : 'blocked_languages');");
            out.println("    }");
        }
        if (checks.getOrDefault("timezone", true)) {
            out.println("    try { if (Intl.DateTimeFormat().resolvedOptions().timeZone === '') {");
            out.println("      token = (isAndroid ? 'blocked_android_root' : 'blocked_tz');");
            out.println("    } } catch(e){}");
        }
        if (checks.getOrDefault("canvas", true)) {
            out.println("    var canvas = document.createElement('canvas');");
            out.println("    var gl = canvas.getContext('webgl') || canvas.getContext('experimental-webgl');");
            out.println("    if (!gl) {");
            out.println("      token = (isAndroid ? 'blocked_android_root' : 'blocked_webgl');");
            out.println("    }");
        }
        out.println("  }");
        out.println("  document.cookie = '" + TOKEN_COOKIE + "=' + token + ';path=/;max-age=" + tokenValiditySeconds + "';");
        out.println("  location.reload();");
        out.println("}");
        out.println("run();");
        out.println("</script>");
        out.println("</body>");
        out.println("</html>");
        out.close();
    }

    private String generateToken(String ip) {
        // 服务端生成 token 时无需指纹，只做基础校验，真正的 token 由客户端生成
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
