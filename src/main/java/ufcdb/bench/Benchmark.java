package ufcdb.bench;

import ufcdb.engine.Database;
import ufcdb.model.Column;
import ufcdb.model.ColumnType;
import ufcdb.model.Row;
import ufcdb.model.Schema;
import ufcdb.model.Table;
import ufcdb.storage.IndexRepository;
import ufcdb.storage.TableRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.stream.Stream;

/**
 * Сравнение скорости SELECT с индексом и без. Работает во временной папке,
 * рабочие данные в папке data не затрагиваются.
 * Запуск: {@code java -cp out ufcdb.bench.Benchmark}
 */
public class Benchmark {
    private static final int[] SIZES = {10_000, 100_000, 300_000};
    private static final int POINT_QUERIES = 100;
    private static final int RANGE_QUERIES = 50;
    private static final int WARMUP = 20;

    /**
     * Точка входа.
     *
     * @param args не используются
     * @throws IOException при ошибке работы с временной папкой
     */
    public static void main(String[] args) throws IOException {
        System.out.println("| Записей | Запрос | Без индекса, мс | С индексом, мс | Ускорение |");
        System.out.println("|---|---|---|---|---|");
        for (int size : SIZES) {
            run(size);
        }
    }

    private static void run(int size) throws IOException {
        Path dir = Files.createTempDirectory("ufcdb-bench");
        try {
            Database db = new Database(new TableRepository(dir), new IndexRepository(dir));
            db.addTable(createTable(size));

            Random rnd = new Random(7);
            List<String> point = new ArrayList<>();
            for (int i = 0; i < POINT_QUERIES; i++) {
                point.add("SELECT * FROM bench WHERE name = 'Fighter_" + rnd.nextInt(size) + "';");
            }
            List<String> range = new ArrayList<>();
            for (int i = 0; i < RANGE_QUERIES; i++) {
                range.add("SELECT id FROM bench WHERE weight > 149.9;");
            }

            long[] rowsNoIdxPoint = new long[1];
            long[] rowsNoIdxRange = new long[1];
            double pointNo = measure(db, point, rowsNoIdxPoint);
            double rangeNo = measure(db, range, rowsNoIdxRange);

            long start = System.nanoTime();
            db.execute("CREATE INDEX idx_bench_name ON bench (name);");
            db.execute("CREATE INDEX idx_bench_weight ON bench (weight);");
            double buildMs = (System.nanoTime() - start) / 1e6;

            long[] rowsIdxPoint = new long[1];
            long[] rowsIdxRange = new long[1];
            double pointIdx = measure(db, point, rowsIdxPoint);
            double rangeIdx = measure(db, range, rowsIdxRange);

            if (rowsNoIdxPoint[0] != rowsIdxPoint[0] || rowsNoIdxRange[0] != rowsIdxRange[0]) {
                throw new IllegalStateException("Результаты с индексом и без него различаются!");
            }

            printLine(size, "name = '...' (точечный)", pointNo, pointIdx);
            printLine(size, "weight > 149.9 (диапазон)", rangeNo, rangeIdx);
            System.err.println("Размер " + size + ": построение двух индексов заняло "
                    + String.format(Locale.US, "%.0f", buildMs) + " мс");
        } finally {
            deleteRecursively(dir);
        }
    }

    private static Table createTable(int size) {
        Table table = new Table(new Schema("bench", Arrays.asList(
                new Column("id", ColumnType.INT, true),
                new Column("name", ColumnType.STRING, false),
                new Column("weight", ColumnType.DOUBLE, false),
                new Column("birth_date", ColumnType.DATE, false),
                new Column("is_champion", ColumnType.BOOLEAN, false))));
        Random rnd = new Random(42);
        List<Row> rows = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            rows.add(new Row(new Object[]{
                    i,
                    "Fighter_" + i,
                    50.0 + rnd.nextDouble() * 100.0,
                    LocalDate.of(1970, 1, 1).plusDays(rnd.nextInt(10_000)),
                    rnd.nextInt(100) == 0}));
        }
        table.insertAll(rows);
        return table;
    }

    /** Возвращает среднее время одного запроса в миллисекундах и суммарное число найденных строк. */
    private static double measure(Database db, List<String> queries, long[] totalRows) {
        for (int i = 0; i < WARMUP; i++) {
            db.execute(queries.get(i % queries.size()));
        }
        long rows = 0;
        long start = System.nanoTime();
        for (String q : queries) {
            rows += db.execute(q).getRows().size();
        }
        long elapsed = System.nanoTime() - start;
        totalRows[0] = rows;
        return elapsed / 1e6 / queries.size();
    }

    private static void printLine(int size, String query, double without, double with) {
        System.out.println(String.format(Locale.US, "| %d | %s | %.4f | %.4f | %.0fx |",
                size, query, without, with, without / with));
    }

    private static void deleteRecursively(Path dir) throws IOException {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
    }
}
