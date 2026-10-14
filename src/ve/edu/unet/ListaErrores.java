package ve.edu.unet;

import java.util.ArrayList;
import java.util.List;

/* Acumula los errores de todas las fases para mostrarlos juntos en la consola */
public class ListaErrores {
	private final List<ErrorCompilacion> errores = new ArrayList<>();

	public void agregar(ErrorCompilacion.Fase fase, int linea, String mensaje){
		errores.add(new ErrorCompilacion(fase, linea, mensaje));
	}

	public boolean hayErrores(){
		return !errores.isEmpty();
	}

	public List<ErrorCompilacion> getErrores(){
		return errores;
	}
}
