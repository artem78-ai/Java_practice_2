package ufcdb.model;

/** Ошибка при работе с базой данных (неверные данные, нарушение ключа, сбой записи на диск). */
public class DbException extends RuntimeException {
    public DbException(String message) {
        super(message);
    }

    public DbException(String message, Throwable cause) {
        super(message, cause);
    }
}
