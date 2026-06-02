package org.example.module;

import java.nio.file.Path;

public interface FileModule {
    boolean supports(Path filePath);

    String getDescription();

    String execute(Path filePath);

    default String getFileType() {
        return "unknown";
    }
}