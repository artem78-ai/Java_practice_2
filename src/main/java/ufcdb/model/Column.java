package ufcdb.model;

/** Описание одного столбца таблицы. */
public class Column {
    private final String name;
    private final ColumnType type;
    private final boolean primaryKey;
    private final String refTable;
    private final String refColumn;

    /**
     * Обычный столбец без внешнего ключа.
     *
     * @param name       имя столбца
     * @param type       тип данных
     * @param primaryKey true, если столбец - первичный ключ
     */
    public Column(String name, ColumnType type, boolean primaryKey) {
        this(name, type, primaryKey, null, null);
    }

    /**
     * Столбец, который может быть внешним ключом.
     *
     * @param name       имя столбца
     * @param type       тип данных
     * @param primaryKey true, если столбец - первичный ключ
     * @param refTable   таблица, на которую ссылается столбец (или null)
     * @param refColumn  столбец этой таблицы (или null)
     */
    public Column(String name, ColumnType type, boolean primaryKey, String refTable, String refColumn) {
        this.name = name;
        this.type = type;
        this.primaryKey = primaryKey;
        this.refTable = refTable;
        this.refColumn = refColumn;
    }

    public String getName() { return name; }
    public ColumnType getType() { return type; }
    public boolean isPrimaryKey() { return primaryKey; }

    /** @return true, если столбец ссылается на другую таблицу */
    public boolean isForeignKey() { return refTable != null; }

    /** @return имя таблицы, на которую ссылается столбец, или null */
    public String getRefTable() { return refTable; }

    /** @return имя столбца в другой таблице или null */
    public String getRefColumn() { return refColumn; }
}
