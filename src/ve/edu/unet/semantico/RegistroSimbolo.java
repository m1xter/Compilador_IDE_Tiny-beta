package ve.edu.unet.semantico;

import java.util.ArrayList;
import java.util.List;

/* Entrada de la tabla de simbolos.
 *
 * Direccion de memoria:
 *  - Simbolos globales (declarados en el cuerpo principal): direccion absoluta
 *    en la memoria de datos, se accede como  d(GP)  con GP = 0.
 *  - Simbolos locales y parametros (dentro de una funcion): desplazamiento
 *    relativo al puntero de marco, se accede como  d(FP).
 *  - Arreglos: la direccion es la del elemento 0, el elemento i esta en direccion+i.
 */
public class RegistroSimbolo {
	public enum Clase { VARIABLE, ARREGLO, PARAMETRO, FUNCION }

	private String identificador;
	private int NumLinea;
	private int DireccionMemoria;
	private Tipo tipo;
	private Clase clase;
	private int tamano = 1;
	private boolean global;
	private String ambito;

	/* Solo para funciones / procedimientos */
	private List<Tipo> tiposParametros = new ArrayList<>();
	private int tamanoLocales;        // celdas reservadas para variables locales en el marco
	private int direccionEntrada = -1; // direccion de la primera instruccion (la fija el generador)
	private int direccionFin = -1;     // primera direccion despues del cuerpo (la fija el generador)
	/* Para variables locales y parametros: funcion a la que pertenecen */
	private RegistroSimbolo funcion;

	public RegistroSimbolo(String identificador, int numLinea, Tipo tipo, Clase clase, String ambito) {
		super();
		this.identificador = identificador;
		this.NumLinea = numLinea;
		this.tipo = tipo;
		this.clase = clase;
		this.ambito = ambito;
	}

	public String getIdentificador() {
		return identificador;
	}

	public int getNumLinea() {
		return NumLinea;
	}

	public int getDireccionMemoria() {
		return DireccionMemoria;
	}

	public void setDireccionMemoria(int direccionMemoria) {
		DireccionMemoria = direccionMemoria;
	}

	/* Para variables: tipo de la variable (o de los elementos si es arreglo).
	 * Para funciones: tipo de retorno (VOID si es procedimiento). */
	public Tipo getTipo() {
		return tipo;
	}

	public Clase getClase() {
		return clase;
	}

	public boolean esFuncion() {
		return clase == Clase.FUNCION;
	}

	public boolean esArreglo() {
		return clase == Clase.ARREGLO;
	}

	public int getTamano() {
		return tamano;
	}

	public void setTamano(int tamano) {
		this.tamano = tamano;
	}

	public boolean esGlobal() {
		return global;
	}

	public void setGlobal(boolean global) {
		this.global = global;
	}

	public String getAmbito() {
		return ambito;
	}

	public List<Tipo> getTiposParametros() {
		return tiposParametros;
	}

	public int getTamanoLocales() {
		return tamanoLocales;
	}

	public void setTamanoLocales(int tamanoLocales) {
		this.tamanoLocales = tamanoLocales;
	}

	public int getDireccionEntrada() {
		return direccionEntrada;
	}

	public void setDireccionEntrada(int direccionEntrada) {
		this.direccionEntrada = direccionEntrada;
	}

	public int getDireccionFin() {
		return direccionFin;
	}

	public void setDireccionFin(int direccionFin) {
		this.direccionFin = direccionFin;
	}

	public RegistroSimbolo getFuncion() {
		return funcion;
	}

	public void setFuncion(RegistroSimbolo funcion) {
		this.funcion = funcion;
	}
}
