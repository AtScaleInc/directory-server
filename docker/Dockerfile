FROM maven:3.8.6-eclipse-temurin-11

RUN apt update && \
    apt install -y subversion gnupg && \
    apt clean && \
    rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/* /usr/share/man/?? /usr/share/man/??_*
