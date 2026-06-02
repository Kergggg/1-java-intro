package org.example.servlets;

import org.example.accounts.AccountService;
import org.example.accounts.UserProfile;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@WebServlet("/api/auth")
public class AuthServlet extends HttpServlet {
    private AccountService accountService;

    ///Метод init() вызывается один раз при первом обращении к сервлету (или при старте приложения)
    //Получает AccountService из контекста приложения
    @Override
    public void init() throws ServletException {
        accountService = (AccountService) getServletContext().getAttribute("accountService");
    }

    ///Метод doPost() - точка входа
    //Вызывается, когда приходит POST-запрос на /api/auth
    //POST-запросы берутся из JavaScript в index.html:
//    fetch(ctx + '/api/auth', {
//        method: 'POST',
//                body: 'action=login&email=' + email + '&pass=' + pass
//    })
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        //Читает параметр action из запроса. Возможные значения:
        //"login" - пользователь хочет войти
        //"register" (или любое другое) - пользователь хочет зарегистрироваться
        String action = req.getParameter("action");

        /// Вход в систему
        if ("login".equals(action)) {
            //Получаем парамтеры почты и пароля
            String email = req.getParameter("email");
            String pass = req.getParameter("pass");

            if (email == null || email.trim().isEmpty() || pass == null || pass.trim().isEmpty()) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                //Ошибка 400 - пустые данные в поле для почты/пароля
                //Устанавливается HTTP статус 400 Bad Request
                //JavaScript получит статус 400 и покажет сообщение "Ошибка входа"
                return;
            }

            //Поиск пользователя в базе с такой почтой
            UserProfile profile = accountService.getUserByEmail(email);

            //profile == null - пользователь не найден
            //!profile.getPass().equals(pass) - пароль неверный
            if (profile == null || !profile.getPass().equals(pass)) {
                resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                //Устанавливается статус 401 Unauthorized (не авторизован)
                //JS покажет "Неверный email или пароль"
                return;
            }

            accountService.addSession(req.getSession().getId(), profile);
            //Если переходим к этому этапу, то пользователь смог войти
            //req.getSession().getID() - получает ID текущей сессии (Tomcat создаст её автоматом)
            //accountService.addSession(...) - сохраняет связь "ID сессии -> профиль пользователя"
            //При каждом запросе Томкэт будет передавать этот sessionld, и сервер будет знать, какой пользователь залогинен

            //Отправляется статус 200 (то-бишь, всё хорошо и можно переходит в сам файловый менеджер на /files)
            resp.setStatus(HttpServletResponse.SC_OK);
        } else { /// Регистрация нового пользователя
            String email = req.getParameter("email");
            String pass = req.getParameter("pass");

            if (email == null || email.trim().isEmpty() || pass == null || pass.trim().isEmpty()) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                //400 - пустые поля почты/пароля
                return;
            }

            if (accountService.userExists(email)) {
                //Проверка, есть ли уже пользователь с такой почтой
                resp.setStatus(HttpServletResponse.SC_CONFLICT);
                //409 - пользователь уже существует с такой почтой
                return;
            }

            ///Создаём нового пользователя
            //Создаётся объект UserProfile с почтой и паролем
            //Сохраняется в emailToProfile
            UserProfile profile = new UserProfile(email, pass);
            accountService.addNewUser(profile);

            //Автоматический вход после регистрации
            //Сразу после регистрации создаёт сессию (пользователь автоматом логинется)
            accountService.addSession(req.getSession().getId(), profile);

            //Возвращем статус 200, если всё ок
            resp.setStatus(HttpServletResponse.SC_OK);
        }
    }
}