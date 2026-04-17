## 🔐 Cyberlearnix Secure Authentication - Quick Reference

### **New Login Response Format (httpOnly Cookies)**

```json
{
  "token": "eyJhbGciOiJIUzUxMiJ9...",
  "expiresIn": 3600,
  "tokenType": "Bearer",
  "user": {
    "id": "9f4ea44b-966c-4e76-9646-bae02bfc116b",
    "email": "user@example.com",
    "role": "admin|teacher|student",
    "isFirstLogin": false
  }
}
```

**Refresh Token**: Automatically set in httpOnly cookie (not in JSON body)

---

### **Frontend Implementation Changes**

#### Old (Insecure) ❌
```javascript
// DON'T DO THIS ANYMORE
const response = await fetch('/api/auth/login', {
  method: 'POST',
  body: JSON.stringify({ email, password })
});

const { token, refreshToken, user } = await response.json();
localStorage.setItem('token', token);
localStorage.setItem('refreshToken', refreshToken); // VULNERABLE TO XSS!
```

#### New (Secure) ✅
```javascript
// DO THIS INSTEAD
const response = await fetch('/api/auth/login', {
  method: 'POST',
  credentials: 'include', // ← Important!
  body: JSON.stringify({ email, password })
});

const { token, user } = await response.json();
// Keep token in memory only, NEVER localStorage
window.accessToken = token; // Memory only
// Refresh token is in httpOnly cookie automatically

// Redirect by role
const routes = {
  admin: '/admin/dashboard',
  teacher: '/teacher/dashboard',
  student: '/student/dashboard'
};
window.location.href = routes[user.role];
```

---

### **Using Access Token**

```javascript
// For protected endpoints
fetch('/api/protected', {
  method: 'GET',
  credentials: 'include', // ← Send cookies (including refresh token)
  headers: {
    'Authorization': `Bearer ${window.accessToken}`, // ← Send access token
    'Content-Type': 'application/json'
  }
});
```

---

### **Logout Handler**

```javascript
async function logout() {
  const response = await fetch('/api/auth/logout', {
    method: 'POST',
    credentials: 'include',
    headers: {
      'Authorization': `Bearer ${window.accessToken}`
    }
  });

  if (response.ok) {
    // Clear local state
    window.accessToken = null;
    // Cookie cleared automatically by backend
    window.location.href = '/login';
  }
}
```

---

### **Handle Token Expiration**

```javascript
async function makeApiCall(url, options) {
  let response = await fetch(url, {
    ...options,
    credentials: 'include',
    headers: {
      ...options.headers,
      'Authorization': `Bearer ${window.accessToken}`
    }
  });

  if (response.status === 401) {
    // Token expired, try refresh
    const refreshResponse = await fetch('/api/auth/refresh-token', {
      method: 'POST',
      credentials: 'include'
    });

    if (refreshResponse.ok) {
      const { token } = await refreshResponse.json();
      window.accessToken = token;
      
      // Retry original request
      response = await fetch(url, {
        ...options,
        credentials: 'include',
        headers: {
          ...options.headers,
          'Authorization': `Bearer ${window.accessToken}`
        }
      });
    } else {
      // Redirect to login
      window.location.href = '/login';
      return null;
    }
  }

  return response;
}
```

---

### **Security Features Enabled**

| Feature | Status | Details |
|---------|--------|---------|
| Rate Limiting | ✅ | 5 attempts per 15 min per IP |
| Audit Logging | ✅ | All auth events tracked |
| CORS | ✅ | Credentials enabled |
| httpOnly Cookies | ✅ | Refresh token immune to XSS |
| CSP Headers | ✅ | Strict content policy |
| CSRF Protection | ✅ | SameSite=Strict |
| Password Validation | ✅ | Uppercase, lowercase, number, special char |
| JWT HS512 | ✅ | Secure token signing |
| Token Blacklist | ✅ | Logout invalidates immediately |

---

### **Common Issues & Solutions**

#### Issue: "Cookie not being sent in requests"
**Solution**: Add `credentials: 'include'` to all fetch calls

#### Issue: "CORS error when logging in"
**Solution**: Ensure domain is in `CORS_ALLOWED_ORIGINS` environment variable

#### Issue: "401 Unauthorized after token refresh"
**Solution**: Check if refresh token cookie is being sent (credentials: 'include')

#### Issue: "Rate limit exceeded"
**Solution**: Wait 15 minutes or use different IP

#### Issue: "Cannot read localStorage.token"
**Solution**: Token is no longer in localStorage! Use `window.accessToken` (memory only)

---

### **Database Queries**

**View recent login attempts**:
```sql
SELECT email, action, ip_address, timestamp, success 
FROM auth_audits 
WHERE timestamp > NOW() - INTERVAL '24 hours'
ORDER BY timestamp DESC 
LIMIT 100;
```

**Check suspicious activity**:
```sql
SELECT email, count(*) as failed_attempts
FROM auth_audits
WHERE action = 'LOGIN_FAILED' AND timestamp > NOW() - INTERVAL '1 hour'
GROUP BY email HAVING count(*) >= 3;
```

---

### **API Endpoints Reference**

| Endpoint | Method | Protected | Cookie Auth |
|----------|--------|-----------|------------|
| `/api/auth/login` | POST | ❌ | ❌ |
| `/api/auth/logout` | POST | ✅ | ✅ |
| `/api/auth/refresh-token` | POST | ❌ | ✅ |
| `/api/auth/register` | POST | ✅ (admin) | ❌ |
| `/api/auth/verify-otp-login` | POST | ❌ | ❌ |
| `/api/auth/change-password` | POST | ✅ | ✅ |

---

**Need Help?** Check [SECURITY_IMPLEMENTATION_GUIDE.md](../docs/SECURITY_IMPLEMENTATION_GUIDE.md) for detailed documentation.
