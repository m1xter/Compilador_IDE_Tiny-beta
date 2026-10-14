package ve.edu.unet.nodosAST;

import ve.edu.unet.semantico.RegistroSimbolo;
import ve.edu.unet.semantico.Tipo;

/* Declaracion de UNA variable:  int x   |   int A[10]
 * Una declaracion multiple (int x, y, A[3]) produce varios NodoDeclaracion
 * enlazados como hermanos. tamano = -1 indica variable escalar. */
public class NodoDeclaracion extends NodoBase {
	private Tipo tipoDeclarado;
	private String nombre;
	private int tamano;
	private RegistroSimbolo simbolo;

	public NodoDeclaracion(Tipo tipoDeclarado, String nombre, int tamano) {
		super();
		this.tipoDeclarado = tipoDeclarado;
		this.nombre = nombre;
		this.tamano = tamano;
	}

	public Tipo getTipoDeclarado() {
		return tipoDeclarado;
	}

	public void setTipoDeclarado(Tipo tipoDeclarado) {
		this.tipoDeclarado = tipoDeclarado;
	}

	public String getNombre() {
		return nombre;
	}

	public int getTamano() {
		return tamano;
	}

	public boolean esArreglo() {
		return tamano >= 0;
	}

	public RegistroSimbolo getSimbolo() {
		return simbolo;
	}

	public void setSimbolo(RegistroSimbolo simbolo) {
		this.simbolo = simbolo;
	}
}
