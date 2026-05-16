package com.unsa.kmsf;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.*;
import java.util.*;

public class ConfigLoader {
    private static String encryptedConfig = null;
    private static final Gson gson = new Gson();
    private static final Type configType = new TypeToken<Map<String, Object>>(){}.getType();
    private static File stisFile = null;

    public static synchronized void loadConfig(String filePath) throws IOException {
        if (encryptedConfig != null) return;
        // 支持相对路径和绝对路径
        Path path;
        if (filePath == null || filePath.isEmpty()) {
            // 默认：当前工作目录下的 Settings/.stis
            path = Paths.get("Settings/.stis");
        } else {
            path = Paths.get(filePath);
        }
        stisFile = path.toFile();

        if (!stisFile.exists()) {
            // 如果不存在，尝试从 classpath 复制默认配置
            InputStream is = ConfigLoader.class.getClassLoader().getResourceAsStream("default.stis");
            if (is != null) {
                Files.createDirectories(stisFile.getParentFile().toPath());
                String rawJson = new String(is.readAllBytes());
                Files.write(stisFile.toPath(), rawJson.getBytes());
            } else {
                throw new FileNotFoundException("KMSF .stis 文件不存在，且未找到默认模板: " + path);
            }
        }
        String rawJson = Files.readString(stisFile.toPath());
        encryptedConfig = SecureUtils.encrypt(rawJson);
    }

    public static Map<String, Object> getConfig() {
        if (encryptedConfig == null) throw new IllegalStateException("KMSF 配置未加载");
        String json = SecureUtils.decrypt(encryptedConfig);
        return gson.fromJson(json, configType);
    }

    public static synchronized void updateConfig(Map<String, Object> newConfig) throws IOException {
        String json = gson.toJson(newConfig);
        encryptedConfig = SecureUtils.encrypt(json);
        if (stisFile != null) {
            Files.write(stisFile.toPath(), json.getBytes());
        }
    }
}
