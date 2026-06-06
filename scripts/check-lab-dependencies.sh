#!/bin/bash
# =============================================================================
# CyberLearnix Lab — Dependency Verification Script (Student Container)
# =============================================================================
# Run inside a live student container via:
#   POST /api/labs/admin/containers/{assignmentId}/verify-deps
#
# Output format (one line per tool):
#   RESULT|PASS|<tool>|<version>
#   RESULT|FAIL|<tool>|<error>
#
# The backend parses these lines into structured JSON.
# =============================================================================

PASS=0
FAIL=0

check() {
    local tool="$1"
    local cmd="$2"
    local version
    version=$(eval "$cmd" 2>&1 | head -1 | tr -d '\n')
    if [ $? -eq 0 ] && [ -n "$version" ]; then
        echo "RESULT|PASS|${tool}|${version}"
        PASS=$((PASS + 1))
    else
        echo "RESULT|FAIL|${tool}|not found or not working"
        FAIL=$((FAIL + 1))
    fi
}

check_cmd() {
    local tool="$1"
    local cmd="$2"
    local ver_cmd="$3"
    if command -v "$cmd" &>/dev/null; then
        local version
        version=$(eval "$ver_cmd" 2>&1 | head -1 | tr -d '\n')
        echo "RESULT|PASS|${tool}|${version}"
        PASS=$((PASS + 1))
    else
        echo "RESULT|FAIL|${tool}|binary not found in PATH"
        FAIL=$((FAIL + 1))
    fi
}

check_python_pkg() {
    local pkg="$1"
    if python3 -c "import ${pkg}" 2>/dev/null; then
        local ver
        ver=$(python3 -c "import ${pkg}; print(getattr(${pkg}, '__version__', 'installed'))" 2>/dev/null || echo "installed")
        echo "RESULT|PASS|python:${pkg}|${ver}"
        PASS=$((PASS + 1))
    else
        echo "RESULT|FAIL|python:${pkg}|import failed"
        FAIL=$((FAIL + 1))
    fi
}

# ── System ────────────────────────────────────────────────────────────────────
check_cmd "bash"      "bash"      "bash --version"
check_cmd "curl"      "curl"      "curl --version"
check_cmd "wget"      "wget"      "wget --version"
check_cmd "git"       "git"       "git --version"
check_cmd "vim"       "vim"       "vim --version"
check_cmd "nano"      "nano"      "nano --version"
check_cmd "unzip"     "unzip"     "unzip -v"

# ── Python ────────────────────────────────────────────────────────────────────
check_cmd "python3"   "python3"   "python3 --version"
check_cmd "pip3"      "pip3"      "pip3 --version"
check_python_pkg "requests"
check_python_pkg "cryptography"
check_python_pkg "flask"
check_python_pkg "pytest"

# ── Node.js / npm ─────────────────────────────────────────────────────────────
check_cmd "node"      "node"      "node --version"
check_cmd "npm"       "npm"       "npm --version"

# ── Java ─────────────────────────────────────────────────────────────────────
check_cmd "java"      "java"      "java -version 2>&1"
check_cmd "javac"     "javac"     "javac -version 2>&1"

# ── Build tools ───────────────────────────────────────────────────────────────
check_cmd "gcc"       "gcc"       "gcc --version"
check_cmd "g++"       "g++"       "g++ --version"
check_cmd "make"      "make"      "make --version"

# ── Networking / security tools ───────────────────────────────────────────────
check_cmd "nmap"      "nmap"      "nmap --version"
check_cmd "netcat"    "nc"        "nc --version 2>&1 || echo netcat-openbsd"
check_cmd "ping"      "ping"      "ping -V 2>&1 || ping -c1 127.0.0.1"
check_cmd "nslookup"  "nslookup"  "nslookup -version 2>&1 || echo installed"
check_cmd "traceroute" "traceroute" "traceroute --version 2>&1 || echo installed"
check_cmd "ifconfig"  "ifconfig"  "ifconfig --version 2>&1 || echo installed"
check_cmd "ip"        "ip"        "ip -V 2>&1"
check_cmd "ssh"       "ssh"       "ssh -V 2>&1"

# ── Database clients ─────────────────────────────────────────────────────────
check_cmd "sqlite3"   "sqlite3"   "sqlite3 --version"
check_cmd "psql"      "psql"      "psql --version"

# ── Runtime connectivity test ─────────────────────────────────────────────────
if curl -sf --max-time 5 https://www.google.com -o /dev/null 2>/dev/null; then
    echo "RESULT|PASS|network:internet|outbound HTTP/S reachable"
    PASS=$((PASS + 1))
else
    echo "RESULT|WARN|network:internet|outbound HTTPS not reachable (may be expected)"
fi

# Python runtime sanity check
if python3 -c "import socket; s=socket.socket(); s.bind(('127.0.0.1',0)); s.close(); print('ok')" 2>/dev/null | grep -q ok; then
    echo "RESULT|PASS|python:runtime|socket bind test passed"
    PASS=$((PASS + 1))
else
    echo "RESULT|FAIL|python:runtime|socket test failed"
    FAIL=$((FAIL + 1))
fi

# Node.js runtime sanity check
if node -e "require('http'); console.log('ok')" 2>/dev/null | grep -q ok; then
    echo "RESULT|PASS|node:runtime|http module available"
    PASS=$((PASS + 1))
else
    echo "RESULT|FAIL|node:runtime|http module failed"
    FAIL=$((FAIL + 1))
fi

# ── Summary ───────────────────────────────────────────────────────────────────
TOTAL=$((PASS + FAIL))
echo "SUMMARY|${PASS}|${FAIL}|${TOTAL}"
