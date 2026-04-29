package org.example;

import org.example.accounts.AccountService;
import org.example.accounts.UserProfile;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

@WebListener
public class AppContextListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        // Создаем экземпляр AccountService один раз при старте приложения
        AccountService accountService = new AccountService();

        // Сохраняем в контексте сервлета (доступно всем сервлетам)
        ///Потому что данные пользователей и сессий должны быть общими для всех сервлетов
        ///и всех пользователей. Если создать несколько экземпляров,
        ///пользователь, зарегистрировавшийся через AuthServlet, не сможет войти в MainServlet —
        ///они будут работать с разными хранилищами.
        sce.getServletContext().setAttribute("accountService", accountService);
    }
}