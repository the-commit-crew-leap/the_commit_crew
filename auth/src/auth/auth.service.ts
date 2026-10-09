import { Injectable, BadRequestException, UnauthorizedException, OnModuleInit } from '@nestjs/common';
import * as bcrypt from 'bcrypt';
import { v4 as uuidv4 } from 'uuid';
import { TokenService } from './token.service';
import { DatabaseService } from './database.service';
import { RegisterDto, LoginDto } from '../dto/auth.dto';
import { UsersRepository } from './repositories/users.repository';
import { SecureLoggerService } from '../common/services/secure-logger.service';

@Injectable()
export class AuthService implements OnModuleInit {
  private readonly bcryptCostFactor: number;
  private readonly logger = new SecureLoggerService(AuthService.name);

  constructor(
    private tokenService: TokenService,
    private usersRepository: UsersRepository,
    private databaseService: DatabaseService,
  ) {
    // Read cost factor from environment variable or default to 12
    this.bcryptCostFactor = parseInt(process.env.BCRYPT_COST_FACTOR || '12', 10);
  }

  async onModuleInit() {
    // Create default test user for development if it doesn't exist
    const testUserExists = await this.usersRepository.findByUsername('testuser');

    if (!testUserExists) {
      const hashedPassword = await bcrypt.hash('testpass', this.bcryptCostFactor);
      const accountId = `ACC-${uuidv4().slice(0, 8).toUpperCase()}`;

      try {
        // Create account in trading database first
        await this.databaseService.createAccount(accountId, 'Test User');

        // Create user with credentials in auth database
        const user = await this.usersRepository.createUser({
          username: 'testuser',
          email: 'test@example.com',
          passwordHash: hashedPassword,
        });

        await this.usersRepository.linkUserToTradingAccount(user.userId, accountId, 'CLIENT');

        // Note: Trading account linking would be done separately if needed
        this.logger.log('Default test user created: testuser/testpass', undefined, {
          username: 'testuser',
          accountId,
        });
      } catch (error) {
        this.logger.error('Failed to create default test user', String(error));
      }
    }
  }

  async register(registerDto: RegisterDto): Promise<any> {
    const { username, email, password, fullName } = registerDto;

    this.logger.log(`Attempting to register user`, undefined, { username, email });

    // Check if user already exists
    const existingUser = await this.usersRepository.findByUsername(username);
    if (existingUser) {
      this.logger.warn(`Registration failed: user already exists`, undefined, { username });
      throw new BadRequestException('User already exists');
    }

    // Hash password with configured cost factor
    const hashedPassword = await bcrypt.hash(password, this.bcryptCostFactor);

    // Generate unique account ID
    const accountId = `ACC-${uuidv4().slice(0, 8).toUpperCase()}`;

    try {
      // Create account in trading database FIRST
      await this.databaseService.createAccount(accountId, fullName || username);

      // Create user with credentials in auth database
      const user = await this.usersRepository.createUser({
        username,
        email,
        fullName,
        passwordHash: hashedPassword,
      });

      await this.usersRepository.linkUserToTradingAccount(user.userId, accountId, 'CLIENT');

      this.logger.logAuthEvent('REGISTER_SUCCESS', username);

      return {
        userId: user.userId,
        username: user.username,
        email: user.email,
        accountId,
        createdAt: user.createdAt,
      };
    } catch (error: unknown) {
      this.logger.error(`Registration failed for user ${username}:`, String(error));
      throw new BadRequestException('Registration failed. Please try again.');
    }
  }

  async login(loginDto: LoginDto): Promise<any> {
    const { username, password } = loginDto;

    this.logger.log(`Login attempt`, undefined, { username });

    // Find user in database with credential
    const user = await this.usersRepository.findByUsernameWithCredentialAndTradingAccounts(username);
    if (!user) {
      this.logger.warn(`Login failed: user not found`, undefined, { username });
      throw new UnauthorizedException('Invalid credentials');
    }

    // Check if account is locked due to failed login attempts
    if (user.credential?.lockedUntil && new Date() < user.credential.lockedUntil) {
      this.logger.warn(`Login failed: account locked`, undefined, { username });
      throw new UnauthorizedException('Account is locked. Please try again later.');
    }

    // Auto-reactivate temporary lockouts once the lock window has passed.
    if (user.credential?.lockedUntil && new Date() >= user.credential.lockedUntil) {
      await this.usersRepository.resetFailedLoginAttempts(user.userId);
      user.status = 'ACTIVE';
      user.credential.failedLoginAttempts = 0;
    }

    // Check if user account is active
    if (user.status !== 'ACTIVE') {
      this.logger.warn(`Login failed: user not active`, undefined, { username });
      throw new UnauthorizedException('User account is not active');
    }

    // Verify password against stored hash
    const isValid = await bcrypt.compare(password, user.credential?.passwordHash || '');
    if (!isValid) {
      // Increment failed login attempts
      await this.usersRepository.incrementFailedLoginAttempts(user.userId);
      this.logger.warn(
        `Login failed: invalid password`,
        undefined,
        { username, attempt: user.credential?.failedLoginAttempts },
      );
      throw new UnauthorizedException('Invalid credentials');
    }

    // Reset failed login attempts and update last login on successful login
    await this.usersRepository.resetFailedLoginAttempts(user.userId);
    await this.usersRepository.updateLastLogin(user.userId);

    this.logger.logAuthEvent('LOGIN_SUCCESS', username);

    const linkedAccount = user.tradingAccounts?.[0];

    if (!linkedAccount) {
      this.logger.warn(`Login failed: no linked trading account`, undefined, { username });
      throw new UnauthorizedException('No trading account linked to user');
    }

    // Generate tokens
    return this.tokenService.issue(username, linkedAccount.accountId, linkedAccount.role);
  }

  async refresh(refreshToken: string): Promise<any> {
    if (!this.tokenService.verify(refreshToken)) {
      throw new UnauthorizedException('Invalid refresh token');
    }

    const decoded = this.tokenService.decode(refreshToken) as
      | { username?: string; accountId?: string; role?: string }
      | null;

    if (!decoded?.username) {
      throw new UnauthorizedException('Invalid refresh token payload');
    }

    let accountId = decoded.accountId;
    let role = decoded.role;

    if (!accountId) {
      const user = await this.usersRepository.findByUsernameWithCredentialAndTradingAccounts(decoded.username);
      const linkedAccount = user?.tradingAccounts?.[0];
      accountId = linkedAccount?.accountId;
      role = linkedAccount?.role;
    }

    return this.tokenService.issue(decoded.username, accountId, role);
  }

  async validate(token: string): Promise<any> {
    if (!this.tokenService.verify(token)) {
      throw new UnauthorizedException('Invalid token');
    }

    const decoded = this.tokenService.decode(token) as
      | { username?: string; accountId?: string; role?: string }
      | null;

    if (!decoded?.username) {
      throw new UnauthorizedException('Invalid token payload');
    }

    let accountId = decoded.accountId;
    let role = decoded.role;

    if (!accountId) {
      const user = await this.usersRepository.findByUsernameWithCredentialAndTradingAccounts(decoded.username);
      const linkedAccount = user?.tradingAccounts?.[0];
      accountId = linkedAccount?.accountId;
      role = linkedAccount?.role;
    }

    return {
      username: decoded.username,
      accountId: accountId ?? null,
      role: role ?? null,
      valid: true,
    };
  }

  async deleteUser(userId: number): Promise<{ message: string }> {
    throw new BadRequestException('User deletion is not implemented yet');
  }
}
