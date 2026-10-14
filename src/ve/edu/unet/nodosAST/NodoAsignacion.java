package ve.edu.unet.nodosAST;

/* destino := expresion
 * El destino es un NodoIdentificador (variable escalar) o un NodoArreglo (A[i]). */
public class NodoAsignacion extends NodoBase {
	private NodoBase destino;
	private NodoBase expresion;

	public NodoAsignacion(NodoBase destino, NodoBase expresion) {
		super();
		this.destino = destino;
		this.expresion = expresion;
	}

	public NodoBase getDestino() {
		return destino;
	}

	public NodoBase getExpresion() {
		return expresion;
	}

	public void setExpresion(NodoBase expresion) {
		this.expresion = expresion;
	}
}
