package ufcdb.query;

import ufcdb.model.DbException;

import java.util.ArrayList;
import java.util.List;

/** Лексический анализатор: разбивает текст SQL-запроса на токены. */
public final class Tokenizer {
    private Tokenizer() {
    }

    /**
     * Разбивает запрос на токены.
     *
     * @param sql текст запроса
     * @return список токенов
     * @throws DbException при незакрытой строке или недопустимом символе
     */
    public static List<Token> tokenize(String sql) {
        List<Token> tokens = new ArrayList<>();
        int n = sql.length();
        int i = 0;
        while (i < n) {
            char c = sql.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
            } else if (c == '\'') {
                StringBuilder sb = new StringBuilder();
                i++;
                boolean closed = false;
                while (i < n) {
                    char ch = sql.charAt(i);
                    if (ch == '\'') {
                        if (i + 1 < n && sql.charAt(i + 1) == '\'') { // '' внутри строки - это одна кавычка
                            sb.append('\'');
                            i += 2;
                            continue;
                        }
                        closed = true;
                        i++;
                        break;
                    }
                    sb.append(ch);
                    i++;
                }
                if (!closed) {
                    throw new DbException("Строка не закрыта кавычкой");
                }
                tokens.add(new Token(TokenType.STRING, sb.toString()));
            } else if (Character.isDigit(c)
                    || (c == '-' && i + 1 < n && Character.isDigit(sql.charAt(i + 1)))) {
                int start = i++;
                while (i < n && (Character.isDigit(sql.charAt(i)) || sql.charAt(i) == '.')) {
                    i++;
                }
                tokens.add(new Token(TokenType.NUMBER, sql.substring(start, i)));
            } else if (Character.isLetter(c) || c == '_') {
                int start = i++;
                while (i < n && (Character.isLetterOrDigit(sql.charAt(i)) || sql.charAt(i) == '_')) {
                    i++;
                }
                tokens.add(new Token(TokenType.WORD, sql.substring(start, i)));
            } else if (c == '<' || c == '>' || c == '!') {
                if (i + 1 < n && (sql.charAt(i + 1) == '=' || (c == '<' && sql.charAt(i + 1) == '>'))) {
                    tokens.add(new Token(TokenType.SYMBOL, sql.substring(i, i + 2)));
                    i += 2;
                } else if (c == '!') {
                    throw new DbException("Недопустимый символ: !");
                } else {
                    tokens.add(new Token(TokenType.SYMBOL, String.valueOf(c)));
                    i++;
                }
            } else if ("(),*;=".indexOf(c) >= 0) {
                tokens.add(new Token(TokenType.SYMBOL, String.valueOf(c)));
                i++;
            } else {
                throw new DbException("Недопустимый символ: " + c);
            }
        }
        return tokens;
    }
}
