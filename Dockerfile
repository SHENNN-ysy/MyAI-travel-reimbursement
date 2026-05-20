FROM eclipse-temurin:21-jre
ENV TZ=Asia/Shanghai
WORKDIR /app
RUN apt-get update && apt-get install -y libstdc++6 nodejs npm && rm -rf /var/lib/apt/lists/*
COPY target/MyAI-travel-reimbursement-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
