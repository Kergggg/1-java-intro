package org.example.db;

import org.hibernate.Session;
import org.hibernate.query.Query;

import java.util.HashMap;
import java.util.Map;

/// HIBERNATE позволяет работать с бд через Java-объекты, а не через SQL-запросы
/// ИЗМЕНЕНИЯ:
// 1) DatabaseConnection - связь с бд
// 2) UserEntity - jpa-сущность (описывает таблицу users) через аннотации
// 3) HibernateUtil - управляет SessionFactory (подключение к бд)
// 4) hibernate.cfg.xml - конфигурация Hibernate (url, логин, пароль, диалект)

// SessionFactory - фабрика по производству Session-объектов.
// Можно представить как пул соединений с бд + кэш + генератор запросов
//SF:
// 1) каждый раз, когда нужно работать с бд - берём SF из фабрики
// 2) url, логин, пароль, диалект, настройки кэширования
// 3) держит несколько открытых подключений к бд (повторное использование)
// 4) знает структуру таблиц, связи между сущностями
// 5) один раз при старте анализирует сущности и готовит запросы

// SF нужно для создания session и хранения конфигурации

/// TRANSACTION - это группа операций с бд, которые выполняются как единое целое,
/// либо все успешно применяются, либо не применяются.

/// После FROM можно написать имя jpa-сущности

/// Чтение одного пользователя по логину:
//Query<UserEntity> query = session.createQuery(
//    "FROM UserEntity WHERE login = :login",
//    UserEntity.class
//);
//query.setParameter("login", "alice");
//UserEntity user = query.uniqueResult();

public class DatabaseConnection {

    /// Сохранение нового пользователя
    public static void saveUser(String login, String email, String password) {
        /// Создаём новый Java-объект типа UserEntity с переданными логином, email и паролем
        //На этом этапе объект существует только в памяти Java, бд о нём ничего не знает
        //[Java-память]
        //┌─────────────────────┐
        //│ UserEntity          │
        //│ • login = "alice"   │
        //│ • email = "a@b.c"   │
        //│ • password = "123"  │
        //│ • id = null         │ ← id ещё нет, он будет сгенерирован БД
        //└─────────────────────┘
        UserEntity user = new UserEntity(login, email, password);
        /// Создаём ОДНУ фабрику сессий, открываем новую сессию (рабочее соединение с бд)
        // Session - это как подключение к бд + рабочий стол для операций
        //SessionFactory (завод)
        //     │
        //     │ openSession()
        //     ↓
        //Session (рабочая сессия)
        //     │
        //     │ Connection к PostgreSQL
        //     ↓
        //База данных
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            /// Hibernate отправляет в PostgreSQL команду BEGIN (или START TRANSACTION).
            // Транзакция группирует несколько операция в одну атомарную единицу.
            // Если что-то идёт не так - изменения не применяются.
            /// Без beginTransaction() нельзя выполнить save() или update().
            session.beginTransaction();

            /// Подготавливаем запрос в sql: INSERT INTO users (login, email, password) VALUES (?, ?, ?)
            // Hibernate не отправляет INSERT сразу в базу, вместо этого:
            // 1) проверяет объект user
            // 2) помещает его в свой persistence context (кэш первого уровня)
            // 3) генерирует id (если используется стратегия IDENTITY или SEQUENCE)
            // 4) создаёт sql-запрос INSERT, но держит его в очереди
            session.save(user);

            /// Hibernate сбрасывает кэш в бд
            // 1) отправляется реальный sql запрос INSERT в постгрис
            // 2) постгрис выполняет вставку
            // 3) если есть @GeneratedValue(strategy = GenerationType.IDENTITY) - постгрис возвращает сгенерированный id
            // 4) hibernate обновляет поле id в нашем java-объекте
            // 5) отправляется команда COMMIT (фиксация изменений)
            session.getTransaction().commit();
        } //закрываем сессию

        /// Вызов: saveUser("alice", "a@b.c", "123")
        ///            │
        ///            ↓
        /// Шаг 1: new UserEntity(...)
        ///            │
        ///            ↓
        /// Шаг 2: sessionFactory.openSession()
        ///            │
        ///            ↓
        /// Шаг 3: session.beginTransaction()
        ///            │
        ///            ↓
        /// Шаг 4: session.save(user)
        ///            │   ┌─────────────────────────────┐
        ///            │   │ Hibernate: проверяет объект, │
        ///            │   │ кладёт в кэш, готовит INSERT │
        ///            │   └─────────────────────────────┘
        ///            ↓
        /// Шаг 5: session.getTransaction().commit()
        ///            │
        ///            ├──→ Отправка INSERT в PostgreSQL
        ///            │
        ///            ├──→ Получение сгенерированного ID
        ///            │
        ///            ├──→ update user.id = полученный ID
        ///            │
        ///            └──→ COMMIT
        ///            ↓
        /// Шаг 6: Автоматическое закрытие Session
        ///            ↓
        /// Результат: пользователь сохранён в БД
        ///           user.id больше не null!
    }

    /// Получить пользователя по логину
    public static UserEntity getUserFromDb(String login) {
        /// Открываем сессию
        //Для чтения (SELECT) транзакция не обязательна.
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {

            /// Создаём HQL-запрос (на языке Hibernate Query Language)
            // HQL не зависит от конкретной бд (один и тот же код для постгрис, майскл, оракл и тд)
            // Работает с объектами, а не результатами запроса
            // Проверяется при запуске приложения
            /// Метод createQuery() создаёт объект запроса на основе HQL-строки (это как чертёж запроса, который мы потом настроим и выполним)
            Query<UserEntity> query = session.createQuery(
                    "FROM UserEntity WHERE login = :login", UserEntity.class);

            ///Устанавливаем параметр
            //Есть защита от sql-инъекций (если в запросе есть кавычки, то они экранируются)
            query.setParameter("login", login);

            /// Hibernate превращает HQL в SQL
            //Отправляет sql в бд
            //Получает ResultSet (строки из бд)
            //Автоматически создаёт объект UserEntity
            //Заполняет все поля объекта(id, login, email, password)
            //Возвращает объект (или null, если ничего не найдено)
            return query.uniqueResult();
            /// Метод uniqueResult() выплняет запрос и возвращает ОДИН результат (или null, если ничего не найдено)
        }
    }

    /// Загрузить всех пользователей (Map<login, UserEntity>)
    public static Map<String, UserEntity> loadAllUsers() {
        /// Создаём пустую карту для результата
        //String - логин пользователя
        //UserEntity - сам объект пользователя
        Map<String, UserEntity> userMap = new HashMap<>();

        /// Открываем новую сессию для работы с бд
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            /// Создаём запрос на HQL, который означает "выбери всех пользователей из таблицы users)
            Query<UserEntity> query = session.createQuery("FROM UserEntity", UserEntity.class);

            /// Выполняем запрос и получаем список
            //Что делает query.list():
            //1) Hibernate превращает HQL в SQL для постгрис
            //2) Отправляет SQL в бд
            //3) Получает все строки из таблицы users
            //4) Каждую строку превращает в объект UserEntity
            //5) Возвращает List<UserEntity> (мб пустым)
            for (UserEntity user : query.list()) {
                /// Проходим по каждому пользователю из списка и добавляем его в Map
                userMap.put(user.getLogin(), user);
                /// Пример:
                //userMap (заполненный):
                //┌─────────────────────────────────────────────┐
                //│ Ключ       → Значение                       │
                //├─────────────────────────────────────────────┤
                //│ "alice"    → UserEntity{id=1, alice, ...}  │
                //│ "bob"      → UserEntity{id=2, bob, ...}    │
                //│ "carol"    → UserEntity{id=3, carol, ...}  │
                //└─────────────────────────────────────────────┘
            }
        }
        return userMap; //Возвращаем карту, в которой каждому логину соответствует объект пользователя
    }

    /// Проверить существование логина
    //true - пользователь найден
    //false - пользователь не найден
    public static boolean loginExistsInDb(String login) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Query<Long> query = session.createQuery(
                    "SELECT COUNT(*) FROM UserEntity WHERE login = :login", Long.class);
            query.setParameter("login", login);
            return query.uniqueResult() > 0;
        }
    }
}