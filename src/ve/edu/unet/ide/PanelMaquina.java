package ve.edu.unet.ide;

import ve.edu.unet.Compilador;
import ve.edu.unet.tm.Instruccion;
import ve.edu.unet.tm.TinyMachine;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/* Vista del estado de la Tiny Machine:
 *  - Registros (PC, GP, MP, AC, AC1, FP, ...) resaltando los que cambio la ultima instruccion
 *  - Memoria de instrucciones con la instruccion actual (PC) resaltada
 *  - Memoria de datos con la ultima celda escrita/leida y las zonas (globales, literales, pila)
 */
public class PanelMaquina extends JPanel {

	private static final Color COLOR_PC = new Color(255, 236, 140);
	private static final Color COLOR_CAMBIO = new Color(255, 183, 120);
	private static final Color COLOR_LECTURA = new Color(190, 222, 255);
	private static final Color COLOR_GLOBAL = new Color(226, 245, 226);
	private static final Color COLOR_LITERAL = new Color(250, 246, 220);
	private static final Color COLOR_PILA = new Color(236, 230, 250);
	private static final Color COLOR_MARCO = new Color(214, 200, 245);
	private static final Color COLOR_RUPTURA = new Color(255, 205, 205);
	private static final javax.swing.border.Border RELLENO = BorderFactory.createEmptyBorder(0, 5, 0, 8);

	private TinyMachine tm;
	private int[] lineasFuente = new int[0];
	private Map<Integer, String> etiquetas = Collections.emptyMap();
	private java.util.List<Compilador.MarcoFuncion> marcos = Collections.emptyList();
	private int cantidadInstrucciones = 0;
	private int finEstatico = 1;     // primera direccion que no es global ni literal
	private int dirHP = -1;          // celda con el apuntador del heap (si existe)
	private final Set<Integer> rupturasInstruccion = new TreeSet<>();

	private final ModeloRegistros modeloRegistros = new ModeloRegistros();
	private final ModeloInstrucciones modeloInstrucciones = new ModeloInstrucciones();
	private final ModeloDatos modeloDatos = new ModeloDatos();
	private final JTable tablaRegistros = new JTable(modeloRegistros);
	private final JTable tablaInstrucciones = new JTable(modeloInstrucciones);
	private final JTable tablaDatos = new JTable(modeloDatos);
	private final JCheckBox seguirPila = new JCheckBox("Seguir MP", true);

	public PanelMaquina() {
		super(new BorderLayout(4, 4));

		configurarTabla(tablaRegistros);
		tablaRegistros.getColumnModel().getColumn(0).setPreferredWidth(40);
		tablaRegistros.getColumnModel().getColumn(1).setPreferredWidth(50);
		tablaRegistros.getColumnModel().getColumn(2).setPreferredWidth(80);
		tablaRegistros.setDefaultRenderer(Object.class, new RenderRegistros());
		JPanel registros = new JPanel(new BorderLayout());
		registros.setBorder(titulo("Registros"));
		registros.add(tablaRegistros.getTableHeader(), BorderLayout.NORTH);
		registros.add(tablaRegistros, BorderLayout.CENTER);

		configurarTabla(tablaInstrucciones);
		tablaInstrucciones.getColumnModel().getColumn(0).setPreferredWidth(25);
		tablaInstrucciones.getColumnModel().getColumn(0).setMaxWidth(30);
		tablaInstrucciones.getColumnModel().getColumn(1).setPreferredWidth(45);
		tablaInstrucciones.getColumnModel().getColumn(1).setMinWidth(48);
		tablaInstrucciones.getColumnModel().getColumn(1).setMaxWidth(60);
		tablaInstrucciones.getColumnModel().getColumn(2).setPreferredWidth(150);
		tablaInstrucciones.getColumnModel().getColumn(2).setMinWidth(140);
		tablaInstrucciones.getColumnModel().getColumn(3).setPreferredWidth(45);
		tablaInstrucciones.getColumnModel().getColumn(3).setMinWidth(44);
		tablaInstrucciones.getColumnModel().getColumn(3).setMaxWidth(55);
		tablaInstrucciones.getColumnModel().getColumn(4).setPreferredWidth(300);
		tablaInstrucciones.setDefaultRenderer(Object.class, new RenderInstrucciones());
		tablaInstrucciones.setToolTipText("Doble clic: poner/quitar punto de ruptura en la instruccion");
		tablaInstrucciones.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if(e.getClickCount() == 2){
					int fila = tablaInstrucciones.rowAtPoint(e.getPoint());
					if(fila >= 0 && !rupturasInstruccion.remove(fila)) rupturasInstruccion.add(fila);
					modeloInstrucciones.fireTableRowsUpdated(fila, fila);
				}
			}
		});
		JPanel instrucciones = new JPanel(new BorderLayout());
		instrucciones.setBorder(titulo("Memoria de Instrucciones (iMem)"));
		instrucciones.add(new JScrollPane(tablaInstrucciones), BorderLayout.CENTER);

		configurarTabla(tablaDatos);
		tablaDatos.getColumnModel().getColumn(0).setPreferredWidth(50);
		tablaDatos.getColumnModel().getColumn(0).setMaxWidth(70);
		tablaDatos.getColumnModel().getColumn(1).setPreferredWidth(70);
		tablaDatos.getColumnModel().getColumn(1).setMaxWidth(110);
		tablaDatos.getColumnModel().getColumn(2).setPreferredWidth(40);
		tablaDatos.getColumnModel().getColumn(2).setMaxWidth(50);
		tablaDatos.getColumnModel().getColumn(3).setPreferredWidth(150);
		tablaDatos.setDefaultRenderer(Object.class, new RenderDatos());
		JPanel datos = new JPanel(new BorderLayout());
		datos.setBorder(titulo("Memoria de Datos (dMem)"));
		datos.add(new JScrollPane(tablaDatos), BorderLayout.CENTER);
		JPanel opcionesDatos = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		opcionesDatos.add(seguirPila);
		JButton irGlobales = new JButton("Ir a globales");
		irGlobales.addActionListener(e -> desplazarA(tablaDatos, 0, false));
		JButton irPila = new JButton("Ir a la pila");
		irPila.addActionListener(e -> { if(tm != null) desplazarA(tablaDatos, tm.getRegistro(6), true); });
		opcionesDatos.add(irGlobales);
		opcionesDatos.add(irPila);
		datos.add(opcionesDatos, BorderLayout.SOUTH);

		JPanel leyenda = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		leyenda.add(muestra(COLOR_PC, "PC"));
		leyenda.add(muestra(COLOR_CAMBIO, "escrito"));
		leyenda.add(muestra(COLOR_LECTURA, "leido"));
		leyenda.add(muestra(COLOR_GLOBAL, "globales"));
		leyenda.add(muestra(COLOR_LITERAL, "literales/heap"));
		leyenda.add(muestra(COLOR_PILA, "pila"));
		leyenda.add(muestra(COLOR_MARCO, "FP"));
		leyenda.add(muestra(COLOR_RUPTURA, "ruptura"));

		JPanel izquierda = new JPanel(new BorderLayout(4, 4));
		izquierda.add(registros, BorderLayout.NORTH);
		izquierda.add(datos, BorderLayout.CENTER);

		JSplitPane division = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, instrucciones, izquierda);
		division.setResizeWeight(0.62);
		division.setBorder(null);
		add(division, BorderLayout.CENTER);
		add(leyenda, BorderLayout.SOUTH);
	}

	private static TitledBorder titulo(String t){
		TitledBorder b = BorderFactory.createTitledBorder(t);
		b.setTitleFont(b.getTitleFont() == null ? new Font(Font.SANS_SERIF, Font.BOLD, 12) : b.getTitleFont().deriveFont(Font.BOLD));
		return b;
	}

	private static JComponent muestra(Color c, String texto){
		JLabel l = new JLabel(texto);
		l.setIcon(new Icon() {
			public void paintIcon(Component comp, Graphics g, int x, int y) {
				g.setColor(c); g.fillRect(x, y, 12, 12);
				g.setColor(Color.GRAY); g.drawRect(x, y, 12, 12);
			}
			public int getIconWidth()  { return 13; }
			public int getIconHeight() { return 13; }
		});
		l.setFont(l.getFont().deriveFont(11f));
		return l;
	}

	private static void configurarTabla(JTable t){
		t.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
		/* colores fijos: no depender del tema (claro/oscuro) del sistema operativo */
		t.setBackground(Color.WHITE);
		t.setForeground(Color.BLACK);
		t.setRowHeight(19);
		t.setFillsViewportHeight(true);
		t.setShowGrid(false);
		t.setIntercellSpacing(new Dimension(0, 0));
		t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		t.getTableHeader().setReorderingAllowed(false);
	}

	/* ------------------------- API ------------------------- */

	public void setMaquina(TinyMachine tm, int cantidadInstrucciones, int[] lineasFuente, Map<Integer, String> etiquetas,
						   java.util.List<Compilador.MarcoFuncion> marcos){
		this.tm = tm;
		this.marcos = marcos == null ? Collections.<Compilador.MarcoFuncion>emptyList() : marcos;
		this.cantidadInstrucciones = Math.max(1, cantidadInstrucciones);
		this.lineasFuente = lineasFuente == null ? new int[0] : lineasFuente;
		this.etiquetas = etiquetas == null ? Collections.emptyMap() : etiquetas;
		this.finEstatico = 1;
		this.dirHP = -1;
		for(Map.Entry<Integer, String> e : this.etiquetas.entrySet()){
			finEstatico = Math.max(finEstatico, e.getKey() + 1);
			if(e.getValue().startsWith("HP")) dirHP = e.getKey();
		}
		rupturasInstruccion.clear();
		modeloInstrucciones.fireTableDataChanged();
		actualizar();
	}

	public Set<Integer> getRupturasInstruccion(){
		return rupturasInstruccion;
	}

	public int lineaFuente(int direccion){
		return direccion >= 0 && direccion < lineasFuente.length ? lineasFuente[direccion] : 0;
	}

	/* Refresca las tres vistas con el estado actual de la maquina */
	public void actualizar(){
		modeloRegistros.fireTableDataChanged();
		modeloInstrucciones.fireTableRowsUpdated(0, Math.max(0, modeloInstrucciones.getRowCount() - 1));
		modeloDatos.fireTableRowsUpdated(0, TinyMachine.DADDR_SIZE - 1);
		if(tm == null) return;
		desplazarA(tablaInstrucciones, tm.getPC(), true);
		int escrita = tm.getUltimaDireccionEscrita();
		if(escrita >= 0)
			desplazarA(tablaDatos, escrita, true);
		else if(seguirPila.isSelected() && tm.getRegistro(6) < TinyMachine.DADDR_SIZE - 1)
			desplazarA(tablaDatos, tm.getRegistro(6), true);
	}

	private static void desplazarA(JTable tabla, int fila, boolean centrar){
		if(fila < 0 || fila >= tabla.getRowCount()) return;
		Rectangle celda = tabla.getCellRect(fila, 0, true);
		if(centrar){
			Rectangle visible = tabla.getVisibleRect();
			if(visible.contains(celda)) return;
			celda.y = Math.max(0, celda.y - visible.height / 2);
			celda.height = visible.height;
		}
		tabla.scrollRectToVisible(celda);
	}

	/* ------------------------- modelos ------------------------- */

	private class ModeloRegistros extends AbstractTableModel {
		private final String[] columnas = {"Reg", "Nombre", "Valor"};
		/* orden de presentacion: primero los que pide el enunciado */
		private final int[] orden = {7, 5, 6, 0, 1, 4, 2, 3};

		public int getRowCount()    { return TinyMachine.NO_REGS; }
		public int getColumnCount() { return columnas.length; }
		public String getColumnName(int c) { return columnas[c]; }

		public Object getValueAt(int fila, int col) {
			int r = orden[fila];
			switch(col){
				case 0: return "r" + r;
				case 1: return TinyMachine.NOMBRES_REGISTROS[r];
				default: return tm == null ? "" : String.valueOf(tm.getRegistro(r));
			}
		}

		boolean modificado(int fila){
			return tm != null && tm.registroModificado(orden[fila]);
		}
	}

	private class ModeloInstrucciones extends AbstractTableModel {
		private final String[] columnas = {"", "Dir", "Instruccion", "Linea", "Comentario"};

		public int getRowCount()    { return cantidadInstrucciones; }
		public int getColumnCount() { return columnas.length; }
		public String getColumnName(int c) { return columnas[c]; }

		public Object getValueAt(int fila, int col) {
			if(tm == null) return "";
			switch(col){
				case 0: return fila == tm.getPC() ? "▶" : (rupturasInstruccion.contains(fila) ? "●" : "");
				case 1: return String.valueOf(fila);
				case 2: return tm.getInstruccion(fila).texto();
				case 3: { int l = lineaFuente(fila); return l > 0 ? String.valueOf(l) : ""; }
				default: return tm.getInstruccion(fila).comentario;
			}
		}
	}

	private class ModeloDatos extends AbstractTableModel {
		private final String[] columnas = {"Dir", "Valor", "Car", "Contenido"};

		public int getRowCount()    { return TinyMachine.DADDR_SIZE; }
		public int getColumnCount() { return columnas.length; }
		public String getColumnName(int c) { return columnas[c]; }

		public Object getValueAt(int fila, int col) {
			if(tm == null) return col == 0 ? String.valueOf(fila) : "";
			int v = tm.getDato(fila);
			switch(col){
				case 0: return String.valueOf(fila);
				case 1: return String.valueOf(v);
				case 2: return (v >= 32 && v < 127) ? "'" + (char)v + "'" : "";
				default: return descripcion(fila);
			}
		}

		private String descripcion(int dir){
			StringBuilder sb = new StringBuilder();
			String e = etiquetas.get(dir);
			if(e != null) sb.append(e);
			int mp = tm.getRegistro(6), fp = tm.getRegistro(4);
			if(fp != 0 && dir > mp){
				/* celda del marco actual (FP): nombre del parametro/local si se conoce la funcion */
				int d = dir - fp;
				Compilador.MarcoFuncion m = marcoActual();
				String nombre = m != null ? m.desplazamientos.get(d) : null;
				if(d == 0) sb.append("FP anterior");
				else if(d == 1) sb.append("dir. retorno");
				else if(nombre != null) sb.append(m.nombre).append(".").append(nombre);
				else if(d < 0 && m != null && -d <= m.desplazamientos.size()) sb.append("local FP").append(d);
				if(sb.length() > 0) sb.append("  [FP").append(d >= 0 ? "+" : "").append(d).append("]");
			}
			if(dir == mp) sb.append(sb.length() > 0 ? "  " : "").append("<- MP (tope)");
			if(fp != 0 && dir == fp) sb.append("  <- FP");
			return sb.toString();
		}

		/* Funcion cuyo marco apunta FP: la que contiene al PC (si el PC esta en una
		 * rutina de tiempo de ejecucion no se conoce y se muestran desplazamientos) */
		private Compilador.MarcoFuncion marcoActual(){
			int pc = tm.getPC();
			for(Compilador.MarcoFuncion m : marcos)
				if(pc >= m.inicio && pc < m.fin) return m;
			return null;
		}
	}

	/* ------------------------- renderers ------------------------- */

	private class RenderRegistros extends DefaultTableCellRenderer {
		@Override
		public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foco, int fila, int col) {
			super.getTableCellRendererComponent(t, v, false, false, fila, col);
			setBackground(modeloRegistros.modificado(fila) ? COLOR_CAMBIO : (fila == 0 ? COLOR_PC : Color.WHITE));
			setForeground(Color.BLACK);
			setFont(fila < 5 ? t.getFont().deriveFont(Font.BOLD) : t.getFont());
			setHorizontalAlignment(col == 2 ? RIGHT : LEFT);
			setBorder(RELLENO);
			return this;
		}
	}

	private class RenderInstrucciones extends DefaultTableCellRenderer {
		@Override
		public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foco, int fila, int col) {
			super.getTableCellRendererComponent(t, v, false, false, fila, col);
			Color fondo = Color.WHITE;
			if(rupturasInstruccion.contains(fila)) fondo = COLOR_RUPTURA;
			if(tm != null && fila == tm.getPC()) fondo = COLOR_PC;
			setBackground(fondo);
			setForeground(col == 4 ? new Color(0x70, 0x70, 0x70) : (col == 0 ? new Color(0xC6, 0x28, 0x28) : Color.BLACK));
			if(tm != null && fila == tm.getPC() && col == 0) setForeground(new Color(0x1B, 0x5E, 0x20));
			setFont(tm != null && fila == tm.getPC() ? t.getFont().deriveFont(Font.BOLD) : t.getFont());
			setHorizontalAlignment(col == 1 || col == 3 ? RIGHT : LEFT);
			setBorder(RELLENO);
			return this;
		}
	}

	private class RenderDatos extends DefaultTableCellRenderer {
		@Override
		public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foco, int fila, int col) {
			super.getTableCellRendererComponent(t, v, false, false, fila, col);
			Color fondo = Color.WHITE;
			if(tm != null){
				int mp = tm.getRegistro(6), fp = tm.getRegistro(4);
				String e = etiquetas.get(fila);
				if(e != null && !e.startsWith("\"") && !e.startsWith("HP") && fila > 0) fondo = COLOR_GLOBAL;
				else if(fila > 0 && fila < finEstatico) fondo = COLOR_LITERAL;
				else if(dirHP > 0 && fila >= finEstatico && fila < tm.getDato(dirHP)) fondo = COLOR_LITERAL;  // heap usado
				if(fila > mp) fondo = COLOR_PILA;
				if(fp != 0 && fila == fp) fondo = COLOR_MARCO;
				if(fila == tm.getUltimaDireccionLeida()) fondo = COLOR_LECTURA;
				if(fila == tm.getUltimaDireccionEscrita()) fondo = COLOR_CAMBIO;
				setFont(fila == mp || fila == tm.getUltimaDireccionEscrita() ? t.getFont().deriveFont(Font.BOLD) : t.getFont());
			}
			setBackground(fondo);
			setForeground(col == 3 ? new Color(0x30, 0x30, 0x60) : Color.BLACK);
			setHorizontalAlignment(col == 0 || col == 1 ? RIGHT : LEFT);
			setBorder(RELLENO);
			return this;
		}
	}
}
