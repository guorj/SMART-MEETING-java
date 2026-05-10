#!/bin/sh
MAVEN_HOME="$HOME/.m2/wrapper/dists/apache-maven-3.9.6"
MAVEN_BIN="$MAVEN_HOME/bin/mvn"

if [ ! -f "$MAVEN_BIN" ]; then
    echo "Downloading Apache Maven 3.9.6..."
    mkdir -p "$HOME/.m2/wrapper/dists"
    curl -sL "https://archive.apache.org/dist/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.tar.gz" -o "$HOME/.m2/wrapper/dists/maven.tar.gz"
    cd "$HOME/.m2/wrapper/dists"
    tar xzf maven.tar.gz
    rm -f maven.tar.gz
    cd -
fi

exec "$MAVEN_BIN" "$@"
