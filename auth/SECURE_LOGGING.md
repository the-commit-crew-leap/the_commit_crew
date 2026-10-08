# Secure Logging Implementation Guide

## Overview

The secure logging system prevents sensitive data (passwords, tokens, API keys) from being logged in your authentication service. It consists of three main components:

1. **DataSanitizer** - Utility that masks sensitive fields
2. **SecureLoggerService** - Extended logger that sanitizes data
3. **RequestSanitizerMiddleware** - Middleware that sanitizes HTTP requests/responses

## Key Features

### ✅ What Gets Masked

- `password`, `passwordHash`, `password_hash`
- `accessToken`, `refreshToken`, `token`
- `apiKey`, `secret`
- `creditCard`, `ssn`, `pin`
- Any field containing these words (case-insensitive)

### ✅ Automatic Sanitization

All three components automatically sanitize sensitive data:
- Entire objects are traversed recursively
- Nested objects and arrays are handled
- Depth limit (5 levels) prevents infinite recursion
- Non-object data types are passed through unchanged

---

## Usage Examples

### 1. Using SecureLoggerService in Services

```typescript
import { SecureLoggerService } from '../common/services/secure-logger.service';

@Injectable()
export class MyService {
  private readonly logger = new SecureLoggerService(MyService.name);

  async handleLogin(loginDto: LoginDto) {
    // Log with data sanitization - passwords will be masked
    this.logger.log('Login attempt', undefined, loginDto);
    // Output: Login attempt | {"username":"john","password":"***REDACTED***"}
    
    // Log auth events with partial username masking
    this.logger.logAuthEvent('LOGIN_SUCCESS', 'john_doe');
    // Output: LOGIN_SUCCESS | username: jo***
  }

  async updateUser(userData: UserDto) {
    // Error logging with sanitization
    try {
      // ... code ...
    } catch (error) {
      // Password in userData will be masked automatically
      this.logger.error('User update failed', error.stack, undefined, userData);
    }
  }
}
```

### 2. RequestSanitizerMiddleware

The middleware is **automatically applied** to all `api/auth` routes. It:
- Logs incoming requests with sanitized bodies
- Sanitizes outgoing responses (status < 400)
- Masks all sensitive fields

**Configured in [auth.module.ts](../../auth/auth.module.ts#L30-L34)**

### 3. Direct Sanitization in Utilities

```typescript
import { DataSanitizer } from '../common/utils/data-sanitizer.util';

// Sanitize an object
const sensitiveData = {
  username: 'john_doe',
  password: 'SecurePass123!',
  email: 'john@example.com'
};

const sanitized = DataSanitizer.sanitize(sensitiveData);
// Result: { username: 'john_doe', password: '***REDACTED***', email: 'john@example.com' }

// Partially mask a string
const maskedEmail = DataSanitizer.sanitizeString('john@example.com', 'email');
// Result: 'jo***@example.com'

const maskedPhone = DataSanitizer.sanitizeString('1234567890', 'phone');
// Result: '***-***-7890'
```

---

## Implementation in Auth Service

The SecureLoggerService is already integrated in [auth.service.ts](../../auth/auth.service.ts):

```typescript
// Register endpoint logs without exposing password
await this.logger.log('Attempting to register user', undefined, { username, email });
// Logs: "Attempting to register user | {"username":"john_doe","email":"john@example.com"}"
// Password is NOT included in the log

// Login endpoint logs auth events
await this.logger.logAuthEvent('LOGIN_SUCCESS', username);
// Logs: "LOGIN_SUCCESS | username: jo***"

// Failed attempts are logged with attempts counter
this.logger.warn(
  'Login failed: invalid password',
  undefined,
  { username, attempt: attemptCount }
);
```

---

## Best Practices

### ✅ DO

- **Always use SecureLoggerService** for auth-related logging
- **Log auth events** using `logAuthEvent()` for audit trails
- **Include context** like username/user_id (they're not sensitive)
- **Log failures** to detect attacks (failed login attempts, etc.)

```typescript
// ✅ GOOD - Uses SecureLoggerService
this.logger.logAuthEvent('LOGIN_SUCCESS', username);
```

### ❌ DON'T

- **Never log raw request/response objects** that may contain passwords
- **Never stringify DTOs** that contain sensitive fields
- **Don't bypass the sanitizer** for "debugging" purposes
- **Never log tokens** or secrets in debug mode

```typescript
// ❌ BAD - Password would be logged
console.log('Login attempt:', loginDto);

// ❌ BAD - Token exposed
console.log('Access token:', token);

// ✅ GOOD - Uses secure logger
this.logger.log('Login attempt', undefined, loginDto);
```

---

## Adding Custom Sensitive Fields

To add more fields that should be masked, update `DataSanitizer`:

```typescript
// In data-sanitizer.util.ts
private static readonly SENSITIVE_FIELDS = [
  'password',
  'token',
  'myCustomField',  // Add here
  'anotherSecret',
  // ...
];
```

Any field containing these words (case-insensitive) will be masked.

---

## Audit Trail & Security Events

Use `logAuthEvent()` for security audit trails:

```typescript
// Login events
this.logger.logAuthEvent('LOGIN_SUCCESS', username);
this.logger.logAuthEvent('LOGIN_FAILED', username, { reason: 'Invalid credentials' });

// Registration events
this.logger.logAuthEvent('REGISTER_SUCCESS', username);
this.logger.logAuthEvent('REGISTER_FAILED', username, { reason: 'User exists' });

// Token events
this.logger.logAuthEvent('TOKEN_REFRESH_SUCCESS', username);
this.logger.logAuthEvent('TOKEN_VALIDATION_FAILED', username);

// Account security events
this.logger.logAuthEvent('ACCOUNT_LOCKED', username, { attempts: 5 });
```

These events can be collected and monitored for:
- Failed login attempts
- Brute force attacks
- Account lockouts
- Unusual activity patterns

---

## Testing Secure Logging

```bash
# Start the auth service
npm run start:dev

# Register a user (check logs - password should NOT appear)
curl -X POST http://localhost:3000/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "email": "test@example.com",
    "password": "SecurePass123!"
  }'

# Expected log output:
# Incoming POST request to /api/auth/register | {"username":"testuser","email":"test@example.com","password":"***REDACTED***"}
# Attempting to register user | {"username":"testuser","email":"test@example.com"}
# REGISTER_SUCCESS | username: te***
```

The password should **NEVER** appear in logs.

---

## Environment Configuration

Logging level can be controlled via environment:

```bash
# Development (verbose logging)
ENVIRONMENT=dev NODE_ENV=development npm run start:dev

# Production (minimal logging)
ENVIRONMENT=prod NODE_ENV=production npm run start
```

See [app.module.ts](../../app.module.ts#L17) for TypeORM logging configuration.

---

## Performance Considerations

- **Sanitization overhead**: Minimal - only traverses object tree once
- **Depth limit**: Set to 5 levels by default to prevent deep recursion
- **Async logging**: All operations are synchronous, suitable for logging
- **Production ready**: No performance impact on request handling

---

## Compliance

This implementation helps with:
- **OWASP**: A09:2021 – Logging and Monitoring Failures
- **GDPR/CCPA**: Prevents PII from being logged unnecessarily
- **PCI-DSS**: Requirement 3.2 - Never log sensitive card data
- **ISO 27001**: A.12.4 - Logging and monitoring

---

## Files Modified/Created

- ✅ [data-sanitizer.util.ts](../../common/utils/data-sanitizer.util.ts) - Core sanitization logic
- ✅ [secure-logger.service.ts](../../common/services/secure-logger.service.ts) - Extended logger
- ✅ [request-sanitizer.middleware.ts](../../common/middleware/request-sanitizer.middleware.ts) - HTTP middleware
- ✅ [auth.service.ts](../../auth/auth.service.ts) - Updated with logging
- ✅ [auth.module.ts](../../auth/auth.module.ts) - Middleware registration

---

## Questions or Issues?

For security concerns with logging, review:
1. The list of sensitive fields in `DataSanitizer`
2. The depth limit in the `sanitize()` method
3. Environment-specific logging levels
