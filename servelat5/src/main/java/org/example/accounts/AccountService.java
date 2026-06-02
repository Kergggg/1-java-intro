package org.example.accounts;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/// Сервис для хранения и управления пользователями и сессиями.
/// Работает как "база данных" в памяти сервера.
public class AccountService {
    //Хранилище всех зарегистрированных пользователей (ключ - логин)
    private final Map<String, UserProfile> loginToProfile;
    //Хранилище активных сессий тех, кто залогинен (ключ - JSESSIONID)
    private final Map<String, UserProfile> sessionIdToProfile;
    //Путь к родительской папке, где будут храниться домашние папки пользователей
    private static final String USERS_ROOT = "C:/filemanager/";

    public AccountService() {
        loginToProfile = new HashMap<>();
        sessionIdToProfile = new HashMap<>();
        new File(USERS_ROOT).mkdirs(); // создать папку, если её нет

        /// Инициализирует две пустые карты
        /// Создаёт рд папку
    }

    public void addNewUser(UserProfile userProfile) {
        loginToProfile.put(userProfile.getLogin(), userProfile);
        new File(USERS_ROOT + userProfile.getLogin()).mkdirs();

        /// Сохраняет пользователя в loginToProfile
        /// Создаёт домашнюю папку пользователя: C:/filemanager/[логин]/
    }

    public UserProfile getUserByLogin(String login) {
        return loginToProfile.get(login);

        /// Поиск пользователя по логину (используется при входе)
    }

    public UserProfile getUserBySessionId(String sessionId) {
        return sessionIdToProfile.get(sessionId);

        /// Проверка авторизации (по id сессии получаем профиль)
    }

    public void addSession(String sessionId, UserProfile userProfile) {
        sessionIdToProfile.put(sessionId, userProfile);

        /// Привязываем id сессии к профилю (вход пользователя)
    }

    public void deleteSession(String sessionId) {
        sessionIdToProfile.remove(sessionId);

        /// Удаляем связь сессии с профилем (выход пользователя)
    }

    public boolean loginExists(String login) {
        return loginToProfile.containsKey(login);

        /// Проверяем занят ли логин (при регистрации)
    }

    public String getUserRootFolder(String login) {
        return USERS_ROOT + login;

        /// Получаем путь к домашней папке пользователя
    }
}