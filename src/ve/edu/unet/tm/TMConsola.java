package ve.edu.unet.tm;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/* Interfaz de consola del simulador, con los comandos de tm.c de Louden:
 *   s [n]      ejecuta n instrucciones (paso a paso, por defecto 1)
 *   g          ejecuta hasta HALT
 *   r          muestra los registros
 *   i [b [n]]  muestra n instrucciones desde b
 *   d [b [n]]  muestra n celdas de memoria de datos desde b
 *   t          activa/desactiva la traza
 *   c          reinicia la maquina
 *   q          salir
 */
public class TMConsola {

	public static void main(String[] args) throws Exception {
		if(args.length < 1){
			System.err.println("uso: TMConsola archivo.tm");
			return;
		}
		String texto = new String(Files.readAllBytes(Paths.get(args[0])), StandardCharsets.UTF_8);
		CargadorTM cargador = new CargadorTM();
		Instruccion[] programa = cargador.cargar(texto, TinyMachine.IADDR_SIZE);
		for(String e : cargador.getErrores()) System.err.println(e);
		if(!cargador.getErrores().isEmpty()) return;

		TinyMachine tm = new TinyMachine();
		tm.cargar(programa);
		BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
		boolean traza = false;
		System.out.println("TM simulacion (escriba h para ayuda)...");
		while(true){
			System.out.print("Comando: ");
			String linea = in.readLine();
			if(linea == null) return;
			String[] p = linea.trim().split("\\s+");
			if(p[0].isEmpty()) continue;
			char cmd = p[0].charAt(0);
			switch(cmd){
				case 'q': return;
				case 'h':
					System.out.println("s [n] paso, g ejecutar, r registros, i [b [n]] instrucciones, d [b [n]] datos, t traza, c reiniciar, q salir");
					break;
				case 't':
					traza = !traza;
					System.out.println("traza " + (traza ? "activada" : "desactivada"));
					break;
				case 'c':
					tm.reiniciar();
					System.out.println("maquina reiniciada");
					break;
				case 'r':
					for(int i = 0; i < TinyMachine.NO_REGS; i++)
						System.out.printf("r[%d] %-3s = %d%n", i, TinyMachine.NOMBRES_REGISTROS[i], tm.getRegistro(i));
					break;
				case 'i': {
					int b = p.length > 1 ? Integer.parseInt(p[1]) : tm.getPC();
					int n = p.length > 2 ? Integer.parseInt(p[2]) : 1;
					for(int i = b; i < b + n && i < TinyMachine.IADDR_SIZE; i++)
						System.out.printf("%5d: %s%n", i, tm.getInstruccion(i));
					break;
				}
				case 'd': {
					int b = p.length > 1 ? Integer.parseInt(p[1]) : 0;
					int n = p.length > 2 ? Integer.parseInt(p[2]) : 1;
					for(int i = b; i < b + n && i < TinyMachine.DADDR_SIZE; i++)
						System.out.printf("%5d: %d%n", i, tm.getDato(i));
					break;
				}
				case 's': case 'g': {
					long n = cmd == 'g' ? Long.MAX_VALUE : (p.length > 1 ? Long.parseLong(p[1]) : 1);
					for(long k = 0; k < n && !tm.terminada(); k++){
						if(traza || cmd == 's')
							System.out.printf("%5d: %s%n", tm.getPC(), tm.getInstruccion(tm.getPC()));
						if(tm.paso() == TinyMachine.Estado.ESPERANDO_ENTRADA){
							String error;
							do {
								System.out.print(tm.getEntradaPendiente() == TinyMachine.TipoEntrada.ENTERO
										? "Entrada (entero) para IN: " : "Entrada (texto) para INC: ");
								String dato = in.readLine();
								if(dato == null) return;
								error = tm.proporcionarEntrada(dato);
								if(error != null) System.out.println(error);
							} while(error != null);
						}
					}
					if(tm.terminada()) System.out.println(tm.getMensaje() + " (" + tm.getPasos() + " instrucciones ejecutadas)");
					break;
				}
				default:
					System.out.println("comando desconocido: " + cmd);
			}
		}
	}
}
