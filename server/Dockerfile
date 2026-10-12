# Build native SQLite dependency outside the runtime image. Node major and
# Debian libc must match between build and runtime stages.
FROM node:22-bookworm-slim AS dependencies
WORKDIR /srv/app
RUN apt-get update \
    && apt-get install -y --no-install-recommends python3 make g++ \
    && rm -rf /var/lib/apt/lists/*
COPY package.json package-lock.json ./
RUN npm ci --omit=dev --no-audit --no-fund

# Runtime is intentionally nonroot and has no compiler toolchain.
FROM node:22-bookworm-slim
ENV NODE_ENV=production \
    HOST=0.0.0.0 \
    PORT=8765 \
    TRIAL_DB_PATH=/var/lib/prompt-overlay/entitlements.sqlite
WORKDIR /srv/app
RUN groupadd --gid 10001 appuser \
    && useradd --uid 10001 --gid 10001 --home-dir /nonexistent --no-create-home appuser \
    && mkdir -p /var/lib/prompt-overlay \
    && chown -R appuser:appuser /srv/app /var/lib/prompt-overlay
COPY --from=dependencies --chown=appuser:appuser /srv/app/node_modules/ ./node_modules/
COPY --chown=appuser:appuser package.json package-lock.json ./
COPY --chown=appuser:appuser src/ ./src/
USER 10001:10001
EXPOSE 8765
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD node -e "require('http').get('http://127.0.0.1:8765/health',r=>process.exit(r.statusCode===200?0:1)).on('error',()=>process.exit(1))"
CMD ["node","src/index.js"]
