package ve.edu.unet.nodosAST;

import ve.edu.unet.semantico.RegistroSimbolo;
import ve.edu.unet.semantico.Tipo;

/* Declaracion de funcion (tipoRetorno != VOID) o procedimiento (tipoRetorno == VOID)
 *   function int f(int a, bool b) <cuerpo> end
 *   procedure p(string s) <cuerpo> end
 * parametros: lista de NodoParametro enlazada por hermanos (puede ser null) */
public class NodoFuncion extends NodoBase {
	private Tipo tipoRetorno;
	private String nombre;
	private NodoBase parametros;
	private NodoBase cuerpo;
	private RegistroSimbolo simbolo;

	public NodoFuncion(Tipo tipoRetorno, String nombre, NodoBase parametros, NodoBase cuerpo) {
		super();
		this.tipoRetorno = tipoRetorno;
		this.nombre = nombre;
		this.parametros = parametros;
		this.cuerpo = cuerpo;
	}

	public Tipo getTipoRetorno() {
		return tipoRetorno;
	}

	public boolean esProcedimiento() {
		return tipoRetorno == Tipo.VOID;
	}

	public String getNombre() {
		return nombre;
	}

	public NodoBase getParametros() {
		return parametros;
	}

	public NodoBase getCuerpo() {
		return cuerpo;
	}

	public RegistroSimbolo getSimbolo() {
		return simbolo;
	}

	public void setSimbolo(RegistroSimbolo simbolo) {
		this.simbolo = simbolo;
	}
}
