# NAS 上的更新中转站：把 GitHub release 缓存到局域网
# 用法见 tools/README-nas-mirror.md
FROM node:22-alpine

WORKDIR /app
COPY nas-release-proxy.mjs /app/nas-release-proxy.mjs

ENV PORT=8088 \
    HOST=0.0.0.0 \
    CACHE_DIR=/cache \
    UPSTREAM=https://github.com \
    REPOSITORY=chinahhy/Hazix

VOLUME ["/cache"]
EXPOSE 8088

HEALTHCHECK --interval=5m --timeout=15s --start-period=10s \
  CMD node -e "fetch('http://127.0.0.1:8088/healthz').then(r=>process.exit(r.ok?0:1)).catch(()=>process.exit(1))"

CMD ["node", "/app/nas-release-proxy.mjs"]
