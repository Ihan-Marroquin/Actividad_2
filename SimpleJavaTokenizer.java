import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * SimpleJavaTokenizer
 * - Tokeniza código Java de forma sencilla.
 * - Construye una tabla de símbolos con identificadores y su información.
 * - Muestra tokens, su categoría y la ubicación (línea:columna).
 * Comentarios y nombres se han adaptado al español para mayor claridad.
 */
public class SimpleJavaTokenizer {

    enum Category { Keyword, Operador, Identificador, Constante, Puntuacion }

    // Representa un token encontrado en el código fuente
    static class Token {
        Category category;
        String lexeme;
        int line;
        int col;
        String valueType;

        Token(Category c, String lex, int l, int co) {
            this.category = c;
            this.lexeme = lex;
            this.line = l;
            this.col = co;
            this.valueType = null;
        }

        Token(Category c, String lex, int l, int co, String vtype) {
            this(c, lex, l, co);
            this.valueType = vtype;
        }

        @Override
        public String toString() {
            String vt = valueType == null ? "" : " <" + valueType + ">";
            return String.format("%3d:%-3d  %-30s %-12s%s", line, col, "\"" + lexeme + "\"", nombreCategoria(category), vt);
        }
    }

    // Mapea la categoría interna a un nombre legible en español
    private static String nombreCategoria(Category c) {
        switch (c) {
            case Keyword: return "Reservada";
            case Operador: return "Operador";
            case Identificador: return "Identificador";
            case Constante: return "Constante";
            case Puntuacion: return "Puntuacion";
            default: return c.toString();
        }
    }

    // Información básica de un identificador en la tabla de símbolos
    static class SymbolInfo {
        String name;
        // tipo: clase, campo, metodo, parametro, local, etc.
        String kind; 
        String declaredType;
        int firstLine, firstCol;
        int occurrences;

        SymbolInfo(String name, String kind, String declaredType, int line, int col) {
            this.name = name;
            this.kind = kind;
            this.declaredType = declaredType;
            this.firstLine = line;
            this.firstCol = col;
            this.occurrences = 1;
        }

        void bump() { occurrences++; }

        @Override
        public String toString() {
            // Muestra la información de forma legible en español
            return String.format("%-20s %-12s %-12s %4d:%-3d  apariciones=%d",
                    name,
                    kind == null ? "desconocido" : kind,
                    declaredType == null ? "-" : declaredType,
                    firstLine, firstCol,
                    occurrences);
        }
    }

        // Palabras reservadas reconocidas
        static final Set<String> PALABRAS_RESERVADAS = new HashSet<>(Arrays.asList(
            "public","private","protected","static","final","class","int","double","void",
            "String","new","this","return","if","else","for","while","switch","case","break",
            "continue","boolean","true","false"
        ));

        // Operadores compuestos (dos caracteres)
        static final Set<String> OPERADORES_MULTIPLES = new HashSet<>(Arrays.asList(
            "==", "<=", ">=", "!=", "++", "--", "+=", "-=", "*=", "/=", "&&", "||", "-="
        ));

        // Puntuación simple
        static final Set<Character> PUNTUACION_SIMPLE = new HashSet<>(Arrays.asList(
            '(',')','{','}','[',']',',',';'
        ));
        // Operadores de un solo carácter
        static final Set<Character> OPERADOR_SIMPLE = new HashSet<>(Arrays.asList(
            '+','-','*','/','%','<','>','!','=','.',':'
        ));

    private final char[] input;
    private int pos = 0;
    private int line = 1;
    private int col = 1;

    private final List<Token> tokens = new ArrayList<>();
    // Tabla de símbolos (mantenemos el orden de aparición)
    private final Map<String, SymbolInfo> tablaSimbolos = new LinkedHashMap<>();

    private Token lastSignificantToken = null;
    // Indicador si estamos dentro de la lista de parámetros
    private boolean enListaParametros = false;
    // Tipo pendiente para una posible declaración
    private String tipoPendienteParaDecl = null; 
    private boolean fuePalabraClass = false; 
    private boolean fueTipoRetornoMetodo = false;
    private String nombreClaseActual = null;

    public SimpleJavaTokenizer(String src) {
        this.input = src.toCharArray();
    }

    // --- Métodos auxiliares para lectura del input ---
    // `peek` y `next` permiten inspeccionar y consumir caracteres
    // manteniendo la posición y la información de línea/columna.

    private char peek() {
        if (pos >= input.length) return (char)-1;
        return input[pos];
    }
    private char peek(int ahead) {
        int p = pos + ahead;
        if (p >= input.length) return (char)-1;
        return input[p];
    }
    
    // Avanza un carácter y actualiza `line` y `col`.
    private char next() {
        if (pos >= input.length) return (char)-1;
        char c = input[pos++];
        if (c == '\n') {
            line++; col = 1;
        } else {
            col++;
        }
        return c;
    }

    // Añade un token a la lista y actualiza la tabla de símbolos
    // Si el token es un identificador, registra su primera aparición,
    // tipo (si se conoce) y cuenta ocurrencias.
    private void emit(Token t) {
        tokens.add(t);
        if (t.category == Category.Identificador) {
            SymbolInfo s = tablaSimbolos.get(t.lexeme);
            if (s == null) {
                String kind = "desconocido";
                String dtype = null;
                if (fuePalabraClass) {
                    kind = "clase";
                } else if (tipoPendienteParaDecl != null) {
                    kind = "desconocido"; 
                    dtype = tipoPendienteParaDecl;
                }
                s = new SymbolInfo(t.lexeme, kind, dtype, t.line, t.col);
                tablaSimbolos.put(t.lexeme, s);
            } else {
                s.bump();
                if (s.declaredType == null && tipoPendienteParaDecl != null) {
                    s.declaredType = tipoPendienteParaDecl;
                }
            }
        }
        if (t.category != Category.Puntuacion || !"".equals(t.lexeme)) {
            lastSignificantToken = t;
        }
    }

    private boolean isWhitespace(char c) {
        return c == ' ' || c == '\t' || c == '\r' || c == '\n';
    }

    private boolean isIdentificadorStart(char c) {
        return Character.isLetter(c) || c == '_' || c == '$';
    }
    private boolean isIdentificadorPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }
    private boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    /**
     * Recorre todo el texto y produce la lista de tokens.
     *
     * Estrategia general:
     * - Ignora espacios y comentarios.
     * - Detecta literales de cadena, números, identificadores/palabras
     *   reservadas, operadores y signos de puntuación.
     * - Mantiene información contextual simple para completar la
     *   tabla de símbolos (por ejemplo, si se vio un tipo antes del
     *   identificador, se sugiere que es una declaración).
     */
    public void tokenizeAll() {
        while (pos < input.length) {
            char c = peek();
            int tokLine = line;
            int tokCol = col;
            if (isWhitespace(c)) {
                next();
                continue;
            }
            // Manejo de comentarios: línea (`//`) y bloque (`/* ... */`).
            // Se descartan y no generan tokens; detectamos cierre de bloque.
            if (c == '/') {
                char n = peek(1);
                if (n == '/') {
                    next(); next();
                    while (peek() != (char)-1 && peek() != '\n') next();
                    continue; 
                } else if (n == '*') {
                    next(); next();
                    boolean closed = false;
                    while (peek() != (char)-1) {
                        char a = next();
                        if (a == '*' && peek() == '/') { next(); closed = true; break; }
                    }
                    if (!closed) {
                        System.err.println("Advertencia: comentario de bloque sin terminar en " + tokLine + ":" + tokCol);
                    }
                    continue;
                }
            }

            // Literales de cadena: se respetan escapes simples y se advierte
            // si la cadena no se cierra en la misma línea.
            if (c == '"') {
                next();
                StringBuilder sb = new StringBuilder();
                boolean closed = false;
                while (peek() != (char)-1) {
                    char ch = next();
                    if (ch == '\\') {
                        if (peek() != (char)-1) {
                            char esc = next();
                            sb.append('\\').append(esc);
                            continue;
                        } else {
                            sb.append('\\'); break;
                        }
                    }
                    if (ch == '"') { closed = true; break; }
                    if (ch == '\n') {
                        System.err.println("Advertencia: literal de cadena sin terminar empezando en " + tokLine + ":" + tokCol + " — cerrando al final de línea.");
                        break;
                    }
                    sb.append(ch);
                }
                String lex = "\"" + sb.toString() + "\"";
                Token t = new Token(Category.Constante, lex, tokLine, tokCol, "string");
                emit(t);
                tipoPendienteParaDecl = null;
                fuePalabraClass = false;
                continue;
            }

            // Literales numéricos: detecta enteros y decimales simples.
            // Si hay múltiples puntos se genera una advertencia y se
            // emite lo que pudo reconocer.
            if (isDigit(c)) {
                StringBuilder sb = new StringBuilder();
                boolean seenDot = false;
                while (isDigit(peek())) sb.append(next());
                        if (peek() == '.') {
                    seenDot = true;
                    sb.append(next());
                    while (isDigit(peek())) sb.append(next());
                    if (peek() == '.') {
                        String lexNum = sb.toString();
                        Token t;
                        if (seenDot) t = new Token(Category.Constante, lexNum, tokLine, tokCol, "double");
                        else t = new Token(Category.Constante, lexNum, tokLine, tokCol, "int");
                        emit(t);
                                System.err.println("Advertencia: literal numérico malformado (múltiples puntos) en " + tokLine + ":" + tokCol + " — emitido \"" + lexNum + "\" y continua.");
                                tipoPendienteParaDecl = null;
                                fuePalabraClass = false;
                        continue;
                    } else {
                        String lexNum = sb.toString();
                        Token t = new Token(Category.Constante, lexNum, tokLine, tokCol, "double");
                        emit(t);
                                tipoPendienteParaDecl = null;
                                fuePalabraClass = false;
                        continue;
                    }
                } else {
                    String lexNum = sb.toString();
                    Token t = new Token(Category.Constante, lexNum, tokLine, tokCol, "int");
                    emit(t);
                    tipoPendienteParaDecl = null;
                    fuePalabraClass = false;
                    continue;
                }
            }

            // Identificadores y palabras reservadas. Si es palabra
            // reservada se emite como tal; si es identificador se agrega
            // a la tabla de símbolos y se usan heurísticas (tipo pendiente,
            // paréntesis siguiente, etc.) para determinar si es método,
            // parámetro o campo.
            if (isIdentificadorStart(c)) {
                StringBuilder sb = new StringBuilder();
                sb.append(next());
                while (isIdentificadorPart(peek())) sb.append(next());
                String lex = sb.toString();
                if (PALABRAS_RESERVADAS.contains(lex)) {
                    Token t = new Token(Category.Keyword, lex, tokLine, tokCol);
                    emit(t);
                    if ("class".equals(lex)) fuePalabraClass = true;
                    else fuePalabraClass = false;

                    if (lex.equals("int") || lex.equals("double") || lex.equals("String") || Character.isUpperCase(lex.charAt(0))) {
                        tipoPendienteParaDecl = lex;
                        fueTipoRetornoMetodo = true;
                    } else {
                        tipoPendienteParaDecl = null;
                        fueTipoRetornoMetodo = false;
                    }
                    continue;
                } else {
                    Token t = new Token(Category.Identificador, lex, tokLine, tokCol);
                    emit(t);

                    if (fuePalabraClass) {
                        SymbolInfo s = tablaSimbolos.get(lex);
                        if (s != null) s.kind = "clase";
                        nombreClaseActual = lex;
                        fuePalabraClass = false;
                        tipoPendienteParaDecl = null;
                        continue;
                    }
                    if (tipoPendienteParaDecl != null) {
                        if (peek() == '(') {
                            SymbolInfo s = tablaSimbolos.get(lex);
                            if (s != null) {
                                s.kind = "metodo";
                                s.declaredType = tipoPendienteParaDecl;
                            }
                        } else {
                            SymbolInfo s = tablaSimbolos.get(lex);
                            if (s != null) {
                                if (enListaParametros) s.kind = "parametro";
                                else {
                                    s.kind = "campo"; 
                                }
                                s.declaredType = tipoPendienteParaDecl;
                            }
                        }
                        tipoPendienteParaDecl = null;
                    }
                    fuePalabraClass = false;
                    continue;
                }
            }

            char c1 = c;
            char c2 = peek(1);
            // Operadores compuestos (dos caracteres) y operadores/puntuación
            // de un solo carácter.
            String two = "" + c1 + c2;
            if (OPERADORES_MULTIPLES.contains(two)) {
                next(); next();
                Token t = new Token(Category.Operador, two, tokLine, tokCol);
                emit(t);
                tipoPendienteParaDecl = null;
                fuePalabraClass = false;
                continue;
            }
            if (PUNTUACION_SIMPLE.contains(c1)) {
                next();
                Token t = new Token(Category.Puntuacion, String.valueOf(c1), tokLine, tokCol);
                if ("(".equals(t.lexeme)) {
                    enListaParametros = true;
                } else if (")".equals(t.lexeme)) {
                    enListaParametros = false;
                }
                emit(t);
                tipoPendienteParaDecl = null;
                fuePalabraClass = false;
                continue;
            }
            if (OPERADOR_SIMPLE.contains(c1)) {
                next();
                Token t = new Token(OPERADOR_SIMPLE.contains(c1) && c1 != '.' ? Category.Operador : Category.Puntuacion, String.valueOf(c1), tokLine, tokCol);
                emit(t);
                tipoPendienteParaDecl = null;
                fuePalabraClass = false;
                continue;
            }

            char unknown = next();
            Token t = new Token(Category.Puntuacion, String.valueOf(unknown), tokLine, tokCol);
            emit(t);
            tipoPendienteParaDecl = null;
            fuePalabraClass = false;
        }

        refineSymbolKinds();
    }

    private void refineSymbolKinds() {
        // Pase adicional para mejorar la clasificación de símbolos.
        // Usa contexto vecinos (tipo antes del identificador, paréntesis,
        // modificadores) para decidir si algo es método, campo, local, etc.
        for (int i = 0; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            if (t.category == Category.Keyword && (t.lexeme.equals("int") || t.lexeme.equals("double") || t.lexeme.equals("String") || !Character.isLowerCase(t.lexeme.charAt(0)) )) {
                if (i+2 < tokens.size()) {
                    Token cand = tokens.get(i+1);
                    Token next = tokens.get(i+2);
                    if (cand.category == Category.Identificador && next.category == Category.Puntuacion && "(".equals(next.lexeme)) {
                        SymbolInfo s = tablaSimbolos.get(cand.lexeme);
                        if (s != null) {
                            s.kind = "metodo";
                            s.declaredType = t.lexeme;
                        }
                    } else if (cand.category == Category.Identificador && next.category == Category.Operador && "=".equals(next.lexeme)
                               || next.category == Category.Puntuacion && (";".equals(next.lexeme) || ",".equals(next.lexeme))) {
                        SymbolInfo s = tablaSimbolos.get(cand.lexeme);
                        if (s != null) {
                            if (hasModifierBefore(i)) s.kind = "campo";
                            else s.kind = s.kind == null || s.kind.equals("desconocido") ? "local" : s.kind;
                            s.declaredType = t.lexeme;
                        }
                    }
                }
            }
            if (t.category == Category.Keyword && t.lexeme.equals("class") && i+1 < tokens.size()) {
                Token cand = tokens.get(i+1);
                if (cand.category == Category.Identificador) {
                    SymbolInfo s = tablaSimbolos.get(cand.lexeme);
                    if (s != null) s.kind = "clase";
                    nombreClaseActual = cand.lexeme;
                }
            }
        }
    }

    private boolean hasModifierBefore(int i) {
        // Busca modificadores (public/private/protected/static) en las
        // tokens previas hasta una pequeña ventana. Se usa para diferenciar
        // campos públicos/privados de variables locales.
        for (int j = Math.max(0, i-6); j < i; j++) {
            Token t = tokens.get(j);
            if (t.category == Category.Keyword &&
                    (t.lexeme.equals("public") || t.lexeme.equals("private") || t.lexeme.equals("protected") || t.lexeme.equals("static"))) {
                return true;
            }
        }
        return false;
    }

    public void imprimirTokens() {
        // Imprime la lista secuencial de tokens con su categoría y posición.
        // Esta salida sirve para inspeccionar exactamente qué reconoció
        // el tokenizador en orden de aparición.
        System.out.println("=== Tokens Encontrados ===");
        for (Token t : tokens) {
            System.out.println(t.toString());
        }
    }

    // Imprime la tabla de símbolos con los identificadores encontrados.
    // Para cada identificador se muestra su clasificación (clase/campo/metodo/parametro/local),
    // el tipo declarado cuando está disponible, la primera posición y cuántas apariciones hubo.
    public void imprimirTablaSimbolos() {
        System.out.println("\n=== Tabla de Símbolos ===");
        for (Map.Entry<String, SymbolInfo> e : tablaSimbolos.entrySet()) {
            System.out.println(e.getValue().toString());
        }
    }

    public static void main(String[] args) throws IOException {
        String source;
        if (args.length >= 1) {
            source = new String(Files.readAllBytes(Paths.get(args[0])), "UTF-8");
        } else {
            source = sampleSource();
        }
        // Punto de entrada de prueba: si se pasa un archivo se tokeniza ese,
        // en caso contrario se usa un ejemplo embebido. Se ejecuta el
        // tokenizador y se muestran tokens y la tabla de símbolos.
        SimpleJavaTokenizer lexer = new SimpleJavaTokenizer(source);
        lexer.tokenizeAll();
        lexer.imprimirTokens();
        lexer.imprimirTablaSimbolos();
    }

    private static String sampleSource() {
        return ""
        + "public class PotionBrewer {\n"
        + "    // Ingredient costs in gold coins\n"
        + "    private static final double HERB_PRICE = 5.50;\n"
        + "    private static final int MUSHROOM_PRICE = 3;\n"
        + "    private String brewerName;\n"
        + "    private double goldCoins;\n"
        + "    private int potionsBrewed;\n"
        + "\n"
        + "    public PotionBrewer(String name, double startingGold) {\n"
        + "        this.brewerName = name;\n"
        + "        this.goldCoins = startingGold;\n"
        + "        this.potionsBrewed = 0;\n"
        + "    }\n"
        + "\n"
        + "    public static void main(String[] args) {\n"
        + "        PotionBrewer wizard = new PotionBrewer(\"Gandalf, the Wise\", 100.0);\n"
        + "        String[] ingredients = {\"Mandrake Root\", \"Dragon Scale\", \"Phoenix Feather\"};\n"
        + "\n"
        + "        wizard.brewHealthPotion(3, 2); // 3 herbs, 2 mushrooms\n"
        + "        wizard.brewHealthPotion(5, 4);\n"
        + "\n"
        + "        wizard.printStatus();\n"
        + "    }\n"
        + "\n"
        + "    /* Brews a potion if we have enough gold */\n"
        + "    public void brewHealthPotion(int herbCount, int mushroomCount) {\n"
        + "        double totalCost = (herbCount * HERB_PRICE) + (mushroomCount * MUSHROOM_PRICE\n"
        + ");\n"
        + "        if (totalCost <= this.goldCoins) {\n"
        + "            this.goldCoins -= totalCost; // Deduct the cost\n"
        + "            this.potionsBrewed++;\n"
        + "            System.out.println(\"Success! Potion brewed for \" + totalCost + \" gold.\");\n"
        + "        } else {\n"
        + "            System.out.println(\"Not enough gold! Need: \" + totalCost);\n"
        + "        }\n"
        + "    }\n"
        + "    // Prints the current brewer status\n"
        + "    public void printStatus() {\n"
        + "        System.out.println(\"\\n=== Brewer Status ===\");\n"
        + "        System.out.println(\"Name: \" + this.brewerName);\n"
        + "        System.out.println(\"Gold remaining: \" + this.goldCoins);\n"
        + "        System.out.println(\"Potions brewed: \" + this.potionsBrewed);\n"
        + "    }\n"
        + "}\n";
    }
}
