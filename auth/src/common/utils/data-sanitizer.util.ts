/**
 * Utility to sanitize sensitive data before logging
 * Masks passwords, tokens, and other sensitive fields
 */

export class DataSanitizer {
  private static readonly SENSITIVE_FIELDS = [
    'password',
    'passwordHash',
    'password_hash',
    'accessToken',
    'access_token',
    'refreshToken',
    'refresh_token',
    'token',
    'apiKey',
    'api_key',
    'secret',
    'creditCard',
    'credit_card',
    'ssn',
    'pin',
  ];

  /**
   * Sanitizes an object by masking sensitive fields
   * @param obj The object to sanitize
   * @param depth Maximum depth to traverse (prevents infinite recursion)
   * @returns A new object with sensitive fields masked
   */
  static sanitize(obj: any, depth: number = 5): any {
    if (depth === 0 || obj === null || obj === undefined) {
      return obj;
    }

    if (typeof obj !== 'object') {
      return obj;
    }

    if (Array.isArray(obj)) {
      return obj.map(item => this.sanitize(item, depth - 1));
    }

    const sanitized: any = {};
    for (const key in obj) {
      if (Object.prototype.hasOwnProperty.call(obj, key)) {
        if (this.isSensitiveField(key)) {
          sanitized[key] = '***REDACTED***';
        } else if (typeof obj[key] === 'object' && obj[key] !== null) {
          sanitized[key] = this.sanitize(obj[key], depth - 1);
        } else {
          sanitized[key] = obj[key];
        }
      }
    }
    return sanitized;
  }

  /**
   * Checks if a field name is considered sensitive
   * @param fieldName The field name to check
   * @returns True if the field is sensitive
   */
  private static isSensitiveField(fieldName: string): boolean {
    const lowerField = fieldName.toLowerCase();
    return this.SENSITIVE_FIELDS.some(
      sensitive => lowerField.includes(sensitive.toLowerCase()),
    );
  }

  /**
   * Sanitizes a string for logging (e.g., email, username with partial masking)
   * @param value The value to sanitize
   * @param type The type of value (email, phone, etc.)
   * @returns A partially masked string
   */
  static sanitizeString(value: string, type: 'email' | 'phone' | 'default' = 'default'): string {
    if (!value) return value;

    if (type === 'email') {
      const [local, domain] = value.split('@');
      if (local && domain) {
        return `${local.substring(0, 2)}***@${domain}`;
      }
    }

    if (type === 'phone') {
      return `***-***-${value.slice(-4)}`;
    }

    // Default: show first 2 and last 2 characters
    if (value.length > 4) {
      return `${value.substring(0, 2)}***${value.substring(value.length - 2)}`;
    }

    return '***';
  }
}
