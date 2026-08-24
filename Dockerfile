FROM node:18-alpine

WORKDIR /app

COPY Server/package*.json ./
RUN npm ci --only=production

COPY Server/server.js ./
COPY Server/dashboard/ ./dashboard/

EXPOSE 3001

CMD ["node", "server.js"]