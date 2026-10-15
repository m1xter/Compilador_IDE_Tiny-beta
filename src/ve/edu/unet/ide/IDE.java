package ve.edu.unet.ide;

import ve.edu.unet.Compilador;
import ve.edu.unet.ErrorCompilacion;
import ve.edu.unet.tm.CargadorTM;
import ve.edu.unet.tm.Instruccion;
import ve.edu.unet.tm.TinyMachine;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Hashtable;

/* Ventana principal del IDE Tiny++.
 *
 * Integra: editor -> Compilador (en memoria) -> CargadorTM -> TinyMachine -> PanelMaquina.
 * La maquina se ejecuta en el hilo de Swing por medio de un javax.swing.Timer: en cada
 * "tic" se ejecutan una o varias instrucciones (segun la velocidad) y se refresca la vista.
 * Las instrucciones IN/INC detienen el temporizador hasta que el usuario escribe el dato
 * en el campo de entrada del panel "Entrada / Salida".
 */
public class IDE extends JFrame {

	private static final String EJEMPLO =
			"{ Programa de ejemplo Tiny++ }\n" +
			"int n, i;\n" +
			"int v[10];\n" +
			"string nombre;\n" +
			"\n" +
			"function int factorial(int x)\n" +
			"    if x <= 1 then\n" +
			"        return 1\n" +
			"    else\n" +
			"        return x * factorial(x - 1)\n" +
			"    end\n" +
			"end;\n" +
			"\n" +
			"procedure saludar(string quien)\n" +
			"    write \"Hola, \" + quien + \"!\"\n" +
			"end;\n" +
			"\n" +
			"write \"Como te llamas?\";\n" +
			"read nombre;\n" +
			"saludar(nombre);\n" +
			"write \"Factoriales de 0 a 5 (guardados en v):\";\n" +
			"i := 0;\n" +
			"while i <= 5 do\n" +
			"    v[i] := factorial(i);\n" +
			"    write v[i];\n" +
			"    i := i + 1\n" +
			"end\n";

	private enum Modo { SIN_PROGRAMA, LISTO, CORRIENDO, PAUSADO, ESPERANDO, TERMINADO }

	private final EditorCodigo editor = new EditorCodigo();
	private final PanelMaquina panelMaquina = new PanelMaquina();
	private final DefaultListModel<Mensaje> mensajes = new DefaultListModel<>();
	private final JList<Mensaje> listaMensajes = new JList<>(mensajes);
	private final JTextArea areaSalida = new JTextArea();
	private final JTextField campoEntrada = new JTextField();
	private final JButton botonEnviar = new JButton("Enviar");
	private final JLabel etiquetaEntrada = new JLabel("Entrada:");
	private final JTextArea areaCodigoTM = areaSoloLectura();
	private final JTextArea areaAST = areaSoloLectura();
	private final JTextArea areaTabla = areaSoloLectura();
	private final JTabbedPane pestanasInferiores = new JTabbedPane();
	private final JTabbedPane pestanasDerecha = new JTabbedPane();
	private final JLabel barraEstado = new JLabel(" ");
	private final JLabel barraMaquina = new JLabel(" ");
	private final JSlider velocidad = new JSlider(1, 10, 6);

	private final TinyMachine tm = new TinyMachine();
	private final Timer temporizador = new Timer(30, e -> tic());
	private Modo modo = Modo.SIN_PROGRAMA;
	private boolean reanudarTrasEntrada = false;

	private File archivoActual = null;
	private boolean modificado = false;
	/* true si el codigo cargado en la TM corresponde al texto actual del editor */
	private boolean compilado = false;
	private Compilador.Resultado ultimoResultado = null;

	private Action accionCompilar, accionEjecutar, accionPaso, accionPasoLinea, accionPausa, accionReiniciar;

	public IDE() {
		super("Tiny++ IDE");
		setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				if(confirmarDescartar()) System.exit(0);
			}
		});

		crearAcciones();
		setJMenuBar(crearMenu());

		/* ---- consola de mensajes del compilador ---- */
		listaMensajes.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
		listaMensajes.setBackground(Color.WHITE);
		listaMensajes.setForeground(Color.BLACK);
		listaMensajes.setCellRenderer(new RenderMensaje());
		listaMensajes.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				Mensaje m = listaMensajes.getSelectedValue();
				if(m != null && m.linea > 0) editor.irALinea(m.linea);
			}
		});

		/* ---- entrada / salida del programa ---- */
		areaSalida.setEditable(false);
		areaSalida.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
		areaSalida.setBackground(new Color(0x1E, 0x1E, 0x1E));
		areaSalida.setForeground(new Color(0xE0, 0xE0, 0xE0));
		areaSalida.setCaretColor(Color.WHITE);
		areaSalida.setMargin(new Insets(4, 6, 4, 6));
		tm.setSalida(texto -> {
			areaSalida.append(texto);
			areaSalida.setCaretPosition(areaSalida.getDocument().getLength());
		});
		campoEntrada.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
		campoEntrada.setForeground(Color.BLACK);
		campoEntrada.setDisabledTextColor(Color.GRAY);
		campoEntrada.setCaretColor(Color.BLACK);
		campoEntrada.addActionListener(e -> enviarEntrada());
		botonEnviar.addActionListener(e -> enviarEntrada());
		habilitarEntrada(false);
		JPanel filaEntrada = new JPanel(new BorderLayout(6, 0));
		filaEntrada.setBorder(new EmptyBorder(4, 4, 4, 4));
		filaEntrada.add(etiquetaEntrada, BorderLayout.WEST);
		filaEntrada.add(campoEntrada, BorderLayout.CENTER);
		filaEntrada.add(botonEnviar, BorderLayout.EAST);
		JPanel panelES = new JPanel(new BorderLayout());
		panelES.add(new JScrollPane(areaSalida), BorderLayout.CENTER);
		panelES.add(filaEntrada, BorderLayout.SOUTH);

		pestanasInferiores.addTab("Consola", new JScrollPane(listaMensajes));
		pestanasInferiores.addTab("Entrada / Salida", panelES);

		JSplitPane izquierda = new JSplitPane(JSplitPane.VERTICAL_SPLIT, editor, pestanasInferiores);
		izquierda.setResizeWeight(0.7);

		pestanasDerecha.addTab("Depurador TM", panelMaquina);
		pestanasDerecha.addTab("Codigo TM", new JScrollPane(areaCodigoTM));
		pestanasDerecha.addTab("AST", new JScrollPane(areaAST));
		pestanasDerecha.addTab("Tabla de simbolos", new JScrollPane(areaTabla));

		JSplitPane principal = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, izquierda, pestanasDerecha);
		principal.setResizeWeight(0.42);

		JPanel estado = new JPanel(new BorderLayout());
		estado.setBorder(new EmptyBorder(3, 8, 3, 8));
		estado.setOpaque(true);
		estado.setBackground(new Color(0xEC, 0xEC, 0xEC));
		barraMaquina.setForeground(Color.BLACK);
		estado.add(barraEstado, BorderLayout.CENTER);
		estado.add(barraMaquina, BorderLayout.EAST);

		getContentPane().add(crearBarraHerramientas(), BorderLayout.NORTH);
		getContentPane().add(principal, BorderLayout.CENTER);
		getContentPane().add(estado, BorderLayout.SOUTH);

		editor.setAlModificar(() -> {
			if(!modificado){ modificado = true; actualizarTitulo(); }
			compilado = false;
		});
		editor.setTexto(EJEMPLO);
		modificado = false;

		setSize(1500, 900);
		setLocationRelativeTo(null);
		actualizarTitulo();
		actualizarControles();
		estado("Listo. Escriba un programa Tiny++ y presione Ejecutar (F5) o Paso (F7).", false);
	}

	/* ===================================================================
	 *  Acciones, menu y barra de herramientas
	 * =================================================================== */

	private Action accion(String nombre, String atajo, String ayuda, Runnable r){
		Action a = new AbstractAction(nombre) {
			public void actionPerformed(ActionEvent e) { r.run(); }
		};
		if(atajo != null) a.putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(atajo));
		a.putValue(Action.SHORT_DESCRIPTION, ayuda + (atajo != null ? "  (" + atajo.replace("ctrl ", "Ctrl+") + ")" : ""));
		return a;
	}

	private void crearAcciones(){
		accionCompilar  = accion("Compilar", "ctrl B", "Compilar el programa", this::compilar);
		accionEjecutar  = accion("▶ Ejecutar", "F5", "Ejecutar / continuar hasta el final o un punto de ruptura", this::ejecutar);
		accionPaso      = accion("Paso instr.", "F7", "Ejecutar una instruccion de la TM", this::pasoInstruccion);
		accionPasoLinea = accion("Paso linea", "F8", "Ejecutar hasta la siguiente linea del codigo fuente", this::pasoLinea);
		accionPausa     = accion("⏸ Pausa", "F6", "Pausar la ejecucion", this::pausar);
		accionReiniciar = accion("⟲ Reiniciar", "ctrl R", "Reiniciar la maquina (PC = 0)", this::reiniciar);
	}

	private JMenuBar crearMenu(){
		JMenuBar barra = new JMenuBar();
		JMenu archivo = new JMenu("Archivo");
		archivo.add(new JMenuItem(accion("Nuevo", "ctrl N", "Nuevo programa", this::nuevo)));
		archivo.add(new JMenuItem(accion("Abrir...", "ctrl O", "Abrir un programa .tny", this::abrir)));
		archivo.add(new JMenuItem(accion("Guardar", "ctrl S", "Guardar", () -> guardar(false))));
		archivo.add(new JMenuItem(accion("Guardar como...", "ctrl shift S", "Guardar como", () -> guardar(true))));
		archivo.addSeparator();
		archivo.add(new JMenuItem(accion("Exportar codigo .tm...", null, "Guardar el codigo objeto generado", this::exportarTM)));
		archivo.add(new JMenuItem(accion("Depurar archivo .tm...", null, "Cargar un archivo .tm directamente en la TM", this::abrirTM)));
		archivo.addSeparator();
		archivo.add(new JMenuItem(accion("Salir", null, "Salir", () -> { if(confirmarDescartar()) System.exit(0); })));
		JMenu ejecutar = new JMenu("Ejecutar");
		ejecutar.add(new JMenuItem(accionCompilar));
		ejecutar.addSeparator();
		ejecutar.add(new JMenuItem(accionEjecutar));
		ejecutar.add(new JMenuItem(accionPausa));
		ejecutar.add(new JMenuItem(accionPaso));
		ejecutar.add(new JMenuItem(accionPasoLinea));
		ejecutar.add(new JMenuItem(accionReiniciar));
		JMenu ayuda = new JMenu("Ayuda");
		ayuda.add(new JMenuItem(accion("Lenguaje Tiny++", "F1", "Resumen del lenguaje", this::mostrarAyuda)));
		barra.add(archivo);
		barra.add(ejecutar);
		barra.add(ayuda);
		return barra;
	}

	private JToolBar crearBarraHerramientas(){
		JToolBar barra = new JToolBar();
		barra.setFloatable(false);
		barra.add(accion("Nuevo", null, "Nuevo programa", this::nuevo));
		barra.add(accion("Abrir", null, "Abrir un programa .tny", this::abrir));
		barra.add(accion("Guardar", null, "Guardar", () -> guardar(false)));
		barra.addSeparator();
		barra.add(accionCompilar);
		barra.addSeparator();
		barra.add(accionEjecutar);
		barra.add(accionPausa);
		barra.add(accionPaso);
		barra.add(accionPasoLinea);
		barra.add(accionReiniciar);
		barra.addSeparator();
		barra.add(new JLabel(" Velocidad: "));
		Hashtable<Integer, JLabel> marcas = new Hashtable<>();
		marcas.put(1, new JLabel("lenta"));
		marcas.put(10, new JLabel("max"));
		velocidad.setLabelTable(marcas);
		velocidad.setPaintLabels(true);
		velocidad.setMaximumSize(new Dimension(180, 50));
		velocidad.setToolTipText("Velocidad de ejecucion (las velocidades bajas animan instruccion por instruccion)");
		velocidad.addChangeListener(e -> temporizador.setDelay(retardo()));
		barra.add(velocidad);
		return barra;
	}

	/* ===================================================================
	 *  Archivos
	 * =================================================================== */

	private JFileChooser selector(String descripcion, String extension){
		JFileChooser fc = new JFileChooser(archivoActual != null ? archivoActual.getParentFile() : new File("."));
		fc.setFileFilter(new FileNameExtensionFilter(descripcion, extension));
		return fc;
	}

	private void nuevo(){
		if(!confirmarDescartar()) return;
		detenerMaquina();
		editor.setTexto("");
		archivoActual = null;
		modificado = false;
		actualizarTitulo();
	}

	private void abrir(){
		if(!confirmarDescartar()) return;
		JFileChooser fc = selector("Programas Tiny++ (*.tny)", "tny");
		if(fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
		try {
			String t = new String(Files.readAllBytes(fc.getSelectedFile().toPath()), StandardCharsets.UTF_8);
			detenerMaquina();
			editor.setTexto(t);
			archivoActual = fc.getSelectedFile();
			modificado = false;
			actualizarTitulo();
			estado("Abierto " + archivoActual.getName(), false);
		} catch (IOException ex) {
			error("No se pudo abrir el archivo: " + ex.getMessage());
		}
	}

	private boolean guardar(boolean como){
		File destino = archivoActual;
		if(como || destino == null){
			JFileChooser fc = selector("Programas Tiny++ (*.tny)", "tny");
			if(fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return false;
			destino = fc.getSelectedFile();
			if(!destino.getName().contains(".")) destino = new File(destino.getPath() + ".tny");
		}
		try {
			Files.write(destino.toPath(), editor.getTexto().getBytes(StandardCharsets.UTF_8));
			archivoActual = destino;
			modificado = false;
			actualizarTitulo();
			estado("Guardado " + destino.getName(), false);
			return true;
		} catch (IOException ex) {
			error("No se pudo guardar: " + ex.getMessage());
			return false;
		}
	}

	private void exportarTM(){
		if(!compilado && !compilar()) return;
		JFileChooser fc = selector("Codigo TM (*.tm)", "tm");
		if(archivoActual != null)
			fc.setSelectedFile(new File(archivoActual.getParentFile(), archivoActual.getName().replaceAll("\\.tny$", "") + ".tm"));
		if(fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
		try {
			Files.write(fc.getSelectedFile().toPath(), ultimoResultado.codigoTM.getBytes(StandardCharsets.UTF_8));
			estado("Codigo TM exportado a " + fc.getSelectedFile().getName(), false);
		} catch (IOException ex) {
			error("No se pudo guardar: " + ex.getMessage());
		}
	}

	/* Carga un .tm (por ejemplo generado por el compilador original) sin codigo fuente */
	private void abrirTM(){
		JFileChooser fc = selector("Codigo TM (*.tm)", "tm");
		if(fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
		try {
			String t = new String(Files.readAllBytes(fc.getSelectedFile().toPath()), StandardCharsets.UTF_8);
			mensajes.clear();
			if(!cargarEnMaquina(t, new int[0], Collections.emptyMap(), Collections.emptyList())) return;
			compilado = false;
			ultimoResultado = null;
			areaCodigoTM.setText(t);
			areaCodigoTM.setCaretPosition(0);
			pestanasDerecha.setSelectedIndex(0);
			agregarMensaje("Archivo TM cargado: " + fc.getSelectedFile().getName(), 0, false);
			estado("Archivo .tm cargado en la maquina. Use Paso o Ejecutar.", false);
		} catch (IOException ex) {
			error("No se pudo abrir el archivo: " + ex.getMessage());
		}
	}

	private boolean confirmarDescartar(){
		if(!modificado) return true;
		int r = JOptionPane.showConfirmDialog(this, "El programa tiene cambios sin guardar. Desea guardarlos?",
				"Cambios sin guardar", JOptionPane.YES_NO_CANCEL_OPTION);
		if(r == JOptionPane.YES_OPTION) return guardar(false);
		return r == JOptionPane.NO_OPTION;
	}

	private void actualizarTitulo(){
		setTitle("Tiny++ IDE - " + (archivoActual != null ? archivoActual.getName() : "sin titulo") + (modificado ? " *" : ""));
	}

	/* ===================================================================
	 *  Compilacion
	 * =================================================================== */

	private boolean compilar(){
		detenerMaquina();
		mensajes.clear();
		editor.limpiarErrores();
		String nombre = archivoActual != null ? archivoActual.getName() : "sin_titulo.tny";
		long inicio = System.nanoTime();
		Compilador.Resultado r = Compilador.compilar(editor.getTexto(), nombre);
		long ms = (System.nanoTime() - inicio) / 1_000_000;
		ultimoResultado = r;
		areaAST.setText(r.ast);
		areaAST.setCaretPosition(0);
		areaTabla.setText(r.tablaSimbolos);
		areaTabla.setCaretPosition(0);
		pestanasInferiores.setSelectedIndex(0);

		if(!r.exito){
			for(ErrorCompilacion e : r.errores.getErrores()){
				agregarMensaje(e.toString(), e.getLinea(), true);
				editor.marcarError(e.getLinea());
			}
			agregarMensaje(r.errores.getErrores().size() + " error(es). No se genero codigo objeto.", 0, true);
			areaCodigoTM.setText("");
			compilado = false;
			modo = Modo.SIN_PROGRAMA;
			actualizarControles();
			estado("Compilacion con errores (clic en un mensaje para ir a la linea)", true);
			int primera = r.errores.getErrores().get(0).getLinea();
			editor.irALinea(primera);
			return false;
		}
		areaCodigoTM.setText(r.codigoTM);
		areaCodigoTM.setCaretPosition(0);
		agregarMensaje("Compilacion exitosa de " + nombre + ": " + r.lineasFuente.length + " instrucciones TM (" + ms + " ms)", 0, false);
		if(!cargarEnMaquina(r.codigoTM, r.lineasFuente, r.etiquetasMemoria, r.marcos)) return false;
		compilado = true;
		estado("Programa compilado y cargado en la TM", false);
		return true;
	}

	private boolean cargarEnMaquina(String codigo, int[] lineas, java.util.Map<Integer, String> etiquetas,
									java.util.List<Compilador.MarcoFuncion> marcos){
		CargadorTM cargador = new CargadorTM();
		Instruccion[] programa = cargador.cargar(codigo, TinyMachine.IADDR_SIZE);
		if(!cargador.getErrores().isEmpty()){
			for(String e : cargador.getErrores()) agregarMensaje("TM: " + e, 0, true);
			estado("El codigo TM tiene errores", true);
			return false;
		}
		int cantidad = 0;
		for(int i = 0; i < programa.length; i++)
			if(programa[i].op != Instruccion.Opcode.HALT || !programa[i].comentario.isEmpty() || i == 0) cantidad = i + 1;
		cantidad = Math.max(cantidad, lineas.length);
		tm.cargar(programa);
		panelMaquina.setMaquina(tm, cantidad, lineas, etiquetas, marcos);
		areaSalida.setText("");
		modo = Modo.LISTO;
		habilitarEntrada(false);
		refrescar();
		return true;
	}

	/* Antes de ejecutar: compila si el texto cambio */
	private boolean prepararEjecucion(){
		if(!compilado && ultimoResultado == null && modo != Modo.SIN_PROGRAMA)
			return true;   // hay un .tm cargado directamente
		if(!compilado){
			if(!compilar()) return false;
		}
		if(modo == Modo.TERMINADO){
			reiniciarMaquina();
		}
		return true;
	}

	/* ===================================================================
	 *  Control de la ejecucion
	 * =================================================================== */

	private void ejecutar(){
		if(modo == Modo.CORRIENDO || modo == Modo.ESPERANDO) return;
		if(!prepararEjecucion()) return;
		pestanasInferiores.setSelectedIndex(1);
		pestanasDerecha.setSelectedIndex(0);
		modo = Modo.CORRIENDO;
		temporizador.setDelay(retardo());
		temporizador.start();
		actualizarControles();
		estado("Ejecutando...", false);
	}

	private void pausar(){
		if(modo != Modo.CORRIENDO) return;
		temporizador.stop();
		modo = Modo.PAUSADO;
		refrescar();
		estado("Pausado en PC = " + tm.getPC(), false);
	}

	private void pasoInstruccion(){
		if(modo == Modo.CORRIENDO || modo == Modo.ESPERANDO) return;
		if(!prepararEjecucion()) return;
		pestanasDerecha.setSelectedIndex(0);
		int pc = tm.getPC();
		Instruccion ins = tm.getInstruccion(pc);
		tm.paso();
		modo = Modo.PAUSADO;
		despuesDeEjecutar(false);
		if(modo == Modo.PAUSADO)
			estado("Ejecutada " + pc + ": " + ins.texto() + (ins.comentario.isEmpty() ? "" : "   (" + ins.comentario + ")"), false);
	}

	/* Ejecuta instrucciones hasta que cambie la linea del codigo fuente */
	private void pasoLinea(){
		if(modo == Modo.CORRIENDO || modo == Modo.ESPERANDO) return;
		if(!prepararEjecucion()) return;
		pestanasDerecha.setSelectedIndex(0);
		int lineaInicial = panelMaquina.lineaFuente(tm.getPC());
		int limite = 200000;
		modo = Modo.PAUSADO;
		do {
			tm.paso();
			if(tm.getEstado() != TinyMachine.Estado.LISTA) break;
			int l = panelMaquina.lineaFuente(tm.getPC());
			if(l != 0 && l != lineaInicial) break;
		} while(--limite > 0);
		despuesDeEjecutar(false);
		if(modo == Modo.PAUSADO)
			estado("Detenido en la linea " + panelMaquina.lineaFuente(tm.getPC()) + " (PC = " + tm.getPC() + ")", false);
	}

	private void reiniciar(){
		if(modo == Modo.SIN_PROGRAMA){
			compilar();
			return;
		}
		reiniciarMaquina();
		estado("Maquina reiniciada: PC = 0", false);
	}

	private void reiniciarMaquina(){
		temporizador.stop();
		tm.reiniciar();
		areaSalida.setText("");
		habilitarEntrada(false);
		reanudarTrasEntrada = false;
		modo = Modo.LISTO;
		refrescar();
	}

	private void detenerMaquina(){
		temporizador.stop();
		habilitarEntrada(false);
		editor.setLineaEjecucion(-1);
		if(modo != Modo.SIN_PROGRAMA) modo = Modo.LISTO;
	}

	private int retardo(){
		int[] retardos = {600, 300, 150, 80, 40, 15, 10, 10, 10, 10};
		return retardos[velocidad.getValue() - 1];
	}

	private int instruccionesPorTic(){
		int[] cantidad = {1, 1, 1, 1, 1, 1, 10, 200, 2000, 50000};
		return cantidad[velocidad.getValue() - 1];
	}

	/* Un tic del temporizador en modo CORRIENDO */
	private void tic(){
		if(modo != Modo.CORRIENDO){
			temporizador.stop();
			return;
		}
		int n = instruccionesPorTic();
		for(int k = 0; k < n; k++){
			int lineaAntes = panelMaquina.lineaFuente(tm.getPC());
			tm.paso();
			if(tm.getEstado() != TinyMachine.Estado.LISTA) break;
			int pc = tm.getPC();
			int linea = panelMaquina.lineaFuente(pc);
			boolean rupturaInstr = panelMaquina.getRupturasInstruccion().contains(pc);
			boolean rupturaLinea = linea != 0 && linea != lineaAntes && editor.getPuntosRuptura().contains(linea);
			if(rupturaInstr || rupturaLinea){
				temporizador.stop();
				modo = Modo.PAUSADO;
				refrescar();
				estado("Punto de ruptura: " + (rupturaLinea ? "linea " + linea : "instruccion " + pc), false);
				return;
			}
		}
		despuesDeEjecutar(true);
	}

	/* Revisa el estado de la maquina luego de ejecutar instrucciones */
	private void despuesDeEjecutar(boolean corriendo){
		switch(tm.getEstado()){
			case DETENIDA:
				temporizador.stop();
				modo = Modo.TERMINADO;
				estado("Programa terminado (HALT) - " + tm.getPasos() + " instrucciones ejecutadas", false);
				break;
			case ERROR:
				temporizador.stop();
				modo = Modo.TERMINADO;
				areaSalida.append("\n[Error de la TM] " + tm.getMensaje() + "\n");
				estado("Error en ejecucion: " + tm.getMensaje(), true);
				break;
			case ESPERANDO_ENTRADA:
				temporizador.stop();
				reanudarTrasEntrada = corriendo;
				modo = Modo.ESPERANDO;
				pestanasInferiores.setSelectedIndex(1);
				habilitarEntrada(true);
				estado(tm.getEntradaPendiente() == TinyMachine.TipoEntrada.ENTERO
						? "La instruccion IN espera un numero: escribalo abajo y presione Enter"
						: "El programa espera una linea de texto: escribala abajo y presione Enter", false);
				break;
			default:
				break;
		}
		refrescar();
	}

	private void enviarEntrada(){
		if(modo != Modo.ESPERANDO) return;
		String dato = campoEntrada.getText();
		String error = tm.proporcionarEntrada(dato);
		if(error != null){
			estado("Entrada invalida: " + error, true);
			campoEntrada.selectAll();
			campoEntrada.requestFocusInWindow();
			return;
		}
		areaSalida.append("> " + dato + "\n");
		campoEntrada.setText("");
		habilitarEntrada(false);
		if(reanudarTrasEntrada){
			modo = Modo.CORRIENDO;
			despuesDeEjecutar(true);
			if(modo == Modo.CORRIENDO){
				temporizador.start();
				estado("Ejecutando...", false);
			}
		}else{
			modo = Modo.PAUSADO;
			despuesDeEjecutar(false);
			if(modo == Modo.PAUSADO) estado("Dato recibido. PC = " + tm.getPC(), false);
		}
		actualizarControles();
	}

	private void habilitarEntrada(boolean si){
		campoEntrada.setEnabled(si);
		botonEnviar.setEnabled(si);
		campoEntrada.setBackground(si ? new Color(255, 249, 196) : new Color(0xEE, 0xEE, 0xEE));
		etiquetaEntrada.setText(!si ? "Entrada:" :
				tm.getEntradaPendiente() == TinyMachine.TipoEntrada.ENTERO ? "IN (entero):" : "read (texto):");
		etiquetaEntrada.setForeground(si ? new Color(0xB2, 0x3C, 0x17) : Color.GRAY);
		if(si) campoEntrada.requestFocusInWindow();
	}

	/* ===================================================================
	 *  Refresco de la interfaz
	 * =================================================================== */

	private void refrescar(){
		panelMaquina.actualizar();
		int linea = modo == Modo.SIN_PROGRAMA || !compilado ? -1 : panelMaquina.lineaFuente(tm.getPC());
		editor.setLineaEjecucion(modo == Modo.LISTO && tm.getPasos() == 0 ? -1 : linea);
		barraMaquina.setText("PC=" + tm.getPC() + "   instrucciones ejecutadas: " + tm.getPasos() + "   estado: " + textoModo());
		actualizarControles();
	}

	private String textoModo(){
		switch(modo){
			case SIN_PROGRAMA: return "sin programa";
			case LISTO: return "listo";
			case CORRIENDO: return "ejecutando";
			case PAUSADO: return "pausado";
			case ESPERANDO: return "esperando entrada";
			default: return "terminado";
		}
	}

	private void actualizarControles(){
		boolean corriendo = modo == Modo.CORRIENDO;
		boolean esperando = modo == Modo.ESPERANDO;
		accionCompilar.setEnabled(!corriendo && !esperando);
		accionEjecutar.setEnabled(!corriendo && !esperando);
		accionPaso.setEnabled(!corriendo && !esperando && modo != Modo.TERMINADO);
		accionPasoLinea.setEnabled(!corriendo && !esperando && modo != Modo.TERMINADO);
		accionPausa.setEnabled(corriendo);
		accionReiniciar.setEnabled(!corriendo);
	}

	private void estado(String texto, boolean esError){
		barraEstado.setText(texto);
		barraEstado.setForeground(esError ? new Color(0xC6, 0x28, 0x28) : new Color(0x33, 0x33, 0x33));
	}

	private void error(String texto){
		JOptionPane.showMessageDialog(this, texto, "Error", JOptionPane.ERROR_MESSAGE);
	}

	private void agregarMensaje(String texto, int linea, boolean esError){
		mensajes.addElement(new Mensaje(texto, linea, esError));
	}

	private static JTextArea areaSoloLectura(){
		JTextArea a = new JTextArea();
		a.setEditable(false);
		a.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
		a.setBackground(Color.WHITE);
		a.setForeground(Color.BLACK);
		a.setCaretColor(Color.BLACK);
		a.setMargin(new Insets(4, 6, 4, 6));
		return a;
	}

	private void mostrarAyuda(){
		String texto =
				"TINY++ - resumen del lenguaje\n\n" +
				"Tipos:        int, bool, string        Arreglos: int v[10];\n" +
				"Declaracion:  int a, b, v[5];   (antes de usarse; locales al bloque donde aparecen)\n" +
				"Literales:    123   true   false   \"texto\"\n" +
				"Operadores:   + - * /   < <= > >= = <>   and or not   (+ concatena cadenas)\n" +
				"Sentencias:   x := e;   v[i*2+1] := e;   read x;   write e;\n" +
				"              if c then ... else ... end   repeat ... until c   while c do ... end\n" +
				"Subrutinas:   function int f(int a, string s) ... return e ... end;\n" +
				"              procedure p(bool b) ... end;          llamada: p(true); x := f(1, \"a\")\n" +
				"Comentarios:  { ... }\n\n" +
				"Las sentencias se separan con ';'. Las condiciones deben ser bool.\n" +
				"Las variables del cuerpo principal son globales; las de una funcion o bloque son locales.\n\n" +
				"Depurador: F5 ejecutar/continuar, F6 pausa, F7 paso de instruccion, F8 paso de linea,\n" +
				"clic en el margen del editor = punto de ruptura de linea,\n" +
				"doble clic en la memoria de instrucciones = punto de ruptura de instruccion.";
		JTextArea area = areaSoloLectura();
		area.setText(texto);
		JOptionPane.showMessageDialog(this, new JScrollPane(area), "Ayuda Tiny++", JOptionPane.INFORMATION_MESSAGE);
	}

	/* ---------- mensajes de la consola ---------- */

	private static class Mensaje {
		final String texto;
		final int linea;
		final boolean esError;

		Mensaje(String texto, int linea, boolean esError) {
			this.texto = texto;
			this.linea = linea;
			this.esError = esError;
		}

		@Override
		public String toString() {
			return texto;
		}
	}

	private static class RenderMensaje extends DefaultListCellRenderer {
		@Override
		public Component getListCellRendererComponent(JList<?> lista, Object valor, int indice, boolean sel, boolean foco) {
			super.getListCellRendererComponent(lista, valor, indice, sel, foco);
			Mensaje m = (Mensaje)valor;
			if(!sel){
				setBackground(Color.WHITE);
				setForeground(m.esError ? new Color(0xC6, 0x28, 0x28) : new Color(0x1B, 0x5E, 0x20));
			}
			setText((m.esError ? "✖ " : "✔ ") + m.texto);
			return this;
		}
	}

	/* =================================================================== */

	public static void main(String[] args) {
		try {
			/* En Linux el tema GTK (sobre todo si es oscuro) mezcla sus colores con los
			 * del IDE; ahi se usa el aspecto multiplataforma de Java (Metal). */
			String sistema = UIManager.getSystemLookAndFeelClassName();
			if(sistema.toLowerCase().contains("gtk"))
				UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
			else
				UIManager.setLookAndFeel(sistema);
		} catch (Exception ignored) { }
		SwingUtilities.invokeLater(() -> new IDE().setVisible(true));
	}
}
