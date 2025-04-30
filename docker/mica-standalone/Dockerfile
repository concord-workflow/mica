FROM library/debian:stable
LABEL maintainer="ibodrov@gmail.com"

ENTRYPOINT ["/usr/local/bin/mica_venv/bin/dumb-init", "--"]

RUN apt-get update && \
    apt-get -y upgrade && \
    apt-get -y install git curl bash python3 python3-pip python-is-python3 && \
    apt-get clean && \
    pip3 install --no-cache-dir --break-system-packages dumb-init virtualenv

ADD --chmod=755 ./get_jdk_url.sh /tmp/
ADD --chmod=755 ./get_arch.sh /tmp/
ENV JDK_VERSION="21"
RUN export DEFAULT_TARGETARCH=$(/tmp/get_arch.sh); \
    export TARGETARCH=${TARGETARCH:-${DEFAULT_TARGETARCH}}; \
    curl --location --output /tmp/jdk.tar.gz $(/tmp/get_jdk_url.sh) && \
    mkdir /opt/jdk && \
    tar xpf /tmp/jdk.tar.gz --strip 1 -C /opt/jdk && \
    rm /tmp/jdk.tar.gz

ENV JAVA_HOME=/opt/jdk
ENV PATH="${JAVA_HOME}/bin:${PATH}"
ENV LC_CTYPE=en_US.UTF-8
ENV LANG=en_US.UTF-8

RUN virtualenv /usr/local/bin/mica_venv && \
    /usr/local/bin/mica_venv/bin/pip3 --no-cache-dir install dumb-init

RUN groupadd -g 456 concord && \
    useradd --no-log-init -u 456 -g concord -m -s /sbin/nologin concord && \
    echo "[safe]\n\tdirectory = *\n" > ~concord/.gitconfig && \
    chown concord:concord ~concord/.gitconfig

EXPOSE 8001

COPY ./target/deps/* /opt/mica/lib/
COPY ./target/release/* /opt/mica/lib/
ADD --chmod=755 ./start.sh /opt/mica/

RUN mkdir -p /opt/concord/server/logs && \
    chown -R concord:concord /opt/concord/server/logs

USER concord

CMD ["bash", "/opt/mica/start.sh"]
