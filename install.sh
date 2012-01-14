#!/bin/sh

mvn -Dfile=native/linux-amd64-jiminger/target/linux-amd64-jiminger.jar -DgroupId=com.jiminger -DartifactId=linux-amd64-jiminger-jar -Dversion=1.0-SNAPSHOT -Dpackaging=jar install:install-file



