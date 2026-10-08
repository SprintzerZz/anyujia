FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
RUN groupadd --system app && useradd --system --gid app app
RUN mkdir -p /home/app/logs/rocketmqlogs && chown -R app:app /home/app
COPY --chown=app:app anyujia-coupon.jar /app/app.jar
USER app
ENTRYPOINT ["java", "-Duser.timezone=Asia/Shanghai", "-Xlog:gc*:file=/tmp/coupon-gc.log:time,uptime,level,tags:filecount=5,filesize=20M", "-jar", "/app/app.jar"]
