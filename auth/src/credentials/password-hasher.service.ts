import { Injectable } from '@nestjs/common';
import * as bcrypt from 'bcrypt';
import { ConfigService } from '@nestjs/config';
import { SanitizedLogger } from '../common/logger/sanitized.logger';

/**
 * Service for hashing and verifying passwords using bcrypt.
 * 
 * Security guarantees:
 * - Passwords are never stored in plaintext
 * - Bcrypt generates and manages salts automatically
 * - Cost factor is configurable and defensible
 * - Uses bcrypt at cost 12 or above (industry standard)
 * 
 * @example
 * const hash = await this.passwordHasher.hashPassword('myPassword123');
 * const isValid = await this.passwordHasher.verifyPassword('myPassword123', hash);
 */
@Injectable()
export class PasswordHasherService {
  private readonly costFactor: number;

  constructor(
    private readonly configService: ConfigService,
    private readonly logger: SanitizedLogger,
  ) {
    this.costFactor = this.configService.get<number>('BCRYPT_COST_FACTOR', 12);
    
    if (this.costFactor < 12) {
      throw new Error(
        `BCRYPT_COST_FACTOR must be 12 or above for security. Current: ${this.costFactor}. ` +
        `Cost 12 = ~40-80ms per hash. See .env.example for more details.`,
      );
    }

    this.logger.log(
      `PasswordHasherService initialized with bcrypt cost factor: ${this.costFactor}`,
    );
  }

  /**
   * Hashes a plaintext password using bcrypt.
   * 
   * @param plaintext - The plaintext password to hash
   * @returns A promise that resolves to the bcrypt hash (includes salt)
   * @throws Error if plaintext is empty or invalid
   * 
   * @remarks
   * - Bcrypt automatically generates a salt and includes it in the hash
   * - Hash format: $2b$costFactor$salt$hash
   * - The hash includes all information needed to verify the password
   */
  async hashPassword(plaintext: string): Promise<string> {
    if (!plaintext || plaintext.trim().length === 0) {
      throw new Error('Password cannot be empty');
    }

    try {
      const hash = await bcrypt.hash(plaintext, this.costFactor);
      this.logger.debug(
        `Password hashed successfully using bcrypt cost factor ${this.costFactor}`,
      );
      return hash;
    } catch (error) {
      this.logger.error('Error hashing password', error instanceof Error ? error.stack : String(error));
      throw error;
    }
  }

  /**
   * Verifies a plaintext password against a bcrypt hash.
   * 
   * @param plaintext - The plaintext password to verify
   * @param hash - The bcrypt hash to compare against
   * @returns A promise that resolves to true if the password matches, false otherwise
   * 
   * @remarks
   * - This performs a constant-time comparison to prevent timing attacks
   * - Never logs the plaintext password or comparison result
   */
  async verifyPassword(plaintext: string, hash: string): Promise<boolean> {
    if (!plaintext || !hash) {
      this.logger.warn('Password or hash is missing during verification');
      return false;
    }

    try {
      const isMatch = await bcrypt.compare(plaintext, hash);
      this.logger.debug('Password verification completed');
      return isMatch;
    } catch (error) {
      this.logger.error('Error verifying password', error instanceof Error ? error.stack : String(error));
      return false;
    }
  }

  /**
   * Gets the currently configured bcrypt cost factor.
   * Used for documentation and testing purposes.
   * 
   * @returns The bcrypt cost factor (12 or above)
   */
  getCostFactor(): number {
    return this.costFactor;
  }
}
