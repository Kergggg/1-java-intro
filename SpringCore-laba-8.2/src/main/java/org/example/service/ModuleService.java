package org.example.service;

import org.example.module.FileModule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;

@Service
public class ModuleService {

    @Autowired
    private List<FileModule> modules;

    public void processFile(String filename) {
        Path filePath = Paths.get(filename);

        if (!Files.exists(filePath)) {
            System.out.println("Error: File/directory not found: " + filename);
            return;
        }

        List<FileModule> supportedModules = modules.stream()
                .filter(module -> module.supports(filePath))
                .collect(Collectors.toList());

        if (supportedModules.isEmpty()) {
            System.out.println("No modules found that support this file type.");
            System.out.println("File: " + filename);
            if (Files.isDirectory(filePath)) {
                System.out.println("This is a directory. Available directory modules:");
                modules.stream()
                        .filter(m -> m.getFileType().equals("directory"))
                        .forEach(m -> System.out.println("  - " + m.getDescription()));
            }
            return;
        }

        System.out.println("\n=== File Processor ===");
        System.out.println("File: " + filename);
        System.out.println("Type: " + getFileType(filePath));
        System.out.println("\nAvailable operations:");

        for (int i = 0; i < supportedModules.size(); i++) {
            System.out.println((i + 1) + ". " + supportedModules.get(i).getDescription());
        }
        System.out.println("0. Exit");

        Scanner scanner = new Scanner(System.in);
        System.out.print("\nChoose operation (0-" + supportedModules.size() + "): ");

        try {
            int choice = Integer.parseInt(scanner.nextLine());

            if (choice == 0) {
                System.out.println("Exiting...");
                return;
            }

            if (choice < 1 || choice > supportedModules.size()) {
                System.out.println("Invalid choice!");
                return;
            }

            FileModule selectedModule = supportedModules.get(choice - 1);
            System.out.println("\n=== Executing: " + selectedModule.getDescription() + " ===");
            System.out.println();

            String result = selectedModule.execute(filePath);
            System.out.println(result);

        } catch (NumberFormatException e) {
            System.out.println("Invalid input! Please enter a number.");
        } catch (Exception e) {
            System.out.println("Error during execution: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String getFileType(Path path) {
        if (Files.isDirectory(path)) {
            return "directory";
        }

        String fileName = path.toString().toLowerCase();
        if (fileName.endsWith(".txt")) return "text file";
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") || fileName.endsWith(".png")) return "image";
        if (fileName.endsWith(".mp3")) return "audio";
        return "unknown";
    }
}