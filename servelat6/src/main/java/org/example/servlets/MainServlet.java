package org.example.servlets;

import org.example.FileInfo;
import org.example.accounts.AccountService;
import org.example.accounts.UserProfile;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@WebServlet("/files")
public class MainServlet extends HttpServlet {

    private AccountService accountService;
    private static final String USERS_ROOT = "C:/filemanager/";

    @Override
    public void init() throws ServletException {
        accountService = (AccountService) getServletContext().getAttribute("accountService");
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        // Проверка авторизации
        String sessionId = req.getSession().getId();
        UserProfile currentUser = accountService.getUserBySessionId(sessionId);

        if (currentUser == null) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        String downloadPath = req.getParameter("download");

        if (downloadPath != null && !downloadPath.isEmpty()) {
            downloadFile(downloadPath, currentUser, resp);
        } else {
            showDirectory(req, resp, currentUser);
        }
    }

    private void downloadFile(String filePath, UserProfile user, HttpServletResponse resp)
            throws IOException {

        // Проверка, что файл внутри папки пользователя
        String userRoot = USERS_ROOT + user.getLogin();
        File requestedFile = new File(filePath);

        if (!requestedFile.getCanonicalPath().startsWith(new File(userRoot).getCanonicalPath())) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Доступ запрещен");
            return;
        }

        File file = new File(filePath);

        if (!file.exists() || !file.isFile()) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Файл не найден");
            return;
        }

        resp.setContentType("application/octet-stream");
        resp.setHeader("Content-Disposition", "attachment; filename=\"" + file.getName() + "\"");
        resp.setContentLengthLong(file.length());

        try (FileInputStream fis = new FileInputStream(file);
             OutputStream os = resp.getOutputStream()) {

            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
        }
    }

    private void showDirectory(HttpServletRequest req, HttpServletResponse resp, UserProfile user)
            throws ServletException, IOException {

        String userRoot = USERS_ROOT + user.getLogin();
        String path = req.getParameter("path");

        if (path == null || path.isEmpty()) {
            path = userRoot;
        }

        path = normalizePath(path);

        // Защита: проверка, что путь внутри папки пользователя
        File requestedPath = new File(path);
        File userRootFile = new File(userRoot);

        if (!requestedPath.getCanonicalPath().startsWith(userRootFile.getCanonicalPath())) {
            resp.sendRedirect(req.getContextPath() + "/files");
            return;
        }

        File currentDirectory = new File(path);

        if (!currentDirectory.exists() || !currentDirectory.isDirectory()) {
            resp.sendRedirect(req.getContextPath() + "/files");
            return;
        }

        File[] files = currentDirectory.listFiles();
        List<FileInfo> fileList = new ArrayList<>();

        if (files != null) {
            for (File file : files) {
                FileInfo info = new FileInfo();
                info.setName(file.getName());
                info.setPath(normalizePath(file.getAbsolutePath()));
                info.setDirectory(file.isDirectory());

                if (file.isFile()) {
                    info.setSize(formatFileSize(file.length()));
                } else {
                    info.setSize("");
                }

                info.setLastModified(formatDate(file.lastModified()));
                fileList.add(info);
            }
        }

        Collections.sort(fileList, new Comparator<FileInfo>() {
            @Override
            public int compare(FileInfo o1, FileInfo o2) {
                if (o1.isDirectory() && !o2.isDirectory()) {
                    return -1;
                } else if (!o1.isDirectory() && o2.isDirectory()) {
                    return 1;
                } else {
                    return o1.getName().compareToIgnoreCase(o2.getName());
                }
            }
        });

        req.setAttribute("currentPath", path);
        req.setAttribute("files", fileList);
        req.setAttribute("currentTime", formatTime(System.currentTimeMillis()));

        File parentDirectory = currentDirectory.getParentFile();
        String parentPath = parentDirectory != null ? normalizePath(parentDirectory.getAbsolutePath()) : null;

        // Проверка, что родительская папка не выходит за пределы userRoot
        if (parentPath != null) {
            File parentFile = new File(parentPath);
            if (!parentFile.getCanonicalPath().startsWith(userRootFile.getCanonicalPath())) {
                parentPath = null; // не показываем кнопку "Вверх"
            }
        }

        req.setAttribute("parentPath", parentPath);

        req.getRequestDispatcher("/WEB-INF/mypage.jsp").forward(req, resp);
    }

    private String formatDate(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("M.d.yy, h:mm:ss a");
        return sdf.format(new Date(timestamp));
    }

    private String formatTime(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
        return sdf.format(new Date(timestamp));
    }

    private String formatFileSize(long size) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.1f KB", size / 1024.0);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", size / (1024.0 * 1024));
        } else {
            return String.format("%.1f GB", size / (1024.0 * 1024 * 1024));
        }
    }

    private String normalizePath(String path) {
        if (path == null) return null;
        return path.replace('\\', '/');
    }

    private String encodePath(String path) {
        if (path == null) return null;
        try {
            String normalized = path.replace('\\', '/');
            return URLEncoder.encode(normalized, StandardCharsets.UTF_8.toString())
                    .replace("+", "%20");
        } catch (Exception e) {
            return path;
        }
    }
}
