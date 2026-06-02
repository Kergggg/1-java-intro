package org.example.module.directory;

import org.example.module.FileModule;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

@Component
public class DirectoryFileTypeCountModule implements FileModule {

    @Override
    public boolean supports(Path filePath) {
        return Files.isDirectory(filePath);
    }

    @Override
    public String getDescription() {
        return "Count files by extension in directory";
    }

    @Override
    public String execute(Path dirPath) {
        try {
            Map<String, Integer> extensionCount = new HashMap<>();
            Map<String, Long> extensionSize = new HashMap<>();

            processDirectory(dirPath, extensionCount, extensionSize);

            StringBuilder result = new StringBuilder("File types in directory:\n\n");
            result.append(String.format("%-15s %-10s %-12s\n", "Extension", "Count", "Total Size"));
            result.append(String.format("%-15s %-10s %-12s\n", "---------", "-----", "----------"));

            extensionCount.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .forEach(entry -> {
                        String ext = entry.getKey().isEmpty() ? "(no extension)" : entry.getKey();
                        long size = extensionSize.getOrDefault(entry.getKey(), 0L);
                        result.append(String.format("%-15s %-10d %-12s\n",
                                ext, entry.getValue(), formatSize(size)));
                    });

            int totalFiles = extensionCount.values().stream().mapToInt(Integer::intValue).sum();
            long totalSize = extensionSize.values().stream().mapToLong(Long::longValue).sum();

            result.append(String.format("\nTotal: %d files, %s", totalFiles, formatSize(totalSize)));

            return result.toString();
        } catch (IOException e) {
            return "Error analyzing directory: " + e.getMessage();
        }
    }

    private void processDirectory(Path path, Map<String, Integer> count, Map<String, Long> size) throws IOException {
        if (Files.isDirectory(path)) {
            for (Path child : Files.newDirectoryStream(path)) {
                processDirectory(child, count, size);
            }
        } else {
            String fileName = path.toString();
            int lastDot = fileName.lastIndexOf('.');
            String extension = (lastDot > 0) ? fileName.substring(lastDot + 1).toLowerCase() : "";

            count.put(extension, count.getOrDefault(extension, 0) + 1);
            size.put(extension, size.getOrDefault(extension, 0L) + Files.size(path));
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