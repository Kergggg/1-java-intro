package org.example;

public class FileInfo {
    private String name; //имя
    private String path; //путь
    private boolean isDirectory; //папка или файл?
    private String size; //размер (если файл)
    private String lastModified; //последнее изменение
    private String creationDate;  //дата создания

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public boolean isDirectory() { return isDirectory; }
    public void setDirectory(boolean directory) { isDirectory = directory; }

    public String getSize() { return size; }
    public void setSize(String size) { this.size = size; }

    public String getLastModified() { return lastModified; }
    public void setLastModified(String lastModified) { this.lastModified = lastModified; }

    public String getCreationDate() { return creationDate; }
    public void setCreationDate(String creationDate) { this.creationDate = creationDate; }
}