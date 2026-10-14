package ve.edu.unet.nodosAST;

/* Constante booleana: true (1) o false (0) */
public class NodoBooleano extends NodoBase {
	private boolean valor;

	public NodoBooleano(boolean valor) {
		super();
		this.valor = valor;
	}

	public boolean getValor() {
		return valor;
	}
}
