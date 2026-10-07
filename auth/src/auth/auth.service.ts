import { Injectable, BadRequestException, UnauthorizedException } from '@nestjs/common';
import * as bcrypt from 'bcrypt';
import { TokenService } from './token.service';
import { RegisterDto, LoginDto } from '../dto/auth.dto';
import { UsersRepository } from './repositories/users.repository';

@Injectable()
export class AuthService {
  private readonly bcryptCostFactor: number;

  constructor(
    private tokenService: TokenService,
    private usersRepository: UsersRepository,
  ) {
    // Read cost factor from environment variable or default to 12
    this.bcryptCostFactor = parseInt(process.env.BCRYPT_COST_FACTOR || '12', 10);
  }

  async register(registerDto: RegisterDto): Promise<any> {
    const { username, email, password } = registerDto;

    // Check if user exists
    const existingUser = await this.usersRepository.findByUsername(username);
    if (existingUser) {
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

    return {
      user_id: user.user_id,
      username: user.username,
      email: user.email,
      created_at: user.created_at,
    };
  }

  async login(loginDto: LoginDto): Promise<any> {
    const { username, password } = loginDto;

    // Find user in database
    const user = await this.usersRepository.findByUsernameWithCredential(username);
    if (!user) {
      throw new UnauthorizedException('Invalid credentials');
    }

    // Check if account is locked
    if (user.credential?.locked_until && new Date() < user.credential.locked_until) {
      throw new UnauthorizedException('Account is locked. Please try again later.');
    }

    // Verify password
    const isValid = await bcrypt.compare(password, user.credential?.password_hash || '');
    if (!isValid) {
      // Increment failed login attempts
      await this.usersRepository.incrementFailedLoginAttempts(user.user_id);
      throw new UnauthorizedException('Invalid credentials');
    }

    // Reset failed login attempts on successful login
    await this.usersRepository.resetFailedLoginAttempts(user.user_id);
    await this.usersRepository.updateLastLogin(user.user_id);

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