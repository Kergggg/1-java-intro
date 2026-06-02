package org.example.module.image;

import org.example.module.FileModule;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

@Component
public class ImageColorCountModule implements FileModule {

    @Override
    public boolean supports(Path filePath) {
        String name = filePath.toString().toLowerCase();
        return (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png"))
                && Files.isRegularFile(filePath);
    }

    @Override
    public String getDescription() {
        return "Count unique colors in image (may take time for large images)";
    }

    @Override
    public String execute(Path filePath) {
        try {
            BufferedImage image = ImageIO.read(filePath.toFile());
            if (image == null) {
                return "Cannot read image file";
            }

            Set<Integer> uniqueColors = new HashSet<>();
            int width = image.getWidth();
            int height = image.getHeight();

            int step = Math.max(1, (width * height) / 100000);

            for (int y = 0; y < height; y += step) {
                for (int x = 0; x < width; x += step) {
                    uniqueColors.add(image.getRGB(x, y));
                }
            }

            double estimatedColors = uniqueColors.size();
            double totalPixels = (double) width * height;
            double sampledPixels = totalPixels / (step * step);

            return String.format(
                    "Unique colors (sampled): ~%.0f\nTotal pixels: %d\nColor variety: %.2f%%",
                    estimatedColors, (int) totalPixels, (estimatedColors / totalPixels) * 100
            );
        } catch (IOException e) {
            return "Error reading image: " + e.getMessage();
        }
    }

    @Override
    public String getFileType() {
        return "image";
    }
}