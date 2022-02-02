FROM ubuntu:20.04 AS build

COPY . /tests
WORKDIR /tests



ENV TERM=xterm-256color
ENV HOST=host.docker.internal

RUN apt-get update
RUN apt-get upgrade
RUN apt-get install -y curl
RUN apt-get install -y raptor2-utils
RUN apt-get install -y jq

SHELL ["/bin/bash", "-c"]
CMD ./test_api_functionality.sh
