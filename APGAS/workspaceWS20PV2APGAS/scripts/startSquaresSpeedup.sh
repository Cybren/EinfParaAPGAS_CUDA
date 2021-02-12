#!/bin/bash
currentDir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
cd ${currentDir}

for t in 1 2 4 8 16 32; do
for i in {1..5}; do
FOO=$(mktemp)
sed "s|NUMPLACES|1|g;
     s|THREADS|$t|g;
     s|PPERNODE|1|g;
     s|VERBOSELAUNCHER|false|g;
     s|HOME|${HOME}|g;" < ./jobcommitSquares.sh > ${FOO}
sbatch ${FOO}
done
done
rm ${FOO}

for i in {1..5}; do
FOO=$(mktemp)
sed "s|NUMPLACES|2|g;
     s|THREADS|32|g;
     s|PPERNODE|1|g;
     s|VERBOSELAUNCHER|false|g;
     s|HOME|${HOME}|g;" < ./jobcommitSquares.sh > ${FOO}
sbatch ${FOO}
done
rm ${FOO}

for i in {1..5}; do
FOO=$(mktemp)
sed "s|NUMPLACES|4|g;
     s|THREADS|32|g;
     s|PPERNODE|1|g;
     s|VERBOSELAUNCHER|false|g;
     s|HOME|${HOME}|g;" < ./jobcommitSquares.sh > ${FOO}
sbatch ${FOO}
done
rm ${FOO}
