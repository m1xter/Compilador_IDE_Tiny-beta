@echo off
rem Compila el proyecto Tiny++ en Windows (equivalente a build.sh)
cd /d "%~dp0"
set LIB=lib
set GEN=src\ve\edu\unet
echo ^>^> JFlex
java -jar %LIB%\jflex-full-1.8.2.jar -q --nobak -d %GEN% src\especificacion\lexico.flex || exit /b 1
echo ^>^> CUP
java -jar %LIB%\java-cup-11b.jar -destdir %GEN% -parser parser -symbols sym src\especificacion\sintactico.cup || exit /b 1
echo ^>^> javac
if exist build rmdir /s /q build
mkdir build\classes
dir /s /b src\*.java > build\fuentes.txt
javac --release 11 -encoding UTF-8 -d build\classes -cp %LIB%\java-cup-11b-runtime.jar @build\fuentes.txt || exit /b 1
echo ^>^> jar
pushd build\classes
jar xf ..\..\%LIB%\java-cup-11b-runtime.jar java_cup
popd
jar cfe TinyPlusIDE.jar ve.edu.unet.Main -C build\classes .
echo Listo: TinyPlusIDE.jar
