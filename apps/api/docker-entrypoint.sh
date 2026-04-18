#!/bin/sh
set -eu

APP_USER="${APP_USER:-spring}"
UPLOAD_DIR="${UPLOAD_PATH:-/app/uploads}"
JAVA_BIN="${JAVA_BIN:-/opt/java/openjdk/bin/java}"

mkdir -p "$UPLOAD_DIR"

# Bind-mounted host directories can keep root ownership across redeploys.
# Fix the upload directory before dropping privileges so image uploads keep working.
chown -R "$APP_USER:$APP_USER" "$UPLOAD_DIR"

exec su -s /bin/sh "$APP_USER" -c "exec \"$JAVA_BIN\" -jar /app/app.jar"
