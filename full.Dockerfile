FROM debian
LABEL authors="martin@saul.io"
WORKDIR /usr/local/build

RUN mkdir /usr/local/app
RUN apt update
RUN apt install -y git tree wget make openjdk-17-jdk

## Go time
RUN wget https://go.dev/dl/go1.23.3.linux-amd64.tar.gz
RUN tar -C /usr/local -xvzf go1.23.3.linux-amd64.tar.gz
ENV PATH="/usr/local/go/bin:${PATH}"
RUN rm go1.23.3.linux-amd64.tar.gz
RUN git clone https://github.com/teslamotors/vehicle-command.git tesla
WORKDIR /usr/local/build/tesla
RUN mkdir /usr/local/app/tesla
RUN go build -o /usr/local/app/tesla ./...
RUN ls -la /usr/local/app/tesla

## Java time
WORKDIR /usr/local/build/java
COPY . .
RUN ./gradlew clean build --no-daemon
RUN mv build/libs/*SHOT.jar /usr/local/app/einher.jar

## We're done. Clean up and validation then off we go.
WORKDIR /usr/local/app
RUN rm -rf /usr/local/build
RUN tree

ENTRYPOINT ["java","-jar","einher.jar"]