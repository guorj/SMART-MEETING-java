FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# 安装 curl（健康检查用）
RUN apk add --no-cache curl

# 复制 JAR
COPY target/meeting-server-0.1.0.jar app.jar

# 复制 Web 前端
COPY ../web /usr/share/nginx/html/web

# JVM 参数
ENV JAVA_OPTS="-Xms512m -Xmx2g -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:MaxRAMPercentage=75.0"

EXPOSE 8765

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
