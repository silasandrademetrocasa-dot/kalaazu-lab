# Kalaazu LAB — service only. MariaDB MUST be provisioned separately.
FROM eclipse-temurin:25-jdk AS builder
WORKDIR /source
COPY . .
RUN chmod +x ./gradlew && ./gradlew --no-daemon installDist -x test --console=plain

FROM eclipse-temurin:25-jre
RUN apt-get update && apt-get install -y --no-install-recommends \
      python3 python3-venv ca-certificates bash \
    && rm -rf /var/lib/apt/lists/*
RUN python3 -m venv /opt/bridge-venv
COPY gateway/requirements.txt /tmp/bridge-requirements.txt
RUN /opt/bridge-venv/bin/pip install --no-cache-dir -r /tmp/bridge-requirements.txt
COPY --from=builder /source/build/install/main /opt/kalaazu
WORKDIR /opt/kalaazu-lab
COPY gateway ./gateway
COPY client ./client
COPY deploy/start.sh ./deploy/start.sh
COPY deploy/secured_gateway.py ./deploy/secured_gateway.py
RUN chmod +x deploy/start.sh
ENV PYTHONUNBUFFERED=1 AUTO_START=true GAME_VERSION=V4 \
    WEB_SERVER_PORT=8081 GAME_SERVER_PORT=8080 POLICY_SERVER_PORT=1843 \
    CHAT_SERVER_PORT=8082 KALAAZU_HTTP=http://127.0.0.1:8081 \
    KALAAZU_GAME_HOST=127.0.0.1 KALAAZU_GAME_PORT=8080 \
    BRIDGE_BIND=0.0.0.0 BRIDGE_COOKIE_SECURE=true \
    SPRING_PROFILES_ACTIVE=prod SPRING_JPA_HIBERNATE_DDL_AUTO=validate
EXPOSE 10000
CMD ["/bin/bash", "./deploy/start.sh"]
