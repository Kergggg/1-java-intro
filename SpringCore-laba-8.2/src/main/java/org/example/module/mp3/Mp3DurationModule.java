package org.example.module.mp3;

import org.example.module.FileModule;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.mp3.MP3File;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class Mp3DurationModule implements FileModule {

    @Override
    public boolean supports(Path filePath) {
        return filePath.toString().toLowerCase().endsWith(".mp3") && Files.isRegularFile(filePath);
    }

    @Override
    public String getDescription() {
        return "Get MP3 duration in seconds and readable format";
    }

    @Override
    public String execute(Path filePath) {
        try {
            MP3File mp3File = (MP3File) AudioFileIO.read(filePath.toFile());
            int durationSeconds = mp3File.getAudioHeader().getTrackLength();

            int hours = durationSeconds / 3600;
            int minutes = (durationSeconds % 3600) / 60;
            int seconds = durationSeconds % 60;

            String readableTime;
            if (hours > 0) {
                readableTime = String.format("%d:%02d:%02d", hours, minutes, seconds);
            } else {
                readableTime = String.format("%d:%02d", minutes, seconds);
            }

            return String.format("Duration: %d seconds (%s)", durationSeconds, readableTime);
        } catch (Exception e) {
            return "Error reading MP3 duration: " + e.getMessage();
        }
    }

    @Override
    public String getFileType() {
        return "audio";
    }
}