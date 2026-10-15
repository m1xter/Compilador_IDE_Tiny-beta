package ve.edu.unet.tm;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/* Lee un programa en el formato de texto de la TM (archivo .tm):
 *   * comentario
 *   12:  LDC  0,5(0)     comentario opcional
 *   13:  ADD  0,1,0
 * Las instrucciones pueden aparecer en cualquier orden: la direccion la da el
 * numero antes de ':' (el generador emite los saltos parchados fuera de orden).
 */
public class CargadorTM {

	private static final Pattern INSTRUCCION = Pattern.compile(
			"^\\s*(\\d+)\\s*:\\s*([A-Za-z]+)\\s+(-?\\d+)\\s*,\\s*(-?\\d+)\\s*(?:,\\s*(-?\\d+)|\\(\\s*(-?\\d+)\\s*\\))(.*)$");

	private final List<String> errores = new ArrayList<>();

	public List<String> getErrores(){
		return errores;
	}

	public Instruccion[] cargar(String texto, int tamanoMemoria){
		Instruccion[] iMem = new Instruccion[tamanoMemoria];
		for(int i = 0; i < iMem.length; i++)
			iMem[i] = Instruccion.halt();
		String[] lineas = texto.split("\r?\n");
		for(int n = 0; n < lineas.length; n++){
			String linea = lineas[n];
			String t = linea.trim();
			if(t.isEmpty() || t.startsWith("*"))
				continue;
			Matcher m = INSTRUCCION.matcher(linea);
			if(!m.matches()){
				errores.add("linea " + (n + 1) + ": formato de instruccion invalido: " + t);
				continue;
			}
			int direccion = Integer.parseInt(m.group(1));
			Instruccion.Opcode op;
			try {
				op = Instruccion.Opcode.valueOf(m.group(2).toUpperCase());
			} catch (IllegalArgumentException e) {
				errores.add("linea " + (n + 1) + ": instruccion desconocida " + m.group(2));
				continue;
			}
			boolean formatoRO = m.group(5) != null;
			if(formatoRO != (op.clase == Instruccion.Clase.RO)){
				errores.add("linea " + (n + 1) + ": " + op + " usa el formato " + (op.clase == Instruccion.Clase.RO ? "r,s,t" : "r,d(s)"));
				continue;
			}
			if(direccion >= tamanoMemoria){
				errores.add("linea " + (n + 1) + ": direccion " + direccion + " fuera de la memoria de instrucciones");
				continue;
			}
			int a1 = Integer.parseInt(m.group(3));
			int a2 = Integer.parseInt(m.group(4));
			int a3 = Integer.parseInt(formatoRO ? m.group(5) : m.group(6));
			if(a1 < 0 || a1 > 7 || a3 < 0 || a3 > 7 || (formatoRO && (a2 < 0 || a2 > 7))){
				errores.add("linea " + (n + 1) + ": numero de registro invalido");
				continue;
			}
			String comentario = m.group(7).trim();
			if(comentario.startsWith("*")) comentario = comentario.substring(1).trim();
			iMem[direccion] = new Instruccion(op, a1, a2, a3, comentario);
		}
		return iMem;
	}
}
