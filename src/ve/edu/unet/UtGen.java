package ve.edu.unet;

import java.util.Arrays;

/* La idea principal de esta clase (Utilidades de Generacion)es ayudar a emitir las
 * sentencias en el asembler de la Tiny Machine (TM), haciendo mas sencilla la
 * implementacion de un generador de codigo objeto para la misma.
 * Esta clase maneja y ayuda gestionar el numero de linea (localidad de sentencia)
 * en la cual se debe emitir una instruccion TM.
 *
 * Tiny++: el codigo ya no se imprime por pantalla sino que se acumula en un
 * StringBuilder (getCodigo) y ademas se recuerda, para cada direccion de
 * instruccion, la linea del codigo fuente que la genero (usado por el depurador).
 */

public class UtGen {
	private static int instruccionActual=0;	//Direccion (num linea) actual de emision de la instruccion
	private static int instruccionMasAlta=0;	//Almacena la direccion de la instruccion que ha resultado ser la mayor hasta ahora
	public static boolean debug=true;

	private static StringBuilder codigo = new StringBuilder();
	/* lineaFuente[i] = linea del programa Tiny++ que genero la instruccion i (0 = ninguna) */
	private static int[] lineaFuente = new int[256];
	/* Linea del codigo fuente que se esta traduciendo en este momento (la fija el Generador) */
	public static int lineaActual = 0;

	/* PC = program counter, registro[7] donde se almacena la direccion (linea)
	 *  actual de ejecucion del codigo objeto
	 */
	public static final int  PC=7;

	/* mp = "memory pointer" coloca al registro[6] a apuntar al tope de la pila.
	 * En Tiny++ es el apuntador de pila: apunta a la PRIMERA CELDA LIBRE y la pila
	 * crece hacia direcciones menores (push: ST x,0(MP) ; LDA MP,-1(MP)).
	 */
	public static final int  MP=6;

	/* gp = "global pointer" coloca al registro[5] a apuntar
	 * al inicio de la memoria para el almacenamiento de variables globales
	 */
	public static final int  GP=5;

	/* fp = "frame pointer" registro[4]: base del registro de activacion de la
	 * funcion en ejecucion (parametros en FP+k, locales en FP-k)
	 */
	public static final int  FP=4;

	/* Registros auxiliares usados por las rutinas de tiempo de ejecucion */
	public static final int  R3=3;
	public static final int  R2=2;

	/* Defino al registro[0] como el acumulador 1 */
	public static final int  AC=0;

	/* Defino al registro[1] como el acumulador 2 */
	public static final int  AC1=1;

	/* Reinicia el estado para una nueva compilacion */
	public static void iniciar(){
		instruccionActual = 0;
		instruccionMasAlta = 0;
		lineaActual = 0;
		codigo = new StringBuilder();
		lineaFuente = new int[256];
	}

	public static String getCodigo(){
		return codigo.toString();
	}

	public static int getCantidadInstrucciones(){
		return instruccionMasAlta;
	}

	public static int[] getLineasFuente(){
		return Arrays.copyOf(lineaFuente, instruccionMasAlta);
	}

	public static void emitirComentario(String c){
		if(debug) codigo.append("*      ").append(c).append('\n');
	}

	private static void registrarLinea(int direccion){
		if(direccion >= lineaFuente.length)
			lineaFuente = Arrays.copyOf(lineaFuente, Math.max(direccion + 1, lineaFuente.length * 2));
		lineaFuente[direccion] = lineaActual;
	}

	private static void emitirLinea(String texto, String c){
		codigo.append(texto);
		if(debug)
			codigo.append("      ").append(c);
		codigo.append('\n');
	}

	/* Este procedimiento emite sentencias RO (Solo Registro)
	 * de la TM    opcode r,s,t
	 * r[r] = reg[s] op reg[t]
	 *
	 * op = codigo de la operacion
	 * r = registro destino
	 * s = primero registro operando
	 * t = segundo registro operando
	 * c = comentario a emitir en modo debug
	 */
	public static void emitirRO(String op, int r, int s, int t, String c){
		registrarLinea(instruccionActual);
		emitirLinea((instruccionActual++)+":       "+op+"       "+r+","+s+","+t, c);
		if(instruccionMasAlta < instruccionActual)
			instruccionMasAlta = instruccionActual;
	}

	/* Este procedimiento emite sentencias RM (Registro a memoria)
	 * de la TM		opcode r,d(s)  		a=d+reg[s]
	 * 				reg[PC_REG]=a  o	reg[r] = x (dependiendo de instruccion de carga)
	 *
	 * op = codigo de la operacion
	 * r = registro destino
	 * d = desplazamiento
	 * s = registro con la direccion base
	 * c = comentario a emitir en modo debug
	 */
	public static void emitirRM(String op, int r, int d, int s, String c){
		registrarLinea(instruccionActual);
		emitirLinea((instruccionActual++)+":       "+op+"       "+r+","+d+"("+s+")", c);
		if(instruccionMasAlta < instruccionActual)
			instruccionMasAlta = instruccionActual;
	}

	/* La funcion emitirSalto, salta "cantidad" de localidades de codigo
	 * para el reajuste posterior. Tambien devuelve la posicion actual
	 * del codgio generado
	 */
	public static int emitirSalto( int cantidad){
		int anterior = instruccionActual;
		for(int i = 0; i < cantidad; i++)
			registrarLinea(anterior + i);   // la instruccion que se parche despues pertenece a esta linea
		instruccionActual += cantidad;
		if(instruccionMasAlta < instruccionActual)
			instruccionMasAlta = instruccionActual;
		return anterior;
	}

	/* Direccion donde se emitira la siguiente instruccion */
	public static int direccionActual(){
		return instruccionActual;
	}

	/* La funcion cargar respaldo, cambia la direccion de emision de codigo actual,
	 * a una localidad que haya sido obviada (saltada) cuando se emitio un salto.
	 */
	public static void cargarRespaldo( int direccion ){
		if(instruccionActual > instruccionMasAlta)
			emitirComentario("BUG encontrado en la funcion cargarRespaldo");
		instruccionActual = direccion;
	}

	/* La funcion restaurar respaldo, restaura la direccion de emision de codigo actual
	 * a la posicion mas alta alcanzada previamente y que no ha sido emitida aun
	 */
	public static void restaurarRespaldo(){
		instruccionActual = instruccionMasAlta;
	}

	/* El procedimiento emitirRM_Abs convierte una referencia absoluta
	 * a una referencia relativa al pc (program counter, registro [7])
	 * cuando se emite una instruccion de tipos registro a memoria (RM)
	 * de la TM
	 *
	 * op = codigo de la operacion
	 * r = registro destino
	 * a = la localidad absoluta en memoria
	 * c = comentario a emitir en modo debug
	 */
	public static void emitirRM_Abs(String op, int r, int a, String c){
		registrarLinea(instruccionActual);
		emitirLinea((instruccionActual)+":       "+op+"       "+r+","+(a-(instruccionActual+1))+"("+PC+")", c);
		++instruccionActual;
		if(instruccionMasAlta < instruccionActual)
			instruccionMasAlta = instruccionActual;
	}

	/* Rellena una instruccion de salto que se dejo pendiente con emitirSalto(1):
	 * emite en "posicion" un salto (op) hacia la direccion absoluta "destino". */
	public static void parcharSalto(int posicion, String op, int r, int destino, String c){
		int lineaGuardada = lineaActual;
		lineaActual = (posicion < lineaFuente.length) ? lineaFuente[posicion] : lineaActual;
		cargarRespaldo(posicion);
		emitirRM_Abs(op, r, destino, c);
		restaurarRespaldo();
		lineaActual = lineaGuardada;
	}
}
