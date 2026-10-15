package ve.edu.unet;

import java_cup.runtime.DefaultSymbolFactory;
import java_cup.runtime.SymbolFactory;
import ve.edu.unet.nodosAST.NodoBase;
import ve.edu.unet.nodosAST.Util;
import ve.edu.unet.semantico.AnalizadorSemantico;
import ve.edu.unet.semantico.RegistroSimbolo;
import ve.edu.unet.semantico.TablaSimbolos;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/* Fachada del compilador: ejecuta todas las fases en orden
 *   lexico+sintactico (JFlex/CUP) -> AST -> semantico -> generacion de codigo TM
 * La usan tanto la linea de comandos (Main) como el IDE. */
public class Compilador {

	public static class Resultado {
		public boolean exito;
		public ListaErrores errores;
		public String ast = "";
		public String tablaSimbolos = "";
		public String codigoTM = "";
		/* lineasFuente[i] = linea del programa que genero la instruccion i */
		public int[] lineasFuente = new int[0];
		/* nombre de las celdas estaticas de la memoria de datos (variables globales, literales) */
		public Map<Integer, String> etiquetasMemoria = Collections.emptyMap();
		/* subrutinas: rango de instrucciones y nombre de cada celda de su marco */
		public List<MarcoFuncion> marcos = new ArrayList<>();
	}

	/* Informacion para el depurador: mientras el PC esta en [inicio, fin) el marco
	 * apuntado por FP pertenece a esta funcion; desplazamientos: offset(FP) -> nombre */
	public static class MarcoFuncion {
		public final String nombre;
		public final int inicio, fin;
		public final Map<Integer, String> desplazamientos = new TreeMap<>();

		MarcoFuncion(String nombre, int inicio, int fin) {
			this.nombre = nombre;
			this.inicio = inicio;
			this.fin = fin;
		}
	}

	@SuppressWarnings("deprecation")  // DefaultSymbolFactory, igual que en el Tiny original
	public static Resultado compilar(String fuente, String nombreArchivo){
		Resultado r = new Resultado();
		ListaErrores errores = new ListaErrores();
		r.errores = errores;

		/* 1. Analisis lexico y sintactico: construye el AST */
		NodoBase raiz = null;
		try {
			SymbolFactory sf = new DefaultSymbolFactory();
			parser p = new parser(new Lexico(new StringReader(fuente), sf, errores), sf, errores);
			p.parse();
			raiz = p.action_obj.getASTroot();
		} catch (Exception e) {
			errores.agregar(ErrorCompilacion.Fase.SINTACTICO, 1, "fallo inesperado del analizador: " + e.getMessage());
		}
		if(errores.hayErrores()){
			r.exito = false;
			return r;
		}
		r.ast = Util.astComoTexto(raiz);

		/* 2. Analisis semantico: ambitos, tipos, direcciones */
		TablaSimbolos tabla = new TablaSimbolos();
		new AnalizadorSemantico(tabla, errores).analizar(raiz);
		if(errores.hayErrores()){
			r.tablaSimbolos = tabla.comoTexto();
			r.exito = false;
			return r;
		}

		/* 3. Generacion de codigo objeto para la TM */
		Generador.setTablaSimbolos(tabla);
		Generador.setNombreArchivo(nombreArchivo);
		Generador.generarCodigoObjeto(raiz);
		r.codigoTM = UtGen.getCodigo();
		r.lineasFuente = UtGen.getLineasFuente();
		r.etiquetasMemoria = Generador.getEtiquetasMemoria();
		for(RegistroSimbolo f : tabla.getTodos()){
			if(!f.esFuncion()) continue;
			MarcoFuncion m = new MarcoFuncion(f.getIdentificador(), f.getDireccionEntrada(), f.getDireccionFin());
			for(RegistroSimbolo v : tabla.getTodos()){
				if(v.getFuncion() != f) continue;
				for(int i = 0; i < v.getTamano(); i++)
					m.desplazamientos.put(v.getDireccionMemoria() + i, v.getIdentificador() + (v.esArreglo() ? "[" + i + "]" : ""));
			}
			r.marcos.add(m);
		}
		r.tablaSimbolos = tabla.comoTexto();   // ya incluye las direcciones de entrada de las funciones
		r.exito = true;
		return r;
	}
}
