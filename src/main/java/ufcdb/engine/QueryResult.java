package ufcdb.engine;

import java.util.Collections;
import java.util.List;

/**
 * Результат выполнения запроса. Для SELECT содержит заголовки и строки,
 * для остальных команд - только сообщение.
 */
public class QueryResult {
    private final List<String> columns;
    private final List<Object[]> rows;
    private final String message;

    private QueryResult(List<String> columns, List<Object[]> rows, String message) {
        this.columns = columns;
        this.rows = rows;
        this.message = message;
    }

    /**
     * @param columns имена столбцов результата
     * @param rows    строки результата
     * @return результат SELECT
     */
    public static QueryResult ofRows(List<String> columns, List<Object[]> rows) {
        return new QueryResult(columns, rows, null);
    }

    /**
     * @param message текст сообщения
     * @return результат команды без выборки
     */
    public static QueryResult ofMessage(String message) {
        return new QueryResult(null, Collections.<Object[]>emptyList(), message);
    }

    /** @return true, если результат - таблица (SELECT) */
    public boolean hasRows() {
        return columns != null;
    }

    public List<String> getColumns() {
        return columns;
    }

    public List<Object[]> getRows() {
        return rows;
    }

    public String getMessage() {
        return message;
    }
}
