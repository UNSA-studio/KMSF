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

    // 硬编码不可修改的保护路径
    private static final List<String> MANDATORY_PROTECTED = Arrays.asList(
        "Settings.folder", "Settings.folder/*", "root"
    );

    public static synchronized void loadConfig(String filePath) throws IOException {
        if (encryptedConfig != null) return;
        Path path;
        if (filePath == null || filePath.isEmpty()) {
            path = Paths.get("Settings/.stis");
        } else {
            path = Paths.get(filePath);
        }
        stisFile = path.toFile();

        if (!stisFile.exists()) {
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
        Map<String, Object> config = gson.fromJson(rawJson, configType);

        // 强制注入硬编码路径
        List<String> currentList = (List<String>) config.get("file_not_accessible");
        if (currentList == null) {
            currentList = new ArrayList<>();
            config.put("file_not_accessible", currentList);
        }
        boolean tampered = false;
        for (String mandatory : MANDATORY_PROTECTED) {
            if (!currentList.contains(mandatory)) {
                currentList.add(mandatory);
                tampered = true;
            }
        }
        if (tampered) {
            System.err.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            System.err.println("WARNING: The .stis file has been tampered with and you are at risk!");
            System.err.println("Missing mandatory protections have been restored:");
            for (String mandatory : MANDATORY_PROTECTED) {
                if (!currentList.contains(mandatory)) {
                    System.err.println("  - " + mandatory);
                }
            }
            System.err.println("Please check your configuration file immediately.");
            System.err.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            // 将修正后的配置写回文件
            String newJson = gson.toJson(config);
            Files.write(stisFile.toPath(), newJson.getBytes());
        }

        encryptedConfig = SecureUtils.encrypt(gson.toJson(config));
    }

    public static Map<String, Object> getConfig() {
        if (encryptedConfig == null) throw new IllegalStateException("KMSF 配置未加载");
        String json = SecureUtils.decrypt(encryptedConfig);
        return gson.fromJson(json, configType);
    }

    public static synchronized void updateConfig(Map<String, Object> newConfig) throws IOException {
        // 更新时同样强制注入硬编码路径
        List<String> currentList = (List<String>) newConfig.get("file_not_accessible");
        if (currentList == null) {
            currentList = new ArrayList<>();
            newConfig.put("file_not_accessible", currentList);
        }
        for (String mandatory : MANDATORY_PROTECTED) {
            if (!currentList.contains(mandatory)) {
                currentList.add(mandatory);
            }
        }
        String json = gson.toJson(newConfig);
        encryptedConfig = SecureUtils.encrypt(json);
        if (stisFile != null) {
            Files.write(stisFile.toPath(), json.getBytes());
        }
    }
}