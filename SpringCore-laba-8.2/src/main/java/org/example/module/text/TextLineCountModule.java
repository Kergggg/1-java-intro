package org.example.module.text;

import org.example.module.FileModule;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class TextLineCountModule implements FileModule {

    @Override
    public boolean supports(Path filePath) {
        return filePath.toString().toLowerCase().endsWith(".txt") && Files.isRegularFile(filePath);
    }

    @Override
    public String getDescription() {
        return "Count number of lines in text file";
    }

    @Override
    public String execute(Path filePath) {
        try {
            long lineCount = Files.lines(filePath).count();
            return "Total lines: " + lineCount;
        } catch (IOException e) {
            return "Error reading file: " + e.getMessage();
        }
    }

    @Override
    public String getFileType() {
        return "text";
    }
}