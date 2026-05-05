package org.example.db;

import javax.persistence.*;

/// Сущность (Entity) - это java-класс, который:
/// 1) представляет одну запись в таблице бд
/// 2) каждое поле - это колонка в таблице
/// 3) каждый объект - это одна строка в таблице

@Entity
/// @Entity - сообщает хибернейт, что это класс - сущность, которую нужно сохранять в бд
@Table(name = "users")
public class UserEntity {

    @Id
    /// @Id - указывает, что поле первичный ключ в таблице
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    /// @GeneratedValue - говорит хибернейт, что значение этого поля будет сгенерировано автоматически
    //strategy = GenerationType.IDENTITY - указывает, что бд сама сгенерирует этот ключ
    private Long id;

    @Column(unique = true, nullable = false)
    /// unique - значение должно быть уникальным
    private String login;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    ///Этот класс должен иметь пустой конструктор
    //Хибернейт при загрузке данных из бд:
    // 1) создаёт объект через пустой конструктор
    // 2) заполняет поля через сеттеры
    public UserEntity() {}

    public UserEntity(String login, String email, String password) {
        this.login = login;
        this.email = email;
        this.password = password;
    }

    // Геттеры и сеттеры
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getLogin() { return login; }
    public void setLogin(String login) { this.login = login; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}