# syntax=docker/dockerfile:1
FROM amazoncorretto:21

# 앱 작업 디렉토리 설정
WORKDIR /app

# 실행할 모듈의 JAR 파일 복사 (예: api 모듈)
ARG JAR_FILE=${JAR_DIR}/${JAR_FILE}
COPY ${JAR_FILE} app.jar

# 포트 오픈 (Spring Boot 기본 포트)
EXPOSE 8080

# 앱 실행
ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=stage"]