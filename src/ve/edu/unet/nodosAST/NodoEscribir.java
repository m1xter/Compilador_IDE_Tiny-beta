package ve.edu.unet.nodosAST;

/* write expresion */
public class NodoEscribir extends NodoBase {
	private NodoBase expresion;

	public NodoEscribir(NodoBase expresion) {
		super();
		this.expresion = expresion;
	}

	public NodoEscribir() {
		super();
	}

	public NodoBase getExpresion() {
		return expresion;
	}

	public void setExpresion(NodoBase expresion) {
		this.expresion = expresion;
	}
}
