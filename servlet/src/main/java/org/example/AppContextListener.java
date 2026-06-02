package org.example;

import org.example.accounts.AccountService;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

@WebListener
public class AppContextListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        AccountService accountService = new AccountService();
        sce.getServletContext().setAttribute("accountService", accountService);
    }
}