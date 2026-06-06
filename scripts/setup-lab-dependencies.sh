#!/bin/bash
# =============================================================================
# CyberLearnix Lab — Dependency Setup Script
# =============================================================================
# This script is saved as the "Setup Script" for a course lab via:
#   PUT /api/labs/courses/{courseId}/setup-script
#
# It runs ONCE inside the base Docker container at BUILD time (admin builds).
# The result is committed as a new image; students get containers from it.
#
# Usage: paste this entire script into the admin "Lab Image Setup" > script box.
# =============================================================================

set -euo pipefail

log() { echo "[SETUP] $*"; }

# ── 1. System update ──────────────────────────────────────────────────────────
log "Updating apt package index..."
apt-get update -y -q

# ── 2. Core build & scripting tools ──────────────────────────────────────────
log "Installing core tools..."
DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
    curl wget git \
    vim nano \
    build-essential gcc g++ make \
    unzip zip \
    ca-certificates gnupg lsb-release \
    procps

# ── 3. Python 3 + pip ─────────────────────────────────────────────────────────
log "Installing Python 3..."
DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
    python3 python3-pip python3-venv python3-dev

python3 -m pip install --upgrade pip --quiet

log "Installing common Python packages..."
pip3 install --quiet \
    requests \
    cryptography \
    pycryptodome \
    flask \
    pytest \
    pwntools 2>/dev/null || true   # pwntools may need extra deps; non-fatal

# ── 4. Node.js (LTS) ─────────────────────────────────────────────────────────
log "Installing Node.js LTS..."
curl -fsSL https://deb.nodesource.com/setup_20.x | bash - -q
DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends nodejs

# ── 5. Java (OpenJDK 17) ──────────────────────────────────────────────────────
log "Installing Java 17..."
DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
    openjdk-17-jdk-headless

# Set JAVA_HOME for all users
echo 'export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64' >> /etc/environment
echo 'export PATH=$PATH:$JAVA_HOME/bin' >> /etc/environment

# ── 6. Networking & security tools ────────────────────────────────────────────
log "Installing networking tools..."
DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
    net-tools \
    netcat-openbsd \
    nmap \
    tcpdump \
    iputils-ping \
    dnsutils \
    traceroute \
    iproute2 \
    openssh-client

# ── 7. Database clients ───────────────────────────────────────────────────────
log "Installing DB clients..."
DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
    sqlite3 \
    postgresql-client 2>/dev/null || true

# ── 8. Cleanup ────────────────────────────────────────────────────────────────
log "Cleaning up..."
apt-get clean
rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*

# ── 9. Verification summary ───────────────────────────────────────────────────
log ""
log "=== INSTALLED VERSIONS ==="
log "Python3  : $(python3 --version 2>&1)"
log "pip3     : $(pip3 --version 2>&1 | cut -d' ' -f1-2)"
log "Node.js  : $(node --version 2>&1)"
log "npm      : $(npm --version 2>&1)"
log "Java     : $(java -version 2>&1 | head -1)"
log "gcc      : $(gcc --version 2>&1 | head -1)"
log "git      : $(git --version 2>&1)"
log "curl     : $(curl --version 2>&1 | head -1)"
log "nmap     : $(nmap --version 2>&1 | head -1)"
log "netcat   : $(nc --version 2>&1 || echo 'installed')"
log "sqlite3  : $(sqlite3 --version 2>&1)"
log ""
log "=== SETUP COMPLETE ==="
