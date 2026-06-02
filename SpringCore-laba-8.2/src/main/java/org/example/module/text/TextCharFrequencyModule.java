package org.example.module.text;

import org.example.module.FileModule;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class TextCharFrequencyModule implements FileModule {

    @Override
    public boolean supports(Path filePath) {
        return filePath.toString().toLowerCase().endsWith(".txt") && Files.isRegularFile(filePath);
    }

    @Override
    public String getDescription() {
        return "Count frequency of each character in text file";
    }

    @Override
    public String execute(Path filePath) {
        try {
            String content = new String(Files.readAllBytes(filePath));

            Map<Character, Integer> frequency = new HashMap<>();
            for (char c : content.toCharArray()) {
                if (!Character.isWhitespace(c)) {
                    frequency.put(c, frequency.getOrDefault(c, 0) + 1);
                }
            }

            Map<Character, Integer> sorted = frequency.entrySet().stream()
                    .sorted(Map.Entry.<Character, Integer>comparingByValue().reversed())
                    .limit(20)
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            Map.Entry::getValue,
                            (e1, e2) -> e1,
                            LinkedHashMap::new
                    ));

            StringBuilder result = new StringBuilder("Character frequency (top 20):\n");
            for (Map.Entry<Character, Integer> entry : sorted.entrySet()) {
                result.append(String.format("  '%c' : %d times\n", entry.getKey(), entry.getValue()));
            }

            return result.toString();
        } catch (IOException e) {
            return "Error reading file: " + e.getMessage();
        }
    }

    @Override
    public String getFileType() {
        return "text";
    }
}