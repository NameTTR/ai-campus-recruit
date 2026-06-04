FROM docker.m.daocloud.io/library/node:22-alpine AS build

WORKDIR /app
COPY package*.json ./
RUN npm config set registry https://registry.npmmirror.com && npm ci
COPY . .
RUN npm run build

FROM docker.m.daocloud.io/library/nginx:1.27-alpine

ENV GATEWAY_UPSTREAM=http://gateway-service:8080
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
