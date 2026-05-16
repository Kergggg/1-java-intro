package org.example.module.image;

import org.example.module.FileModule;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class ImageSizeModule implements FileModule {

    @Override
    public boolean supports(Path filePath) {
        String name = filePath.toString().toLowerCase();
        return (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png"))
                && Files.isRegularFile(filePath);
    }

    @Override
    public String getDescription() {
        return "Get image dimensions (width x height)";
    }

    @Override
    public String execute(Path filePath) {
        try {
            BufferedImage image = ImageIO.read(filePath.toFile());
            if (image == null) {
                return "Cannot read image file (unsupported format or corrupted)";
            }
            return String.format("Image dimensions: %d x %d pixels", image.getWidth(), image.getHeight());
        } catch (IOException e) {
            return "Error reading image: " + e.getMessage();
        }
    }

    @Override
    public String getFileType() {
        return "image";
    }
}