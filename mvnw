#!/bin/sh
MAVEN_HOME="$HOME/.m2/wrapper/dists/apache-maven-3.9.6"
MAVEN_BIN="$MAVEN_HOME/bin/mvn"

if [ ! -f "$MAVEN_BIN" ]; then
    echo "Downloading Apache Maven 3.9.6 (~9MB)..."
    mkdir -p "$HOME/.m2/wrapper/dists"
    TAR="$HOME/.m2/wrapper/dists/maven.tar.gz"
    URL="https://archive.apache.org/dist/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.tar.gz"
    if curl -fL --progress-bar "$URL" -o "$TAR"; then
        :
    else
        echo "Primary mirror failed, retrying repo.maven.apache.org..." >&2
        curl -fL --progress-bar "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.tar.gz" -o "$TAR"
    fi
    cd "$HOME/.m2/wrapper/dists"
    tar xzf maven.tar.gz
    rm -f maven.tar.gz
    cd - >/dev/null
    echo "Maven 3.9.6 ready."
fi

exec "$MAVEN_BIN" "$@"
