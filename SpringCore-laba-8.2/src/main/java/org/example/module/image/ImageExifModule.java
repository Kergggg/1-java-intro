package org.example.module.image;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.Tag;
import org.example.module.FileModule;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class ImageExifModule implements FileModule {

    @Override
    public boolean supports(Path filePath) {
        String name = filePath.toString().toLowerCase();
        return (name.endsWith(".jpg") || name.endsWith(".jpeg")) && Files.isRegularFile(filePath);
    }

    @Override
    public String getDescription() {
        return "Extract EXIF metadata from image (camera, date, GPS, etc.)";
    }

    @Override
    public String execute(Path filePath) {
        try {
            File imageFile = filePath.toFile();
            Metadata metadata = ImageMetadataReader.readMetadata(imageFile);

            StringBuilder result = new StringBuilder("EXIF Information:\n");

            for (Directory directory : metadata.getDirectories()) {
                result.append("\n[").append(directory.getName()).append("]\n");
                for (Tag tag : directory.getTags()) {
                    result.append(String.format("  %s: %s\n", tag.getTagName(), tag.getDescription()));
                }
                if (directory.hasErrors()) {
                    for (String error : directory.getErrors()) {
                        result.append("  ERROR: ").append(error).append("\n");
                    }
                }
            }

            return result.toString();
        } catch (Exception e) {
            return "Error reading EXIF data: " + e.getMessage();
        }
    }

    @Override
    public String getFileType() {
        return "image";
    }
}