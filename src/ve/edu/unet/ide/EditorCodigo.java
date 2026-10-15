package ve.edu.unet.ide;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/* Editor de codigo Tiny++:
 *  - resaltado de sintaxis (palabras reservadas, tipos, numeros, cadenas, comentarios)
 *  - margen con numeros de linea; un clic en el margen pone/quita un punto de ruptura
 *  - resaltado de la linea en ejecucion (depurador) y de las lineas con error
 */
public class EditorCodigo extends JPanel {

	private static final Pattern PALABRAS = Pattern.compile(
			"\\b(if|then|else|end|repeat|until|while|do|read|write|function|procedure|return|and|or|not)\\b");
	private static final Pattern TIPOS = Pattern.compile("\\b(int|bool|string|true|false)\\b");
	private static final Pattern NUMEROS = Pattern.compile("\\b\\d+\\b");
	private static final Pattern CADENAS = Pattern.compile("\"[^\"\\n]*\"?");
	private static final Pattern COMENTARIOS = Pattern.compile("\\{[^}]*\\}?");

	private final JTextPane texto;
	private final Margen margen;
	private final StyledDocument doc;
	private final SimpleAttributeSet estiloNormal = new SimpleAttributeSet();
	private final SimpleAttributeSet estiloPalabra = new SimpleAttributeSet();
	private final SimpleAttributeSet estiloTipo = new SimpleAttributeSet();
	private final SimpleAttributeSet estiloNumero = new SimpleAttributeSet();
	private final SimpleAttributeSet estiloCadena = new SimpleAttributeSet();
	private final SimpleAttributeSet estiloComentario = new SimpleAttributeSet();

	private final Set<Integer> puntosRuptura = new TreeSet<>();
	private final Set<Integer> lineasError = new TreeSet<>();
	private int lineaEjecucion = -1;
	private Object marcaEjecucion;
	private final java.util.List<Object> marcasError = new java.util.ArrayList<>();
	private boolean resaltadoPendiente = false;
	private Runnable alModificar = () -> {};

	public EditorCodigo() {
		super(new BorderLayout());
		texto = new JTextPane() {
			/* sin ajuste de linea: aparece la barra horizontal */
			@Override
			public boolean getScrollableTracksViewportWidth() {
				return getUI().getPreferredSize(this).width <= getParent().getSize().width;
			}
		};
		texto.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
		/* colores fijos: el resaltado esta pensado para fondo claro, sin importar el tema del sistema */
		texto.setBackground(Color.WHITE);
		texto.setForeground(new Color(0x20, 0x20, 0x20));
		texto.setCaretColor(Color.BLACK);
		texto.setSelectionColor(new Color(0xB3, 0xD4, 0xFC));
		texto.setSelectedTextColor(null);
		texto.setMargin(new Insets(4, 6, 4, 6));
		doc = texto.getStyledDocument();
		configurarEstilos();

		/* tabulador = 4 espacios visuales */
		TabStop[] tabs = new TabStop[30];
		int ancho = texto.getFontMetrics(texto.getFont()).charWidth('m') * 4;
		for(int i = 0; i < tabs.length; i++) tabs[i] = new TabStop((i + 1) * ancho);
		SimpleAttributeSet parrafo = new SimpleAttributeSet();
		StyleConstants.setTabSet(parrafo, new TabSet(tabs));
		doc.setParagraphAttributes(0, 0, parrafo, false);

		JScrollPane scroll = new JScrollPane(texto);
		margen = new Margen();
		scroll.setRowHeaderView(margen);
		add(scroll, BorderLayout.CENTER);

		doc.addDocumentListener(new DocumentListener() {
			public void insertUpdate(DocumentEvent e)  { cambio(); }
			public void removeUpdate(DocumentEvent e)  { cambio(); }
			public void changedUpdate(DocumentEvent e) { }
		});
	}

	private void configurarEstilos(){
		StyleConstants.setForeground(estiloNormal, new Color(0x20, 0x20, 0x20));
		StyleConstants.setForeground(estiloPalabra, new Color(0x00, 0x33, 0xB3));
		StyleConstants.setBold(estiloPalabra, true);
		StyleConstants.setForeground(estiloTipo, new Color(0x87, 0x10, 0x94));
		StyleConstants.setBold(estiloTipo, true);
		StyleConstants.setForeground(estiloNumero, new Color(0x17, 0x50, 0xEB));
		StyleConstants.setForeground(estiloCadena, new Color(0x06, 0x7D, 0x17));
		StyleConstants.setForeground(estiloComentario, new Color(0x8C, 0x8C, 0x8C));
		StyleConstants.setItalic(estiloComentario, true);
	}

	private void cambio(){
		limpiarErrores();
		alModificar.run();
		margen.repaint();
		if(!resaltadoPendiente){
			resaltadoPendiente = true;
			SwingUtilities.invokeLater(this::resaltarSintaxis);
		}
	}

	public void setAlModificar(Runnable r){
		alModificar = r;
	}

	private void resaltarSintaxis(){
		resaltadoPendiente = false;
		String t = getTexto();
		doc.setCharacterAttributes(0, t.length(), estiloNormal, true);
		aplicar(PALABRAS, t, estiloPalabra);
		aplicar(TIPOS, t, estiloTipo);
		aplicar(NUMEROS, t, estiloNumero);
		aplicar(CADENAS, t, estiloCadena);
		aplicar(COMENTARIOS, t, estiloComentario);
	}

	private void aplicar(Pattern p, String t, AttributeSet estilo){
		Matcher m = p.matcher(t);
		while(m.find())
			doc.setCharacterAttributes(m.start(), m.end() - m.start(), estilo, true);
	}

	/* ------------------------- texto ------------------------- */

	public String getTexto(){
		try {
			return doc.getText(0, doc.getLength());
		} catch (BadLocationException e) {
			return "";
		}
	}

	public void setTexto(String t){
		texto.setText(t.replace("\r\n", "\n"));
		texto.setCaretPosition(0);
		puntosRuptura.clear();
		resaltarSintaxis();
	}

	public JTextPane getAreaTexto(){
		return texto;
	}

	public Set<Integer> getPuntosRuptura(){
		return puntosRuptura;
	}

	private int cantidadLineas(){
		return doc.getDefaultRootElement().getElementCount();
	}

	private int inicioLinea(int linea){
		Element e = doc.getDefaultRootElement().getElement(Math.max(0, Math.min(linea - 1, cantidadLineas() - 1)));
		return e.getStartOffset();
	}

	public void irALinea(int linea){
		if(linea < 1) return;
		texto.requestFocusInWindow();
		texto.setCaretPosition(inicioLinea(linea));
		mostrarLinea(linea);
	}

	private void mostrarLinea(int linea){
		try {
			Rectangle r = texto.modelToView2D(inicioLinea(linea)).getBounds();
			r.height *= 3;
			r.y = Math.max(0, r.y - r.height / 3);
			texto.scrollRectToVisible(r);
		} catch (BadLocationException ignored) { }
	}

	/* ------------------------- marcas ------------------------- */

	/* Linea que se esta ejecutando en el depurador (-1 para quitar la marca) */
	public void setLineaEjecucion(int linea){
		if(linea == lineaEjecucion) return;
		lineaEjecucion = linea;
		Highlighter h = texto.getHighlighter();
		if(marcaEjecucion != null){
			h.removeHighlight(marcaEjecucion);
			marcaEjecucion = null;
		}
		if(linea > 0){
			try {
				marcaEjecucion = h.addHighlight(inicioLinea(linea), inicioLinea(linea),
						new PintorLinea(new Color(255, 236, 140)));
			} catch (BadLocationException ignored) { }
			mostrarLinea(linea);
		}
		margen.repaint();
		texto.repaint();
	}

	public void marcarError(int linea){
		if(linea < 1) return;
		lineasError.add(linea);
		try {
			marcasError.add(texto.getHighlighter().addHighlight(inicioLinea(linea), inicioLinea(linea),
					new PintorLinea(new Color(255, 205, 205))));
		} catch (BadLocationException ignored) { }
		margen.repaint();
	}

	public void limpiarErrores(){
		if(lineasError.isEmpty()) return;
		for(Object m : marcasError) texto.getHighlighter().removeHighlight(m);
		marcasError.clear();
		lineasError.clear();
		margen.repaint();
	}

	/* Pinta el fondo de toda la linea (no solo del texto) */
	private static class PintorLinea implements Highlighter.HighlightPainter {
		private final Color color;

		PintorLinea(Color color) {
			this.color = color;
		}

		@Override
		public void paint(Graphics g, int p0, int p1, Shape bounds, JTextComponent c) {
			try {
				Rectangle r = c.modelToView2D(p0).getBounds();
				g.setColor(color);
				g.fillRect(0, r.y, c.getWidth(), r.height);
			} catch (BadLocationException ignored) { }
		}
	}

	/* ------------------------- margen ------------------------- */

	private class Margen extends JComponent {
		Margen() {
			setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
			setToolTipText("Clic para poner o quitar un punto de ruptura");
			addMouseListener(new MouseAdapter() {
				@Override
				public void mousePressed(MouseEvent e) {
					int pos = texto.viewToModel2D(new Point(0, e.getY()));
					int linea = doc.getDefaultRootElement().getElementIndex(pos) + 1;
					if(!puntosRuptura.remove(linea)) puntosRuptura.add(linea);
					repaint();
				}
			});
		}

		@Override
		public Dimension getPreferredSize() {
			int digitos = Math.max(3, String.valueOf(cantidadLineas()).length());
			return new Dimension(getFontMetrics(getFont()).charWidth('0') * digitos + 30, texto.getPreferredSize().height);
		}

		@Override
		protected void paintComponent(Graphics g) {
			Graphics2D g2 = (Graphics2D)g;
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			Rectangle clip = g.getClipBounds();
			g.setColor(new Color(0xF0, 0xF0, 0xF0));
			g.fillRect(clip.x, clip.y, clip.width, clip.height);
			g.setColor(new Color(0xD0, 0xD0, 0xD0));
			g.drawLine(getWidth() - 1, clip.y, getWidth() - 1, clip.y + clip.height);
			FontMetrics fm = g.getFontMetrics();
			int n = cantidadLineas();
			for(int linea = 1; linea <= n; linea++){
				Rectangle r;
				try {
					r = texto.modelToView2D(inicioLinea(linea)).getBounds();
				} catch (BadLocationException e) {
					continue;
				}
				if(r.y + r.height < clip.y) continue;
				if(r.y > clip.y + clip.height) break;
				if(lineasError.contains(linea)){
					g.setColor(new Color(255, 205, 205));
					g.fillRect(0, r.y, getWidth() - 1, r.height);
				}
				if(linea == lineaEjecucion){
					g.setColor(new Color(255, 236, 140));
					g.fillRect(0, r.y, getWidth() - 1, r.height);
				}
				if(puntosRuptura.contains(linea)){
					g.setColor(new Color(0xD3, 0x2F, 0x2F));
					int d = Math.min(r.height - 4, 11);
					g.fillOval(4, r.y + (r.height - d) / 2, d, d);
				}
				if(linea == lineaEjecucion){
					g.setColor(new Color(0x1B, 0x5E, 0x20));
					int y = r.y + r.height / 2;
					g.fillPolygon(new int[]{4, 13, 4}, new int[]{y - 5, y, y + 5}, 3);
				}
				String num = String.valueOf(linea);
				g.setColor(new Color(0x80, 0x80, 0x80));
				g.drawString(num, getWidth() - 6 - fm.stringWidth(num), r.y + fm.getAscent() + (r.height - fm.getHeight()) / 2);
			}
		}
	}
}
