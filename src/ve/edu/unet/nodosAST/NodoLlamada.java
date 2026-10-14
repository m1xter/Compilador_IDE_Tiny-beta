package ve.edu.unet.nodosAST;

import ve.edu.unet.semantico.RegistroSimbolo;

/* Llamada a funcion/procedimiento: nombre(arg1, arg2, ...)
 * Puede aparecer como sentencia o como factor de una expresion.
 * argumentos: expresiones enlazadas por hermanos (puede ser null) */
public class NodoLlamada extends NodoBase {
	private String nombre;
	private NodoBase argumentos;
	private RegistroSimbolo simbolo;

	public NodoLlamada(String nombre, NodoBase argumentos) {
		super();
		this.nombre = nombre;
		this.argumentos = argumentos;
	}

	public String getNombre() {
		return nombre;
	}

	public NodoBase getArgumentos() {
		return argumentos;
	}

	public RegistroSimbolo getSimbolo() {
		return simbolo;
	}

	public void setSimbolo(RegistroSimbolo simbolo) {
		this.simbolo = simbolo;
	}
}
