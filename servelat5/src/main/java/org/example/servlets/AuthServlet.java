package org.example.servlets;

import org.example.accounts.AccountService;
import org.example.accounts.UserProfile;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/// Привязывает сервлет к url-адресу /api/auth
@WebServlet("/api/auth")
public class AuthServlet extends HttpServlet {
    //Ссылка на сервис, который хранит пользователей и сессии
    private AccountService accountService;

    @Override
    /// Метод init() вызывается один раз при первом обращении к сервлету или при старте приложения
    public void init() throws ServletException {
        //Получаем accountService из контекста приложения
        accountService = (AccountService) getServletContext().getAttribute("accountService");
    }

    @Override
    /// Точка входа
    /// Вызывается, когда приходит POST-запрос на url сервлета
    /// POST-запросы берутся из JavaScript в index.html:
    // fetch(ctx + '/api/auth', {
    //     method: 'POST',  // ← именно POST
    //     body: 'action=login&login=' + login + '&pass=' + pass
    // })
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        //Читаем параметр action из тела запроса
        //Если там "login", то пользователь хочет войти
        //Если там "register", то пользователь хочет зарегистрироваться
        String action = req.getParameter("action");
        //Пример запроса в коде:
        //POST /api/auth
        //Body: action=login&login=alice&pass=123
        //       ↑
        //    action = "login"

        //Если action = "login"
        if ("login".equals(action)) {
            //Получаем параметры логина и пароля
            String login = req.getParameter("login");
            String pass = req.getParameter("pass");
            //Берутся из index.html:
            //<input type="text" id="loginLogin" placeholder="Логин">
            //<input type="password" id="loginPassword" placeholder="Пароль">
            //JavaScript отправляет:
            //body: 'action=login&login=' + encodeURIComponent(login) + '&pass=' + encodeURIComponent(pass)

            //Проверка пустые ли логин и пароль
            if (login == null || login.trim().isEmpty() || pass == null || pass.trim().isEmpty()) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                //Если проверка не проходит, то передаётся ошибка 400 с надписью "Ошибка входа"
                return;
            }

            //Ищем пользователя с таким логином
            //Если из метода getUserByLogin возвращается null, то пользователь не найден
            //Иначе - найден
            UserProfile profile = accountService.getUserByLogin(login);

            //Проверка найден ли пользователь и правильный ли пароль
            if (profile == null || !profile.getPass().equals(pass)) {
                resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                //Передаётся ошибка 401 с сообщением "Неверный логин или пароль"
                return;
            }

            //Получаем или создаём id сессии
            String sessionId = req.getSession().getId();
            //Сохраняем связь "id сессии -> профиль пользователя"
            accountService.addSession(sessionId, profile);
            //Теперь при каждом запросе Tomcat будет передавать этот sessinId
            //через cookie, и сервер будет знать, какой пользователь залогинен.

            resp.setStatus(HttpServletResponse.SC_OK);
            //Отправляем статус 200, тк всё окей

            /// В JavaScript:
            //if (r.status === 200) {
            //    window.location.href = ctx + '/files';  // переход в файловый менеджер
            //}
        } else { //если action != "login"
            //Получаем параметры: логин, почта, пароль
            String login = req.getParameter("login");
            String email = req.getParameter("email");
            String pass = req.getParameter("pass");
            //JS отправляет:
            //body: 'action=register&login=' + encodeURIComponent(login) +
            //      '&email=' + encodeURIComponent(email) + '&pass=' + encodeURIComponent(pass)

            if (login == null || login.trim().isEmpty() ||
                    email == null || email.trim().isEmpty() ||
                    pass == null || pass.trim().isEmpty()) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                //Статус 400
                return;
            }

            if (accountService.loginExists(login)) {
                resp.setStatus(HttpServletResponse.SC_CONFLICT);
                //Статус 409 - пользователь уже существует
                return;
            }

            //Создаём объект UserProfile с логином, почтой и паролем
            //Сохраняем в loginToProfile
            //Создаём домашнюю папку пользователя на диске
            UserProfile profile = new UserProfile(login, email, pass);
            accountService.addNewUser(profile);

            //Сразу после регистрации создаём сессию (пользователь становится залогиненным автоматически)
            //Т.е. отдельно входить не нужно после регистрации
            String sessionId = req.getSession().getId();
            accountService.addSession(sessionId, profile);
            resp.setStatus(HttpServletResponse.SC_OK);
            //Статус 200
        }
    }
}

/// ┌─────────────────────────────────────────────────────────────────────────┐
/// │                           ПОЛЬЗОВАТЕЛЬ                                   │
/// └─────────────────────────────────────────────────────────────────────────┘
///                                     │
///                     ┌───────────────┴───────────────┐
///                     │                               │
///                     ▼                               ▼
///             Вход (login)                     Регистрация (register)
///                     │                               │
///                     ▼                               ▼
///         POST /api/auth?action=login       POST /api/auth?action=register
///         Body: login=alice&pass=123        Body: login=bob&email=bob@mail.ru&pass=456
///                     │                               │
///                     ▼                               ▼
/// ┌─────────────────────────────────────────────────────────────────────────┐
/// │                           AuthServlet.doPost()                           │
/// └─────────────────────────────────────────────────────────────────────────┘
///                     │                               │
///                     ▼                               ▼
///         1. login = "alice"                 1. login = "bob"
///            pass = "123"                        email = "bob@mail.ru"
///                                                 pass = "456"
///                     │                               │
///                     ▼                               ▼
///         2. Проверка на пустоту              2. Проверка на пустоту
///                     │                               │
///                     ▼                               ▼
///         3. getUserByLogin("alice")          3. loginExists("bob") → false
///            → profile = UserProfile              → можно регистрировать
///                     │                               │
///                     ▼                               ▼
///         4. Проверка пароля                  4. new UserProfile(...)
///            "123".equals("123") → true           addNewUser(profile)
///                     │                          → создана папка
///                     ▼                          C:/filemanager/bob/
///         5. addSession(sessionId, profile)
///            → пользователь залогинен          5. addSession(sessionId, profile)
///                                               → пользователь залогинен
///                     │                               │
///                     ▼                               ▼
///         6. Статус 200 OK                    6. Статус 200 OK
///                     │                               │
///                     └───────────────┬───────────────┘
///                                     ▼
///                          JavaScript получает 200 OK
///                                     │
///                                     ▼
///                     window.location.href = '/servlet/files'
///                                     │
///                                     ▼
///                     MainServlet (файловый менеджер)