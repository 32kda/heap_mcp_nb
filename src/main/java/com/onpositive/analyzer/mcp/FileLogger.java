package com.onpositive.analyzer.mcp;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ArrayList;

public final class FileLogger {

    private static final DateTimeFormatter TIMESTAMP_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10 MB
    private static final int MAX_BACKUP_INDEX = 7;

    private final Path logDir;
    private final Path logFile;
    private final Object lock = new Object();

    private static class Holder {
        static final FileLogger INSTANCE = new FileLogger();
    }

    public static FileLogger getInstance() {
        return Holder.INSTANCE;
    }

    private FileLogger() {
        String userHome = System.getProperty("user.home", ".");
        this.logDir = Paths.get(userHome, ".heap_mcp");
        this.logFile = logDir.resolve("heap_mcp.log");
        initDir();
    }

    private void initDir() {
        try {
            Files.createDirectories(logDir);
        } catch (IOException e) {
            System.err.println("[FileLogger] Failed to create log directory: " + logDir + " - " + e.getMessage());
        }
    }

    public void log(String level, String message) {
        synchronized (lock) {
            try {
                rotateIfNeeded();
                String line = String.format("[%s] [%s] %s%n",
                        TIMESTAMP_FMT.format(LocalDateTime.now()), level, message);
                Files.write(logFile, line.getBytes(StandardCharsets.UTF_8),
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                System.err.println("[FileLogger] Write failed: " + e.getMessage());
            }
        }
    }

    public void logToolCall(String toolName, String args) {
        log("CALL", toolName + " | args=" + args);
    }

    public void logToolResult(String toolName, boolean isError, String result) {
        String status = isError ? "ERROR" : "OK";
        String truncated = result.length() > 2000 ? result.substring(0, 2000) + "..." : result;
        log("RESULT", toolName + " | status=" + status + " | " + truncated);
    }

    public void logToolError(String toolName, Throwable error) {
        synchronized (lock) {
            try {
                rotateIfNeeded();
                String header = String.format("[%s] [ERROR] %s | %s: %s%n",
                        TIMESTAMP_FMT.format(LocalDateTime.now()), toolName,
                        error.getClass().getName(), error.getMessage());
                Files.write(logFile, header.getBytes(StandardCharsets.UTF_8),
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                try (StringWriter sw = new StringWriter(); PrintWriter pw = new PrintWriter(sw)) {
                    error.printStackTrace(pw);
                    pw.flush();
                    Files.write(logFile, sw.toString().getBytes(StandardCharsets.UTF_8),
                            StandardOpenOption.APPEND);
                }
            } catch (IOException e) {
                System.err.println("[FileLogger] Write failed: " + e.getMessage());
            }
        }
    }

    public void logError(String className, Throwable error) {
        synchronized (lock) {
            try {
                rotateIfNeeded();
                String header = String.format("[%s] [ERROR] %s | %s: %s%n",
                        TIMESTAMP_FMT.format(LocalDateTime.now()), className,
                        error.getClass().getName(), error.getMessage());
                Files.write(logFile, header.getBytes(StandardCharsets.UTF_8),
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                try (StringWriter sw = new StringWriter(); PrintWriter pw = new PrintWriter(sw)) {
                    error.printStackTrace(pw);
                    pw.flush();
                    Files.write(logFile, sw.toString().getBytes(StandardCharsets.UTF_8),
                            StandardOpenOption.APPEND);
                }
            } catch (IOException e) {
                System.err.println("[FileLogger] Write failed: " + e.getMessage());
            }
        }
    }

    private void rotateIfNeeded() throws IOException {
        if (!Files.exists(logFile)) {
            return;
        }
        long size = Files.size(logFile);
        if (size < MAX_FILE_SIZE) {
            return;
        }

        Path backupDir = logDir.resolve("archive");
        Files.createDirectories(backupDir);

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path rotated = backupDir.resolve("heap_mcp_" + timestamp + ".log");
        Files.move(logFile, rotated, StandardCopyOption.REPLACE_EXISTING);

        cleanOldArchives(backupDir);
    }

    private void cleanOldArchives(Path backupDir) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(backupDir, "heap_mcp_*.log")) {
            List<Path> archives = new ArrayList<>();
            for (Path p : stream) {
                archives.add(p);
            }
            archives.sort(Path::compareTo);
            while (archives.size() > MAX_BACKUP_INDEX) {
                Files.deleteIfExists(archives.remove(0));
            }
        }
    }
}
