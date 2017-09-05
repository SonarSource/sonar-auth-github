#!/bin/sh

# Faster JVM startup
# See http://zeroturnaround.com/rebellabs/your-maven-build-is-slow-speed-it-up/
MAVEN_OPTS="-XX:+TieredCompilation -XX:TieredStopAtLevel=1 $MAVEN_OPTS"
# 
mvn clean install -Dsource.skip -DskipSanityChecks -Dmaven.test.skip -e -B -V $*
