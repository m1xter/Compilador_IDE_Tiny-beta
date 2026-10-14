package ve.edu.unet.nodosAST;

import ve.edu.unet.semantico.RegistroSimbolo;

/* Acceso a un elemento de un arreglo: nombre[indice]
 * Se usa tanto en expresiones (lectura) como destino de asignaciones/read. */
public class NodoArreglo extends NodoBase {
	private String nombre;
	private NodoBase indice;
	private RegistroSimbolo simbolo;

	public NodoArreglo(String nombre, NodoBase indice) {
		super();
		this.nombre = nombre;
		this.indice = indice;
	}

	public String getNombre() {
		return nombre;
	}

	public NodoBase getIndice() {
		return indice;
	}

	public RegistroSimbolo getSimbolo() {
		return simbolo;
	}

	public void setSimbolo(RegistroSimbolo simbolo) {
		this.simbolo = simbolo;
	}
}
