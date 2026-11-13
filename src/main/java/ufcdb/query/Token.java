package ufcdb.query;

/** Один токен SQL-запроса. */
public class Token {
    private final TokenType type;
    private final String text;

    public Token(TokenType type, String text) {
        this.type = type;
        this.text = text;
    }

    public TokenType getType() {
        return type;
    }

    public String getText() {
        return text;
    }

    /**
     * @param keyword ключевое слово
     * @return true, если токен - это слово keyword (без учёта регистра)
     */
    public boolean isKeyword(String keyword) {
        return type == TokenType.WORD && text.equalsIgnoreCase(keyword);
    }

    /**
     * @param symbol символ
     * @return true, если токен - это символ symbol
     */
    public boolean isSymbol(String symbol) {
        return type == TokenType.SYMBOL && text.equals(symbol);
    }

    @Override
    public String toString() {
        return type + "(" + text + ")";
    }
}
