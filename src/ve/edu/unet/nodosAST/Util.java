package ve.edu.unet.nodosAST;

/* Impresion del AST en forma de arbol con sangria (para la consola y el IDE) */
public class Util {

	static int sangria = 0;
	private static StringBuilder salida;

	public static void imprimirAST(NodoBase raiz){
		System.out.print(astComoTexto(raiz));
	}

	public static String astComoTexto(NodoBase raiz){
		salida = new StringBuilder();
		sangria = 0;
		imprimir(raiz);
		return salida.toString();
	}

	private static void imprimir(NodoBase raiz){
		sangria += 2;
		while (raiz != null) {
			printSpaces();
			imprimirNodo(raiz);
			if (raiz instanceof NodoIf){
				NodoIf n = (NodoIf)raiz;
				etiqueta("prueba:");   imprimir(n.getPrueba());
				etiqueta("then:");     imprimir(n.getParteThen());
				if(n.getParteElse()!=null){
					etiqueta("else:"); imprimir(n.getParteElse());
				}
			}
			else if (raiz instanceof NodoRepeat){
				etiqueta("cuerpo:");   imprimir(((NodoRepeat)raiz).getCuerpo());
				etiqueta("until:");    imprimir(((NodoRepeat)raiz).getPrueba());
			}
			else if (raiz instanceof NodoWhile){
				etiqueta("prueba:");   imprimir(((NodoWhile)raiz).getPrueba());
				etiqueta("cuerpo:");   imprimir(((NodoWhile)raiz).getCuerpo());
			}
			else if (raiz instanceof NodoAsignacion){
				imprimir(((NodoAsignacion)raiz).getDestino());
				imprimir(((NodoAsignacion)raiz).getExpresion());
			}
			else if (raiz instanceof NodoLeer)
				imprimir(((NodoLeer)raiz).getDestino());
			else if (raiz instanceof NodoEscribir)
				imprimir(((NodoEscribir)raiz).getExpresion());
			else if (raiz instanceof NodoArreglo)
				imprimir(((NodoArreglo)raiz).getIndice());
			else if (raiz instanceof NodoOperacion){
				imprimir(((NodoOperacion)raiz).getOpIzquierdo());
				imprimir(((NodoOperacion)raiz).getOpDerecho());
			}
			else if (raiz instanceof NodoFuncion){
				NodoFuncion f = (NodoFuncion)raiz;
				etiqueta("parametros:"); imprimir(f.getParametros());
				etiqueta("cuerpo:");     imprimir(f.getCuerpo());
			}
			else if (raiz instanceof NodoLlamada)
				imprimir(((NodoLlamada)raiz).getArgumentos());
			else if (raiz instanceof NodoRetorno)
				imprimir(((NodoRetorno)raiz).getExpresion());
			raiz = raiz.getHermanoDerecha();
		}
		sangria -= 2;
	}

	private static void etiqueta(String texto){
		sangria += 1;
		printSpaces();
		salida.append(texto).append('\n');
		sangria -= 1;
	}

	static void printSpaces(){
		for(int i=0; i<sangria; i++)
			salida.append(' ');
	}

	static void imprimirNodo(NodoBase raiz){
		String linea = "  (linea " + raiz.getLinea() + ")";
		if(raiz instanceof NodoRepeat)
			salida.append("Repeat");
		else if(raiz instanceof NodoWhile)
			salida.append("While");
		else if(raiz instanceof NodoIf)
			salida.append("If");
		else if(raiz instanceof NodoAsignacion)
			salida.append("Asignacion :=");
		else if(raiz instanceof NodoLeer)
			salida.append("Read");
		else if(raiz instanceof NodoEscribir)
			salida.append("Write");
		else if(raiz instanceof NodoDeclaracion){
			NodoDeclaracion d = (NodoDeclaracion)raiz;
			salida.append("Declaracion ").append(d.getTipoDeclarado()).append(' ').append(d.getNombre());
			if(d.esArreglo()) salida.append('[').append(d.getTamano()).append(']');
		}
		else if(raiz instanceof NodoFuncion){
			NodoFuncion f = (NodoFuncion)raiz;
			salida.append(f.esProcedimiento() ? "Procedimiento " : "Funcion " + f.getTipoRetorno() + " ").append(f.getNombre());
		}
		else if(raiz instanceof NodoParametro){
			NodoParametro p = (NodoParametro)raiz;
			salida.append("Parametro ").append(p.getTipoDeclarado()).append(' ').append(p.getNombre());
		}
		else if(raiz instanceof NodoLlamada)
			salida.append("Llamada ").append(((NodoLlamada)raiz).getNombre()).append("(...)");
		else if(raiz instanceof NodoRetorno)
			salida.append("Return");
		else if(raiz instanceof NodoOperacion)
			salida.append("Operacion: ").append(((NodoOperacion)raiz).getOperacion());
		else if(raiz instanceof NodoValor)
			salida.append("Valor: ").append(((NodoValor)raiz).getValor());
		else if(raiz instanceof NodoBooleano)
			salida.append("Booleano: ").append(((NodoBooleano)raiz).getValor());
		else if(raiz instanceof NodoCadena)
			salida.append("Cadena: \"").append(((NodoCadena)raiz).getValor()).append('"');
		else if(raiz instanceof NodoIdentificador)
			salida.append("ID: ").append(((NodoIdentificador)raiz).getNombre());
		else if(raiz instanceof NodoArreglo)
			salida.append("Elemento de arreglo: ").append(((NodoArreglo)raiz).getNombre()).append("[ ]");
		else
			salida.append("Tipo de nodo desconocido");
		salida.append(linea).append('\n');
	}
}
