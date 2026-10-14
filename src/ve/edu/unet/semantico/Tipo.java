package ve.edu.unet.semantico;

/* Tipos del lenguaje Tiny++.
 * VOID  : "tipo" de retorno de los procedimientos.
 * ERROR : se asigna a expresiones mal tipadas para no reportar errores en cascada. */
public enum Tipo {
	INT("int"), BOOL("bool"), STRING("string"), VOID("void"), ERROR("<error>");

	private final String nombre;

	Tipo(String nombre) {
		this.nombre = nombre;
	}

	@Override
	public String toString() {
		return nombre;
	}
}
