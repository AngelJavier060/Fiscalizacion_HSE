# Build context = raíz del repo (ver docker-compose.yml)
FROM ghcr.io/cirruslabs/flutter:3.35.7 AS flutter
WORKDIR /flutter
COPY app/pubspec.yaml app/pubspec.lock ./
COPY app/lib ./lib
COPY app/web ./web
COPY app/assets ./assets
RUN git config --global --add safe.directory /sdks/flutter \
    && flutter config --no-analytics --enable-web \
    && flutter pub get \
    && flutter build web --release --base-href /app/ --no-wasm-dry-run

FROM node:20-alpine AS angular
WORKDIR /web
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ .
RUN npm run build -- --configuration=production

FROM nginx:stable-alpine
COPY --from=angular /web/dist/fiscalizacion-hse/browser /usr/share/nginx/html
COPY --from=flutter /flutter/build/web /usr/share/nginx/html/app
COPY frontend/nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
