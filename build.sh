#!/bin/bash

if [ ! -d ./skija ]; then
 git clone https://github.com/JetBrains/skija.git
else
 pushd ./skija && git pull && popd
fi

SEP=:
if [ `uname` == "Msys" ]; then
  SEP=\;
fi

java -jar ./libs/lombok.jar delombok skija/src/main/java -d skiko/src/jvmMain/java \
   --classpath=./libs/annotations-19.0.0.jar${SEP}./libs/lombok.jar

cd skiko && ./gradlew publishToMavenLocal
