package ve.edu.unet;

import ve.edu.unet.nodosAST.*;
import ve.edu.unet.semantico.RegistroSimbolo;
import ve.edu.unet.semantico.TablaSimbolos;
import ve.edu.unet.semantico.Tipo;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

public class Generador {
	/* ================= DISPOSICION DE LA MEMORIA DE DATOS (Tiny++) =================
	 *
	 *  dMem[max] |  ...       |<- MP inicial (tope de la pila, crece hacia abajo)
	 *            |  pila      |   registros de activacion y temporales
	 *            |   |        |
	 *            |   v        |
	 *            |  libre     |
	 *            |   ^        |
	 *            |   |        |
	 *            |  heap      |   cadenas creadas en ejecucion (read, concatenacion)
	 *            |------------|<- inicioHeap
	 *            | literales  |   cadenas constantes: [longitud, c1, c2, ...]
	 *            | HP         |<- dirHP: celda con la siguiente direccion libre del heap
	 *            | globales   |   variables del cuerpo principal (direccion absoluta)
	 *  dMem[1]   |            |
	 *  dMem[0]   |     0      |<- GP = 0. Siempre vale 0: es la "cadena vacia"
	 *
	 * Una cadena se representa por la DIRECCION de su bloque [longitud, c1, ..., cn],
	 * por eso una variable string ocupa una sola celda (como un int).
	 *
	 * ================= REGISTRO DE ACTIVACION (marco de pila) =====================
	 * Para una llamada f(a1, ..., aN) con L celdas de variables locales:
	 *
	 *      FP+N+1 | a1         |  argumentos (paso por valor), los apila el llamador
	 *      ...    | ...        |
	 *      FP+2   | aN         |
	 *      FP+1   | dir.retorno|  la apila el llamador
	 *      FP     | FP anterior|  enlace de control, lo guarda el llamado (prologo)
	 *      FP-1   | local 1    |  variables locales (inicializadas en 0)
	 *      ...    | ...        |
	 *      FP-L   | local L    |
	 *             | temporales |<- MP
	 *
	 * El valor de retorno de una funcion se devuelve en el registro AC.
	 * =============================================================================== */

	private static TablaSimbolos tablaSimbolos = null;
	private static String nombreArchivo = "programa.tny";

	/* literal -> direccion de su bloque en memoria */
	private static Map<String, Integer> literales;
	private static int dirHP;
	private static int inicioHeap;
	/* nombre legible de cada celda estatica (para el depurador) */
	private static Map<Integer, String> etiquetasMemoria;

	/* Rutinas de tiempo de ejecucion (se emiten solo si el programa las necesita) */
	private static boolean usaEscribirCadena, usaLeerCadena, usaConcatenar, usaComparar, usaArreglos, escribeBool;
	private static int rutinaEscribirCadena, rutinaLeerCadena, rutinaConcatenar, rutinaCompararCadenas, rutinaErrorIndice;

	private static final String MSG_INDICE = "Error en ejecucion: indice de arreglo fuera de rango";

	public static void setTablaSimbolos(TablaSimbolos tabla){
		tablaSimbolos = tabla;
	}

	public static void setNombreArchivo(String nombre){
		nombreArchivo = nombre;
	}

	public static Map<Integer, String> getEtiquetasMemoria(){
		return etiquetasMemoria;
	}

	public static void generarCodigoObjeto(NodoBase raiz){
		if(tablaSimbolos == null){
			throw new IllegalStateException("fije la tabla de simbolos antes de generar codigo objeto");
		}
		UtGen.iniciar();
		literales = new LinkedHashMap<>();
		etiquetasMemoria = new TreeMap<>();
		usaEscribirCadena = usaLeerCadena = usaConcatenar = usaComparar = usaArreglos = escribeBool = false;

		analizarRequerimientos(raiz);
		if(usaArreglos){
			agregarLiteral(MSG_INDICE);
			usaEscribirCadena = true;
		}
		if(escribeBool){
			agregarLiteral("true");
			agregarLiteral("false");
		}
		asignarMemoriaEstatica();

		generarPreludioEstandar();
		generarRutinasEjecucion();
		UtGen.emitirComentario("==== Programa principal ====");
		generarSecuencia(raiz);
		/*Genero el codigo de finalizacion de ejecucion del codigo*/
		UtGen.lineaActual = 0;
		UtGen.emitirComentario("Fin de la ejecucion.");
		UtGen.emitirRO("HALT", 0, 0, 0, "");
	}

	/* ====================================================================
	 *  Paso previo: recolectar literales y saber que rutinas se necesitan
	 * ==================================================================== */

	private static void analizarRequerimientos(NodoBase nodo){
		for(; nodo != null; nodo = nodo.getHermanoDerecha()){
			if(nodo instanceof NodoCadena)
				agregarLiteral(((NodoCadena)nodo).getValor());
			else if(nodo instanceof NodoArreglo)
				usaArreglos = true;
			else if(nodo instanceof NodoEscribir){
				Tipo t = ((NodoEscribir)nodo).getExpresion().getTipo();
				if(t == Tipo.STRING) usaEscribirCadena = true;
				if(t == Tipo.BOOL){ usaEscribirCadena = true; escribeBool = true; }
			}
			else if(nodo instanceof NodoLeer){
				if(((NodoLeer)nodo).getDestino().getTipo() == Tipo.STRING) usaLeerCadena = true;
			}
			else if(nodo instanceof NodoOperacion){
				NodoOperacion op = (NodoOperacion)nodo;
				if(op.getOperacion() == tipoOp.mas && op.getTipo() == Tipo.STRING) usaConcatenar = true;
				if((op.getOperacion() == tipoOp.igual || op.getOperacion() == tipoOp.diferente)
						&& op.getOpDerecho().getTipo() == Tipo.STRING) usaComparar = true;
			}
			/* recorrer los hijos */
			for(NodoBase hijo : hijos(nodo))
				analizarRequerimientos(hijo);
		}
	}

	private static NodoBase[] hijos(NodoBase n){
		if(n instanceof NodoIf) return new NodoBase[]{((NodoIf)n).getPrueba(), ((NodoIf)n).getParteThen(), ((NodoIf)n).getParteElse()};
		if(n instanceof NodoRepeat) return new NodoBase[]{((NodoRepeat)n).getCuerpo(), ((NodoRepeat)n).getPrueba()};
		if(n instanceof NodoWhile) return new NodoBase[]{((NodoWhile)n).getPrueba(), ((NodoWhile)n).getCuerpo()};
		if(n instanceof NodoAsignacion) return new NodoBase[]{((NodoAsignacion)n).getDestino(), ((NodoAsignacion)n).getExpresion()};
		if(n instanceof NodoLeer) return new NodoBase[]{((NodoLeer)n).getDestino()};
		if(n instanceof NodoEscribir) return new NodoBase[]{((NodoEscribir)n).getExpresion()};
		if(n instanceof NodoArreglo) return new NodoBase[]{((NodoArreglo)n).getIndice()};
		if(n instanceof NodoOperacion) return new NodoBase[]{((NodoOperacion)n).getOpIzquierdo(), ((NodoOperacion)n).getOpDerecho()};
		if(n instanceof NodoFuncion) return new NodoBase[]{((NodoFuncion)n).getCuerpo()};
		if(n instanceof NodoLlamada) return new NodoBase[]{((NodoLlamada)n).getArgumentos()};
		if(n instanceof NodoRetorno) return new NodoBase[]{((NodoRetorno)n).getExpresion()};
		return new NodoBase[0];
	}

	private static void agregarLiteral(String valor){
		literales.putIfAbsent(valor, -1);
	}

	/* Ubica la celda HP, los literales y el inicio del heap despues de las globales */
	private static void asignarMemoriaEstatica(){
		etiquetasMemoria.put(0, "(cadena vacia)");
		for(RegistroSimbolo s : tablaSimbolos.getTodos()){
			if(!s.esGlobal() || s.esFuncion()) continue;
			if(s.esArreglo()){
				for(int i = 0; i < s.getTamano(); i++)
					etiquetasMemoria.put(s.getDireccionMemoria() + i, s.getIdentificador() + "[" + i + "]");
			}else{
				etiquetasMemoria.put(s.getDireccionMemoria(), s.getIdentificador());
			}
		}
		dirHP = tablaSimbolos.getTamanoGlobal();
		etiquetasMemoria.put(dirHP, "HP (heap)");
		int siguiente = dirHP + 1;
		for(Map.Entry<String, Integer> e : literales.entrySet()){
			e.setValue(siguiente);
			String nombre = "\"" + abreviar(e.getKey()) + "\"";
			etiquetasMemoria.put(siguiente, nombre + ".longitud");
			for(int i = 0; i < e.getKey().length(); i++)
				etiquetasMemoria.put(siguiente + 1 + i, nombre + "[" + i + "]");
			siguiente += e.getKey().length() + 1;
		}
		inicioHeap = siguiente;
	}

	private static String abreviar(String s){
		return s.length() > 12 ? s.substring(0, 12) + "..." : s;
	}

	/* ====================================================================
	 *  Preludio y rutinas de tiempo de ejecucion
	 * ==================================================================== */

	private static void generarPreludioEstandar(){
		UtGen.emitirComentario("Compilacion TINY++ para el codigo objeto TM");
		UtGen.emitirComentario("Archivo: "+ nombreArchivo);
		/*Genero inicializaciones del preludio estandar*/
		/*Todos los registros en tiny comienzan en cero*/
		UtGen.emitirComentario("Preludio estandar:");
		UtGen.emitirRM("LD", UtGen.MP, 0, UtGen.AC, "cargar la maxima direccion desde la localidad 0");
		UtGen.emitirRM("ST", UtGen.AC, 0, UtGen.AC, "limpio el registro de la localidad 0");
		if(usaLeerCadena || usaConcatenar){
			UtGen.emitirRM("LDC", UtGen.AC, inicioHeap, 0, "inicio del heap de cadenas");
			UtGen.emitirRM("ST", UtGen.AC, dirHP, UtGen.GP, "HP = inicio del heap");
		}
		for(Map.Entry<String, Integer> e : literales.entrySet()){
			String texto = e.getKey();
			int dir = e.getValue();
			UtGen.emitirComentario("literal \"" + texto + "\" en la direccion " + dir);
			UtGen.emitirRM("LDC", UtGen.AC, texto.length(), 0, "longitud de la cadena");
			UtGen.emitirRM("ST", UtGen.AC, dir, UtGen.GP, "guardo la longitud");
			for(int i = 0; i < texto.length(); i++){
				UtGen.emitirRM("LDC", UtGen.AC, texto.charAt(i), 0, "caracter '" + texto.charAt(i) + "'");
				UtGen.emitirRM("ST", UtGen.AC, dir + 1 + i, UtGen.GP, "");
			}
		}
	}

	private static void generarRutinasEjecucion(){
		if(!(usaEscribirCadena || usaLeerCadena || usaConcatenar || usaComparar))
			return;
		int saltoPrincipal = UtGen.emitirSalto(1);
		UtGen.emitirComentario("==== Rutinas de tiempo de ejecucion ====");
		UtGen.emitirComentario("Convencion: se llaman con LDA 3,1(7) + salto; regresan con LDA 7,0(3)");
		if(usaEscribirCadena) generarRutinaEscribirCadena();
		if(usaLeerCadena)     generarRutinaLeerCadena();
		if(usaConcatenar)     generarRutinaConcatenar();
		if(usaComparar)       generarRutinaCompararCadenas();
		if(usaArreglos)       generarRutinaErrorIndice();
		UtGen.parcharSalto(saltoPrincipal, "LDA", UtGen.PC, UtGen.direccionActual(), "salto al programa principal");
	}

	/* escribir_cadena: AC = direccion de la cadena. Escribe sus caracteres y un salto de linea */
	private static void generarRutinaEscribirCadena(){
		rutinaEscribirCadena = UtGen.direccionActual();
		UtGen.emitirComentario("-> rutina escribir_cadena (AC = direccion de la cadena)");
		push(UtGen.R3, "guardo la direccion de retorno");
		UtGen.emitirRM("LD", UtGen.AC1, 0, UtGen.AC, "AC1 = longitud (contador)");
		UtGen.emitirRM("LDA", UtGen.R2, 1, UtGen.AC, "R2 = direccion del primer caracter");
		int ciclo = UtGen.direccionActual();
		int salida = UtGen.emitirSalto(1);
		UtGen.emitirRM("LD", UtGen.R3, 0, UtGen.R2, "R3 = caracter actual");
		UtGen.emitirRO("OUTC", UtGen.R3, 0, 0, "escribo el caracter");
		UtGen.emitirRM("LDA", UtGen.R2, 1, UtGen.R2, "avanzo al siguiente caracter");
		UtGen.emitirRM("LDA", UtGen.AC1, -1, UtGen.AC1, "contador--");
		UtGen.emitirRM_Abs("LDA", UtGen.PC, ciclo, "repito el ciclo");
		UtGen.parcharSalto(salida, "JEQ", UtGen.AC1, UtGen.direccionActual(), "si contador == 0 termino");
		UtGen.emitirRM("LDC", UtGen.R3, '\n', 0, "salto de linea");
		UtGen.emitirRO("OUTC", UtGen.R3, 0, 0, "");
		pop(UtGen.R3, "recupero la direccion de retorno");
		UtGen.emitirRM("LDA", UtGen.PC, 0, UtGen.R3, "regreso");
	}

	/* leer_cadena: lee una linea de la entrada (caracter a caracter con INC) y la
	 * guarda en el heap. Devuelve en AC la direccion de la nueva cadena. */
	private static void generarRutinaLeerCadena(){
		rutinaLeerCadena = UtGen.direccionActual();
		UtGen.emitirComentario("-> rutina leer_cadena (retorna AC = direccion de la cadena leida)");
		push(UtGen.R3, "guardo la direccion de retorno");
		UtGen.emitirRM("LD", UtGen.AC1, dirHP, UtGen.GP, "AC1 = HP (inicio de la nueva cadena)");
		UtGen.emitirRM("LDA", UtGen.R2, 1, UtGen.AC1, "R2 = donde va el primer caracter");
		int ciclo = UtGen.direccionActual();
		UtGen.emitirRO("INC", UtGen.R3, 0, 0, "R3 = siguiente caracter de la entrada");
		UtGen.emitirRM("LDA", UtGen.AC, -'\n', UtGen.R3, "AC = caracter - '\\n'");
		int salida = UtGen.emitirSalto(1);
		UtGen.emitirRM("ST", UtGen.R3, 0, UtGen.R2, "guardo el caracter");
		UtGen.emitirRM("LDA", UtGen.R2, 1, UtGen.R2, "R2++");
		UtGen.emitirRM_Abs("LDA", UtGen.PC, ciclo, "repito el ciclo");
		UtGen.parcharSalto(salida, "JEQ", UtGen.AC, UtGen.direccionActual(), "fin de linea: termino");
		UtGen.emitirRO("SUB", UtGen.AC, UtGen.R2, UtGen.AC1, "AC = longitud + 1");
		UtGen.emitirRM("LDA", UtGen.AC, -1, UtGen.AC, "AC = longitud");
		UtGen.emitirRM("ST", UtGen.AC, 0, UtGen.AC1, "guardo la longitud al inicio del bloque");
		UtGen.emitirRM("ST", UtGen.R2, dirHP, UtGen.GP, "HP = primera celda libre");
		UtGen.emitirRM("LDA", UtGen.AC, 0, UtGen.AC1, "AC = direccion de la cadena");
		pop(UtGen.R3, "recupero la direccion de retorno");
		UtGen.emitirRM("LDA", UtGen.PC, 0, UtGen.R3, "regreso");
	}

	/* concatenar: AC1 = cadena izquierda, AC = cadena derecha. Crea la cadena
	 * resultado en el heap y devuelve su direccion en AC. */
	private static void generarRutinaConcatenar(){
		rutinaConcatenar = UtGen.direccionActual();
		UtGen.emitirComentario("-> rutina concatenar (AC1 + AC, retorna AC = nueva cadena)");
		push(UtGen.R3, "guardo la direccion de retorno");
		push(UtGen.AC, "guardo la cadena derecha");
		UtGen.emitirRM("LD", UtGen.R2, dirHP, UtGen.GP, "R2 = HP = bloque del resultado");
		push(UtGen.R2, "guardo la direccion del resultado");
		UtGen.emitirRM("LDA", UtGen.R2, 1, UtGen.R2, "R2 = destino del primer caracter");
		copiarCadena("izquierda");
		UtGen.emitirRM("LD", UtGen.AC1, 2, UtGen.MP, "AC1 = cadena derecha (guardada en la pila)");
		copiarCadena("derecha");
		UtGen.emitirRM("ST", UtGen.R2, dirHP, UtGen.GP, "HP = primera celda libre");
		pop(UtGen.AC, "AC = direccion del resultado");
		UtGen.emitirRO("SUB", UtGen.AC1, UtGen.R2, UtGen.AC, "AC1 = longitud + 1");
		UtGen.emitirRM("LDA", UtGen.AC1, -1, UtGen.AC1, "AC1 = longitud total");
		UtGen.emitirRM("ST", UtGen.AC1, 0, UtGen.AC, "guardo la longitud");
		UtGen.emitirRM("LDA", UtGen.MP, 1, UtGen.MP, "descarto la cadena derecha de la pila");
		pop(UtGen.R3, "recupero la direccion de retorno");
		UtGen.emitirRM("LDA", UtGen.PC, 0, UtGen.R3, "regreso");
	}

	/* copia los caracteres de la cadena AC1 en R2, R2 avanza (usa R3 y AC) */
	private static void copiarCadena(String cual){
		UtGen.emitirRM("LD", UtGen.R3, 0, UtGen.AC1, "R3 = longitud de la cadena " + cual);
		UtGen.emitirRM("LDA", UtGen.AC1, 1, UtGen.AC1, "AC1 = primer caracter");
		int ciclo = UtGen.direccionActual();
		int salida = UtGen.emitirSalto(1);
		UtGen.emitirRM("LD", UtGen.AC, 0, UtGen.AC1, "copio un caracter");
		UtGen.emitirRM("ST", UtGen.AC, 0, UtGen.R2, "");
		UtGen.emitirRM("LDA", UtGen.AC1, 1, UtGen.AC1, "origen++");
		UtGen.emitirRM("LDA", UtGen.R2, 1, UtGen.R2, "destino++");
		UtGen.emitirRM("LDA", UtGen.R3, -1, UtGen.R3, "contador--");
		UtGen.emitirRM_Abs("LDA", UtGen.PC, ciclo, "repito el ciclo");
		UtGen.parcharSalto(salida, "JEQ", UtGen.R3, UtGen.direccionActual(), "fin de la copia");
	}

	/* comparar_cadenas: AC1 y AC son cadenas; retorna AC = 1 si son iguales, 0 si no */
	private static void generarRutinaCompararCadenas(){
		rutinaCompararCadenas = UtGen.direccionActual();
		UtGen.emitirComentario("-> rutina comparar_cadenas (AC = (AC1 == AC))");
		push(UtGen.R3, "guardo la direccion de retorno");
		push(UtGen.FP, "guardo FP: se usa como registro auxiliar");
		UtGen.emitirRM("LD", UtGen.R2, 0, UtGen.AC1, "R2 = longitud izquierda");
		UtGen.emitirRM("LD", UtGen.R3, 0, UtGen.AC, "R3 = longitud derecha");
		UtGen.emitirRO("SUB", UtGen.R2, UtGen.R2, UtGen.R3, "");
		int distintaLongitud = UtGen.emitirSalto(1);
		UtGen.emitirRM("LDA", UtGen.AC1, 1, UtGen.AC1, "apunto a los primeros caracteres");
		UtGen.emitirRM("LDA", UtGen.AC, 1, UtGen.AC, "");
		int ciclo = UtGen.direccionActual();
		int iguales = UtGen.emitirSalto(1);
		UtGen.emitirRM("LD", UtGen.R2, 0, UtGen.AC1, "caracter izquierdo");
		UtGen.emitirRM("LD", UtGen.FP, 0, UtGen.AC, "caracter derecho");
		UtGen.emitirRO("SUB", UtGen.R2, UtGen.R2, UtGen.FP, "");
		int distintoCaracter = UtGen.emitirSalto(1);
		UtGen.emitirRM("LDA", UtGen.AC1, 1, UtGen.AC1, "avanzo");
		UtGen.emitirRM("LDA", UtGen.AC, 1, UtGen.AC, "");
		UtGen.emitirRM("LDA", UtGen.R3, -1, UtGen.R3, "contador--");
		UtGen.emitirRM_Abs("LDA", UtGen.PC, ciclo, "repito el ciclo");
		UtGen.parcharSalto(iguales, "JEQ", UtGen.R3, UtGen.direccionActual(), "sin caracteres pendientes: iguales");
		UtGen.emitirRM("LDC", UtGen.AC, 1, 0, "AC = 1 (iguales)");
		UtGen.emitirRM("LDA", UtGen.PC, 1, UtGen.PC, "salto el caso falso");
		int falso = UtGen.direccionActual();
		UtGen.parcharSalto(distintaLongitud, "JNE", UtGen.R2, falso, "longitudes distintas");
		UtGen.parcharSalto(distintoCaracter, "JNE", UtGen.R2, falso, "caracteres distintos");
		UtGen.emitirRM("LDC", UtGen.AC, 0, 0, "AC = 0 (distintas)");
		pop(UtGen.FP, "restauro FP");
		pop(UtGen.R3, "recupero la direccion de retorno");
		UtGen.emitirRM("LDA", UtGen.PC, 0, UtGen.R3, "regreso");
	}

	/* error_indice: escribe un mensaje y detiene la maquina */
	private static void generarRutinaErrorIndice(){
		rutinaErrorIndice = UtGen.direccionActual();
		UtGen.emitirComentario("-> rutina error_indice");
		UtGen.emitirRM("LDC", UtGen.AC, literales.get(MSG_INDICE), 0, "AC = mensaje de error");
		llamarRutina(rutinaEscribirCadena, "escribo el mensaje");
		UtGen.emitirRO("HALT", 0, 0, 0, "detengo la ejecucion");
	}

	/* ---------- utilidades de pila ---------- */

	private static void push(int registro, String c){
		UtGen.emitirRM("ST", registro, 0, UtGen.MP, "push: " + c);
		UtGen.emitirRM("LDA", UtGen.MP, -1, UtGen.MP, "MP--");
	}

	private static void pop(int registro, String c){
		UtGen.emitirRM("LDA", UtGen.MP, 1, UtGen.MP, "MP++");
		UtGen.emitirRM("LD", registro, 0, UtGen.MP, "pop: " + c);
	}

	private static void llamarRutina(int direccion, String c){
		UtGen.emitirRM("LDA", UtGen.R3, 1, UtGen.PC, "R3 = direccion de retorno");
		UtGen.emitirRM_Abs("LDA", UtGen.PC, direccion, "llamo a la rutina: " + c);
	}

	/* ====================================================================
	 *  Sentencias
	 * ==================================================================== */

	private static void generarSecuencia(NodoBase nodo){
		while(nodo != null){
			generarSentencia(nodo);
			nodo = nodo.getHermanoDerecha();
		}
	}

	//Funcion principal de generacion de codigo
	//prerequisito: Fijar la tabla de simbolos antes de generar el codigo objeto
	private static void generarSentencia(NodoBase nodo){
		UtGen.lineaActual = nodo.getLinea();
		if (nodo instanceof  NodoIf){
			generarIf(nodo);
		}else if (nodo instanceof  NodoRepeat){
			generarRepeat(nodo);
		}else if (nodo instanceof  NodoWhile){
			generarWhile(nodo);
		}else if (nodo instanceof  NodoAsignacion){
			generarAsignacion(nodo);
		}else if (nodo instanceof  NodoLeer){
			generarLeer(nodo);
		}else if (nodo instanceof  NodoEscribir){
			generarEscribir(nodo);
		}else if (nodo instanceof NodoDeclaracion){
			generarDeclaracion(nodo);
		}else if (nodo instanceof NodoFuncion){
			generarFuncion(nodo);
		}else if (nodo instanceof NodoLlamada){
			generarLlamada(nodo);
		}else if (nodo instanceof NodoRetorno){
			generarRetorno(nodo);
		}else{
			UtGen.emitirComentario("BUG: Tipo de nodo a generar desconocido");
		}
	}

	private static void generarIf(NodoBase nodo){
		NodoIf n = (NodoIf)nodo;
		int localidadSaltoElse, localidadSaltoEnd = -1;
		if(UtGen.debug)	UtGen.emitirComentario("-> if");
		/*Genero el codigo para la parte de prueba del IF*/
		generarExpresion(n.getPrueba());
		localidadSaltoElse = UtGen.emitirSalto(1);
		UtGen.emitirComentario("If: el salto hacia el else debe estar aqui");
		/*Genero la parte THEN*/
		generarSecuencia(n.getParteThen());
		if(n.getParteElse()!=null){
			localidadSaltoEnd = UtGen.emitirSalto(1);
			UtGen.emitirComentario("If: el salto hacia el final debe estar aqui");
		}
		UtGen.parcharSalto(localidadSaltoElse, "JEQ", UtGen.AC, UtGen.direccionActual(), "if: jmp hacia else (condicion falsa)");
		/*Genero la parte ELSE*/
		if(n.getParteElse()!=null){
			generarSecuencia(n.getParteElse());
			UtGen.parcharSalto(localidadSaltoEnd, "LDA", UtGen.PC, UtGen.direccionActual(), "if: jmp hacia el final");
		}
		if(UtGen.debug)	UtGen.emitirComentario("<- if");
	}

	private static void generarRepeat(NodoBase nodo){
		NodoRepeat n = (NodoRepeat)nodo;
		int localidadSaltoInicio;
		if(UtGen.debug)	UtGen.emitirComentario("-> repeat");
		localidadSaltoInicio = UtGen.direccionActual();
		/* Genero el cuerpo del repeat */
		generarSecuencia(n.getCuerpo());
		/* Genero el codigo de la prueba del repeat */
		UtGen.lineaActual = n.getPrueba().getLinea();
		generarExpresion(n.getPrueba());
		UtGen.emitirRM_Abs("JEQ", UtGen.AC, localidadSaltoInicio, "repeat: jmp hacia el inicio del cuerpo (condicion falsa)");
		if(UtGen.debug)	UtGen.emitirComentario("<- repeat");
	}

	private static void generarWhile(NodoBase nodo){
		NodoWhile n = (NodoWhile)nodo;
		if(UtGen.debug)	UtGen.emitirComentario("-> while");
		int inicio = UtGen.direccionActual();
		generarExpresion(n.getPrueba());
		int saltoFin = UtGen.emitirSalto(1);
		UtGen.emitirComentario("While: el salto hacia el final debe estar aqui");
		generarSecuencia(n.getCuerpo());
		UtGen.lineaActual = n.getLinea();
		UtGen.emitirRM_Abs("LDA", UtGen.PC, inicio, "while: jmp hacia la evaluacion de la condicion");
		UtGen.parcharSalto(saltoFin, "JEQ", UtGen.AC, UtGen.direccionActual(), "while: condicion falsa, salgo del ciclo");
		if(UtGen.debug)	UtGen.emitirComentario("<- while");
	}

	private static void generarDeclaracion(NodoBase nodo){
		NodoDeclaracion n = (NodoDeclaracion)nodo;
		RegistroSimbolo s = n.getSimbolo();
		if(UtGen.debug && s != null)
			UtGen.emitirComentario("declaracion " + n.getTipoDeclarado() + " " + n.getNombre()
					+ (n.esArreglo() ? "[" + n.getTamano() + "]" : "")
					+ (s.esGlobal() ? " en dMem[" + s.getDireccionMemoria() + "]" : " en FP" + s.getDireccionMemoria()));
	}

	private static void generarAsignacion(NodoBase nodo){
		NodoAsignacion n = (NodoAsignacion)nodo;
		if(UtGen.debug)	UtGen.emitirComentario("-> asignacion");
		/* Genero el codigo para la expresion a la derecha de la asignacion */
		generarExpresion(n.getExpresion());
		/* Ahora almaceno el valor resultante */
		almacenar(n.getDestino(), "asignacion");
		if(UtGen.debug)	UtGen.emitirComentario("<- asignacion");
	}

	/* Guarda el valor de AC en el destino (variable o elemento de arreglo) */
	private static void almacenar(NodoBase destino, String c){
		if(destino instanceof NodoIdentificador){
			RegistroSimbolo s = ((NodoIdentificador)destino).getSimbolo();
			UtGen.emitirRM("ST", UtGen.AC, s.getDireccionMemoria(), base(s), c + ": almaceno el valor para el id " + s.getIdentificador());
		}else{
			NodoArreglo a = (NodoArreglo)destino;
			push(UtGen.AC, "guardo el valor a almacenar");
			generarDireccionElemento(a);
			pop(UtGen.AC1, "AC1 = valor a almacenar");
			UtGen.emitirRM("ST", UtGen.AC1, 0, UtGen.AC, c + ": almaceno en " + a.getNombre() + "[...]");
		}
	}

	private static void generarLeer(NodoBase nodo){
		NodoLeer n = (NodoLeer)nodo;
		if(UtGen.debug)	UtGen.emitirComentario("-> leer");
		switch(n.getDestino().getTipo()){
			case STRING:
				llamarRutina(rutinaLeerCadena, "leer_cadena");
				break;
			case BOOL:
				UtGen.emitirRO("IN", UtGen.AC, 0, 0, "leer: lee un valor (0 = false, otro = true)");
				UtGen.emitirRM("JEQ", UtGen.AC, 1, UtGen.PC, "si es 0 queda en false");
				UtGen.emitirRM("LDC", UtGen.AC, 1, 0, "cualquier otro valor es true (1)");
				break;
			default:
				UtGen.emitirRO("IN", UtGen.AC, 0, 0, "leer: lee un valor entero ");
		}
		almacenar(n.getDestino(), "leer");
		if(UtGen.debug)	UtGen.emitirComentario("<- leer");
	}

	private static void generarEscribir(NodoBase nodo){
		NodoEscribir n = (NodoEscribir)nodo;
		if(UtGen.debug)	UtGen.emitirComentario("-> escribir");
		/* Genero el codigo de la expresion que va a ser escrita en pantalla */
		generarExpresion(n.getExpresion());
		/* Ahora genero la salida */
		switch(n.getExpresion().getTipo()){
			case STRING:
				llamarRutina(rutinaEscribirCadena, "escribir_cadena");
				break;
			case BOOL:
				UtGen.emitirRM("JEQ", UtGen.AC, 2, UtGen.PC, "si es falso salto a cargar \"false\"");
				UtGen.emitirRM("LDC", UtGen.AC, literales.get("true"), 0, "AC = \"true\"");
				UtGen.emitirRM("LDA", UtGen.PC, 1, UtGen.PC, "salto la carga de \"false\"");
				UtGen.emitirRM("LDC", UtGen.AC, literales.get("false"), 0, "AC = \"false\"");
				llamarRutina(rutinaEscribirCadena, "escribir_cadena");
				break;
			default:
				UtGen.emitirRO("OUT", UtGen.AC, 0, 0, "escribir: genero la salida de la expresion");
		}
		if(UtGen.debug)	UtGen.emitirComentario("<- escribir");
	}

	/* ---------- subrutinas ---------- */

	private static void generarFuncion(NodoBase nodo){
		NodoFuncion f = (NodoFuncion)nodo;
		RegistroSimbolo s = f.getSimbolo();
		int locales = s.getTamanoLocales();
		int parametros = s.getTiposParametros().size();
		UtGen.emitirComentario("-> " + (f.esProcedimiento() ? "procedimiento " : "funcion ") + f.getNombre()
				+ " (" + parametros + " parametros, " + locales + " celdas locales)");
		/* el cuerpo solo se ejecuta cuando se llama: el flujo normal lo salta */
		int saltoCuerpo = UtGen.emitirSalto(1);
		s.setDireccionEntrada(UtGen.direccionActual());

		UtGen.emitirComentario("prologo de " + f.getNombre());
		UtGen.emitirRM("ST", UtGen.FP, 0, UtGen.MP, "guardo el FP del llamador");
		UtGen.emitirRM("LDA", UtGen.FP, 0, UtGen.MP, "FP = base del nuevo registro de activacion");
		UtGen.emitirRM("LDA", UtGen.MP, -(locales + 1), UtGen.FP, "MP = FP - " + (locales + 1) + " (reservo las variables locales)");
		if(locales > 0){
			/* inicializo las locales en 0 (string vacio / false) */
			UtGen.emitirRM("LDC", UtGen.AC1, locales, 0, "AC1 = cantidad de celdas locales");
			UtGen.emitirRM("LDA", UtGen.R2, -1, UtGen.FP, "R2 = direccion de la primera local");
			UtGen.emitirRM("LDC", UtGen.R3, 0, 0, "R3 = 0");
			int ciclo = UtGen.direccionActual();
			UtGen.emitirRM("JEQ", UtGen.AC1, 4, UtGen.PC, "si no quedan celdas termino");
			UtGen.emitirRM("ST", UtGen.R3, 0, UtGen.R2, "local = 0");
			UtGen.emitirRM("LDA", UtGen.R2, -1, UtGen.R2, "siguiente celda");
			UtGen.emitirRM("LDA", UtGen.AC1, -1, UtGen.AC1, "contador--");
			UtGen.emitirRM_Abs("LDA", UtGen.PC, ciclo, "repito");
		}

		generarSecuencia(f.getCuerpo());

		UtGen.lineaActual = f.getLinea();
		UtGen.emitirComentario("fin de " + f.getNombre() + " sin return: retorno por defecto");
		UtGen.emitirRM("LDC", UtGen.AC, 0, 0, "valor de retorno por defecto");
		generarEpilogo(parametros);
		s.setDireccionFin(UtGen.direccionActual());
		UtGen.parcharSalto(saltoCuerpo, "LDA", UtGen.PC, UtGen.direccionActual(), "salto el cuerpo de " + f.getNombre());
		UtGen.emitirComentario("<- " + f.getNombre());
	}

	/* Destruye el registro de activacion y regresa al llamador (el resultado queda en AC) */
	private static void generarEpilogo(int parametros){
		UtGen.emitirRM("LD", UtGen.AC1, 1, UtGen.FP, "epilogo: AC1 = direccion de retorno");
		UtGen.emitirRM("LDA", UtGen.MP, parametros + 1, UtGen.FP, "libero el marco y los " + parametros + " argumentos");
		UtGen.emitirRM("LD", UtGen.FP, 0, UtGen.FP, "restauro el FP del llamador");
		UtGen.emitirRM("LDA", UtGen.PC, 0, UtGen.AC1, "regreso al llamador");
	}

	private static void generarRetorno(NodoBase nodo){
		NodoRetorno r = (NodoRetorno)nodo;
		if(UtGen.debug)	UtGen.emitirComentario("-> return");
		if(r.getExpresion() != null)
			generarExpresion(r.getExpresion());
		generarEpilogo(r.getFuncion().getTiposParametros().size());
		if(UtGen.debug)	UtGen.emitirComentario("<- return");
	}

	/* Llamada: apila los argumentos y la direccion de retorno y salta a la subrutina */
	private static void generarLlamada(NodoBase nodo){
		NodoLlamada n = (NodoLlamada)nodo;
		RegistroSimbolo s = n.getSimbolo();
		if(UtGen.debug)	UtGen.emitirComentario("-> llamada a " + n.getNombre());
		int i = 1;
		for(NodoBase a = n.getArgumentos(); a != null; a = a.getHermanoDerecha(), i++){
			generarExpresion(a);
			push(UtGen.AC, "argumento " + i);
		}
		UtGen.emitirRM("LDA", UtGen.AC, 3, UtGen.PC, "AC = direccion de retorno (3 instrucciones mas adelante)");
		push(UtGen.AC, "direccion de retorno");
		UtGen.emitirRM_Abs("LDA", UtGen.PC, s.getDireccionEntrada(), "salto a " + n.getNombre());
		if(UtGen.debug)	UtGen.emitirComentario("<- llamada a " + n.getNombre() + " (resultado en AC)");
	}

	/* ====================================================================
	 *  Expresiones: el resultado siempre queda en AC
	 * ==================================================================== */

	private static void generarExpresion(NodoBase nodo){
		if (nodo instanceof NodoValor){
			generarValor(nodo);
		}else if (nodo instanceof NodoBooleano){
			boolean v = ((NodoBooleano)nodo).getValor();
			UtGen.emitirRM("LDC", UtGen.AC, v ? 1 : 0, 0, "cargar constante: " + v);
		}else if (nodo instanceof NodoCadena){
			String v = ((NodoCadena)nodo).getValor();
			UtGen.emitirRM("LDC", UtGen.AC, literales.get(v), 0, "cargar direccion del literal \"" + v + "\"");
		}else if (nodo instanceof NodoIdentificador){
			generarIdentificador(nodo);
		}else if (nodo instanceof NodoArreglo){
			NodoArreglo a = (NodoArreglo)nodo;
			generarDireccionElemento(a);
			UtGen.emitirRM("LD", UtGen.AC, 0, UtGen.AC, "cargar valor de " + a.getNombre() + "[...]");
		}else if (nodo instanceof NodoLlamada){
			generarLlamada(nodo);
		}else if (nodo instanceof NodoOperacion){
			generarOperacion(nodo);
		}else{
			UtGen.emitirComentario("BUG: Tipo de expresion desconocida");
		}
	}

	private static void generarValor(NodoBase nodo){
		NodoValor n = (NodoValor)nodo;
		if(UtGen.debug)	UtGen.emitirComentario("-> constante");
		UtGen.emitirRM("LDC", UtGen.AC, n.getValor(), 0, "cargar constante: "+n.getValor());
		if(UtGen.debug)	UtGen.emitirComentario("<- constante");
	}

	/* Registro base para acceder a una variable: GP (global) o FP (local/parametro) */
	private static int base(RegistroSimbolo s){
		return s.esGlobal() ? UtGen.GP : UtGen.FP;
	}

	private static void generarIdentificador(NodoBase nodo){
		NodoIdentificador n = (NodoIdentificador)nodo;
		RegistroSimbolo s = n.getSimbolo();
		UtGen.emitirRM("LD", UtGen.AC, s.getDireccionMemoria(), base(s), "cargar valor de identificador: "+n.getNombre());
	}

	/* Calcula en AC la direccion de A[indice]:  direccion_base(A) + indice
	 * verificando en ejecucion que 0 <= indice < tamano */
	private static void generarDireccionElemento(NodoArreglo a){
		RegistroSimbolo s = a.getSimbolo();
		if(UtGen.debug)	UtGen.emitirComentario("-> direccion de " + a.getNombre() + "[indice]");
		generarExpresion(a.getIndice());
		UtGen.emitirRM_Abs("JLT", UtGen.AC, rutinaErrorIndice, "indice < 0: error");
		UtGen.emitirRM("LDC", UtGen.AC1, s.getTamano(), 0, "AC1 = tamano del arreglo");
		UtGen.emitirRO("SUB", UtGen.AC1, UtGen.AC, UtGen.AC1, "AC1 = indice - tamano");
		UtGen.emitirRM_Abs("JGE", UtGen.AC1, rutinaErrorIndice, "indice >= tamano: error");
		UtGen.emitirRO("ADD", UtGen.AC, UtGen.AC, base(s), "AC = indice + " + (s.esGlobal() ? "GP" : "FP"));
		UtGen.emitirRM("LDA", UtGen.AC, s.getDireccionMemoria(), UtGen.AC, "AC = direccion de " + a.getNombre() + "[indice]");
		if(UtGen.debug)	UtGen.emitirComentario("<- direccion de " + a.getNombre() + "[indice]");
	}

	private static void generarOperacion(NodoBase nodo){
		NodoOperacion n = (NodoOperacion) nodo;
		if(UtGen.debug)	UtGen.emitirComentario("-> Operacion: " + n.getOperacion());
		if(n.getOperacion() == tipoOp.no){
			generarExpresion(n.getOpDerecho());
			UtGen.emitirRM("LDC", UtGen.AC1, 1, 0, "not: AC1 = 1");
			UtGen.emitirRO("SUB", UtGen.AC, UtGen.AC1, UtGen.AC, "not: AC = 1 - AC");
			if(UtGen.debug)	UtGen.emitirComentario("<- Operacion: " + n.getOperacion());
			return;
		}
		boolean cadenas = n.getOpDerecho().getTipo() == Tipo.STRING;
		/* Genero la expresion izquierda de la operacion */
		generarExpresion(n.getOpIzquierdo());
		/* Almaceno en la pila de valores temporales el valor de la operacion izquierda */
		push(UtGen.AC, "resultado de la expresion izquierda");
		/* Genero la expresion derecha de la operacion */
		generarExpresion(n.getOpDerecho());
		/* Ahora cargo/saco de la pila el valor izquierdo */
		pop(UtGen.AC1, "AC1 = valor izquierdo");
		switch(n.getOperacion()){
			case	mas:
				if(cadenas)
					llamarRutina(rutinaConcatenar, "concatenar");
				else
					UtGen.emitirRO("ADD", UtGen.AC, UtGen.AC1, UtGen.AC, "op: +");
				break;
			case	menos:	UtGen.emitirRO("SUB", UtGen.AC, UtGen.AC1, UtGen.AC, "op: -");
				break;
			case	por:	UtGen.emitirRO("MUL", UtGen.AC, UtGen.AC1, UtGen.AC, "op: *");
				break;
			case	entre:	UtGen.emitirRO("DIV", UtGen.AC, UtGen.AC1, UtGen.AC, "op: /");
				break;
			case	menor:		comparacion("JLT", "<");  break;
			case	menorIgual:	comparacion("JLE", "<="); break;
			case	mayor:		comparacion("JGT", ">");  break;
			case	mayorIgual:	comparacion("JGE", ">="); break;
			case	igual:
				if(cadenas) llamarRutina(rutinaCompararCadenas, "comparar_cadenas");
				else comparacion("JEQ", "=");
				break;
			case	diferente:
				if(cadenas){
					llamarRutina(rutinaCompararCadenas, "comparar_cadenas");
					UtGen.emitirRM("LDC", UtGen.AC1, 1, 0, "");
					UtGen.emitirRO("SUB", UtGen.AC, UtGen.AC1, UtGen.AC, "<>: niego el resultado");
				}
				else comparacion("JNE", "<>");
				break;
			case	y:	UtGen.emitirRO("MUL", UtGen.AC, UtGen.AC1, UtGen.AC, "op: and (1*1 = 1)");
				break;
			case	o:	UtGen.emitirRO("ADD", UtGen.AC, UtGen.AC1, UtGen.AC, "op: or (suma)");
				UtGen.emitirRM("JEQ", UtGen.AC, 1, UtGen.PC, "si la suma es 0 el resultado es false");
				UtGen.emitirRM("LDC", UtGen.AC, 1, 0, "si no, true (1)");
				break;
			default:
				UtGen.emitirComentario("BUG: tipo de operacion desconocida");
		}
		if(UtGen.debug)	UtGen.emitirComentario("<- Operacion: " + n.getOperacion());
	}

	/* AC = (AC1 op AC) ? 1 : 0   usando el salto condicional "salto" sobre AC1 - AC */
	private static void comparacion(String salto, String op){
		UtGen.emitirRO("SUB", UtGen.AC, UtGen.AC1, UtGen.AC, "op: " + op);
		UtGen.emitirRM(salto, UtGen.AC, 2, UtGen.PC, "voy dos instrucciones mas alla si es verdadero");
		UtGen.emitirRM("LDC", UtGen.AC, 0, UtGen.AC, "caso de falso (AC=0)");
		UtGen.emitirRM("LDA", UtGen.PC, 1, UtGen.PC, "Salto incodicional a direccion: PC+1 (es falso evito colocarlo verdadero)");
		UtGen.emitirRM("LDC", UtGen.AC, 1, UtGen.AC, "caso de verdadero (AC=1)");
	}
}
