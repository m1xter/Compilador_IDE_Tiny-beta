package ve.edu.unet.nodosAST;

/* Operacion binaria (o unaria para "not": en ese caso opIzquierdo es null) */
public class NodoOperacion extends NodoBase {
	private NodoBase opIzquierdo;
	private NodoBase opDerecho;
	private tipoOp operacion;

	public NodoOperacion(NodoBase opIzquierdo, tipoOp tipoOp, NodoBase opDerecho) {
		super();
		this.opIzquierdo = opIzquierdo;
		this.opDerecho = opDerecho;
		this.operacion = tipoOp;
	}

	public NodoOperacion(tipoOp tipoOp) {
		super();
		this.operacion = tipoOp;
		this.opIzquierdo = null;
		this.opDerecho = null;
	}

	public NodoBase getOpIzquierdo() {
		return opIzquierdo;
	}

	public void setOpIzquierdo(NodoBase opIzquierdo) {
		this.opIzquierdo = opIzquierdo;
	}

	public NodoBase getOpDerecho() {
		return opDerecho;
	}

	public void setOpDerecho(NodoBase opDerecho) {
		this.opDerecho = opDerecho;
	}

	public tipoOp getOperacion() {
		return operacion;
	}
}
