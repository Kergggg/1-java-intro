package org.example.module.text;

import org.example.module.FileModule;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@Component
public class TextWordCountModule implements FileModule {

    @Override
    public boolean supports(Path filePath) {
        return filePath.toString().toLowerCase().endsWith(".txt") && Files.isRegularFile(filePath);
    }

    @Override
    public String getDescription() {
        return "Count total words and unique words in text file";
    }

    @Override
    public String execute(Path filePath) {
        try {
            String content = new String(Files.readAllBytes(filePath));

            String[] words = content.toLowerCase()
                    .replaceAll("[^a-zA-Zа-яА-Я\\s]", "")
                    .trim()
                    .split("\\s+");

            long totalWords = words.length;
            long uniqueWords = Arrays.stream(words).distinct().count();

            Map<String, Integer> wordFrequency = new HashMap<>();
            for (String word : words) {
                if (word.length() > 2) {
                    wordFrequency.put(word, wordFrequency.getOrDefault(word, 0) + 1);
                }
            }

            String mostCommonWord = wordFrequency.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse("none");

            int maxFrequency = wordFrequency.getOrDefault(mostCommonWord, 0);

            return String.format(
                    "Total words: %d\nUnique words: %d\nMost common word: '%s' (%d times)\nVocabulary richness: %.2f%%",
                    totalWords, uniqueWords, mostCommonWord, maxFrequency,
                    (double) uniqueWords / totalWords * 100
            );
        } catch (IOException e) {
            return "Error reading file: " + e.getMessage();
        }
    }

    @Override
    public String getFileType() {
        return "text";
    }
}