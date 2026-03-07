FROM node:20-alpine

WORKDIR /app

# Install dependencies
COPY package.json package-lock.json ./
RUN npm ci

# Copy source
COPY . .

# Vite env vars (baked into the dev server)
ENV VITE_BACKEND_URL=http://localhost:8080
ENV VITE_WS_URL=ws://localhost:8080/ws

EXPOSE 5173

CMD ["npm", "run", "dev", "--", "--host", "0.0.0.0"]
