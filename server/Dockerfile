# Deployment-only image. This does not package the Android app.
FROM node:22-bookworm-slim
ENV NODE_ENV=production \
    HOST=0.0.0.0 \
    PORT=8765 \
    TRIAL_DB_PATH=/var/lib/prompt-overlay/entitlements.sqlite
WORKDIR /srv/app
COPY package*.json ./
RUN npm install --omit=dev --no-audit --no-fund \
    && npm cache clean --force \
    && groupadd --gid 10001 appuser \
    && useradd --uid 10001 --gid 10001 --home-dir /nonexistent --no-create-home appuser \
    && mkdir -p /var/lib/prompt-overlay \
    && chown -R appuser:appuser /srv/app /var/lib/prompt-overlay
COPY --chown=appuser:appuser src/ ./src/
USER 10001:10001
EXPOSE 8765
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD node -e "require('http').get('http://127.0.0.1:8765/health',r=>process.exit(r.statusCode===200?0:1)).on('error',()=>process.exit(1))"
CMD ["node","src/index.js"]
