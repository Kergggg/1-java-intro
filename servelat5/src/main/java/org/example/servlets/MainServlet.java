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
    //Ссылка на сервис для проверки авторизации:
    private AccountService accountService;
    //Путь к рд папке пользователя:
    private static final String USERS_ROOT = "C:/filemanager/";

    @Override
    public void init() throws ServletException {
        //Получаем из контекста приложения
        accountService = (AccountService) getServletContext().getAttribute("accountService");
    }

    /// Точка входа
    /// Вызывается при каждом GET-запросе к /files
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        /// Проверка авторизации
        //Получаем ID сессии из cookie браузера
        String sessionId = req.getSession().getId();
        //Ищем этот ID в sessionIdToProfile
        UserProfile currentUser = accountService.getUserBySessionId(sessionId);
        //Если ID не найден, то статус 401
        if (currentUser == null) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        //Чтобы понять, нужно ли скачивать файл, ищем параметр "download"
        String downloadPath = req.getParameter("download");
        //Если параметр найден, то вызываем метод для скачивания
        if (downloadPath != null && !downloadPath.isEmpty()) {
            downloadFile(downloadPath, currentUser, resp);
        } else { //иначе вызываем метод открытия директории
            showDirectory(req, resp, currentUser);
        }
    }

    /// Скачивание файла
    private void downloadFile(String filePath, UserProfile user, HttpServletResponse resp)
            throws IOException {

        // Проверка, что файл внутри папки пользователя,
        //чтобы пользователь не мог скачать файлы вне своей домашней папки.
        String userRoot = USERS_ROOT + user.getLogin();
        File requestedFile = new File(filePath);
        if (!requestedFile.getCanonicalPath().startsWith(new File(userRoot).getCanonicalPath())) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Доступ запрещен");
            return;
        }
        /// Например:
        //userRoot = "C:/filemanager/alice"
        //requestedFile = "C:/filemanager/alice/photo.jpg"
        // getCanonicalPath() = "C:/filemanager/alice/photo.jpg"
        // startsWith("C:/filemanager/alice") → true ✅
        //
        //requestedFile = "C:/Windows/system.ini"
        // startsWith("C:/filemanager/alice") → false ❌
        // → 403 Forbidden

        File file = new File(filePath);
        //Проверка существования файла
        if (!file.exists() || !file.isFile()) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Файл не найден");
            return;
        }

        //application/octet-stream - Скачивает как файл
        //attachment - Принудительное скачивание
        //Content-Disposition - файл не откроется в браузере
        //filename="photo.jpg" - Имя файла, которое увидит пользователь
        //resp.setContentLengthLong(file.length()); -
        // 1) Браузер знает, сколько всего байт нужно получить
        // 2) Показывает прогресс-бар загрузки
        // 3) Может проверить целостность файла
        resp.setContentType("application/octet-stream");
        resp.setHeader("Content-Disposition", "attachment; filename=\"" + file.getName() + "\"");
        resp.setContentLengthLong(file.length());

        //Читаем файл и отправляем его браузеру для скачивания
        /// FileInputStream - поток для постепенного чтения файла
        /// OutputStream - поток для записи в браузер
        /// try-with-resources - конструкция, которая автоматически закрывает ресурсы (потоки в данном случае)
        //без try с ресурсами нужно было бы в ручную закрывать потоки
        try (FileInputStream fis = new FileInputStream(file);
             OutputStream os = resp.getOutputStream()) {
            //fis - поток для чтения файла с диска
            //os - поток для записи в браузер

            byte[] buffer = new byte[8192]; //8 КБ оптимальный размер для чтения файла частями
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) { //читаем файл с жёсткого диска и отправляем в браузер кусками
                os.write(buffer, 0, bytesRead);
            }
        }
    }

    ///Отображение содержимого директории с сортировкой (сначала др директории, а потом файлы)
    private void showDirectory(HttpServletRequest req, HttpServletResponse resp, UserProfile user)
            throws ServletException, IOException {
        //Определяем корневую папку пользователя
        String userRoot = USERS_ROOT + user.getLogin();
        //Пример:
        //USERS_ROOT = "C:/filemanager/"
        //user.getLogin() = "alice"
        //userRoot = "C:/filemanager/alice"
        //Папка создаётся в AccountService.addNewUser() при регистрации

        //Получаем путь из параметра запроса
        String path = req.getParameter("path");

        //Если пользователь не указал конкретный путь, то показываем его домашнюю папку
        if (path == null || path.isEmpty()) {
            path = userRoot;
        }

        //Нормализация пути (заменяем обратные слеши на обычные)
        path = normalizePath(path);

        // Защита от выхода из домашней папки: проверка, что путь внутри папки пользователя
        //Получаем путь к файлу/папке и путь к домашней папке пользователя
        File requestedPath = new File(path);
        File userRootFile = new File(userRoot);

        /// ЗАЩИТА ОТ АТАКИ Path Traversals
        ///Метод getCanonicalPath() преобразует в каноническую (нормализованную) форму,
        //удаляя .. и . и разрешая символьные ссылки
        ///Метод startsWith() проверяется, начинается ли путь с другого:
        //String requested = "C:/filemanager/alice/Documents";
        //String userRoot = "C:/filemanager/alice";
        //requested.startsWith(userRoot) → true ✅
        //
        //String requested = "C:/Windows";
        //requested.startsWith(userRoot) → false ❌
        if (!requestedPath.getCanonicalPath().startsWith(userRootFile.getCanonicalPath())) {
            //редирект на домашнюю папку
            resp.sendRedirect(req.getContextPath() + "/files");
            return;
        }

        File currentDirectory = new File(path);

        //Проверка: существует ли папка? точно ли это папка, а не файл?
        if (!currentDirectory.exists() || !currentDirectory.isDirectory()) {
            //редирект на домашнюю страницу
            resp.sendRedirect(req.getContextPath() + "/files");
            return;
        }

        //listFiles(): возвращает массив объектов File для всех файлов и папок внутри директории.
        // Если папка пуста, возвращает пустой массив.
        //Если нет прав доступа - null.
        File[] files = currentDirectory.listFiles();
        List<FileInfo> fileList = new ArrayList<>();

        if (files != null) {
            for (File file : files) {
                //Создаём объект FileInfo для каждого элемента массива
                FileInfo info = new FileInfo();
                //Получаем имя
                info.setName(file.getName());
                //получаем путь
                info.setPath(normalizePath(file.getAbsolutePath()));
                //true - папка, false - файл
                info.setDirectory(file.isDirectory());

                //Если файл, то указываем размер
                if (file.isFile()) {
                    info.setSize(formatFileSize(file.length()));
                } else { //Если папка, то не указываем
                    info.setSize("");
                }

                //Дата изменения
                info.setLastModified(formatDate(file.lastModified()));
                //Добавляем в список
                fileList.add(info);
            }
        }

        /// Сортировка (сначала папки, потом файлы)
        Collections.sort(fileList, new Comparator<FileInfo>() {
            //Компаратор нужен, чтобы правильно сравнивать два объекта, чтобы определить,
            //какой из них должен быть раньше в отсортированном списке

            @Override
            public int compare(FileInfo o1, FileInfo o2) {
                if (o1.isDirectory() && !o2.isDirectory()) { //если о1 папка, а о2 файл, то -1 (значит о1 будет раньше)
                    return -1; //о1 будет перед о2, тк оно меньше
                } else if (!o1.isDirectory() && o2.isDirectory()) { //если о1 файл, а о2 папка, то 1 (значит о2 будет раньше)
                    return 1; //о1 будет после о2, тк оно больше
                } else { //если оба объекта одинакового типа, то возвращается сортировка по алфавиту
                    return o1.getName().compareToIgnoreCase(o2.getName()); //сортировка по алфавиту
                }
            }
        });

        /// Передача данных в JSP
        req.setAttribute("currentPath", path); //текущий путь
        req.setAttribute("files", fileList); //список файлов
        req.setAttribute("currentTime", formatTime(System.currentTimeMillis())); //текущее время

        //Метод getParentFile возвращает объект File, представляющий рд папку текущей папки.
        //Если текущая папка уже рд, то вернётся null.
        File parentDirectory = currentDirectory.getParentFile();
        //Если текущая папка не рд, то нормализуем путь, иначе оставляем null.
        String parentPath = parentDirectory != null ? normalizePath(parentDirectory.getAbsolutePath()) : null;

        /// Проверка, что родительская папка внутри домашней
        //Проверяем, что рд папка существует (если она null с прошлых строк, то мы уже в корне)
        if (parentPath != null) {
            File parentFile = new File(parentPath);
            //Проверяем, совпадают ли пути к корню и текущей папке
            if (!parentFile.getCanonicalPath().startsWith(userRootFile.getCanonicalPath())) {
                parentPath = null;
                //Если совпадают, то не показываем кнопку "Вверх"
            }
        }

        req.setAttribute("parentPath", parentPath);

        //Отправляем на JSP страницу
        req.getRequestDispatcher("/WEB-INF/mypage.jsp").forward(req, resp);
        //forward передаёт управление от сервлета к JSP странице, вместе с данными (то-бишь, атрибутами)
        //Альтернативный sendRedirect() просто перенаправил бы браузер на другой URL.
        //forward() нужен, тк мы уже подготовили данные (список файлов) для отображения через JSP.
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