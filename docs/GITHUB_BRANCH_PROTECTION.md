# GitHub Branch Protection Rules

## Overview

This document defines the branch protection rules to be configured for the `cyberlearnix` backend repository to prevent accidental direct pushes to production branches and enforce code review.

---

## Rules to Configure

### Protected Branch: `main`

Navigate to: **GitHub → Repository Settings → Branches → Add branch protection rule**

Set the **Branch name pattern** to: `main`

| Setting | Value | Reason |
|---------|-------|--------|
| Require a pull request before merging | ✅ Enabled | No direct pushes to main |
| Required approvals | 1 (minimum) | At least one team member must review |
| Dismiss stale PR approvals on new commits | ✅ Enabled | Re-review if code changes after approval |
| Require review from Code Owners | Optional (enable if CODEOWNERS file exists) |  |
| Require status checks to pass before merging | ✅ Enabled | Block merges when CI fails |
| Require branches to be up to date before merging | ✅ Enabled | Prevents stale merges |
| Require conversation resolution before merging | ✅ Recommended | No unresolved comments |
| Do not allow bypassing the above settings | ✅ Enabled | Even admins must follow rules |
| Restrict who can push to matching branches | Optional — restrict to senior devs |  |
| Allow force pushes | ❌ Disabled | Never allow history rewriting |
| Allow deletions | ❌ Disabled | Cannot delete `main` |

### Protected Branch: `release/*` (if applicable)

Apply the same rules as `main` to any `release/` branches.

---

## Recommended CI Status Checks

Once a GitHub Actions CI pipeline is set up, require these checks to pass before merge:

- `build` — Gradle build succeeds
- `test` — Unit/integration tests pass
- `docker-build` — Docker image builds without errors

---

## CODEOWNERS File (Optional but Recommended)

Create a `.github/CODEOWNERS` file to auto-request reviews from relevant owners:

```
# Global owners — reviewed for all changes
*                   @cyberlearnix/backend-team

# Service-specific ownership
/user-service/      @shivakumar
/admin-service/     @shivakumar
/gateway-service/   @shivakumar
/course-service/    @shivakumar
/enrollment-service/ @shivakumar
/shop-service/      @shivakumar
/form-service/      @shivakumar
/notification-service/ @shivakumar
/cms-service/       @shivakumar
/instructor-service/ @shivakumar

# Infrastructure / K8s — requires DevOps review
/helm/              @rohit-devops
/k8s/               @rohit-devops
/docker/            @rohit-devops
/scripts/           @rohit-devops
```

---

## How to Apply (via GitHub UI)

1. Go to your repository on GitHub
2. Click **Settings** → **Branches**
3. Under **Branch protection rules**, click **Add rule**
4. In **Branch name pattern**, enter `main`
5. Check each setting listed in the table above
6. Click **Create** (or **Save changes** if editing)

---

## How to Apply (via GitHub CLI)

```bash
gh api -X PUT repos/{owner}/{repo}/branches/main/protection \
  --field required_status_checks='{"strict":true,"contexts":["build","test"]}' \
  --field enforce_admins=true \
  --field required_pull_request_reviews='{"required_approving_review_count":1,"dismiss_stale_reviews":true}' \
  --field restrictions=null \
  --field allow_force_pushes=false \
  --field allow_deletions=false
```

Replace `{owner}/{repo}` with the actual GitHub repository path.

---

## Summary of Protection Goals

| Risk | Mitigation |
|------|-----------|
| Developer pushes untested code to `main` | PR required, CI must pass |
| Code is merged without review | 1 required approval |
| Secrets accidentally committed | Use `git-secrets` or GitHub secret scanning |
| Force-push rewrites history | Force pushes disabled |
| Stale branch merges cause regressions | Branch must be up to date before merge |
