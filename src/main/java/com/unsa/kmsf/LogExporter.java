package com.unsa.kmsf;

import java.io.*;
import java.nio.file.*;

public class LogExporter {
    public static void main(String[] args) throws IOException {
        // 默认日志路径，与配置一致
        String logPath = "Settings/kmsf.log";
        Path source = Paths.get(logPath);
        if (!Files.exists(source)) {
            System.out.println("日志文件不存在: " + logPath);
            return;
        }
        Path target = Paths.get("kmsf_export.log");
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        System.out.println("日志已导出到: " + target.toAbsolutePath());
    }
}
