package org.example.module.mp3;

import org.example.module.FileModule;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.mp3.MP3File;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class Mp3TitleModule implements FileModule {

    @Override
    public boolean supports(Path filePath) {
        return filePath.toString().toLowerCase().endsWith(".mp3") && Files.isRegularFile(filePath);
    }

    @Override
    public String getDescription() {
        return "Get track title from MP3 metadata (ID3 tags)";
    }

    @Override
    public String execute(Path filePath) {
        try {
            MP3File mp3File = (MP3File) AudioFileIO.read(filePath.toFile());
            Tag tag = mp3File.getTag();

            if (tag == null) {
                return "No metadata found in this MP3 file";
            }

            String title = tag.getFirst(FieldKey.TITLE);
            String artist = tag.getFirst(FieldKey.ARTIST);
            String album = tag.getFirst(FieldKey.ALBUM);
            String year = tag.getFirst(FieldKey.YEAR);

            StringBuilder result = new StringBuilder("MP3 Metadata:\n");
            if (!title.isEmpty()) result.append("  Title: ").append(title).append("\n");
            if (!artist.isEmpty()) result.append("  Artist: ").append(artist).append("\n");
            if (!album.isEmpty()) result.append("  Album: ").append(album).append("\n");
            if (!year.isEmpty()) result.append("  Year: ").append(year).append("\n");

            if (result.length() == 13) {
                return "No meaningful metadata found";
            }

            return result.toString();
        } catch (Exception e) {
            return "Error reading MP3 metadata: " + e.getMessage();
        }
    }

    @Override
    public String getFileType() {
        return "audio";
    }
}