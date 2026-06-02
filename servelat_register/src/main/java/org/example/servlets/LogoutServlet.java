package org.example.servlets;

import org.example.accounts.AccountService;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

//Сервлет для выхода из системы. Удаляет сессию пользователя, чтобы он больше не был залогиненный
@WebServlet("/api/logout")
//Пользователь нажимает "Выйти" -> JS отправляет POST -> Томкэт вызывает этот сервлет
public class LogoutServlet extends HttpServlet {
    private AccountService accountService;

    /// init() - вызывается один раз
    //Забирает AccountService из контекста приложения
    @Override
    public void init() throws ServletException {
        accountService = (AccountService) getServletContext().getAttribute("accountService");
    }

    /// doPost() - вызывается, когда приходит POST-запрос на /api/logout из JS в mypage.jsp:
    //function logout() {
    //    fetch('/servlet/api/logout', {
    //        method: 'POST'  // ← именно POST
    //    }).then(function(response) {
    //        if (response.status === 200) {
    //            window.location.href = '/servlet/';
    //        }
    //    });
    //}
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        //Получаем айди сессии
        //req.getSession не создаёт новую сессию, а получает существующую (по cookie из запроса)
        String sessionId = req.getSession().getId();

        //Удаление сессии
        //Удаляется связь между айди сессии и профилем пользователя из карты
        accountService.deleteSession(sessionId);

        //Если всё ок, то статус 200 и переходим на страницу входа
        resp.setStatus(HttpServletResponse.SC_OK);
    }
}

///COOKIE - маленький фрагмент данных, который сервер отправляет браузеру,
///а браузер сохраняет его на компьютере пользователя и отправляет обратно
///при каждом следующем запросе.
//Куки не создаются явно, а создаются автоматически Томкэт
//При вызове этого кода:
//String sessionId = req.getSession().getId();
//1. Tomcat проверяет: есть ли в запросе cookie с именем JSESSIONID?
//                    ↓
//         ЕСТЬ                      НЕТ
//          ↓                         ↓
//   Использует существующий    Создает НОВУЮ сессию
//          ↓                         ↓
//   Возвращает ID сессии        Создает НОВОЕ cookie
//                                    ↓
//                              Отправляет браузеру
//                              Set-Cookie: JSESSIONID=abc123

//Куки хранятся на компьютере пользователя.
//F12 -> Application -> Cookies -> localhost:8080

//В куки в данном случае хранится только JSESSIONID (например: JSESSIONID = ABC123DEF456789).
//Это уникальный идентификатор, автоматически генерируемый Томкэт для каждой новой сессии.

///Айди сессиий хранятся в куки, а сами сессии на сервере в HashMap.

///АТАКИ
//Если в URL написать:
//http://localhost:8080/servlet/files?path=../../../etc/passwd
//http://localhost:8080/servlet/files?path=.../.../.../Windows
//То это может быть уязвимостью. Злоумышленник может читать любые файлы на сервере