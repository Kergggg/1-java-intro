package org.example.module.mp3;

import org.example.module.FileModule;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.AudioHeader;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class Mp3BitrateModule implements FileModule {

    @Override
    public boolean supports(Path filePath) {
        return filePath.toString().toLowerCase().endsWith(".mp3") && Files.isRegularFile(filePath);
    }

    @Override
    public String getDescription() {
        return "Get MP3 bitrate and audio properties (kHz, mode)";
    }

    @Override
    public String execute(Path filePath) {
        try {
            AudioFile audioFile = AudioFileIO.read(filePath.toFile());
            AudioHeader audioHeader = audioFile.getAudioHeader();

            String bitrate = audioHeader.getBitRate();
            String sampleRate = audioHeader.getSampleRate();
            String encodingType = audioHeader.getFormat();

            long fileSizeBytes = Files.size(filePath);
            double fileSizeKB = fileSizeBytes / 1024.0;
            double fileSizeMB = fileSizeKB / 1024.0;

            return String.format(
                    "Audio Properties:\n" +
                            "  Bitrate: %s\n" +
                            "  Sample rate: %s Hz\n" +
                            "  Encoding: %s\n" +
                            "  File size: %.2f KB (%.2f MB)",
                    bitrate, sampleRate, encodingType, fileSizeKB, fileSizeMB
            );
        } catch (Exception e) {
            return "Error reading MP3 properties: " + e.getMessage();
        }
    }

    @Override
    public String getFileType() {
        return "audio";
    }
}