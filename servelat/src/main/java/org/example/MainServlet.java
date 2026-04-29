package org.example;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@WebServlet("/")
//Сервлет доступен по корневому URL (http://localhost:8080/servlet/)
public class MainServlet extends HttpServlet {
    //HttpServlet - абстрактный класс, который служит основой для создания HTTP-сервлетов для веб-сайтов.
    //Реализует интерфейсы Serializable, Servlet, ServletConfig
    //Подкласс HttpServlet должен переопределять хотя бы один из его методов:
    /// 1) doGet - обрабатывает HTTP GET-запросы от клиента (браузера), те используется для получения данных.
    /// Он вызывается каждый раз, когда пользователь переходит по ссылке,
    /// вводит URl в адресной строке или отправляет форму с методом GET.
    //2) doPost
    //3) doPut
    //4) doDelete
    //5) destroy
    //6) и тд

    //doGet - точка входа для GET-запросов
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
        //HttpServletRequest req - это запрос, который пришёл в сервлет.
        //HttpServletResponse resp - это ответ, который даст сервлет.
            throws ServletException, IOException {

        //Получаем параметр "download" из URL (например, ?download=C:/file.txt)
        String downloadPath = req.getParameter("download");

        //Определяем нужно ли скачивать файл
        if (downloadPath != null && !downloadPath.isEmpty()) { //Если в URL к файлу есть параметр download,то скачиваем
            downloadFile(downloadPath, resp);
        } else { //Иначе - это директория, открываем её
            showDirectory(req, resp);
        }
        //Например:
        //http://localhost:8080/servlet/ -> открываем папку
        //http://localhost:8080/servlet/?download=C:/file.txt -> скачиваем файл
    }

    //отправляет файл браузеру для скачивания
    private void downloadFile(String filePath, HttpServletResponse resp)
            throws IOException {
        File file = new File(filePath);

        /// 1. Устанавливает заголовок Content-Type, который говорит браузеру:
        // "Как интерпретировать данные, которые я сейчас отправлю".
        //application - это не текст, а прикладные данные
        //octet-stream - поток байтов
        //Проще говоря: "Я отправляю вам поток байтов, не пытайтесь его открыть как html, картинку или текст - просто сохраните как файл".
        //чтобы не определять тип каждого файла, просто говорим браузеру, что они бинарные (то0бишь тип не определён)
        resp.setContentType("application/octet-stream"); //Говорит браузеру, что это бинарные данные, которые нужно скачать
        /// 2. Устанавливает заголовок Content-Disposition, который говорит браузеру:
        //"как отобразить этот контент пользователю".
        //attachment - тип отображения (совершает принудительное скачивание, а вот inline открыл внутри браузера по возможности, если это, допустим, пдф)
        //filename=\"" + file.getName() + "\"" - имя файла, с которым он скачается (то-бишь, то, которое было изначально)
        resp.setHeader("Content-Disposition", "attachment; filename=\"" + file.getName() + "\""); //Заставит браузер открыть диалог сохранения
        /// 3. Устанавливает заголовок Content-Length, который говорит браузеру:
        //"Сколько байт я собираюсь отправить"
        //file.length() - возвращает размер файла в байтах, чтобы браузер мог отображать прогресс-бар
        resp.setContentLengthLong(file.length());

///        Шаг 1: Content-Type: application/octet-stream
//        "Это бинарные данные, тип не определен"
//                          ↓
///        Шаг 2: Content-Disposition: attachment; filename="photo.jpg"
//        "Принудительно скачай и сохрани как photo.jpg"
//                          ↓
///        Шаг 3: Content-Length: 524288
//        "Размер файла 512 KB, жди ровно столько байт"
//                          ↓
//       [дальше идет тело файла - сами байты]



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

    //Отображение содержимого директории с сортировкой (сначала др директории, а потом файлы)
    private void showDirectory(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        /// 1. Получение и проверка пути
        String path = req.getParameter("path"); //берём параметр path из URL

        if (path == null || path.isEmpty()) {
            path = System.getProperty("user.home"); //если нет параметра пути, то открываем домашнюю папку (это встроенный способ java узнать эту папку)
        }

        path = normalizePath(path);

        File currentDirectory = new File(path);

        /// 2. Чтение содержимого папки
        File[] files = currentDirectory.listFiles(); //Получаем массив всех файлов и папок
        List<FileInfo> fileList = new ArrayList<>();

        if (files != null) {
            for (File file : files) {
                FileInfo info = new FileInfo(); //Для каждого файла отдельно создаётся объект
                info.setName(file.getName()); //имя
                info.setPath(normalizePath(file.getAbsolutePath())); //полный путь
                info.setDirectory(file.isDirectory()); //проверка: папка или файл?

                if (file.isFile()) { //если это файл
                    info.setSize(formatFileSize(file.length())); //указываем размер для файлов
                } else { //иначе - директория
                    info.setSize(""); //у директорий нет размера
                }

                info.setLastModified(formatDate(file.lastModified())); //дата последнего изменения
                fileList.add(info);
                //Мы создаем список fileList, в котором будем хранить информацию о каждом файле/папке.
                //После того как мы создали объект info и заполнили его данными об одном файле, мы добавляем его в список.
                //В конце у нас будет список из всех файлов и папок текущей директории.
            }
        }

        /// 3. Сортировка (сначала папки, потом файлы)
        Collections.sort(fileList, new Comparator<FileInfo>() {
            //компаратор нужен, чтобы правильно сравнивать два объекта, чтобы определить какой из них должен быть раньше в отсортированном списке

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

        /// 4. Передача данных в JSP
        req.setAttribute("currentPath", path); //текущий путь
        req.setAttribute("files", fileList); //список файлов
        req.setAttribute("currentTime", formatTime(System.currentTimeMillis())); //текущее время

        //путь к родительской папке (дл кнопки "вверх")
        File parentDirectory = currentDirectory.getParentFile(); //получаем родительскую папку (на уровень выше)
        String parentPath = parentDirectory != null ?
                normalizePath(parentDirectory.getAbsolutePath()) : null; //если род папка существует, то берём её путь, иначе null
        req.setAttribute("parentPath", parentPath); //передаём в JSP для кнопки "Вверх"

        //Отправляем на JSP страницу
        req.getRequestDispatcher("/WEB-INF/mypage.jsp").forward(req, resp);
        //forward передаёт управление от сервлета к JSP странице, вместе с данными (то-бишь, атрибутами)
        //Альтернативный sendRedirect() просто перенаправил бы браузер на другой URl.
        //forward() нужен, тк мы уже подготовили данные (список файлов) для отображения через JSP. URL остаётся тот же
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
            return size + " B"; //байты
        } else if (size < 1024 * 1024) {
            return String.format("%.1f KB", size / 1024.0); //килобайты
        } else if (size < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", size / (1024.0 * 1024)); //мегабайты
        } else {
            return String.format("%.1f GB", size / (1024.0 * 1024 * 1024)); //гигабайты
        }
    }

    private String normalizePath(String path) {
        if (path == null) return null;
        return path.replace('\\', '/'); // C:\Users → C:/Users
        //Виндовс использует \, а url /
    }

    private String encodePath(String path) {
        if (path == null) return null;
        try {
            String normalized = path.replace('\\', '/');
            return URLEncoder.encode(normalized, StandardCharsets.UTF_8.toString())
                    .replace("+", "%20"); //замена пробелов
                    //C:/Program Files → C%3A%2FProgram%20Files
        } catch (Exception e) {
            return path;
        }
    }
}