# frontend/Dockerfile
# 阶段一：编译
FROM node:22-alpine as build-stage
WORKDIR /app
COPY package*.json ./
RUN npm install --registry=https://registry.npmmirror.com
COPY . .
ENV NODE_OPTIONS="--max-old-space-size=4096"
RUN npm run build

# 阶段二：运行
FROM nginx:stable-alpine as production-stage
COPY --from=build-stage /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
