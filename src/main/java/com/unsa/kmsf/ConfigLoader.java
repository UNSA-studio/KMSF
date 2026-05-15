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
        stisFile = new File(filePath);
        if (!stisFile.exists()) {
            InputStream is = ConfigLoader.class.getClassLoader().getResourceAsStream("default.stis");
            if (is == null) throw new FileNotFoundException("KMSF .stis 文件不存在: " + filePath);
            String rawJson = new String(is.readAllBytes());
            encryptedConfig = SecureUtils.encrypt(rawJson);
            // 同时写出到指定路径，方便后续修改
            Files.createDirectories(stisFile.getParentFile().toPath());
            Files.write(stisFile.toPath(), rawJson.getBytes());
        } else {
            String rawJson = Files.readString(stisFile.toPath());
            encryptedConfig = SecureUtils.encrypt(rawJson);
        }
    }

    public static Map<String, Object> getConfig() {
        if (encryptedConfig == null) throw new IllegalStateException("KMSF 配置未加载");
        String json = SecureUtils.decrypt(encryptedConfig);
        return gson.fromJson(json, configType);
    }

    // 内部更新内存并持久化到文件（仅 KMSF 进程调用）
    public static synchronized void updateConfig(Map<String, Object> newConfig) throws IOException {
        String json = gson.toJson(newConfig);
        encryptedConfig = SecureUtils.encrypt(json);
        if (stisFile != null) {
            Files.write(stisFile.toPath(), json.getBytes());
        }
    }

    // 外部尝试读取.stis 文件会被过滤器拦截，所以这里不用保护文件系统层
}
