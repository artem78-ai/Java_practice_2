package ufcdb.query;

import ufcdb.model.Row;
import ufcdb.model.Schema;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Объединение условий через AND: запись подходит, если выполнены все части. */
public class AndCondition implements Condition {
    private final List<Condition> parts;

    /**
     * @param parts условия, которые должны выполняться одновременно
     */
    public AndCondition(List<Condition> parts) {
        this.parts = new ArrayList<>(parts);
    }

    /** @return части условия, соединённые через AND */
    public List<Condition> getParts() {
        return java.util.Collections.unmodifiableList(parts);
    }

    @Override
    public Predicate<Row> bind(Schema schema) {
        Predicate<Row> result = row -> true;
        for (Condition c : parts) {
            result = result.and(c.bind(schema));
        }
        return result;
    }
}
