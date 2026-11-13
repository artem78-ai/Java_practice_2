package ufcdb.engine;

import ufcdb.model.Column;
import ufcdb.model.ColumnType;
import ufcdb.model.Row;
import ufcdb.model.Schema;
import ufcdb.model.Table;

import java.time.LocalDate;
import java.util.Arrays;

/** Учебные данные: весовые категории и бойцы UFC. */
public final class DemoData {
    private DemoData() {
    }

    /**
     * Создаёт таблицы divisions и fighters и заполняет их примерами.
     *
     * @param db пустая база данных
     */
    public static void populate(Database db) {
        Table divisions = new Table(new Schema("divisions", Arrays.asList(
                new Column("id", ColumnType.INT, true),
                new Column("name", ColumnType.STRING, false),
                new Column("max_weight", ColumnType.DOUBLE, false))));
        divisions.insertAll(Arrays.asList(
                new Row(new Object[]{1, "Flyweight", 56.7}),
                new Row(new Object[]{2, "Bantamweight", 61.2}),
                new Row(new Object[]{3, "Featherweight", 65.8}),
                new Row(new Object[]{4, "Lightweight", 70.3}),
                new Row(new Object[]{5, "Welterweight", 77.1}),
                new Row(new Object[]{6, "Middleweight", 83.9}),
                new Row(new Object[]{7, "Light Heavyweight", 93.0}),
                new Row(new Object[]{8, "Heavyweight", 120.2})));
        db.addTable(divisions);

        Table fighters = new Table(new Schema("fighters", Arrays.asList(
                new Column("id", ColumnType.INT, true),
                new Column("name", ColumnType.STRING, false),
                new Column("weight", ColumnType.DOUBLE, false),
                new Column("birth_date", ColumnType.DATE, false),
                new Column("is_champion", ColumnType.BOOLEAN, false),
                new Column("division_id", ColumnType.INT, false, "divisions", "id"))));
        fighters.insertAll(Arrays.asList(
                new Row(new Object[]{1, "Islam Makhachev", 70.0, LocalDate.of(1991, 10, 27), true, 4}),
                new Row(new Object[]{2, "Khabib Nurmagomedov", 70.0, LocalDate.of(1988, 9, 20), false, 4}),
                new Row(new Object[]{3, "Conor McGregor", 70.0, LocalDate.of(1988, 7, 14), false, 3}),
                new Row(new Object[]{4, "Max Holloway", 66.0, LocalDate.of(1991, 12, 4), false, 3}),
                new Row(new Object[]{5, "Israel Adesanya", 83.0, LocalDate.of(1989, 7, 22), false, 6}),
                new Row(new Object[]{6, "Jon Jones", 111.0, LocalDate.of(1987, 7, 19), true, 8})));
        db.addTable(fighters);
    }
}
