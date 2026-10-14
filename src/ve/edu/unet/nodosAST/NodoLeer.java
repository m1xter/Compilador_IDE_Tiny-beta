package ve.edu.unet.nodosAST;

/* read destino   (destino: NodoIdentificador o NodoArreglo) */
public class NodoLeer extends NodoBase {
	private NodoBase destino;

	public NodoLeer(NodoBase destino) {
		super();
		this.destino = destino;
	}

	public NodoBase getDestino() {
		return destino;
	}
}
