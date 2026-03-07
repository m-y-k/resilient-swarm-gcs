FROM python:3.11-slim

WORKDIR /app

# Install dependencies
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy source
COPY *.py ./

# Environment defaults (overridden by docker-compose)
ENV BACKEND_URL=http://backend:8080
ENV NUM_DRONES=10
ENV HEARTBEAT_INTERVAL_MS=100

ENTRYPOINT ["python", "main.py"]
