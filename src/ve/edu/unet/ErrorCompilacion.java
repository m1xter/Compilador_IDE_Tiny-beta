package ve.edu.unet;

/* Error detectado en cualquiera de las fases del compilador */
public class ErrorCompilacion {
	public enum Fase { LEXICO, SINTACTICO, SEMANTICO }

	private final Fase fase;
	private final int linea;
	private final String mensaje;

	public ErrorCompilacion(Fase fase, int linea, String mensaje) {
		this.fase = fase;
		this.linea = linea;
		this.mensaje = mensaje;
	}

	public Fase getFase() {
		return fase;
	}

	public int getLinea() {
		return linea;
	}

	public String getMensaje() {
		return mensaje;
	}

	@Override
	public String toString() {
		String nombreFase = fase == Fase.LEXICO ? "lexico" : fase == Fase.SINTACTICO ? "sintactico" : "semantico";
		return "Error " + nombreFase + " en linea " + linea + ": " + mensaje;
	}
}
