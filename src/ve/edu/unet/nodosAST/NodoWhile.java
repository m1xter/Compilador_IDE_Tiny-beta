package ve.edu.unet.nodosAST;

/* while prueba do cuerpo end */
public class NodoWhile extends NodoBase {
	private NodoBase prueba;
	private NodoBase cuerpo;

	public NodoWhile(NodoBase prueba, NodoBase cuerpo) {
		super();
		this.prueba = prueba;
		this.cuerpo = cuerpo;
	}

	public NodoBase getPrueba() {
		return prueba;
	}

	public NodoBase getCuerpo() {
		return cuerpo;
	}
}
