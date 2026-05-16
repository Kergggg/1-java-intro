package org.example;

import org.example.service.ModuleService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;

@SpringBootApplication
public class App {

    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Usage: java -jar file-processor.jar <filename>");
            System.out.println("Example: java -jar file-processor.jar document.txt");
            System.exit(1);
        }

        String filename = args[0];
        ApplicationContext context = SpringApplication.run(App.class, args);

        ModuleService moduleService = context.getBean(ModuleService.class);
        moduleService.processFile(filename);

        System.exit(0);
    }
}