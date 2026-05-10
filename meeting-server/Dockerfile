FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# 安装 curl（健康检查用）
RUN apk add --no-cache curl

# 复制 JAR
COPY target/meeting-server-0.1.0.jar app.jar

# 与 Spring 同源：供可选 Nginx 挂载或镜像内静态副本
COPY src/main/resources/static /usr/share/nginx/html/web

# JVM 参数
ENV JAVA_OPTS="-Xms512m -Xmx2g -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:MaxRAMPercentage=75.0"

EXPOSE 8765

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
