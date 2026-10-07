import { Module } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import { PasswordHasherService } from './password-hasher.service';
import { SanitizedLogger } from '../common/logger/sanitized.logger';

/**
 * Credentials module encapsulates all credential-related services.
 * 
 * Provides:
 * - PasswordHasherService: Bcrypt hashing with cost 12+ and salt
 * - SanitizedLogger: Logger that prevents password leaks
 * 
 * Dependencies:
 * - ConfigModule: For reading BCRYPT_COST_FACTOR environment variable
 */
@Module({
  imports: [ConfigModule],
  providers: [PasswordHasherService, SanitizedLogger],
  exports: [PasswordHasherService, SanitizedLogger],
})
export class CredentialsModule {}
