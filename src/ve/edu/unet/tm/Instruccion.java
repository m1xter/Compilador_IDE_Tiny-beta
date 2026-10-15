package ve.edu.unet.tm;

/* Una instruccion de la Tiny Machine ya decodificada.
 * Igual que en tm.c de Louden se guardan tres argumentos:
 *   RO:  op r,s,t     -> arg1=r, arg2=s, arg3=t
 *   RM:  op r,d(s)    -> arg1=r, arg2=d, arg3=s
 */
public class Instruccion {

	public enum Clase { RO, RM, RA }

	public enum Opcode {
		/* solo registros */
		HALT(Clase.RO), IN(Clase.RO), OUT(Clase.RO), ADD(Clase.RO), SUB(Clase.RO), MUL(Clase.RO), DIV(Clase.RO),
		/* extensiones Tiny++ para cadenas: lectura y escritura de UN caracter */
		INC(Clase.RO), OUTC(Clase.RO),
		/* registro - memoria (acceden a dMem) */
		LD(Clase.RM), ST(Clase.RM),
		/* registro - direccion (no acceden a dMem) */
		LDA(Clase.RA), LDC(Clase.RA), JLT(Clase.RA), JLE(Clase.RA), JGT(Clase.RA), JGE(Clase.RA), JEQ(Clase.RA), JNE(Clase.RA);

		public final Clase clase;

		Opcode(Clase clase) {
			this.clase = clase;
		}
	}

	public final Opcode op;
	public final int arg1, arg2, arg3;
	public final String comentario;

	public Instruccion(Opcode op, int arg1, int arg2, int arg3, String comentario) {
		this.op = op;
		this.arg1 = arg1;
		this.arg2 = arg2;
		this.arg3 = arg3;
		this.comentario = comentario == null ? "" : comentario;
	}

	public static Instruccion halt(){
		return new Instruccion(Opcode.HALT, 0, 0, 0, "");
	}

	/* Texto en el mismo formato que el archivo .tm */
	public String texto(){
		if(op.clase == Clase.RO)
			return String.format("%-5s %d,%d,%d", op, arg1, arg2, arg3);
		return String.format("%-5s %d,%d(%d)", op, arg1, arg2, arg3);
	}

	@Override
	public String toString() {
		return texto();
	}
}
