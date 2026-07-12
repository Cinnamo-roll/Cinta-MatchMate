# Stage 1: Build
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm install
COPY . .
RUN npx vite build

# Stage 2: Serve with Nginx
FROM nginx:stable-alpine
LABEL org.opencontainers.image.title="MatchMate Mobile" \
      org.opencontainers.image.source="https://github.com/Cinnamo-roll/Cinta-MatchMate" \
      org.opencontainers.image.authors="CintaOvO <D1391571546@outlook.com>" \
      org.opencontainers.image.licenses="Apache-2.0"
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
