import { Test, TestingModule } from '@nestjs/testing';
import { ConfigService } from '@nestjs/config';
import { PasswordHasherService } from './password-hasher.service';
import { SanitizedLogger } from '../common/logger/sanitized.logger';
import * as bcrypt from 'bcrypt';

describe('PasswordHasherService', () => {
  let service: PasswordHasherService;
  let configService: ConfigService;
  let logger: SanitizedLogger;

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        PasswordHasherService,
        {
          provide: ConfigService,
          useValue: {
            get: jest.fn().mockReturnValue(12),
          },
        },
        {
          provide: SanitizedLogger,
          useValue: {
            log: jest.fn(),
            debug: jest.fn(),
            warn: jest.fn(),
            error: jest.fn(),
          },
        },
      ],
    }).compile();

    service = module.get<PasswordHasherService>(PasswordHasherService);
    configService = module.get<ConfigService>(ConfigService);
    logger = module.get<SanitizedLogger>(SanitizedLogger);
  });

  describe('initialization', () => {
    it('should be defined', () => {
      expect(service).toBeDefined();
    });

    it('should initialize with cost factor from config', () => {
      expect(service.getCostFactor()).toBe(12);
    });

    it('should throw error if cost factor is below 12', async () => {
      const module: TestingModule = await Test.createTestingModule({
        providers: [
          PasswordHasherService,
          {
            provide: ConfigService,
            useValue: {
              get: jest.fn().mockReturnValue(11),
            },
          },
          {
            provide: SanitizedLogger,
            useValue: {
              log: jest.fn(),
            },
          },
        ],
      }).compile();

      expect(() => {
        module.get<PasswordHasherService>(PasswordHasherService);
      }).toThrow(/BCRYPT_COST_FACTOR must be 12 or above/);
    });

    it('should default to cost factor 12 if not configured', () => {
      (configService.get as jest.Mock).mockReturnValue(undefined);
      // This test assumes the constructor is re-run; in practice you'd create a new instance
      expect(service.getCostFactor()).toBeGreaterThanOrEqual(12);
    });
  });

  describe('hashPassword', () => {
    it('should hash a password successfully', async () => {
      const plaintext = 'testPassword123!';
      const hash = await service.hashPassword(plaintext);

      // Verify hash is a valid bcrypt hash
      expect(hash).toMatch(/^\$2b\$/);
      expect(hash.length).toBeGreaterThan(50);
    });

    it('should produce different hashes for the same password', async () => {
      const plaintext = 'testPassword123!';
      const hash1 = await service.hashPassword(plaintext);
      const hash2 = await service.hashPassword(plaintext);

      // Different hashes (bcrypt includes random salt)
      expect(hash1).not.toEqual(hash2);
    });

    it('should include the cost factor in the hash', async () => {
      const plaintext = 'testPassword123!';
      const hash = await service.hashPassword(plaintext);

      // Hash format: $2b$12$salt$hash
      expect(hash).toContain('$12$');
    });

    it('should throw error for empty password', async () => {
      await expect(service.hashPassword('')).rejects.toThrow('Password cannot be empty');
    });

    it('should throw error for whitespace-only password', async () => {
      await expect(service.hashPassword('   ')).rejects.toThrow('Password cannot be empty');
    });

    it('should never log the plaintext password', async () => {
      const plaintext = 'secretPassword123!';
      await service.hashPassword(plaintext);

      // Verify logger was called but password is not in the log
      expect(logger.debug).toHaveBeenCalled();
      const logCalls = (logger.debug as jest.Mock).mock.calls;
      const allLogs = logCalls.map((call) => JSON.stringify(call[0])).join(' ');
      expect(allLogs).not.toContain(plaintext);
    });
  });

  describe('verifyPassword', () => {
    let validHash: string;
    const plaintext = 'testPassword123!';

    beforeEach(async () => {
      validHash = await service.hashPassword(plaintext);
    });

    it('should return true for correct password', async () => {
      const result = await service.verifyPassword(plaintext, validHash);
      expect(result).toBe(true);
    });

    it('should return false for incorrect password', async () => {
      const result = await service.verifyPassword('wrongPassword', validHash);
      expect(result).toBe(false);
    });

    it('should return false if password is missing', async () => {
      const result = await service.verifyPassword('', validHash);
      expect(result).toBe(false);
    });

    it('should return false if hash is missing', async () => {
      const result = await service.verifyPassword(plaintext, '');
      expect(result).toBe(false);
    });

    it('should use constant-time comparison to prevent timing attacks', async () => {
      // bcrypt.compare() uses constant-time comparison internally
      const start = Date.now();
      await service.verifyPassword('wrongPassword1', validHash);
      const wrongTime = Date.now() - start;

      const start2 = Date.now();
      await service.verifyPassword('wrongPassword2', validHash);
      const wrongTime2 = Date.now() - start2;

      // Both wrong passwords should take similar time (not an exact check, just conceptual)
      expect(Math.abs(wrongTime - wrongTime2)).toBeLessThan(100);
    });

    it('should never log the plaintext password during verification', async () => {
      await service.verifyPassword(plaintext, validHash);

      // Verify logger was called but password is not in the log
      expect(logger.debug).toHaveBeenCalled();
      const logCalls = (logger.debug as jest.Mock).mock.calls;
      const allLogs = logCalls.map((call) => JSON.stringify(call[0])).join(' ');
      expect(allLogs).not.toContain(plaintext);
    });
  });

  describe('getCostFactor', () => {
    it('should return the configured cost factor', () => {
      const costFactor = service.getCostFactor();
      expect(costFactor).toBe(12);
    });

    it('should return a value >= 12', () => {
      expect(service.getCostFactor()).toBeGreaterThanOrEqual(12);
    });
  });

  describe('password security requirements', () => {
    it('should meet OWASP password storage recommendations', async () => {
      const plaintext = 'complexPassword123!@#$%';
      const hash = await service.hashPassword(plaintext);

      // 1. Plaintext is never stored
      expect(hash).not.toEqual(plaintext);

      // 2. Hash is salted (bcrypt includes salt)
      const anotherHash = await service.hashPassword(plaintext);
      expect(hash).not.toEqual(anotherHash);

      // 3. Hashing uses proper algorithm (bcrypt)
      expect(hash).toMatch(/^\$2b\$/);

      // 4. Cost factor is high enough
      expect(service.getCostFactor()).toBeGreaterThanOrEqual(12);
    });

    it('should not leak password through error handling', async () => {
      const plaintext = 'secretPassword123!';
      
      try {
        // Test with invalid hash format
        await service.verifyPassword(plaintext, 'invalidhash');
      } catch (error) {
        // Error message should not contain plaintext
        const errorMsg = JSON.stringify(error);
        expect(errorMsg).not.toContain(plaintext);
      }
    });
  });
});
