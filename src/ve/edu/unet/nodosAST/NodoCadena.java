package ve.edu.unet.nodosAST;

/* Constante de cadena "..." . El generador le asigna una direccion
 * dentro del area de literales de la memoria de datos. */
public class NodoCadena extends NodoBase {
	private String valor;

	public NodoCadena(String valor) {
		super();
		this.valor = valor;
	}

	public String getValor() {
		return valor;
	}
}
