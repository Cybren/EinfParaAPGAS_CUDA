#!/bin/bash

####### Mail Notify / Job Name / Comment #######
#SBATCH --job-name=WS20_PV2_APGAS_Squares

####### Partition #######
#SBATCH --partition=public

####### Ressources #######
#SBATCH --time=20
#SBATCH --mem-per-cpu=1000

####### Node Info #######
#SBATCH --exclusive
#SBATCH -n NUMPLACES
#SBATCH --ntasks-per-node=PPERNODE
#SBATCH --distribution=cyclic

####### Output #######
#SBATCH --output=HOME/out/WS20-PV2-APGAS-Squares-placesNUMPLACES-placesPerNodePPERNODE-threadsTHREADS.out.%j
#SBATCH --error=HOME/out/WS20-PV2-APGAS-Squares-placesNUMPLACES-placesPerNodePPERNODE-threadsTHREADS.err.%j

####### Script #######

cd HOME/workspaceWS20PV2APGAS/bin

echo $(scontrol show hostname | paste -d, -s) | tr "," "\n"> hostfile-${SLURM_JOB_ID}
echo $HOSTNAME > hostfile2-${SLURM_JOB_ID}
sed '1,1d' hostfile-${SLURM_JOB_ID} >> hostfile2-${SLURM_JOB_ID}
myfile=$(< hostfile2-${SLURM_JOB_ID})
for ((z=1;z<PPERNODE;z++))
do
     echo -e "$myfile" >> hostfile2-${SLURM_JOB_ID}
done

java -cp .:HOME/workspaceWS20PV2APGAS/lib/* \
  -Dapgas.places=NUMPLACES \
  -Dapgas.threads=THREADS \
  -Dapgas.hostfile=HOME/workspaceWS20PV2APGAS/bin/hostfile2-${SLURM_JOB_ID} \
  -Dapgas.launcher=apgas.launcher.SrunLauncher \
  -Dapgas.verbose.launcher=VERBOSELAUNCHER \
  group2.Squares 100 10 42 3 10 0


rm hostfile-${SLURM_JOB_ID}
rm hostfile2-${SLURM_JOB_ID}
