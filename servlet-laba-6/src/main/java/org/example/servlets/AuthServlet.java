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

    @Override
    public void init() throws ServletException {
        accountService = (AccountService) getServletContext().getAttribute("accountService");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String action = req.getParameter("action");

        if ("login".equals(action)) {
            String login = req.getParameter("login");
            String pass = req.getParameter("pass");

            if (login == null || login.trim().isEmpty() || pass == null || pass.trim().isEmpty()) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                return;
            }

            UserProfile profile = accountService.getUserByLogin(login);

            if (profile == null || !profile.getPass().equals(pass)) {
                resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }

            String sessionId = req.getSession().getId();
            accountService.addSession(sessionId, profile);
            resp.setStatus(HttpServletResponse.SC_OK);
        } else {
            String login = req.getParameter("login");
            String email = req.getParameter("email");
            String pass = req.getParameter("pass");

            if (login == null || login.trim().isEmpty() ||
                    email == null || email.trim().isEmpty() ||
                    pass == null || pass.trim().isEmpty()) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                return;
            }

            if (accountService.loginExists(login)) {
                resp.setStatus(HttpServletResponse.SC_CONFLICT);
                return;
            }

            UserProfile profile = new UserProfile(login, email, pass);
            accountService.addNewUser(profile);

            String sessionId = req.getSession().getId();
            accountService.addSession(sessionId, profile);
            resp.setStatus(HttpServletResponse.SC_OK);
        }
    }
}
