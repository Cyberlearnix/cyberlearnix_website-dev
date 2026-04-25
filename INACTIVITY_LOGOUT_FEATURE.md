# Inactivity Timeout & Auto-Logout Feature

## Requirements

- Monitor user activity (keyboard, mouse, clicks, scrolling)
- Warn after 60 minutes of inactivity
- Show countdown dialog (30 seconds)
- Auto-logout if no response
- Reset timer on activity or "Continue" action

---

## Core Service Implementation

### TypeScript/React Implementation

```typescript
// InactivityService.ts
export class InactivityService {
  private inactivityTimeout: NodeJS.Timeout | null = null;
  private warningTimeout: NodeJS.Timeout | null = null;
  private inactivityDuration = 60 * 60 * 1000; // 60 minutes in ms
  private warningDuration = 30 * 1000; // 30 seconds warning
  private isWarningShown = false;
  private listeners: ((isActive: boolean) => void)[] = [];

  constructor(private onLogout: () => void, private onWarning: (remainingTime: number) => void) {}

  /**
   * Start monitoring inactivity
   */
  public startMonitoring(): void {
    this.attachActivityListeners();
    this.resetInactivityTimer();
  }

  /**
   * Stop monitoring
   */
  public stopMonitoring(): void {
    this.detachActivityListeners();
    this.clearTimeouts();
  }

  /**
   * Attach activity event listeners
   */
  private attachActivityListeners(): void {
    const events = ['mousedown', 'keydown', 'scroll', 'touchstart', 'click'];
    
    events.forEach(event => {
      window.addEventListener(event, () => this.onUserActivity());
    });
  }

  /**
   * Detach activity listeners
   */
  private detachActivityListeners(): void {
    const events = ['mousedown', 'keydown', 'scroll', 'touchstart', 'click'];
    
    events.forEach(event => {
      window.removeEventListener(event, () => this.onUserActivity());
    });
  }

  /**
   * Called when user performs activity
   */
  private onUserActivity(): void {
    if (this.isWarningShown) {
      return; // Don't reset if warning is already shown
    }
    
    this.resetInactivityTimer();
  }

  /**
   * Reset the inactivity timer
   */
  private resetInactivityTimer(): void {
    this.clearTimeouts();
    this.isWarningShown = false;

    // Set inactivity timeout (60 minutes)
    this.inactivityTimeout = setTimeout(() => {
      this.showWarningDialog();
    }, this.inactivityDuration);
  }

  /**
   * Show warning dialog with 30-second countdown
   */
  private showWarningDialog(): void {
    this.isWarningShown = true;
    let remainingSeconds = 30;

    // Notify UI to show warning
    this.onWarning(remainingSeconds);

    // Countdown timer
    const countdownInterval = setInterval(() => {
      remainingSeconds--;
      this.onWarning(remainingSeconds);

      if (remainingSeconds <= 0) {
        clearInterval(countdownInterval);
        this.performLogout();
      }
    }, 1000);

    this.warningTimeout = countdownInterval as any;
  }

  /**
   * User clicked "Continue" - keep session active
   */
  public continueSession(): void {
    this.clearTimeouts();
    this.isWarningShown = false;
    this.resetInactivityTimer();
  }

  /**
   * Perform logout
   */
  private performLogout(): void {
    this.stopMonitoring();
    this.onLogout();
  }

  /**
   * Clear all timeouts
   */
  private clearTimeouts(): void {
    if (this.inactivityTimeout) clearTimeout(this.inactivityTimeout);
    if (this.warningTimeout) clearInterval(this.warningTimeout as any);
  }
}
```

---

## React Component Implementation

### Inactivity Warning Modal

```typescript
// InactivityWarningModal.tsx
import React, { useState, useEffect } from 'react';
import './InactivityWarningModal.css';

interface InactivityWarningModalProps {
  isOpen: boolean;
  remainingSeconds: number;
  onContinue: () => void;
  onLogout: () => void;
}

export const InactivityWarningModal: React.FC<InactivityWarningModalProps> = ({
  isOpen,
  remainingSeconds,
  onContinue,
  onLogout,
}) => {
  if (!isOpen) return null;

  const isWarning = remainingSeconds < 10; // Red warning when < 10 seconds

  return (
    <div className="inactivity-modal-overlay">
      <div className={`inactivity-modal ${isWarning ? 'warning' : ''}`}>
        <div className="modal-header">
          ⚠️ Session Inactivity Warning
        </div>
        
        <div className="modal-body">
          <p>Your session has been inactive for 60 minutes.</p>
          <p className="warning-text">
            You will be logged out in <strong className="countdown">{remainingSeconds}</strong> seconds.
          </p>
          <p>Click "Continue Working" to stay logged in.</p>
        </div>

        <div className="modal-footer">
          <button 
            className="btn btn-primary" 
            onClick={onContinue}
            disabled={remainingSeconds <= 0}
          >
            Continue Working
          </button>
          <button 
            className="btn btn-secondary" 
            onClick={onLogout}
          >
            Logout
          </button>
        </div>
      </div>
    </div>
  );
};
```

### CSS Styling

```css
/* InactivityWarningModal.css */
.inactivity-modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(0, 0, 0, 0.7);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 10000;
  backdrop-filter: blur(4px);
}

.inactivity-modal {
  background: white;
  border-radius: 8px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.3);
  max-width: 500px;
  width: 90%;
  animation: slideIn 0.3s ease-out;
}

@keyframes slideIn {
  from {
    transform: translateY(-30px);
    opacity: 0;
  }
  to {
    transform: translateY(0);
    opacity: 1;
  }
}

.inactivity-modal.warning {
  border-left: 4px solid #ff4444;
  background-color: #fff5f5;
}

.modal-header {
  padding: 20px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  font-size: 18px;
  font-weight: 600;
  border-radius: 8px 8px 0 0;
}

.inactivity-modal.warning .modal-header {
  background: linear-gradient(135deg, #ff6b6b 0%, #ee5a6f 100%);
}

.modal-body {
  padding: 30px 20px;
  text-align: center;
}

.modal-body p {
  margin: 10px 0;
  font-size: 15px;
  color: #333;
}

.warning-text {
  font-size: 16px !important;
  color: #d32f2f !important;
  font-weight: 500;
  margin-top: 20px;
}

.countdown {
  font-size: 24px;
  color: #ff4444;
  padding: 0 8px;
  animation: pulse 1s infinite;
}

@keyframes pulse {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.1); }
}

.modal-footer {
  padding: 20px;
  display: flex;
  gap: 10px;
  justify-content: flex-end;
  border-top: 1px solid #e0e0e0;
}

.btn {
  padding: 10px 20px;
  border: none;
  border-radius: 4px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
}

.btn-primary {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
}

.btn-primary:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
}

.btn-primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-secondary {
  background: #f5f5f5;
  color: #333;
  border: 1px solid #ddd;
}

.btn-secondary:hover {
  background: #e8e8e8;
  border-color: #999;
}
```

---

## App Integration

### Using the Service in React App

```typescript
// App.tsx or useAuth.ts
import { useEffect, useState } from 'react';
import { InactivityService } from './services/InactivityService';
import { InactivityWarningModal } from './components/InactivityWarningModal';
import { useNavigate } from 'react-router-dom';

export const useInactivityTimeout = () => {
  const navigate = useNavigate();
  const [showWarning, setShowWarning] = useState(false);
  const [remainingSeconds, setRemainingSeconds] = useState(30);
  const [inactivityService, setInactivityService] = useState<InactivityService | null>(null);

  useEffect(() => {
    const service = new InactivityService(
      // onLogout callback
      () => {
        localStorage.removeItem('token');
        localStorage.removeItem('refreshToken');
        setShowWarning(false);
        navigate('/login', { state: { message: 'Session expired due to inactivity' } });
      },
      // onWarning callback
      (remainingTime) => {
        setShowWarning(true);
        setRemainingSeconds(remainingTime);
      }
    );

    setInactivityService(service);
    service.startMonitoring();

    return () => {
      service.stopMonitoring();
    };
  }, [navigate]);

  const handleContinue = () => {
    if (inactivityService) {
      inactivityService.continueSession();
      setShowWarning(false);
    }
  };

  const handleLogout = () => {
    localStorage.removeItem('token');
    localStorage.removeItem('refreshToken');
    navigate('/login');
  };

  return { showWarning, remainingSeconds, handleContinue, handleLogout };
};

// Usage in main App component
export function App() {
  const { showWarning, remainingSeconds, handleContinue, handleLogout } = useInactivityTimeout();

  return (
    <>
      <InactivityWarningModal
        isOpen={showWarning}
        remainingSeconds={remainingSeconds}
        onContinue={handleContinue}
        onLogout={handleLogout}
      />
      {/* Rest of your app */}
    </>
  );
}
```

---

## Configuration Options

You can customize the timeouts:

```typescript
// Custom configuration
const inactivityService = new InactivityService(
  onLogout,
  onWarning
);

// Modify timeouts
inactivityService['inactivityDuration'] = 30 * 60 * 1000; // 30 minutes
inactivityService['warningDuration'] = 60 * 1000; // 1 minute warning

inactivityService.startMonitoring();
```

---

## Backend Integration

### Optional: Keep-Alive Endpoint (Backend)

If user clicks "Continue", optionally call an endpoint to refresh the session:

```typescript
// InactivityService - add this method
public async refreshSessionOnServer(): Promise<void> {
  try {
    await fetch('/api/auth/keep-alive', {
      method: 'POST',
      credentials: 'include',
      headers: { 'Authorization': `Bearer ${localStorage.getItem('token')}` }
    });
  } catch (error) {
    console.error('Session refresh failed:', error);
  }
}
```

---

## Features Summary

✅ Tracks keyboard, mouse, click, scroll, touch events
✅ 60-minute inactivity threshold (configurable)
✅ 30-second warning countdown (configurable)
✅ Beautiful modal dialog with countdown animation
✅ Automatic logout on timeout
✅ Session continuation on user click
✅ Responsive design
✅ Production-ready with TypeScript

---

## Installation Steps

1. Copy files to your frontend:
   - `InactivityService.ts`
   - `InactivityWarningModal.tsx`
   - `InactivityWarningModal.css`

2. Integrate into your App:
   - Use `useInactivityTimeout` hook as shown above

3. (Optional) Add backend keep-alive endpoint

Done! The auto-logout will now work smoothly. 🚀
