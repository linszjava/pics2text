FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /source
COPY . .
RUN --mount=type=cache,target=/root/.m2 \
    mvn --batch-mode --no-transfer-progress -pl mosaic-render-worker -am -DskipTests package

FROM eclipse-temurin:21-jre-jammy
RUN sed -i 's|http://ports.ubuntu.com|https://ports.ubuntu.com|g' /etc/apt/sources.list \
    && apt-get -o Acquire::Retries=5 update \
    && DEBIAN_FRONTEND=noninteractive apt-get -o Acquire::Retries=5 install -y --no-install-recommends \
       python3 python3-pip python3-cffi libvips-tools icc-profiles-free \
    && python3 -m pip install --no-cache-dir --no-deps pyvips==2.2.3 \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system pics2text \
    && useradd --system --gid pics2text --home-dir /opt/pics2text --shell /usr/sbin/nologin pics2text \
    && mkdir -p /opt/pics2text /var/tmp/pics2text-render \
    && chown -R pics2text:pics2text /opt/pics2text /var/tmp/pics2text-render
COPY --from=build /source/mosaic-render-worker/target/mosaic-render-worker-*.jar /opt/pics2text/worker.jar
COPY --from=build /source/mosaic-render-worker/src/main/python/render_print.py /opt/pics2text/render_print.py
USER pics2text
WORKDIR /opt/pics2text
EXPOSE 18082
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=35", "-jar", "/opt/pics2text/worker.jar"]
