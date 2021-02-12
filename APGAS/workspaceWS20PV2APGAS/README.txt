Kompilieren:
cd ~/workspaceWS20PV2APGAS/src
javac -cp .:../lib/\* examples/HelloWorld.java -d ../bin

oder:
~/workspaceWS20PV2APGAS/scripts/compileAll.sh


Ausfuehren:
cd ~/workspaceWS20PV2APGAS/bin
java -cp .:../lib/\* -Dapgas.places=$places -Dapgas.threads=$threads -Dapgas.hostfile=/path/to/hostfile examples.HelloWorld

 - Wenn -Dapgas.hostfile wegelassen wird, werden alle places lokal gestartet.
 - Das hostfile entweder in bin anlegen oder absoluten Pfad angeben. 
 - Das hostfile enthält alle Hosts, einen pro Zeile, in der ersten Zeile steht der lokale Rechner fuer place 0. 
 - Wenn places>hosts, wird der letzte Host wiederholt.

Option um die Startroutine auzugeben: "-Dapgas.verbose.launcher=true"
(ist nützlich um zu sehen ob die Places auf dem gewuenschten Knoten gestartet werden)

Starten auf dem Cluster:
 - Java laden: module load java/11
 - Auf dem Cluster ein out Verzeichnis anlegen: mkdir ~/out
 - Variablen in scripts/startHelloWorld.sh anpassen
 - Job submitten: ./startHelloWorld.sh
 - Ausgabedateien werden ins out Verzeichnis geschrieben
 - Status des Jobs überprüfen: squeue -u ukXXXXX
 - Job abbrechen: scancel JOBID


Quellcode auf den Cluster kopieren:
 - scp -r workspaceWS20PV2APGAS ukXXXXX@its-cs1.its.uni-kassel.de:~
