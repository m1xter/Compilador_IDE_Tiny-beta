# Tiny++: compilador, simulador TM e IDE con depurador visual

Proyecto final de Compiladores e Intérpretes (UNET). Extiende el compilador **Tiny** del
laboratorio (JFlex + CUP, código objeto para la Tiny Machine) al lenguaje **Tiny++** y le añade
un IDE con un depurador visual de la TM.

```
./build.sh                      # Linux/macOS  (build.bat en Windows) -> TinyPlusIDE.jar
java -jar TinyPlusIDE.jar       # abre el IDE
java -jar TinyPlusIDE.jar ejemplos/02_funciones_recursivas.tny -v   # compila por consola (-v: AST, tabla, código)
java -jar TinyPlusIDE.jar --tm ejemplos/02_funciones_recursivas.tm  # simulador TM en consola (s, g, r, d, i, q)
```

Requisitos: JDK 11 o superior. JFlex 1.8.2 y CUP 11b están en `lib/`; no se necesitan Maven ni Gradle.
`build.sh` vuelve a generar `Lexico.java`, `parser.java` y `sym.java` a partir de
`src/especificacion/`. **Nunca edites esos tres archivos a mano.**

---

## 1. El lenguaje Tiny++

```pascal
{ comentario de varias lineas }
int n, v[10];                 { declaracion: int | bool | string, escalares o arreglos }
string nombre;

function int factorial(int x) { funcion con valor de retorno }
    if x <= 1 then return 1 else return x * factorial(x - 1) end
end;

procedure saludar(string s)   { procedimiento: sin valor de retorno }
    write "Hola, " + s
end;

read nombre;
saludar(nombre);
n := 0;
while n < 5 do
    v[n*2 - n] := factorial(n);   { el indice admite expresiones compuestas }
    write v[n];
    n := n + 1
end
```

| Elemento | Sintaxis |
|---|---|
| Tipos | `int`, `bool`, `string`; arreglos `tipo nombre[N]` |
| Literales | `123`, `true`, `false`, `"texto"` |
| Operadores (de menor a mayor precedencia) | `or` · `and` · `not` · `< <= > >= = <>` · `+ -` · `* /` · `-` unario |
| Sentencias | `x := e`, `A[e] := e`, `read x`, `write e`, `if c then … [else …] end`, `repeat … until c`, `while c do … end`, `p(args)`, `return [e]` |
| Subrutinas | `function T f(T a, …) … end`, `procedure p(T a, …) … end` (solo en el nivel principal) |

* Las sentencias se separan con `;`. Se acepta un `;` sobrante antes de `end`, `else` o `until`.
* `+` también concatena cadenas. `=` y `<>` comparan dos valores del mismo tipo (también cadenas).
* `write` de un `bool` imprime `true`/`false`. `read` de un `bool` acepta 0 (false) o cualquier otro entero (true). `read` de un `string` lee una línea.
* Los parámetros se pasan **por valor**. Los arreglos no pueden ser parámetros.
* Las variables sin inicializar valen `0`, `false` o `""`.

### Ámbitos (scopes)
* Las variables declaradas en el **cuerpo principal** son globales: las ven las subrutinas declaradas **después** de ellas.
* Cada función tiene un ámbito propio para sus parámetros y sus variables locales.
* Cada bloque (`then`, `else`, cuerpo de `while` o de `repeat`) abre un ámbito nuevo. Una declaración interna oculta a la externa del mismo nombre.
* Un nombre solo es visible después de su declaración. Por eso la recursión directa funciona, pero no la recursión mutua.

### Verificaciones semánticas
Variable o subrutina no declarada. Redeclaración en el mismo ámbito. Tipos incompatibles en
operaciones aritméticas, lógicas y relacionales, en la asignación y en el paso de parámetros
(cantidad y tipo). Condición de `if`/`while`/`until` que no es `bool`. Índice que no es `int`.
Arreglo usado sin índice, o escalar indexado. Procedimiento usado como valor. `return` con un
tipo distinto al de la función, o `return` fuera de una subrutina. Subrutina declarada dentro de un bloque.
Las pruebas están en `ejemplos/06_errores_semanticos.tny` y `ejemplos/07_errores_sintacticos.tny`.

---

## 2. Arquitectura

```
fuente .tny ─► Lexico (JFlex) ─► parser (CUP) ─► AST ─► AnalizadorSemantico ─► Generador ─► código .tm
                     └──────────── ListaErrores (léxicos, sintácticos y semánticos, con línea) ──┘
código .tm ─► CargadorTM ─► TinyMachine (paso a paso) ◄─► IDE (PanelMaquina, editor, E/S)
```

| Paquete / archivo | Contenido | Módulo |
|---|---|---|
| `src/especificacion/lexico.flex` | Analizador léxico (genera `Lexico.java`) | 1 |
| `src/especificacion/sintactico.cup` | Gramática y construcción del AST (genera `parser.java`, `sym.java`) | 1 |
| `ve.edu.unet.nodosAST` | Nodos del AST (`NodoFuncion`, `NodoArreglo`, `NodoLlamada`…) y su impresión (`Util`) | 1 |
| `ve.edu.unet.semantico` | `Tipo`, `RegistroSimbolo`, `TablaSimbolos` (pila de ámbitos), `AnalizadorSemantico` | 1 |
| `ve.edu.unet.Generador`, `UtGen` | Generación de código TM: marcos de pila, arreglos, cadenas | 2 |
| `ve.edu.unet.tm` | `TinyMachine` (simulador controlable), `CargadorTM`, `Instruccion`, `TMConsola` | 3 |
| `ve.edu.unet.ide` | `IDE`, `EditorCodigo`, `PanelMaquina` | 4 |
| `ve.edu.unet.Compilador`, `Main` | Fachada que encadena las fases; punto de entrada | 4 |

---

## 3. Generación de código (módulo 2)

### Registros
| Reg | Nombre | Uso |
|---|---|---|
| 0 | AC | acumulador: aquí queda el resultado de cada expresión y el valor de retorno |
| 1 | AC1 | segundo operando |
| 2, 3 | R2, R3 | auxiliares de las rutinas de ejecución (R3 guarda la dirección de retorno de las rutinas) |
| 4 | FP | puntero de marco: base del registro de activación actual |
| 5 | GP | puntero global, siempre vale 0 |
| 6 | MP | puntero de pila: **primera celda libre**. La pila crece hacia abajo |
| 7 | PC | contador de programa |

Push: `ST r,0(MP)` y luego `LDA MP,-1(MP)`. Pop: `LDA MP,1(MP)` y luego `LD r,0(MP)`.
Los temporales de las expresiones van a la pila real, no a desplazamientos fijos como en
Tiny. Así no se pisan entre llamadas recursivas.

### Memoria de datos
```
1023 ┌───────────────┐ ◄ MP inicial
     │ pila  ↓       │  registros de activación y temporales
     │ libre         │
     │ heap  ↑       │  cadenas creadas en ejecución (read, concatenación)
     ├───────────────┤ ◄ inicio del heap
     │ literales     │  cada cadena: [longitud, c1, c2, …]
     │ HP            │  celda con la siguiente dirección libre del heap
     │ globales      │  variables del cuerpo principal (incluso las de bloques internos)
   0 │ 0             │  "cadena vacía": las variables string sin inicializar apuntan aquí
     └───────────────┘
```
Un `string` es la **dirección** de su bloque `[longitud, caracteres…]`, así que ocupa una sola
celda. Asignarlo copia la dirección. Las cadenas no se modifican: la concatenación crea otra nueva.

### Registro de activación (marco de pila)
```
FP+N+1  argumento 1      ┐ los apila el llamador, en orden
 …                       │
FP+2    argumento N      ┘
FP+1    dirección de retorno   (la apila el llamador)
FP      FP del llamador        (lo guarda el prólogo)
FP-1    local 1          ┐ el prólogo las reserva y las inicializa en 0
 …                       │
FP-L    local L          ┘
        temporales …           ◄ MP
```
* **Llamada:** evaluar y apilar cada argumento. Luego `LDA AC,3(PC)`, apilar AC (la dirección de retorno) y saltar a la función.
* **Prólogo:** `ST FP,0(MP)`, `LDA FP,0(MP)` y `LDA MP,-(L+1)(FP)`. Después, un ciclo pone las L locales en 0.
* **Epílogo** (en cada `return` y al final del cuerpo): `LD AC1,1(FP)` (dirección de retorno), `LDA MP,N+1(FP)` (desapila el marco y los argumentos), `LD FP,0(FP)` y `LDA PC,0(AC1)`.
* El cuerpo de la función se genera donde aparece la declaración, precedido de un salto que lo esquiva.

### Arreglos
`A[i]` tiene la dirección `base(A) + i`. Para un arreglo global es `i + GP + dir`; para uno local
es `i + FP + desplazamiento`. Antes de calcularla se verifica en ejecución que
`0 <= i < tamaño`; si no se cumple, se salta a la rutina `error_indice`, que imprime un mensaje
y ejecuta HALT (ver `ejemplos/08_indice_fuera_de_rango.tny`).

### Rutinas de ejecución
Se emiten tras el preludio, y solo si el programa las usa: `escribir_cadena`, `leer_cadena`,
`concatenar`, `comparar_cadenas` y `error_indice`. Se llaman con `LDA 3,1(7)` seguido del salto, y
regresan con `LDA 7,0(3)`.

---

## 4. Simulador TM (módulo 3)

`ve.edu.unet.tm.TinyMachine` traduce a Java el `tm.c` de Louden y lo adapta para que se pueda
controlar desde fuera:

* `paso()` ejecuta **una** instrucción y nunca se bloquea. El resto de la API es `reiniciar()`, `cargar()` y getters de registros, `iMem` y `dMem`. También expone qué registro y qué celda cambió la última instrucción.
* `IN` e `INC` no leen de la consola. La máquina pasa a `ESPERANDO_ENTRADA` sin avanzar el PC. Quien la controla (el IDE o la consola) obtiene el dato y llama a `proporcionarEntrada()`, que completa la instrucción.
* La salida se entrega a una interfaz `Salida`. El IDE la muestra en su panel y la consola la imprime.
* Se añadieron dos instrucciones para cadenas: `OUTC r,0,0` escribe el carácter `reg[r]` e `INC r,0,0` lee un carácter de la línea de entrada (al final de la línea entrega `'\n'`). Las demás instrucciones se comportan igual que en el original, y los `.tm` del curso (`ejemplo_generado/`) se ejecutan sin cambios.
* Tamaños: `iMem` = 2048 instrucciones, `dMem` = 1024 celdas.

## 5. IDE (módulo 4)

* **Editor:** resaltado de sintaxis y números de línea. Un clic en el margen pone o quita un punto de ruptura.
* **Consola:** errores léxicos, sintácticos y semánticos con su línea. Al hacer clic en uno, el editor salta a esa línea y la marca en rojo.
* **Entrada / Salida:** muestra lo que el programa escribe. Cuando el programa ejecuta `read`, se activa el campo de entrada.
* **Depurador TM:**
  * *Memoria de instrucciones:* el PC aparece resaltado, junto con la línea fuente de cada instrucción. Doble clic pone o quita un punto de ruptura en esa instrucción.
  * *Registros:* resalta los que cambió la última instrucción.
  * *Memoria de datos:* nombra cada celda (variables globales, literales, `HP`, y el marco actual con `FP`, la dirección de retorno, los parámetros y las locales por su nombre). Colorea la celda escrita, la leída y la zona de pila.
* **Controles:** Compilar (Ctrl+B), Ejecutar/Continuar (F5), Pausa (F6), Paso de instrucción (F7), Paso de línea (F8), Reiniciar (Ctrl+R) y un control de velocidad. Las velocidades bajas animan la ejecución instrucción por instrucción.
* *Archivo → Depurar archivo .tm* carga cualquier `.tm`, sin necesidad de código fuente.

---

