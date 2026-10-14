package ve.edu.unet.nodosAST;

import ve.edu.unet.semantico.RegistroSimbolo;

/* Uso de una variable escalar (o parametro) dentro de una expresion */
public class NodoIdentificador extends NodoBase {
	private String nombre;
	/* Registro de la tabla de simbolos al que se resolvio el nombre (semantico) */
	private RegistroSimbolo simbolo;

	public NodoIdentificador(String nombre) {
		super();
		this.nombre = nombre;
	}

	public NodoIdentificador() {
		super();
	}

	public String getNombre() {
		return nombre;
	}

	public RegistroSimbolo getSimbolo() {
		return simbolo;
	}

	public void setSimbolo(RegistroSimbolo simbolo) {
		this.simbolo = simbolo;
	}
}
