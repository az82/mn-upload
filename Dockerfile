FROM adoptopenjdk/openjdk13-openj9:jdk-13.0.2_8_openj9-0.18.0-alpine-slim
COPY build/libs/mn-upload-*-all.jar mn-upload.jar
EXPOSE 8080
CMD ["java", "-Xmx512m", "-jar", "mn-upload.jar"]
