package ufcdb.query;

import ufcdb.model.DbException;

/** Операторы сравнения в условии WHERE. */
public enum Operator {
    EQ("="), NE("!="), LT("<"), GT(">"), LE("<="), GE(">=");

    private final String symbol;

    Operator(String symbol) {
        this.symbol = symbol;
    }

    public String getSymbol() {
        return symbol;
    }

    /**
     * Находит оператор по тексту.
     *
     * @param s текст оператора (допускается также {@code <>} как "не равно")
     * @return соответствующий оператор
     * @throws DbException если оператор неизвестен
     */
    public static Operator fromSymbol(String s) {
        if (s.equals("<>")) return NE;
        for (Operator op : values()) {
            if (op.symbol.equals(s)) return op;
        }
        throw new DbException("Неизвестный оператор: " + s);
    }

    /**
     * Применяет оператор к результату сравнения compareTo.
     *
     * @param cmp результат {@code a.compareTo(b)}
     * @return true, если отношение выполняется
     */
    public boolean matches(int cmp) {
        switch (this) {
            case EQ: return cmp == 0;
            case NE: return cmp != 0;
            case LT: return cmp < 0;
            case GT: return cmp > 0;
            case LE: return cmp <= 0;
            case GE: return cmp >= 0;
            default: throw new IllegalStateException();
        }
    }
}
