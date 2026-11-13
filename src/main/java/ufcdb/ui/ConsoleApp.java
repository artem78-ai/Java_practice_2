package ufcdb.ui;

import ufcdb.engine.Database;
import ufcdb.engine.DemoData;
import ufcdb.engine.QueryResult;
import ufcdb.model.DbException;
import ufcdb.storage.TableRepository;

import java.nio.file.Paths;
import java.util.List;
import java.util.Scanner;

/**
 * Консольный интерфейс СУБД. Запрос может занимать несколько строк
 * и заканчивается символом ';'.
 */
public class ConsoleApp {

    /**
     * Точка входа. Запускать из корня проекта, данные хранятся в папке data.
     *
     * @param args не используются
     */
    public static void main(String[] args) {
        Database db = new Database(new TableRepository(Paths.get("data")));
        if (db.isEmpty()) {
            DemoData.populate(db);
            System.out.println("База была пустой, созданы учебные таблицы divisions и fighters.");
        }
        System.out.println("Mini DBMS (UFC). Введите help для справки, exit для выхода.");

        Scanner in = new Scanner(System.in);
        StringBuilder buffer = new StringBuilder();
        while (true) {
            System.out.print(buffer.length() == 0 ? "ufcdb> " : "   ...> ");
            if (!in.hasNextLine()) {
                break;
            }
            String line = in.nextLine().trim();
            if (buffer.length() == 0) {
                String cmd = line.toLowerCase();
                if (cmd.isEmpty()) {
                    continue;
                } else if (cmd.equals("exit") || cmd.equals("quit")) {
                    break;
                } else if (cmd.equals("help")) {
                    printHelp();
                    continue;
                } else if (cmd.equals("tables")) {
                    System.out.println("Таблицы: " + String.join(", ", db.tableNames()));
                    continue;
                }
            }
            buffer.append(line).append(' ');
            if (line.endsWith(";")) {
                run(db, buffer.toString());
                buffer.setLength(0);
            }
        }
        System.out.println("До свидания!");
    }

    private static void run(Database db, String sql) {
        try {
            QueryResult result = db.execute(sql);
            if (result.hasRows()) {
                printTable(result.getColumns(), result.getRows());
            } else {
                System.out.println(result.getMessage());
            }
        } catch (DbException e) {
            System.out.println("Ошибка: " + e.getMessage());
        }
    }

    private static void printTable(List<String> columns, List<Object[]> rows) {
        int n = columns.size();
        String[][] cells = new String[rows.size()][n];
        int[] width = new int[n];
        for (int c = 0; c < n; c++) {
            width[c] = columns.get(c).length();
        }
        for (int r = 0; r < rows.size(); r++) {
            for (int c = 0; c < n; c++) {
                Object v = rows.get(r)[c];
                cells[r][c] = v == null ? "NULL" : v.toString();
                width[c] = Math.max(width[c], cells[r][c].length());
            }
        }
        StringBuilder sep = new StringBuilder("+");
        for (int c = 0; c < n; c++) {
            sep.append(repeat('-', width[c] + 2)).append('+');
        }
        System.out.println(sep);
        System.out.println(formatLine(columns.toArray(new String[0]), width));
        System.out.println(sep);
        for (String[] row : cells) {
            System.out.println(formatLine(row, width));
        }
        System.out.println(sep);
        System.out.println("Строк: " + rows.size());
    }

    private static String formatLine(String[] values, int[] width) {
        StringBuilder sb = new StringBuilder("|");
        for (int c = 0; c < values.length; c++) {
            sb.append(' ').append(values[c]).append(repeat(' ', width[c] - values[c].length() + 1)).append('|');
        }
        return sb.toString();
    }

    private static String repeat(char ch, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(ch);
        }
        return sb.toString();
    }

    private static void printHelp() {
        System.out.println("Команды (каждый запрос заканчивается ;):");
        System.out.println("  INSERT INTO fighters VALUES (7, 'Name', 77.0, '1990-01-01', FALSE, 5);");
        System.out.println("  INSERT INTO fighters (id, name) VALUES (8, 'Name');");
        System.out.println("  SELECT * FROM fighters;");
        System.out.println("  SELECT name, weight FROM fighters WHERE weight > 70 AND is_champion = TRUE;");
        System.out.println("  UPDATE fighters SET is_champion = TRUE WHERE id = 2;");
        System.out.println("  DELETE FROM fighters WHERE name = 'Conor McGregor';");
        System.out.println("Служебные: tables - список таблиц, help - справка, exit - выход.");
    }
}
