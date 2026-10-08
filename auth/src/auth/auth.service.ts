import { Injectable, BadRequestException, UnauthorizedException, OnModuleInit } from '@nestjs/common';
import * as bcrypt from 'bcrypt';
import { TokenService } from './token.service';
import { RegisterDto, LoginDto } from '../dto/auth.dto';
import { UsersRepository } from './repositories/users.repository';
import { SecureLoggerService } from '../common/services/secure-logger.service';

@Injectable()
export class AuthService implements OnModuleInit {
  private readonly bcryptCostFactor: number;
  private readonly logger = new SecureLoggerService(AuthService.name);
  private users: Map<string, any> = new Map();

  constructor(
    private tokenService: TokenService,
    private usersRepository: UsersRepository,
  ) {
    // Read cost factor from environment variable or default to 12
    this.bcryptCostFactor = parseInt(process.env.BCRYPT_COST_FACTOR || '12', 10);
  }

  async onModuleInit() {
    // Default test user for development
    const hashedPassword = await bcrypt.hash('testpass', 10);
    this.users.set('testuser', {
      id: 1,
      username: 'testuser',
      email: 'test@example.com',
      password: hashedPassword,
      createdAt: new Date(),
    });
    console.log('Default test user created: testuser/testpass');
  }

  async register(registerDto: RegisterDto): Promise<any> {
    const { username, email, password } = registerDto;

    this.logger.log(`Attempting to register user`, undefined, { username, email });

    // Check if user exists
    const existingUser = await this.usersRepository.findByUsername(username);
    if (existingUser) {
      this.logger.warn(`Registration failed: user already exists`, undefined, { username });
      throw new BadRequestException('User already exists');
    }

    // Hash password with configured cost factor
    const hashedPassword = await bcrypt.hash(password, this.bcryptCostFactor);

    // Store user and credentials in database
    const user = await this.usersRepository.createUser({
      username,
      email,
      passwordHash: hashedPassword,
    });

    this.logger.logAuthEvent('REGISTER_SUCCESS', username);

    return {
      user_id: user.user_id,
      username: user.username,
      email: user.email,
      created_at: user.created_at,
    };
  }

  async login(loginDto: LoginDto): Promise<any> {
    const { username, password } = loginDto;

    this.logger.log(`Login attempt`, undefined, { username });

    // Find user in database
    const user = await this.usersRepository.findByUsernameWithCredential(username);
    if (!user) {
      this.logger.warn(`Login failed: user not found`, undefined, { username });
      throw new UnauthorizedException('Invalid credentials');
    }

    // Check if account is locked
    if (user.credential?.locked_until && new Date() < user.credential.locked_until) {
      this.logger.warn(`Login failed: account locked`, undefined, { username });
      throw new UnauthorizedException('Account is locked. Please try again later.');
    }

    // Verify password
    const isValid = await bcrypt.compare(password, user.credential?.password_hash || '');
    if (!isValid) {
      // Increment failed login attempts
      await this.usersRepository.incrementFailedLoginAttempts(user.user_id);
      this.logger.warn(
        `Login failed: invalid password`,
        undefined,
        { username, attempt: user.credential?.failed_login_attempts },
      );
      throw new UnauthorizedException('Invalid credentials');
    }

    // Reset failed login attempts on successful login
    await this.usersRepository.resetFailedLoginAttempts(user.user_id);
    await this.usersRepository.updateLastLogin(user.user_id);

    this.logger.logAuthEvent('LOGIN_SUCCESS', username);

    // Generate tokens
    return this.tokenService.issue(username);
  }

  async refresh(refreshToken: string): Promise<any> {
    if (!this.tokenService.verify(refreshToken)) {
      throw new UnauthorizedException('Invalid refresh token');
    }

    const decoded = this.tokenService.decode(refreshToken);
    return this.tokenService.issue(decoded.username);
  }

  async validate(token: string): Promise<any> {
    if (!this.tokenService.verify(token)) {
      throw new UnauthorizedException('Invalid token');
    }

    const decoded = this.tokenService.decode(token);
    return { username: decoded.username, valid: true };
  }
}