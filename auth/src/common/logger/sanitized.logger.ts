import { Injectable, Logger } from '@nestjs/common';

/**
 * Sanitized logger that prevents passwords and sensitive data from reaching logs.
 * 
 * This logger intercepts all log messages and removes:
 * - password, plaintext, pwd, secret, token, authorization, bearer
 * - Any values that look like they might be credentials
 * 
 * Security requirement: "No password reaches a log by a direct route"
 */
@Injectable()
export class SanitizedLogger extends Logger {
  private readonly SENSITIVE_KEYS = [
    'password',
    'plaintext',
    'pwd',
    'secret',
    'token',
    'authorization',
    'bearer',
    'apikey',
    'api_key',
    'credentials',
    'hash',
    'salt',
  ];

  /**
   * Sanitizes an object or string to remove sensitive data.
   * 
   * @param data - The data to sanitize
   * @returns Sanitized version of the data
   */
  private sanitize(data: any): any {
    if (data === null || data === undefined) {
      return data;
    }

    // Handle strings
    if (typeof data === 'string') {
      return this.sanitizeString(data);
    }

    // Handle objects
    if (typeof data === 'object') {
      if (Array.isArray(data)) {
        return data.map((item) => this.sanitize(item));
      }

      const sanitized: any = {};
      for (const [key, value] of Object.entries(data)) {
        if (this.isSensitiveKey(key)) {
          sanitized[key] = '[REDACTED]';
        } else {
          sanitized[key] = this.sanitize(value);
        }
      }
      return sanitized;
    }

    return data;
  }

  /**
   * Checks if a key is sensitive.
   */
  private isSensitiveKey(key: string): boolean {
    const lowerKey = key.toLowerCase();
    return this.SENSITIVE_KEYS.some((sensitive) => lowerKey.includes(sensitive));
  }

  /**
   * Sanitizes a string by removing common password patterns.
   */
  private sanitizeString(str: string): string {
    // Remove common patterns like password=xxx, pwd=xxx, etc.
    const patterns = [
      /password["\s:=]+[^,}\s"]*/gi,
      /pwd["\s:=]+[^,}\s"]*/gi,
      /secret["\s:=]+[^,}\s"]*/gi,
      /token["\s:=]+[^,}\s"]*/gi,
      /bearer\s+[^\s]*/gi,
      /authorization["\s:=]+[^,}\s"]*/gi,
    ];

    let sanitized = str;
    patterns.forEach((pattern) => {
      sanitized = sanitized.replace(pattern, (match) => {
        const key = match.split(/[=:"\s]/)[0];
        return `${key}=[REDACTED]`;
      });
    });

    return sanitized;
  }

  /**
   * Logs a message at the LOG level.
   */
  log(message: string, context?: string): void {
    super.log(this.sanitize(message), context);
  }

  /**
   * Logs an error message.
   */
  error(message: string, trace?: string, context?: string): void {
    super.error(this.sanitize(message), this.sanitize(trace), context);
  }

  /**
   * Logs a warning message.
   */
  warn(message: string, context?: string): void {
    super.warn(this.sanitize(message), context);
  }

  /**
   * Logs a debug message.
   */
  debug(message: string, context?: string): void {
    super.debug(this.sanitize(message), context);
  }

  /**
   * Logs a verbose message.
   */
  verbose(message: string, context?: string): void {
    super.verbose(this.sanitize(message), context);
  }
}
