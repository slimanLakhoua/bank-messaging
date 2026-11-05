FROM nginx:1.28.3-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY dist/bank-messaging/browser/ /usr/share/nginx/html/
HEALTHCHECK --interval=10s --timeout=5s --start-period=10s --retries=6 CMD wget -q -O /dev/null http://127.0.0.1/healthz || exit 1
EXPOSE 80
