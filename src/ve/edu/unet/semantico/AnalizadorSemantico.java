package ve.edu.unet.semantico;

import ve.edu.unet.ErrorCompilacion;
import ve.edu.unet.ListaErrores;
import ve.edu.unet.nodosAST.*;

/* Analizador semantico de Tiny++.
 *
 * Recorre el AST en el mismo orden en que esta escrito el programa, de modo que
 * un nombre solo es visible DESPUES de su declaracion. Durante el recorrido:
 *  1. Mantiene la pila de ambitos (TablaSimbolos) y asigna memoria a cada variable.
 *  2. Enlaza cada uso de un nombre con su RegistroSimbolo (lo usa luego el generador).
 *  3. Calcula el tipo de cada expresion (NodoBase.setTipo) y verifica la compatibilidad
 *     de tipos en operaciones, asignaciones, condiciones, paso de parametros y return.
 */
public class AnalizadorSemantico {

	private final TablaSimbolos tabla;
	private final ListaErrores errores;

	public AnalizadorSemantico(TablaSimbolos tabla, ListaErrores errores) {
		this.tabla = tabla;
		this.errores = errores;
	}

	public void analizar(NodoBase raiz){
		analizarSecuencia(raiz);
	}

	private void error(NodoBase nodo, String mensaje){
		errores.agregar(ErrorCompilacion.Fase.SEMANTICO, nodo.getLinea(), mensaje);
	}

	/* ===================== sentencias ===================== */

	private void analizarSecuencia(NodoBase nodo){
		while(nodo != null){
			analizarSentencia(nodo);
			nodo = nodo.getHermanoDerecha();
		}
	}

	/* Un bloque (then, else, cuerpo de un ciclo) tiene su propio ambito */
	private void analizarBloque(NodoBase nodo){
		tabla.abrirAmbitoBloque();
		analizarSecuencia(nodo);
		tabla.cerrarAmbito();
	}

	private void analizarSentencia(NodoBase nodo){
		if(nodo instanceof NodoDeclaracion){
			analizarDeclaracion((NodoDeclaracion)nodo);
		}else if(nodo instanceof NodoFuncion){
			analizarFuncion((NodoFuncion)nodo);
		}else if(nodo instanceof NodoAsignacion){
			NodoAsignacion n = (NodoAsignacion)nodo;
			Tipo destino = analizarDestino(n.getDestino());
			Tipo valor = analizarExpresion(n.getExpresion());
			if(destino != Tipo.ERROR && valor != Tipo.ERROR && destino != valor)
				error(n, "asignacion incompatible: no se puede asignar un valor " + valor + " a una variable " + destino);
		}else if(nodo instanceof NodoLeer){
			analizarDestino(((NodoLeer)nodo).getDestino());
		}else if(nodo instanceof NodoEscribir){
			Tipo t = analizarExpresion(((NodoEscribir)nodo).getExpresion());
			if(t == Tipo.VOID)
				error(nodo, "write: la expresion no produce ningun valor");
		}else if(nodo instanceof NodoIf){
			NodoIf n = (NodoIf)nodo;
			verificarCondicion(n.getPrueba(), "if");
			analizarBloque(n.getParteThen());
			if(n.getParteElse() != null)
				analizarBloque(n.getParteElse());
		}else if(nodo instanceof NodoRepeat){
			NodoRepeat n = (NodoRepeat)nodo;
			/* la condicion del until puede ver las variables declaradas en el cuerpo */
			tabla.abrirAmbitoBloque();
			analizarSecuencia(n.getCuerpo());
			verificarCondicion(n.getPrueba(), "until");
			tabla.cerrarAmbito();
		}else if(nodo instanceof NodoWhile){
			NodoWhile n = (NodoWhile)nodo;
			verificarCondicion(n.getPrueba(), "while");
			analizarBloque(n.getCuerpo());
		}else if(nodo instanceof NodoLlamada){
			analizarLlamada((NodoLlamada)nodo, false);
		}else if(nodo instanceof NodoRetorno){
			analizarRetorno((NodoRetorno)nodo);
		}else{
			error(nodo, "sentencia no reconocida");
		}
	}

	private void analizarDeclaracion(NodoDeclaracion n){
		if(n.esArreglo() && n.getTamano() <= 0){
			error(n, "el arreglo '" + n.getNombre() + "' debe tener tamano mayor que cero");
			return;
		}
		RegistroSimbolo s = tabla.insertarVariable(n.getNombre(), n.getLinea(), n.getTipoDeclarado(), n.esArreglo() ? n.getTamano() : -1);
		if(s == null)
			error(n, "'" + n.getNombre() + "' ya fue declarado en este ambito");
		n.setSimbolo(s);
	}

	private void analizarFuncion(NodoFuncion f){
		if(!tabla.enAmbitoGlobal() || tabla.getFuncionActual() != null){
			error(f, "la subrutina '" + f.getNombre() + "' solo puede declararse en el nivel principal del programa");
			return;
		}
		RegistroSimbolo s = tabla.insertarFuncion(f.getNombre(), f.getLinea(), f.getTipoRetorno());
		if(s == null){
			error(f, "'" + f.getNombre() + "' ya fue declarado en este ambito");
			return;
		}
		f.setSimbolo(s);

		/* Contar parametros: el parametro i (desde 0) de N queda en FP + 1 + N - i
		 * (ver el Registro de Activacion descrito en Generador.java) */
		int n = 0;
		for(NodoBase p = f.getParametros(); p != null; p = p.getHermanoDerecha()) n++;

		tabla.abrirAmbitoFuncion(s);
		int i = 0;
		for(NodoBase p = f.getParametros(); p != null; p = p.getHermanoDerecha(), i++){
			NodoParametro par = (NodoParametro)p;
			s.getTiposParametros().add(par.getTipoDeclarado());
			RegistroSimbolo sp = tabla.insertarParametro(par.getNombre(), par.getLinea(), par.getTipoDeclarado(), 1 + n - i);
			if(sp == null)
				error(par, "parametro '" + par.getNombre() + "' repetido");
			par.setSimbolo(sp);
		}
		analizarSecuencia(f.getCuerpo());
		tabla.cerrarAmbitoFuncion();
	}

	private void analizarRetorno(NodoRetorno r){
		RegistroSimbolo f = tabla.getFuncionActual();
		Tipo t = r.getExpresion() != null ? analizarExpresion(r.getExpresion()) : Tipo.VOID;
		if(f == null){
			error(r, "return fuera de una funcion o procedimiento");
			return;
		}
		r.setFuncion(f);
		if(f.getTipo() == Tipo.VOID){
			if(r.getExpresion() != null)
				error(r, "el procedimiento '" + f.getIdentificador() + "' no puede retornar un valor");
		}else if(r.getExpresion() == null){
			error(r, "la funcion '" + f.getIdentificador() + "' debe retornar un valor de tipo " + f.getTipo());
		}else if(t != Tipo.ERROR && t != f.getTipo()){
			error(r, "la funcion '" + f.getIdentificador() + "' retorna " + f.getTipo() + " pero la expresion es de tipo " + t);
		}
	}

	private void verificarCondicion(NodoBase cond, String donde){
		Tipo t = analizarExpresion(cond);
		if(t != Tipo.ERROR && t != Tipo.BOOL)
			error(cond, "la condicion del " + donde + " debe ser de tipo bool y es de tipo " + t);
	}

	/* Destino de una asignacion o read: variable escalar o elemento de arreglo */
	private Tipo analizarDestino(NodoBase destino){
		/* calcularTipo ya rechaza arreglos sin indice y nombres de subrutinas */
		return analizarExpresion(destino);
	}

	/* ===================== expresiones ===================== */

	private Tipo analizarExpresion(NodoBase nodo){
		Tipo t = calcularTipo(nodo);
		nodo.setTipo(t);
		return t;
	}

	private Tipo calcularTipo(NodoBase nodo){
		if(nodo instanceof NodoValor)
			return Tipo.INT;
		if(nodo instanceof NodoBooleano)
			return Tipo.BOOL;
		if(nodo instanceof NodoCadena)
			return Tipo.STRING;
		if(nodo instanceof NodoIdentificador){
			NodoIdentificador n = (NodoIdentificador)nodo;
			RegistroSimbolo s = tabla.BuscarSimbolo(n.getNombre());
			if(s == null){
				error(n, "la variable '" + n.getNombre() + "' no ha sido declarada");
				return Tipo.ERROR;
			}
			n.setSimbolo(s);
			if(s.esArreglo()){
				error(n, "el arreglo '" + n.getNombre() + "' debe usarse con un indice: " + n.getNombre() + "[...]");
				return Tipo.ERROR;
			}
			if(s.esFuncion()){
				error(n, "'" + n.getNombre() + "' es una subrutina; para llamarla use " + n.getNombre() + "(...)");
				return Tipo.ERROR;
			}
			return s.getTipo();
		}
		if(nodo instanceof NodoArreglo){
			NodoArreglo n = (NodoArreglo)nodo;
			Tipo ti = analizarExpresion(n.getIndice());
			if(ti != Tipo.ERROR && ti != Tipo.INT)
				error(n, "el indice del arreglo '" + n.getNombre() + "' debe ser int y es " + ti);
			RegistroSimbolo s = tabla.BuscarSimbolo(n.getNombre());
			if(s == null){
				error(n, "el arreglo '" + n.getNombre() + "' no ha sido declarado");
				return Tipo.ERROR;
			}
			n.setSimbolo(s);
			if(!s.esArreglo()){
				error(n, "'" + n.getNombre() + "' no es un arreglo, no puede indexarse");
				return Tipo.ERROR;
			}
			return s.getTipo();
		}
		if(nodo instanceof NodoLlamada){
			return analizarLlamada((NodoLlamada)nodo, true);
		}
		if(nodo instanceof NodoOperacion){
			return tipoOperacion((NodoOperacion)nodo);
		}
		error(nodo, "expresion no reconocida");
		return Tipo.ERROR;
	}

	private Tipo tipoOperacion(NodoOperacion n){
		Tipo izq = n.getOpIzquierdo() != null ? analizarExpresion(n.getOpIzquierdo()) : null;
		Tipo der = analizarExpresion(n.getOpDerecho());
		if(izq == Tipo.ERROR || der == Tipo.ERROR)
			return Tipo.ERROR;
		String op = simbolo(n.getOperacion());
		switch(n.getOperacion()){
			case mas:
				if(izq == Tipo.INT && der == Tipo.INT) return Tipo.INT;
				if(izq == Tipo.STRING && der == Tipo.STRING) return Tipo.STRING;  // concatenacion
				error(n, "operador '+' no aplicable a " + izq + " y " + der + " (se permite int+int o string+string)");
				return Tipo.ERROR;
			case menos: case por: case entre:
				if(izq == Tipo.INT && der == Tipo.INT) return Tipo.INT;
				error(n, "operador '" + op + "' requiere operandos int y recibio " + izq + " y " + der);
				return Tipo.ERROR;
			case menor: case menorIgual: case mayor: case mayorIgual:
				if(izq == Tipo.INT && der == Tipo.INT) return Tipo.BOOL;
				error(n, "operador '" + op + "' requiere operandos int y recibio " + izq + " y " + der);
				return Tipo.ERROR;
			case igual: case diferente:
				if(izq == der && izq != Tipo.VOID) return Tipo.BOOL;
				error(n, "operador '" + op + "' requiere operandos del mismo tipo y recibio " + izq + " y " + der);
				return Tipo.ERROR;
			case y: case o:
				if(izq == Tipo.BOOL && der == Tipo.BOOL) return Tipo.BOOL;
				error(n, "operador '" + op + "' requiere operandos bool y recibio " + izq + " y " + der);
				return Tipo.ERROR;
			case no:
				if(der == Tipo.BOOL) return Tipo.BOOL;
				error(n, "operador 'not' requiere un operando bool y recibio " + der);
				return Tipo.ERROR;
			default:
				return Tipo.ERROR;
		}
	}

	/* enExpresion = true cuando la llamada forma parte de una expresion (necesita valor) */
	private Tipo analizarLlamada(NodoLlamada n, boolean enExpresion){
		/* los argumentos se analizan siempre, para reportar sus propios errores */
		int cantidad = 0;
		for(NodoBase a = n.getArgumentos(); a != null; a = a.getHermanoDerecha()){
			analizarExpresion(a);
			cantidad++;
		}
		RegistroSimbolo s = tabla.BuscarSimbolo(n.getNombre());
		if(s == null){
			error(n, "la subrutina '" + n.getNombre() + "' no ha sido declarada");
			return Tipo.ERROR;
		}
		if(!s.esFuncion()){
			error(n, "'" + n.getNombre() + "' no es una funcion ni un procedimiento");
			return Tipo.ERROR;
		}
		n.setSimbolo(s);
		if(cantidad != s.getTiposParametros().size()){
			error(n, "'" + n.getNombre() + "' espera " + s.getTiposParametros().size() + " argumento(s) y recibio " + cantidad);
		}else{
			int i = 0;
			for(NodoBase a = n.getArgumentos(); a != null; a = a.getHermanoDerecha(), i++){
				Tipo esperado = s.getTiposParametros().get(i);
				if(a.getTipo() != Tipo.ERROR && a.getTipo() != esperado)
					error(a, "argumento " + (i + 1) + " de '" + n.getNombre() + "': se esperaba " + esperado + " y se recibio " + a.getTipo());
			}
		}
		if(enExpresion && s.getTipo() == Tipo.VOID){
			error(n, "el procedimiento '" + n.getNombre() + "' no retorna valor, no puede usarse en una expresion");
			return Tipo.ERROR;
		}
		return s.getTipo();
	}

	private static String simbolo(tipoOp op){
		switch(op){
			case menor: return "<";
			case menorIgual: return "<=";
			case mayor: return ">";
			case mayorIgual: return ">=";
			case igual: return "=";
			case diferente: return "<>";
			case mas: return "+";
			case menos: return "-";
			case por: return "*";
			case entre: return "/";
			case y: return "and";
			case o: return "or";
			case no: return "not";
			default: return op.toString();
		}
	}
}
