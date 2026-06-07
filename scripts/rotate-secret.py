#!/usr/bin/env python3
"""
Cyberlearnix secret rotation helper.
Usage: python3 scripts/rotate-secret.py <key> <new-value> [--namespace <ns>]

Examples:
  python3 scripts/rotate-secret.py db-password 'NewP@ssw0rd!' --namespace cyberlearnix-production
  python3 scripts/rotate-secret.py db-password 'NewP@ssw0rd!' --namespace cyberlearnix
  python3 scripts/rotate-secret.py jwt-secret 'NewJWTSecret...'

The script:
  1. Base64-encodes the new value
  2. Patches cyberlearnix-secrets in the target namespace
  3. Optionally also updates the postgres user password (for db-password key)
  4. Triggers a rolling restart of all affected deployments
  5. Waits and reports rollout status
"""

import argparse
import base64
import subprocess
import sys
import time

SECRET_NAME = "cyberlearnix-secrets"

# Which key change requires a postgres ALTER ROLE
POSTGRES_KEYS = {"db-password"}

# Keys that trigger a full namespace rollout restart
ROLLOUT_KEYS = {"db-password", "jwt-secret", "redis-password", "mail-password"}


def run(cmd, check=True, capture=False):
    result = subprocess.run(cmd, shell=True, capture_output=capture, text=True)
    if check and result.returncode != 0:
        print(f"ERROR: {result.stderr or result.stdout}", file=sys.stderr)
        sys.exit(1)
    return result


def b64(value: str) -> str:
    return base64.b64encode(value.encode()).decode()


def patch_secret(key: str, value: str, namespace: str):
    encoded = b64(value)
    cmd = (
        f"kubectl patch secret {SECRET_NAME} -n {namespace} "
        f"--type=json -p='[{{\"op\":\"replace\",\"path\":\"/data/{key}\",\"value\":\"{encoded}\"}}]'"
    )
    result = run(cmd, check=False, capture=True)
    if result.returncode != 0:
        # Key may not exist yet — try add instead
        cmd = (
            f"kubectl patch secret {SECRET_NAME} -n {namespace} "
            f"--type=json -p='[{{\"op\":\"add\",\"path\":\"/data/{key}\",\"value\":\"{encoded}\"}}]'"
        )
        run(cmd)
    print(f"✅ Secret '{key}' updated in namespace '{namespace}'")


def verify_secret(key: str, expected: str, namespace: str):
    result = run(
        f"kubectl get secret {SECRET_NAME} -n {namespace} "
        f"-o jsonpath='{{{{.data.{key}}}}}'",
        capture=True
    )
    actual = base64.b64decode(result.stdout.strip()).decode()
    if actual != expected:
        print(f"❌ Verification failed: stored value does not match", file=sys.stderr)
        sys.exit(1)
    print(f"✅ Verified: secret value matches")


def rotate_postgres_password(new_password: str, namespace: str):
    print("  Updating PostgreSQL user password...")
    sql = f"ALTER USER postgres WITH PASSWORD '{new_password}';"
    result = run(
        f"kubectl exec -n {namespace} postgres-0 -- "
        f"psql -U postgres -c \"{sql}\"",
        capture=True
    )
    if "ALTER ROLE" in result.stdout:
        print("✅ PostgreSQL password updated")
    else:
        print(f"❌ PostgreSQL ALTER ROLE failed: {result.stderr}", file=sys.stderr)
        sys.exit(1)


def rollout_restart(namespace: str):
    print(f"  Restarting all deployments in '{namespace}'...")
    result = run(
        f"kubectl get deployments -n {namespace} -o name",
        capture=True
    )
    deployments = [d.strip() for d in result.stdout.strip().split("\n") if d.strip()]
    if not deployments:
        print("  No deployments found, skipping restart.")
        return
    names = " ".join(d.split("/")[-1] for d in deployments)
    run(f"kubectl rollout restart deployment {names} -n {namespace}")
    print(f"✅ Rolling restart triggered for {len(deployments)} deployments")


def wait_for_rollout(namespace: str, timeout: int = 300):
    print(f"  Waiting up to {timeout}s for rollout to complete...")
    result = run(
        f"kubectl get deployments -n {namespace} -o name",
        capture=True
    )
    deployments = [d.split("/")[-1] for d in result.stdout.strip().split("\n") if d.strip()]
    deadline = time.time() + timeout
    for dep in deployments:
        remaining = int(deadline - time.time())
        if remaining <= 0:
            print(f"⚠️  Timeout reached, {dep} may still be rolling out")
            break
        r = run(
            f"kubectl rollout status deployment/{dep} -n {namespace} --timeout={remaining}s",
            check=False, capture=True
        )
        status = "✅" if r.returncode == 0 else "⚠️ "
        print(f"  {status} {dep}: {r.stdout.strip() or r.stderr.strip()}")


def main():
    parser = argparse.ArgumentParser(description="Rotate a Cyberlearnix secret")
    parser.add_argument("key", help="Secret key name (e.g. db-password, jwt-secret)")
    parser.add_argument("value", help="New secret value (plaintext)")
    parser.add_argument("--namespace", "-n", default="cyberlearnix-production",
                        help="Kubernetes namespace (default: cyberlearnix-production)")
    parser.add_argument("--also-dev", action="store_true",
                        help="Also rotate the same key in the cyberlearnix (dev) namespace")
    parser.add_argument("--no-restart", action="store_true",
                        help="Patch secret only, skip rolling restart")
    parser.add_argument("--no-wait", action="store_true",
                        help="Trigger restart but do not wait for completion")
    args = parser.parse_args()

    namespaces = [args.namespace]
    if args.also_dev and "production" in args.namespace:
        namespaces.append("cyberlearnix")

    for ns in namespaces:
        print(f"\n── Rotating '{args.key}' in namespace '{ns}' ──")

        # 1. Update Postgres password first (before secret patch) so app restarts connect successfully
        if args.key in POSTGRES_KEYS:
            rotate_postgres_password(args.value, ns)

        # 2. Patch the k8s secret
        patch_secret(args.key, args.value, ns)

        # 3. Verify it was stored correctly
        verify_secret(args.key, args.value, ns)

        # 4. Rolling restart
        if not args.no_restart and args.key in ROLLOUT_KEYS:
            rollout_restart(ns)
            if not args.no_wait:
                wait_for_rollout(ns)

    print("\n✅ Secret rotation complete.")


if __name__ == "__main__":
    main()
