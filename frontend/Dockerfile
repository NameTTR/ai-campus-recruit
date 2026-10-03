FROM docker.m.daocloud.io/library/node:22-alpine AS build

ARG VITE_DEMO_MODE=false
ENV VITE_DEMO_MODE=${VITE_DEMO_MODE}

WORKDIR /app
COPY package*.json ./
RUN npm config set registry https://registry.npmmirror.com && npm ci
COPY . .
RUN npm run build

FROM docker.m.daocloud.io/library/nginx:1.27-alpine

ENV GATEWAY_UPSTREAM=http://gateway-service:8080
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY nginx/wait-for-gateway.sh /docker-entrypoint.d/40-wait-for-gateway.sh
RUN chmod +x /docker-entrypoint.d/40-wait-for-gateway.sh
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
