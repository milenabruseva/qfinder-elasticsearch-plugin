FROM eclipse-temurin:17.0.1_12-jdk-focal

RUN apt-get update && \
  apt-get install -y zip unzip && \
  rm -rf /var/lib/apt/lists/*

SHELL ["/bin/bash", "-c"]

RUN curl -s "https://get.sdkman.io" | bash && \
  source "/root/.sdkman/bin/sdkman-init.sh" && \
  sdk install gradle 7.3.3

WORKDIR /plugin