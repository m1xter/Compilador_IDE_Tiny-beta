package ve.edu.unet;

import java_cup.runtime.*;
import java.io.Reader;

%%
/* Habilitar la compatibilidad con el interfaz CUP para el generador sintactico*/
%cup
/* Llamar Lexico a la clase que contiene el analizador Lexico */
%class Lexico
%public
%unicode

/*-- DECLARACIONES --*/
%{
	public Lexico(Reader r, SymbolFactory sf, ListaErrores errores){
		this(r);
		this.sf=sf;
		this.errores=errores;
	}
	private SymbolFactory sf;
	private ListaErrores errores;
	public static boolean debug=false;

	/* Linea actual (JFlex la cuenta desde 0) */
	private int linea(){
		return yyline + 1;
	}

	/* Crea un simbolo para CUP guardando linea (left) y columna (right).
	 * (Se crea directamente porque SymbolFactory no permite indicar la linea) */
	private Symbol token(String nombre, int tipo){
		if(debug) System.out.println("token " + nombre);
		return new Symbol(tipo, linea(), yycolumn + 1, null);
	}

	private Symbol token(String nombre, int tipo, Object valor){
		if(debug) System.out.println("token " + nombre + " " + valor);
		return new Symbol(tipo, linea(), yycolumn + 1, valor);
	}

	private void errorLexico(String mensaje){
		if(errores != null)
			errores.agregar(ErrorCompilacion.Fase.LEXICO, linea(), mensaje);
		else
			System.err.println("Error lexico en linea " + linea() + ": " + mensaje);
	}
%}
%eofval{
	return new Symbol(sym.EOF, linea(), yycolumn + 1, null);
%eofval}

/* Acceso a la columna y fila actual de analisis CUP */
%line
%column

digito			= [0-9]
numero			= {digito}+
letra			= [a-zA-Z]
identificador	= {letra}({letra}|{digito}|"_")*
nuevalinea		= \n | \r\n | \r
espacio			= [ \t\f]+
cadena			= \"[^\"\r\n]*\"
%%
/* ---- palabras reservadas de Tiny ---- */
"if"            { return token("IF", sym.IF); }
"then"          { return token("THEN", sym.THEN); }
"else"          { return token("ELSE", sym.ELSE); }
"end"           { return token("END", sym.END); }
"repeat"        { return token("REPEAT", sym.REPEAT); }
"until"         { return token("UNTIL", sym.UNTIL); }
"read"          { return token("READ", sym.READ); }
"write"         { return token("WRITE", sym.WRITE); }
/* ---- palabras reservadas nuevas de Tiny++ ---- */
"while"         { return token("WHILE", sym.WHILE); }
"do"            { return token("DO", sym.DO); }
"int"           { return token("INT", sym.INT); }
"bool"          { return token("BOOL", sym.BOOL); }
"string"        { return token("STRING", sym.STRING); }
"function"      { return token("FUNCTION", sym.FUNCTION); }
"procedure"     { return token("PROCEDURE", sym.PROCEDURE); }
"return"        { return token("RETURN", sym.RETURN); }
"true"          { return token("TRUE", sym.TRUE); }
"false"         { return token("FALSE", sym.FALSE); }
"and"           { return token("AND", sym.AND); }
"or"            { return token("OR", sym.OR); }
"not"           { return token("NOT", sym.NOT); }
/* ---- operadores y signos ---- */
":="            { return token("ASSIGN", sym.ASSIGN); }
"<="            { return token("LE", sym.LE); }
">="            { return token("GE", sym.GE); }
"<>"            { return token("NE", sym.NE); }
"="             { return token("EQ", sym.EQ); }
"<"             { return token("LT", sym.LT); }
">"             { return token("GT", sym.GT); }
"+"             { return token("PLUS", sym.PLUS); }
"-"             { return token("MINUS", sym.MINUS); }
"*"             { return token("TIMES", sym.TIMES); }
"/"             { return token("OVER", sym.OVER); }
"("             { return token("LPAREN", sym.LPAREN); }
")"             { return token("RPAREN", sym.RPAREN); }
"["             { return token("LBRACKET", sym.LBRACKET); }
"]"             { return token("RBRACKET", sym.RBRACKET); }
","             { return token("COMMA", sym.COMMA); }
";"             { return token("SEMI", sym.SEMI); }
{numero}        {	String texto = yytext();
			if(texto.length() > 9){
				errorLexico("constante numerica demasiado grande: " + texto);
				return token("NUM", sym.NUM, "0");
			}
			return token("NUM", sym.NUM, texto);
		}
{cadena}        {	String texto = yytext();
			/* se quitan las comillas */
			return token("CADENA", sym.CADENA, texto.substring(1, texto.length() - 1));
		}
\"[^\"\r\n]*    { errorLexico("cadena sin cerrar: falta la comilla de cierre"); }
{identificador}	{ return token("ID", sym.ID, yytext()); }
{nuevalinea}    { /* JFlex cuenta las lineas automaticamente con %line */ }
{espacio}       { /* salto espacios en blanco*/ }
"{"[^}]*"}"     { /* salto comentarios (pueden ocupar varias lineas) */ }
"{"[^}]*        { errorLexico("comentario sin cerrar: falta '}'"); }
.               { errorLexico("caracter ilegal '" + yytext() + "'"); }
