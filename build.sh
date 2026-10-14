#!/usr/bin/env bash
# Compila el proyecto Tiny++ completo:
#   1. Genera Lexico.java con JFlex y parser.java/sym.java con CUP
#   2. Compila todas las clases Java en build/classes
#   3. Empaqueta TinyPlusIDE.jar (incluye el runtime de CUP)
# Uso:  ./build.sh        luego:  java -jar TinyPlusIDE.jar
set -e
cd "$(dirname "$0")"
LIB=lib
GEN=src/ve/edu/unet

echo ">> JFlex: src/especificacion/lexico.flex"
java -jar $LIB/jflex-full-1.8.2.jar -q --nobak -d $GEN src/especificacion/lexico.flex

echo ">> CUP: src/especificacion/sintactico.cup"
java -jar $LIB/java-cup-11b.jar -destdir $GEN -parser parser -symbols sym src/especificacion/sintactico.cup

echo ">> javac"
rm -rf build && mkdir -p build/classes
find src -name '*.java' > build/fuentes.txt
javac --release 11 -encoding UTF-8 -d build/classes -cp $LIB/java-cup-11b-runtime.jar @build/fuentes.txt

echo ">> jar"
(cd build/classes && jar xf ../../$LIB/java-cup-11b-runtime.jar java_cup && rm -rf META-INF)
jar cfe TinyPlusIDE.jar ve.edu.unet.Main -C build/classes .
echo "Listo: TinyPlusIDE.jar"
