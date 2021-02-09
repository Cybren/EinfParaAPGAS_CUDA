#!/bin/bash
currentDir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
cd ${currentDir}

FOO=$(mktemp)
sed "s|NUMPLACES|1|g;
     s|THREADS|1|g;
     s|PPERNODE|1|g;
     s|VERBOSELAUNCHER|false|g;
     s|HOME|${HOME}|g;" < ./jobcommitSquaresSeq.sh > ${FOO}
sbatch ${FOO}
rm ${FOO}
