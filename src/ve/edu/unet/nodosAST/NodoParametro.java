package ve.edu.unet.nodosAST;

import ve.edu.unet.semantico.RegistroSimbolo;
import ve.edu.unet.semantico.Tipo;

/* Parametro formal (paso por valor) de una funcion o procedimiento */
public class NodoParametro extends NodoBase {
	private Tipo tipoDeclarado;
	private String nombre;
	private RegistroSimbolo simbolo;

	public NodoParametro(Tipo tipoDeclarado, String nombre) {
		super();
		this.tipoDeclarado = tipoDeclarado;
		this.nombre = nombre;
	}

	public Tipo getTipoDeclarado() {
		return tipoDeclarado;
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
