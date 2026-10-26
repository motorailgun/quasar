#!/bin/sh
# state/ に CA・サーバ証明書・各サーバの秘密の値を作る。既にあるものは作り直さない (冪等)。
set -eu

S=/state
SERVERS="alpha stable legacy"
DOMAIN_SUFFIX=quasar.test
umask 022
mkdir -p "$S/certs" "$S/secrets" "$S/tokens"

if [ ! -f "$S/certs/ca.crt" ]; then
  echo "init: creating CA"
  openssl req -x509 -newkey ec -pkeyopt ec_paramgen_curve:prime256v1 -nodes \
    -keyout "$S/certs/ca.key" -out "$S/certs/ca.crt" -days 3650 \
    -subj "/CN=Quasar Testbed CA" \
    -addext "basicConstraints=critical,CA:TRUE" \
    -addext "keyUsage=critical,keyCertSign,cRLSign" 2>/dev/null
  rm -f "$S/certs/server.crt"
fi

if [ ! -f "$S/certs/server.crt" ]; then
  echo "init: creating server certificate"
  san="DNS:*.$DOMAIN_SUFFIX"
  for s in $SERVERS; do san="$san,DNS:$s.$DOMAIN_SUFFIX"; done
  openssl req -x509 -newkey ec -pkeyopt ec_paramgen_curve:prime256v1 -nodes \
    -CA "$S/certs/ca.crt" -CAkey "$S/certs/ca.key" \
    -keyout "$S/certs/server.key" -out "$S/certs/server.crt" -days 825 \
    -subj "/CN=$DOMAIN_SUFFIX" \
    -addext "subjectAltName=$san" \
    -addext "basicConstraints=critical,CA:FALSE" \
    -addext "keyUsage=critical,digitalSignature" \
    -addext "extendedKeyUsage=serverAuth" 2>/dev/null
fi
# 各コンテナはこれを SSL_CERT_FILE で信頼する (外には出られないので公開 CA は実質使わない)
cat /etc/ssl/certs/ca-certificates.crt "$S/certs/ca.crt" > "$S/certs/ca-bundle.crt"
# Android にユーザー証明書として入れる用 (DER)
openssl x509 -in "$S/certs/ca.crt" -outform DER -out "$S/certs/ca.der"
chmod 644 "$S"/certs/*

b64url() { base64 | tr -d '\n' | tr '+/' '-_'; }
hex() { openssl rand -hex "$1"; }

for s in $SERVERS; do
  f="$S/secrets/$s.env"
  [ -f "$f" ] && continue
  echo "init: creating secrets for $s"
  # VAPID (P-256)。SEC1 の DER は 7 バイト目から秘密鍵 32 バイト、末尾 65 バイトが公開鍵 (非圧縮)
  openssl ecparam -name prime256v1 -genkey -noout -outform DER -out /tmp/vapid.der
  priv=$(dd if=/tmp/vapid.der bs=1 skip=7 count=32 2>/dev/null | b64url)
  pub=$(tail -c 65 /tmp/vapid.der | b64url)
  rm -f /tmp/vapid.der
  cat > "$f" <<EOF
DB_PASS=$(hex 16)
SECRET_KEY_BASE=$(hex 64)
OTP_SECRET=$(hex 64)
VAPID_PRIVATE_KEY=$priv
VAPID_PUBLIC_KEY=$pub
ACTIVE_RECORD_ENCRYPTION_DETERMINISTIC_KEY=$(hex 16)
ACTIVE_RECORD_ENCRYPTION_KEY_DERIVATION_SALT=$(hex 16)
ACTIVE_RECORD_ENCRYPTION_PRIMARY_KEY=$(hex 16)
EOF
done

if [ ! -f "$S/secrets/postgres.env" ]; then
  echo "POSTGRES_PASSWORD=$(hex 16)" > "$S/secrets/postgres.env"
fi
chmod 644 "$S"/secrets/*
echo "init: done"
