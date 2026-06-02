package org.example.module.directory;

import org.example.module.FileModule;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.Date;

@Component
public class DirectoryListModule implements FileModule {

    @Override
    public boolean supports(Path filePath) {
        return Files.isDirectory(filePath);
    }

    @Override
    public String getDescription() {
        return "List all files in directory with details (size, date)";
    }

    @Override
    public String execute(Path dirPath) {
        try {
            StringBuilder result = new StringBuilder();
            result.append(String.format("Contents of directory: %s\n\n", dirPath.toAbsolutePath()));
            result.append(String.format("%-30s %-10s %-20s\n", "Name", "Size", "Modified Date"));
            result.append(String.format("%-30s %-10s %-20s\n", "----", "----", "-------------"));

            Files.list(dirPath)
                    .sorted((p1, p2) -> {
                        if (Files.isDirectory(p1) && !Files.isDirectory(p2)) return -1;
                        if (!Files.isDirectory(p1) && Files.isDirectory(p2)) return 1;
                        return p1.getFileName().toString().compareToIgnoreCase(p2.getFileName().toString());
                    })
                    .forEach(path -> {
                        try {
                            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
                            String name = (Files.isDirectory(path) ? "[DIR] " : "[FILE]") + path.getFileName().toString();
                            String size = Files.isDirectory(path) ? "<DIR>" : formatSize(attrs.size());
                            String modified = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(attrs.lastModifiedTime().toMillis()));
                            result.append(String.format("%-30s %-10s %-20s\n", name, size, modified));
                        } catch (IOException e) {
                            result.append(String.format("%-30s %-10s %-20s\n", path.getFileName(), "ERROR", ""));
                        }
                    });

            long fileCount = Files.list(dirPath).count();
            result.append(String.format("\nTotal items: %d", fileCount));

            return result.toString();
        } catch (IOException e) {
            return "Error reading directory: " + e.getMessage();
        }
    }

    private String formatSize(long size) {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
        if (size < 1024 * 1024 * 1024) return String.format("%.1f MB", size / (1024.0 * 1024));
        return String.format("%.2f GB", size / (1024.0 * 1024 * 1024));
    }

    @Override
    public String getFileType() {
        return "directory";
    }
}