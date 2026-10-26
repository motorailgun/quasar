FROM alpine:3.22
RUN apk add --no-cache openssl
COPY gen.sh /gen.sh
ENTRYPOINT ["/bin/sh", "/gen.sh"]
