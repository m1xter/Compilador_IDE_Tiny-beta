package ve.edu.unet.tm;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

/* Simulador de la Tiny Machine (traduccion a Java de tm.c de K. Louden) modificado
 * para poder controlarlo desde el exterior:
 *  - paso(): ejecuta UNA instruccion y devuelve el estado; nunca bloquea.
 *  - Cuando una instruccion IN/INC necesita datos, la maquina queda en estado
 *    ESPERANDO_ENTRADA sin avanzar; quien la controla (IDE o consola) obtiene el
 *    dato y llama a proporcionarEntrada(), que completa la instruccion.
 *  - La salida (OUT/OUTC) se envia a un objeto Salida.
 *  - Las memorias y registros se exponen con getters, ademas de cual registro y
 *    cual celda de memoria modifico la ultima instruccion (para resaltarlos).
 */
public class TinyMachine {

	public static final int IADDR_SIZE = 2048;  /* tamano de la memoria de instrucciones */
	public static final int DADDR_SIZE = 1024;  /* tamano de la memoria de datos */
	public static final int NO_REGS = 8;
	public static final int PC_REG = 7;

	public static final String[] NOMBRES_REGISTROS = {"AC", "AC1", "R2", "R3", "FP", "GP", "MP", "PC"};

	public enum Estado { LISTA, ESPERANDO_ENTRADA, DETENIDA, ERROR }
	public enum TipoEntrada { NINGUNA, ENTERO, CARACTER }

	public interface Salida {
		void escribir(String texto);
	}

	private Instruccion[] iMem = new Instruccion[IADDR_SIZE];
	private final int[] dMem = new int[DADDR_SIZE];
	private final int[] reg = new int[NO_REGS];

	private Estado estado;
	private String mensaje = "";
	private long pasos;
	private TipoEntrada entradaPendiente = TipoEntrada.NINGUNA;
	/* caracteres pendientes de la ultima linea leida por INC */
	private final Deque<Character> bufferCaracteres = new ArrayDeque<>();

	private int ultimaDireccionEscrita = -1;
	private int ultimaDireccionLeida = -1;
	private final boolean[] registroModificado = new boolean[NO_REGS];

	private Salida salida = texto -> System.out.print(texto);

	public TinyMachine() {
		Arrays.fill(iMem, Instruccion.halt());
		reiniciar();
	}

	public void setSalida(Salida salida){
		this.salida = salida;
	}

	/* Carga un programa (las celdas no definidas quedan como HALT) y reinicia */
	public void cargar(Instruccion[] programa){
		iMem = Arrays.copyOf(programa, IADDR_SIZE);
		for(int i = 0; i < IADDR_SIZE; i++)
			if(iMem[i] == null) iMem[i] = Instruccion.halt();
		reiniciar();
	}

	/* Estado inicial de la TM: registros en 0, dMem[0] = direccion maxima, resto 0 */
	public void reiniciar(){
		Arrays.fill(reg, 0);
		Arrays.fill(dMem, 0);
		dMem[0] = DADDR_SIZE - 1;
		estado = Estado.LISTA;
		mensaje = "";
		pasos = 0;
		entradaPendiente = TipoEntrada.NINGUNA;
		bufferCaracteres.clear();
		ultimaDireccionEscrita = -1;
		ultimaDireccionLeida = -1;
		Arrays.fill(registroModificado, false);
	}

	/* Ejecuta una instruccion (equivale a stepTM de tm.c) */
	public Estado paso(){
		if(estado != Estado.LISTA)
			return estado;
		ultimaDireccionEscrita = -1;
		ultimaDireccionLeida = -1;
		Arrays.fill(registroModificado, false);

		int pc = reg[PC_REG];
		if(pc < 0 || pc >= IADDR_SIZE)
			return fallo("PC fuera de la memoria de instrucciones: " + pc);
		Instruccion ins = iMem[pc];

		/* Entrada: si no hay dato disponible, espero sin avanzar el PC */
		if(ins.op == Instruccion.Opcode.IN){
			entradaPendiente = TipoEntrada.ENTERO;
			estado = Estado.ESPERANDO_ENTRADA;
			return estado;
		}
		if(ins.op == Instruccion.Opcode.INC && bufferCaracteres.isEmpty()){
			entradaPendiente = TipoEntrada.CARACTER;
			estado = Estado.ESPERANDO_ENTRADA;
			return estado;
		}

		int[] antes = reg.clone();
		reg[PC_REG] = pc + 1;
		int r = ins.arg1, s, t, m = 0;
		switch(ins.op.clase){
			case RO:
				s = ins.arg2;
				t = ins.arg3;
				break;
			case RM:
				s = ins.arg3;
				m = ins.arg2 + reg[s];
				if(m < 0 || m >= DADDR_SIZE)
					return fallo("acceso a memoria de datos fuera de rango: dMem[" + m + "] en la instruccion " + pc);
				t = 0;
				break;
			default: /* RA */
				s = ins.arg3;
				m = ins.arg2 + reg[s];
				t = 0;
		}

		switch(ins.op){
			case HALT:
				estado = Estado.DETENIDA;
				mensaje = "HALT: ejecucion terminada";
				reg[PC_REG] = pc;    // el PC queda sobre el HALT
				break;
			case OUT:  salida.escribir(reg[r] + "\n"); break;
			case OUTC: salida.escribir(String.valueOf((char)reg[r])); break;
			case INC:  reg[r] = bufferCaracteres.poll(); break;
			case ADD:  reg[r] = reg[s] + reg[t]; break;
			case SUB:  reg[r] = reg[s] - reg[t]; break;
			case MUL:  reg[r] = reg[s] * reg[t]; break;
			case DIV:
				if(reg[t] == 0){
					reg[PC_REG] = pc;
					return fallo("division entre cero en la instruccion " + pc);
				}
				reg[r] = reg[s] / reg[t];
				break;
			case LD:   reg[r] = dMem[m]; ultimaDireccionLeida = m; break;
			case ST:   dMem[m] = reg[r]; ultimaDireccionEscrita = m; break;
			case LDA:  reg[r] = m; break;
			case LDC:  reg[r] = ins.arg2; break;
			case JLT:  if(reg[r] <  0) reg[PC_REG] = m; break;
			case JLE:  if(reg[r] <= 0) reg[PC_REG] = m; break;
			case JGT:  if(reg[r] >  0) reg[PC_REG] = m; break;
			case JGE:  if(reg[r] >= 0) reg[PC_REG] = m; break;
			case JEQ:  if(reg[r] == 0) reg[PC_REG] = m; break;
			case JNE:  if(reg[r] != 0) reg[PC_REG] = m; break;
			default:   break;
		}
		pasos++;
		marcarCambios(antes);
		return estado;
	}

	/* Completa la instruccion IN / INC que esta esperando datos.
	 * Devuelve un mensaje de error, o null si el dato fue aceptado. */
	public String proporcionarEntrada(String linea){
		if(estado != Estado.ESPERANDO_ENTRADA)
			return "la maquina no esta esperando datos";
		if(entradaPendiente == TipoEntrada.ENTERO){
			int valor;
			try {
				valor = Integer.parseInt(linea.trim());
			} catch (NumberFormatException e) {
				return "se esperaba un numero entero";
			}
			Instruccion ins = iMem[reg[PC_REG]];
			int[] antes = reg.clone();
			reg[ins.arg1] = valor;
			reg[PC_REG]++;
			pasos++;
			Arrays.fill(registroModificado, false);
			marcarCambios(antes);
		}else{
			/* INC: la linea completa queda en el buffer; cada INC toma un caracter y
			 * al final se entrega '\n' para que el programa sepa que termino la linea */
			for(char c : linea.toCharArray()) bufferCaracteres.add(c);
			bufferCaracteres.add('\n');
		}
		entradaPendiente = TipoEntrada.NINGUNA;
		estado = Estado.LISTA;
		if(iMem[reg[PC_REG]].op == Instruccion.Opcode.INC && !bufferCaracteres.isEmpty())
			paso();   // completo la instruccion INC que estaba esperando
		return null;
	}

	private void marcarCambios(int[] antes){
		for(int i = 0; i < NO_REGS; i++)
			registroModificado[i] = antes[i] != reg[i];
	}

	private Estado fallo(String texto){
		estado = Estado.ERROR;
		mensaje = texto;
		return estado;
	}

	/* ------------------- exposicion del estado ------------------- */

	public Estado getEstado()            { return estado; }
	public String getMensaje()           { return mensaje; }
	public long getPasos()               { return pasos; }
	public TipoEntrada getEntradaPendiente() { return entradaPendiente; }
	public int getPC()                   { return reg[PC_REG]; }
	public int getRegistro(int i)        { return reg[i]; }
	public int[] getRegistros()          { return reg.clone(); }
	public boolean registroModificado(int i) { return registroModificado[i]; }
	public int getDato(int direccion)    { return dMem[direccion]; }
	public Instruccion getInstruccion(int direccion) { return iMem[direccion]; }
	public int getUltimaDireccionEscrita() { return ultimaDireccionEscrita; }
	public int getUltimaDireccionLeida()   { return ultimaDireccionLeida; }
	public boolean terminada()           { return estado == Estado.DETENIDA || estado == Estado.ERROR; }
}
