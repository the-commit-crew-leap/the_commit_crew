import { Injectable, NestMiddleware, Logger } from '@nestjs/common';
import { Request, Response, NextFunction } from 'express';
import { DataSanitizer } from '../utils/data-sanitizer.util';

/**
 * Request Sanitizer Middleware
 * Sanitizes incoming request bodies at the HTTP level
 * Logs incoming requests without exposing sensitive data
 */
@Injectable()
export class RequestSanitizerMiddleware implements NestMiddleware {
  private logger = new Logger('RequestSanitizer');

  use(req: Request, res: Response, next: NextFunction): void {
    // Log the incoming request with sanitized body
    const method = req.method;
    const path = req.path;
    const sanitizedBody = DataSanitizer.sanitize(req.body);

    this.logger.log(
      `Incoming ${method} request to ${path} | ${JSON.stringify(sanitizedBody)}`,
    );

    // Do NOT sanitize outgoing responses - they need to include tokens for the client
    // Sanitization is only for logging, not API responses

    next();
  }
}
