package ve.edu.unet.nodosAST;

import ve.edu.unet.semantico.Tipo;

/* Clase base de todos los nodos del AST.
 * Los nodos de una secuencia de sentencias se enlazan mediante HermanoDerecha
 * (igual que en el Tiny original). Ademas cada nodo guarda la linea del codigo
 * fuente donde aparece y, para las expresiones, el tipo calculado por el
 * analizador semantico.
 */
public class NodoBase {
	private NodoBase HermanoDerecha;
	private int linea;
	private Tipo tipo = Tipo.ERROR;

	public NodoBase(NodoBase hermanoDerecha) {
		super();
		HermanoDerecha = hermanoDerecha;
	}

	public NodoBase() {
		super();
		HermanoDerecha = null;
	}

	public NodoBase getHermanoDerecha() {
		return HermanoDerecha;
	}

	public void setHermanoDerecha(NodoBase hermanoDerecha) {
		HermanoDerecha = hermanoDerecha;
	}

	public boolean TieneHermano(){
		return (HermanoDerecha!=null);
	}

	public int getLinea() {
		return linea;
	}

	public void setLinea(int linea) {
		this.linea = linea;
	}

	/* Tipo de la expresion (lo fija el analizador semantico) */
	public Tipo getTipo() {
		return tipo;
	}

	public void setTipo(Tipo tipo) {
		this.tipo = tipo;
	}
}
