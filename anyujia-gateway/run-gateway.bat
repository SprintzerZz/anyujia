@echo off
chcp 65001 >nul
cd /d "D:\Project\frame\anyujia-frameworkjava"
set "JAVA_HOME=D:\DownLoad\Java\JDK17_temurin\jdk-17.0.20+8"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "MAVEN_OPTS=-Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8"
if not defined NACOS_DISCOVERY_IP set "NACOS_DISCOVERY_IP=127.0.0.1"
echo Gateway logs will also be saved to D:\Project\frame\anyujia-frameworkjava\anyujia-gateway\gateway-run.log
powershell -NoProfile -ExecutionPolicy Bypass -Command "& 'D:\DownLoad\apache-maven-3.9.16-bin\apache-maven-3.9.16\bin\mvn.cmd' -q -f 'D:\Project\frame\anyujia-frameworkjava\anyujia-gateway\pom.xml' 'org.codehaus.mojo:exec-maven-plugin:3.5.0:java' '-Dexec.mainClass=com.anyujia.anyujiagateway.AnyujiaGatewayApplication' 2>&1 | Tee-Object -FilePath 'D:\Project\frame\anyujia-frameworkjava\anyujia-gateway\gateway-run.log'"
