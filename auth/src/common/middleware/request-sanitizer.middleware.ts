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

    // Optionally override res.json to sanitize outgoing responses
    const originalJson = res.json.bind(res);
    res.json = function(data: any) {
      // Only sanitize if response status is not an error (errors may need full details)
      if (res.statusCode >= 400) {
        return originalJson(data);
      }
      const sanitized = DataSanitizer.sanitize(data);
      return originalJson(sanitized);
    };

    next();
  }
}
