package ve.edu.unet.semantico;

import java.util.*;

/* Tabla de simbolos con ambitos anidados (pila de tablas hash).
 *
 * - El ambito de la base de la pila es el GLOBAL (cuerpo principal del programa).
 * - Cada funcion abre un ambito para sus parametros y variables locales.
 * - Cada bloque (then, else, cuerpo de repeat/while) abre un ambito propio.
 * Buscar un nombre recorre la pila desde el ambito mas interno hacia el global,
 * por lo que una declaracion interna oculta a otra externa con el mismo nombre.
 *
 * Reserva de memoria:
 * - Variables del cuerpo principal (aunque esten en un bloque interno): memoria
 *   estatica a partir de la direccion 1 (la 0 queda fija en 0 y sirve de
 *   "cadena vacia" para las variables string no inicializadas).
 * - Variables dentro de una funcion: celdas del marco de pila, en desplazamientos
 *   negativos respecto a FP (-1, -2, ...).
 */
public class TablaSimbolos {

	private static class Ambito {
		final String nombre;
		final HashMap<String, RegistroSimbolo> tabla = new HashMap<>();
		Ambito(String nombre) { this.nombre = nombre; }
	}

	private final Deque<Ambito> pila = new ArrayDeque<>();
	private final List<RegistroSimbolo> todos = new ArrayList<>();  // historial para imprimir
	private int direccion;          // siguiente direccion global libre
	private RegistroSimbolo funcionActual = null;
	private int desplazamientoLocal; // celdas locales usadas en la funcion actual
	private int contadorBloques = 0;

	public TablaSimbolos() {
		super();
		direccion = 1;
		pila.push(new Ambito("global"));
	}

	/* ---------------- ambitos ---------------- */

	public void abrirAmbitoBloque(){
		pila.push(new Ambito(nombreAmbitoActual() + "/bloque" + (++contadorBloques)));
	}

	public void abrirAmbitoFuncion(RegistroSimbolo funcion){
		funcionActual = funcion;
		desplazamientoLocal = 0;
		pila.push(new Ambito(funcion.getIdentificador()));
	}

	public void cerrarAmbito(){
		pila.pop();
	}

	public void cerrarAmbitoFuncion(){
		funcionActual.setTamanoLocales(desplazamientoLocal);
		funcionActual = null;
		pila.pop();
	}

	public boolean enAmbitoGlobal(){
		return pila.size() == 1;
	}

	public RegistroSimbolo getFuncionActual(){
		return funcionActual;
	}

	private String nombreAmbitoActual(){
		return pila.peek().nombre;
	}

	/* ---------------- insercion ---------------- */

	/* Devuelve null si el nombre ya existe en el ambito actual */
	public RegistroSimbolo insertarVariable(String nombre, int linea, Tipo tipo, int tamanoArreglo){
		if(pila.peek().tabla.containsKey(nombre))
			return null;
		boolean arreglo = tamanoArreglo >= 0;
		RegistroSimbolo s = new RegistroSimbolo(nombre, linea, tipo,
				arreglo ? RegistroSimbolo.Clase.ARREGLO : RegistroSimbolo.Clase.VARIABLE, nombreAmbitoActual());
		int celdas = arreglo ? tamanoArreglo : 1;
		s.setTamano(celdas);
		if(funcionActual == null){
			s.setGlobal(true);
			s.setDireccionMemoria(direccion);
			direccion += celdas;
		}else{
			s.setGlobal(false);
			desplazamientoLocal += celdas;
			/* el bloque ocupa FP-desplazamientoLocal .. FP-desplazamientoLocal+celdas-1 */
			s.setDireccionMemoria(-desplazamientoLocal);
		}
		insertar(s);
		return s;
	}

	/* El desplazamiento de un parametro lo calcula el analizador (depende del total de parametros) */
	public RegistroSimbolo insertarParametro(String nombre, int linea, Tipo tipo, int desplazamiento){
		if(pila.peek().tabla.containsKey(nombre))
			return null;
		RegistroSimbolo s = new RegistroSimbolo(nombre, linea, tipo, RegistroSimbolo.Clase.PARAMETRO, nombreAmbitoActual());
		s.setGlobal(false);
		s.setDireccionMemoria(desplazamiento);
		insertar(s);
		return s;
	}

	public RegistroSimbolo insertarFuncion(String nombre, int linea, Tipo retorno){
		if(pila.peek().tabla.containsKey(nombre))
			return null;
		RegistroSimbolo s = new RegistroSimbolo(nombre, linea, retorno, RegistroSimbolo.Clase.FUNCION, nombreAmbitoActual());
		s.setGlobal(true);
		insertar(s);
		return s;
	}

	private void insertar(RegistroSimbolo s){
		if(!s.esGlobal()) s.setFuncion(funcionActual);
		pila.peek().tabla.put(s.getIdentificador(), s);
		todos.add(s);
	}

	/* ---------------- busqueda ---------------- */

	public RegistroSimbolo BuscarSimbolo(String identificador){
		for(Ambito a : pila){          // ArrayDeque itera desde el tope (ambito mas interno)
			RegistroSimbolo s = a.tabla.get(identificador);
			if(s != null) return s;
		}
		return null;
	}

	/* Primera direccion global libre luego de todas las variables globales */
	public int getTamanoGlobal(){
		return direccion;
	}

	public List<RegistroSimbolo> getTodos(){
		return todos;
	}

	public String comoTexto(){
		StringBuilder sb = new StringBuilder();
		sb.append(String.format("%-14s %-10s %-8s %-10s %-22s %s%n", "Nombre", "Clase", "Tipo", "Direccion", "Ambito", "Linea"));
		for(RegistroSimbolo s : todos){
			String dir;
			if(s.esFuncion())
				dir = "@" + s.getDireccionEntrada();
			else if(s.esGlobal())
				dir = String.valueOf(s.getDireccionMemoria());
			else
				dir = "FP" + (s.getDireccionMemoria() >= 0 ? "+" : "") + s.getDireccionMemoria();
			String clase = s.getClase().toString().toLowerCase();
			String tipo = s.getTipo().toString();
			if(s.esArreglo()) tipo += "[" + s.getTamano() + "]";
			if(s.esFuncion()) {
				clase = s.getTipo() == Tipo.VOID ? "proced." : "funcion";
				tipo += " " + s.getTiposParametros();
			}
			sb.append(String.format("%-14s %-10s %-8s %-10s %-22s %d%n", s.getIdentificador(), clase, tipo, dir, s.getAmbito(), s.getNumLinea()));
		}
		return sb.toString();
	}

	public void ImprimirClaves(){
		System.out.println("*** Tabla de Simbolos ***");
		System.out.print(comoTexto());
	}
}
