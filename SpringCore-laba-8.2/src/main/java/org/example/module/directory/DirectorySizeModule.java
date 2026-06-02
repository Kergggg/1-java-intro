package org.example.module.directory;

import org.example.module.FileModule;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class DirectorySizeModule implements FileModule {

    @Override
    public boolean supports(Path filePath) {
        return Files.isDirectory(filePath);
    }

    @Override
    public String getDescription() {
        return "Calculate total size of all files in directory";
    }

    @Override
    public String execute(Path dirPath) {
        try {
            long totalSize = calculateSize(dirPath);
            long fileCount = countFiles(dirPath);
            long dirCount = countDirectories(dirPath);

            return String.format(
                    "Directory Statistics:\n" +
                            "  Total size: %s\n" +
                            "  Total files: %d\n" +
                            "  Total subdirectories: %d\n" +
                            "  Average file size: %s",
                    formatSize(totalSize), fileCount, dirCount,
                    fileCount > 0 ? formatSize(totalSize / fileCount) : "N/A"
            );
        } catch (IOException e) {
            return "Error calculating directory size: " + e.getMessage();
        }
    }

    private long calculateSize(Path path) throws IOException {
        long size = 0;
        if (Files.isDirectory(path)) {
            for (Path child : Files.newDirectoryStream(path)) {
                size += calculateSize(child);
            }
        } else {
            size = Files.size(path);
        }
        return size;
    }

    private long countFiles(Path path) throws IOException {
        long count = 0;
        if (Files.isDirectory(path)) {
            for (Path child : Files.newDirectoryStream(path)) {
                if (Files.isDirectory(child)) {
                    count += countFiles(child);
                } else {
                    count++;
                }
            }
        }
        return count;
    }

    private long countDirectories(Path path) throws IOException {
        long count = 0;
        if (Files.isDirectory(path)) {
            for (Path child : Files.newDirectoryStream(path)) {
                if (Files.isDirectory(child)) {
                    count++;
                    count += countDirectories(child);
                }
            }
        }
        return count;
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