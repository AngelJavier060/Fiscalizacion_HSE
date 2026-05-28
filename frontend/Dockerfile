FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build -- --configuration=production

FROM nginx:stable-alpine
# Angular 17+ (application builder) genera los archivos en dist/.../browser/
COPY --from=build /app/dist/fiscalizacion-hse/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
