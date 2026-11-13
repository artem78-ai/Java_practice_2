package ufcdb.query;

import ufcdb.model.Row;
import ufcdb.model.Schema;

import java.util.function.Predicate;

/**
 * Условие отбора записей (часть WHERE). Чтобы проверять записи,
 * условие нужно привязать к схеме таблицы.
 */
public interface Condition {

    /** Условие без ограничений: подходят все записи (запрос без WHERE). */
    Condition ALL = schema -> row -> true;

    /**
     * Привязывает условие к схеме таблицы.
     *
     * @param schema схема таблицы
     * @return предикат для проверки записей
     */
    Predicate<Row> bind(Schema schema);
}
