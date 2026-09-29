FROM python:3.11-slim

WORKDIR /app/analytics

# Install system dependencies
RUN apt-get update && apt-get install -y \
    gcc \
    && rm -rf /var/lib/apt/lists/*

# Copy requirements
COPY analytics/requirements.txt .

# Install Python dependencies
RUN pip install --no-cache-dir -r requirements.txt

# Copy analytics code
COPY analytics/ .

# Set PYTHONUNBUFFERED to see logs in real-time
ENV PYTHONUNBUFFERED=1

# Run the scheduler
CMD ["python", "src/run_scheduler.py"]