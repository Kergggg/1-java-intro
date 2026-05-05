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
        sce.getServletContext().setAttribute("accountService", accountService);
    }

    /// Общий экземпляр accountService нужен для того, чтобы у всех сервлетов он был общим.
    /// Иначе каждый создавал бы его отдельно и данные не были бы общими.
}
