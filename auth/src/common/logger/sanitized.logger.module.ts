import { Module } from '@nestjs/common';
import { SanitizedLogger } from './sanitized.logger';

/**
 * Logger module providing sanitized logging.
 * 
 * Ensures no passwords or sensitive data reach application logs.
 */
@Module({
  providers: [SanitizedLogger],
  exports: [SanitizedLogger],
})
export class SanitizedLoggerModule {}
