# CivicPulse — production image
# Consistent runtime across your machine, teammates' machines, and Render —
# no more "works on my laptop" from differing local Python/OS versions.

FROM python:3.12-slim

# psycopg2 (Postgres driver) needs libpq at runtime; gcc only needed if a
# wheel isn't available for this platform, but keeping it is cheap and safe.
RUN apt-get update && apt-get install -y --no-install-recommends \
        libpq-dev \
        gcc \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Install deps first so this layer is cached unless requirements.txt changes.
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

# Run as a non-root user — standard container hardening.
RUN useradd --create-home appuser
USER appuser

# Render (and most hosts) inject PORT at runtime; default to 5000 for local
# `docker run`. Shell form so $PORT expands.
ENV PORT=5000
EXPOSE 5000

# --preload shares one app import across workers (faster boot, lower memory);
# 2 workers is a sane default for a free/small instance — bump via
# WEB_CONCURRENCY if you upsize later. Gunicorn's own healthcheck-friendly
# defaults are fine here, no extra flags needed.
CMD gunicorn app:app --bind 0.0.0.0:$PORT --workers ${WEB_CONCURRENCY:-2} --preload
