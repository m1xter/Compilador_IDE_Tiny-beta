package ve.edu.unet.nodosAST;

import ve.edu.unet.semantico.RegistroSimbolo;

/* return [expresion] */
public class NodoRetorno extends NodoBase {
	private NodoBase expresion;
	/* Funcion a la que pertenece este return (lo fija el semantico) */
	private RegistroSimbolo funcion;

	public NodoRetorno(NodoBase expresion) {
		super();
		this.expresion = expresion;
	}

	public NodoBase getExpresion() {
		return expresion;
	}

	public RegistroSimbolo getFuncion() {
		return funcion;
	}

	public void setFuncion(RegistroSimbolo funcion) {
		this.funcion = funcion;
	}
}
