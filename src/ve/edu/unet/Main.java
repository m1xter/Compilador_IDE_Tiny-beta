package ve.edu.unet;

import ve.edu.unet.ide.IDE;
import ve.edu.unet.tm.TMConsola;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/* Punto de entrada.
 *   java -jar TinyPlusIDE.jar                        abre el IDE
 *   java -jar TinyPlusIDE.jar programa.tny [-v]      compila y genera programa.tm
 *   java -jar TinyPlusIDE.jar --tm programa.tm       simulador TM en consola (paso a paso)
 */
public class Main {

	public static void main(String[] args) throws Exception {
		if(args.length == 0){
			IDE.main(args);
			return;
		}
		if(args[0].equals("--tm")){
			if(args.length < 2){
				System.err.println("uso: --tm archivo.tm");
				System.exit(2);
			}
			TMConsola.main(new String[]{args[1]});
			return;
		}
		if(args[0].equals("-h") || args[0].equals("--help")){
			System.out.println("uso:\n  (sin argumentos)          abre el IDE\n  archivo.tny [-v] [-o salida.tm]  compila\n  --tm archivo.tm           ejecuta la TM en consola");
			return;
		}
		compilarArchivo(args);
	}

	private static void compilarArchivo(String[] args) throws Exception {
		Path entrada = Paths.get(args[0]);
		Path salida = null;
		boolean verbose = false;
		for(int i = 1; i < args.length; i++){
			if(args[i].equals("-v")) verbose = true;
			else if(args[i].equals("-o") && i + 1 < args.length) salida = Paths.get(args[++i]);
		}
		if(salida == null){
			String nombre = entrada.getFileName().toString();
			int punto = nombre.lastIndexOf('.');
			salida = entrada.resolveSibling((punto > 0 ? nombre.substring(0, punto) : nombre) + ".tm");
		}
		String fuente = new String(Files.readAllBytes(entrada), StandardCharsets.UTF_8);
		Compilador.Resultado r = Compilador.compilar(fuente, entrada.getFileName().toString());
		if(verbose && !r.ast.isEmpty()){
			System.out.println("==== AST ====");
			System.out.print(r.ast);
		}
		if(verbose && !r.tablaSimbolos.isEmpty()){
			System.out.println("==== Tabla de simbolos ====");
			System.out.print(r.tablaSimbolos);
		}
		if(!r.exito){
			for(ErrorCompilacion e : r.errores.getErrores())
				System.err.println(e);
			System.err.println(r.errores.getErrores().size() + " error(es). No se genero codigo.");
			System.exit(1);
		}
		Files.write(salida, r.codigoTM.getBytes(StandardCharsets.UTF_8));
		if(verbose){
			System.out.println("==== Codigo TM ====");
			System.out.print(r.codigoTM);
		}
		System.out.println("Compilacion exitosa: " + salida + " (" + r.lineasFuente.length + " instrucciones)");
	}
}
