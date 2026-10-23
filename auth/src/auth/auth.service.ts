import { Injectable, BadRequestException, ConflictException, UnauthorizedException, OnModuleInit } from '@nestjs/common';
import * as bcrypt from 'bcrypt';
import { v4 as uuidv4 } from 'uuid';
import { OAuth2Client } from 'google-auth-library';
import { JwtService } from '@nestjs/jwt';
import { TokenService } from './token.service';
import { DatabaseService } from './database.service';
import { RegisterDto, LoginDto } from '../dto/auth.dto';
import { UsersRepository } from './repositories/users.repository';
import { SecureLoggerService } from '../common/services/secure-logger.service';

const DUMMY_PASSWORD_HASH =
  '$2b$12$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy';

@Injectable()
export class AuthService implements OnModuleInit {
  private readonly bcryptCostFactor: number;
  private readonly logger = new SecureLoggerService(AuthService.name);

  constructor(
    private tokenService: TokenService,
    private usersRepository: UsersRepository,
    private databaseService: DatabaseService,
    private jwtService: JwtService,
  ) {
    // Read cost factor from environment variable or default to 12
    this.bcryptCostFactor = parseInt(process.env.BCRYPT_COST_FACTOR || '12', 10);
  }

  getGoogleAuthorizationUrl(): string {
    const client = this.googleClient();
    const state = this.jwtService.sign(
      { purpose: 'google-oauth', nonce: uuidv4() },
      { expiresIn: '10m' },
    );

    return client.generateAuthUrl({
      access_type: 'offline',
      scope: ['openid', 'email', 'profile'],
      state,
      prompt: 'select_account',
    });
  }

  async loginWithGoogle(code: string, state: string): Promise<any> {
    const statePayload = this.jwtService.verify<{ purpose?: string }>(state);
    if (statePayload.purpose !== 'google-oauth') {
      throw new UnauthorizedException('Invalid Google sign-in state');
    }

    const client = this.googleClient();
    const { tokens } = await client.getToken(code);
    if (!tokens.id_token) {
      throw new UnauthorizedException('Google did not return an identity token');
    }

    const ticket = await client.verifyIdToken({
      idToken: tokens.id_token,
      audience: process.env.GOOGLE_CLIENT_ID,
    });
    const payload = ticket.getPayload();
    const email = payload?.email?.trim().toLowerCase();

    if (!email || payload?.email_verified !== true) {
      throw new UnauthorizedException('Google email is not verified');
    }

    let user = await this.usersRepository.findByEmail(email);
    if (!user) {
      user = await this.createGoogleUser(email, payload.name || email);
    }

    const userWithAccounts =
      await this.usersRepository.findByUsernameWithCredentialAndTradingAccounts(user.username);
    const linkedAccount = userWithAccounts?.tradingAccounts?.[0];

    if (!linkedAccount) {
      throw new UnauthorizedException('No trading account linked to user');
    }

    const tokensResponse = this.tokenService.issue(
      user.username,
      linkedAccount.accountId,
      linkedAccount.role,
    );

    return {
      ...tokensResponse,
      username: user.username,
      accountId: linkedAccount.accountId,
      role: linkedAccount.role,
    };
  }

  private googleClient(): OAuth2Client {
    const { GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET, GOOGLE_REDIRECT_URI } = process.env;
    if (!GOOGLE_CLIENT_ID || !GOOGLE_CLIENT_SECRET || !GOOGLE_REDIRECT_URI) {
      throw new BadRequestException('Google sign-in is not configured');
    }

    return new OAuth2Client(
      GOOGLE_CLIENT_ID,
      GOOGLE_CLIENT_SECRET,
      GOOGLE_REDIRECT_URI,
    );
  }

  private async createGoogleUser(email: string, fullName: string) {
    const baseUsername = email.split('@')[0].replace(/[^a-zA-Z0-9_-]/g, '_').slice(0, 24) || 'google_user';
    let username = baseUsername.length >= 3 ? baseUsername : `${baseUsername}_user`;
    let suffix = 0;

    while (await this.usersRepository.findByUsername(username)) {
      suffix += 1;
      username = `${baseUsername.slice(0, 30 - String(suffix).length - 1)}_${suffix}`;
    }

    const accountId = `ACC-${uuidv4().slice(0, 8).toUpperCase()}`;
    const passwordHash = await bcrypt.hash(uuidv4(), this.bcryptCostFactor);
    let accountCreated = false;

    try {
      await this.databaseService.createAccount(accountId, fullName);
      accountCreated = true;
      return await this.usersRepository.createUserWithCredentialAndTradingLink({
        username,
        email,
        fullName,
        passwordHash,
        accountId,
        role: 'CLIENT',
      });
    } catch (error) {
      if (accountCreated) {
        await this.compensateCreatedAccount(accountId);
      }
      throw error;
    }
  }

  async onModuleInit() {
    const testUserExists = await this.usersRepository.findByUsername('testuser');

    if (!testUserExists) {
      const hashedPassword = await bcrypt.hash('testpass', this.bcryptCostFactor);
      const accountId = `ACC-${uuidv4().slice(0, 8).toUpperCase()}`;
      let accountCreated = false;

      try {
        await this.databaseService.createAccount(accountId, 'Test User');
        accountCreated = true;

        await this.usersRepository.createUserWithCredentialAndTradingLink({
          username: 'testuser',
          email: 'test@example.com',
          passwordHash: hashedPassword,
          accountId,
          role: 'CLIENT',
        });

        this.logger.log('Default test user created: testuser/testpass', undefined, {
          username: 'testuser',
          accountId,
        });
      } catch (error) {
        this.logger.error('Failed to create default test user', String(error));
        if (accountCreated) {
          await this.compensateCreatedAccount(accountId);
        }
      }
    }
  }

  async register(registerDto: RegisterDto): Promise<any> {
    const { username, email, password, fullName } = registerDto;
    const normalizedEmail = email.trim().toLowerCase();

    this.logger.log('Attempting to register user', undefined, { username, email });

    const existingUser = await this.usersRepository.findByUsername(username);
    if (existingUser) {
      this.logger.warn(`Registration failed: user already exists`, undefined, { username });
      throw new ConflictException('Username or email already exists');
    }

    const existingEmail = await this.usersRepository.findByEmail(normalizedEmail);
    if (existingEmail) {
      this.logger.warn('Registration failed: email already exists', undefined, {
        email: normalizedEmail,
      });
      throw new ConflictException('Username or email already exists');
    }

    const hashedPassword = await bcrypt.hash(password, this.bcryptCostFactor);
    const accountId = `ACC-${uuidv4().slice(0, 8).toUpperCase()}`;
    const linkedRole: 'CLIENT' = 'CLIENT';
    let accountCreated = false;

    try {
      await this.databaseService.createAccount(accountId, fullName || username);
      accountCreated = true;

      const user = await this.usersRepository.createUserWithCredentialAndTradingLink({
        username,
        email: normalizedEmail,
        fullName,
        passwordHash: hashedPassword,
        accountId,
        role: linkedRole,
      });

      this.logger.logAuthEvent('REGISTER_SUCCESS', username);

      return {
        userId: user.userId,
        username: user.username,
        email: user.email,
        accountId,
        role: linkedRole,
        createdAt: user.createdAt,
      };
    } catch (error: unknown) {
      this.logger.error(`Registration failed for user ${username}:`, String(error));

      if (accountCreated) {
        await this.compensateCreatedAccount(accountId);
      }

      throw new BadRequestException('Registration failed. Please try again.');
    }
  }

  async login(loginDto: LoginDto): Promise<any> {
    const { username, password } = loginDto;

    this.logger.log(`Login attempt`, undefined, { username });

    // Find user in database with credential
    const user =
      await this.usersRepository.findByUsernameWithCredentialAndTradingAccounts(username);

    if (!user) {
      await bcrypt.compare(password, DUMMY_PASSWORD_HASH);

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

    // Generate tokens and include account metadata in response body.
    const tokens = this.tokenService.issue(username, linkedAccount.accountId, linkedAccount.role);
    return {
      ...tokens,
      username,
      accountId: linkedAccount.accountId,
      role: linkedAccount.role,
    };
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

    if (!decoded?.username || !decoded.accountId) {
      throw new UnauthorizedException('Invalid token payload');
    }

    const user =
      await this.usersRepository.findByUsernameWithCredentialAndTradingAccounts(
        decoded.username
      );

    const linkedAccount = user?.tradingAccounts?.find(
      account => account.accountId === decoded.accountId
    );

    if (!user || user.status !== 'ACTIVE' || !linkedAccount) {
      throw new UnauthorizedException('Session is no longer valid');
    }

    return {
      username: user.username,
      accountId: linkedAccount.accountId,
      role: linkedAccount.role,
      valid: true
    };
  }

  async deleteUser(userId: number): Promise<{ message: string }> {
    throw new BadRequestException('User deletion is not implemented yet');
  }

  private async compensateCreatedAccount(accountId: string): Promise<void> {
    try {
      await this.databaseService.deleteAccount(accountId);
      this.logger.warn('Compensation succeeded: deleted dangling trading account', undefined, { accountId });
    } catch (deleteError) {
      this.logger.error('Compensation delete failed, attempting closeAccount fallback', String(deleteError), accountId);
      try {
        await this.databaseService.closeAccount(accountId);
        this.logger.warn('Compensation fallback succeeded: account closed', undefined, { accountId });
      } catch (closeError) {
        this.logger.error('Compensation fallback failed: account may remain dangling', String(closeError), accountId);
      }
    }
  }
}
