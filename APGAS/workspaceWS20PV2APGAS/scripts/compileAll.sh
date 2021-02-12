#!/bin/bash
currentDir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
mkdir -p ${currentDir}/../bin
rm -rf ${currentDir}/../bin/*
cd ${currentDir}/../src
find -name "*.java" > sources.txt
javac -nowarn -cp ".:../lib/*:../bin" @sources.txt -d ../bin
rm sources.txt