package ve.edu.unet.nodosAST;

public class NodoRepeat extends NodoBase {
	private NodoBase cuerpo;
	private NodoBase prueba;

	public NodoRepeat(NodoBase cuerpo, NodoBase prueba) {
		super();
		this.cuerpo = cuerpo;
		this.prueba = prueba;
	}

	public NodoRepeat() {
		super();
	}

	public NodoBase getCuerpo() {
		return cuerpo;
	}

	public void setCuerpo(NodoBase cuerpo) {
		this.cuerpo = cuerpo;
	}

	public NodoBase getPrueba() {
		return prueba;
	}

	public void setPrueba(NodoBase prueba) {
		this.prueba = prueba;
	}
}
