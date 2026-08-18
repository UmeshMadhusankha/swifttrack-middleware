FROM node:20-alpine
WORKDIR /app

# Copy the manifest first so Docker can cache the install layer.
COPY package.json ./
RUN npm install --omit=dev

COPY . .
EXPOSE 3002
CMD ["node", "server.js"]
