package org.example.servlets;

import org.example.accounts.AccountService;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/// Контроллер выхода из системы
/// Удаляет сессию пользователя, чтобы он больше не считался залогиненным
@WebServlet("/api/logout")
public class LogoutServlet extends HttpServlet {
    //Ссылка на сервис, который хранит пользователей и сессии
    private AccountService accountService;

    @Override
    public void init() throws ServletException {
        accountService = (AccountService) getServletContext().getAttribute("accountService");
        //Получаем accountService из контекстаприложения
    }

    /// Срабатывает, когда приходит POST-запрос на /api/logout
    /// Берётся из JS в mypage.jsp:
    //function logout() {
    //    fetch('${pageContext.request.contextPath}/api/logout', {
    //        method: 'POST'  // ← именно POST
    //    }).then(function(response) {
    //        if (response.status === 200) {
    //            window.location.href = '${pageContext.request.contextPath}/';
    //        }
    //    });
    //}
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        //Получаем id текущей сессии пользователя:
        //
        //Браузер пользователя                    Сервер Tomcat
        //┌─────────────────────┐                ┌─────────────────────┐
        //│ Cookie:             │   запрос       │                     │
        //│ JSESSIONID=abc123   │ ─────────────→ │ req.getSession()    │
        //└─────────────────────┘                │ возвращает сессию   │
        //                                       │ с ID = "abc123"     │
        //                                       └─────────────────────┘
        String sessionId = req.getSession().getId();
        //Удаляем связь между id сессии и профилем пользователя из карты sesiionIdToProfile
        accountService.deleteSession(sessionId);
        //Статус 200
        resp.setStatus(HttpServletResponse.SC_OK);
    }
}
