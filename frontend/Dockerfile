# Build stage: Node installs the exact locked dependencies and builds the production files.
FROM node:24-alpine AS build
WORKDIR /build

# The package files are copied first, so the installed dependencies stay cached until they change.
COPY package.json package-lock.json ./
RUN npm ci

COPY . .

# The browser calls the API on the same address the page came from. Nginx passes /api on to
# the backend. A container name such as "backend" must never go here: browsers cannot resolve it.
ARG VITE_API_BASE_URL=/api
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
RUN npm run build

# Runtime stage: Nginx serves the built files. No Node, no source code, no development server.
FROM nginx:1.28-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist /usr/share/nginx/html

EXPOSE 80
