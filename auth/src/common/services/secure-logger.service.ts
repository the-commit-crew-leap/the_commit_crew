import { Injectable, Logger } from '@nestjs/common';
import { DataSanitizer } from '../utils/data-sanitizer.util';

/**
 * Secure Logger Service
 * Extends NestJS Logger to automatically sanitize sensitive data
 * Ensures passwords, tokens, and other sensitive data are never logged
 */
@Injectable()
export class SecureLoggerService extends Logger {
  /**
   * Logs a debug message with sanitized data
   * @param message The message to log
   * @param context The context of the log
   * @param data Optional data to log (will be sanitized)
   */
  debug(message: string, context?: string, data?: any): void {
    const sanitized = data ? DataSanitizer.sanitize(data) : null;
    super.debug(
      sanitized ? `${message} | ${JSON.stringify(sanitized)}` : message,
      context,
    );
  }

  /**
   * Logs an info message with sanitized data
   * @param message The message to log
   * @param context The context of the log
   * @param data Optional data to log (will be sanitized)
   */
  log(message: string, context?: string, data?: any): void {
    const sanitized = data ? DataSanitizer.sanitize(data) : null;
    super.log(
      sanitized ? `${message} | ${JSON.stringify(sanitized)}` : message,
      context,
    );
  }

  /**
   * Logs a warning message with sanitized data
   * @param message The message to log
   * @param context The context of the log
   * @param data Optional data to log (will be sanitized)
   */
  warn(message: string, context?: string, data?: any): void {
    const sanitized = data ? DataSanitizer.sanitize(data) : null;
    super.warn(
      sanitized ? `${message} | ${JSON.stringify(sanitized)}` : message,
      context,
    );
  }

  /**
   * Logs an error message with sanitized data
   * @param message The message to log
   * @param trace Optional stack trace
   * @param context The context of the log
   * @param data Optional data to log (will be sanitized)
   */
  error(message: string, trace?: string, context?: string, data?: any): void {
    const sanitized = data ? DataSanitizer.sanitize(data) : null;
    super.error(
      sanitized ? `${message} | ${JSON.stringify(sanitized)}` : message,
      trace,
      context,
    );
  }

  /**
   * Logs an authentication event (login, register, etc.) with partial masking
   * Useful for audit trails without exposing sensitive data
   * @param event The authentication event (e.g., 'LOGIN_SUCCESS', 'LOGIN_FAILED')
   * @param username The username (optional, will be partially masked)
   * @param additionalData Any additional data to log (will be sanitized)
   */
  logAuthEvent(event: string, username?: string, additionalData?: any): void {
    const maskedUsername = username ? DataSanitizer.sanitizeString(username) : 'UNKNOWN';
    const sanitized = additionalData ? DataSanitizer.sanitize(additionalData) : null;
    const message = sanitized
      ? `${event} | username: ${maskedUsername} | ${JSON.stringify(sanitized)}`
      : `${event} | username: ${maskedUsername}`;
    this.log(message, 'AuthEvents');
  }
}
