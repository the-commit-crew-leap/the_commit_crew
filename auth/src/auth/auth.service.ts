import { Injectable, BadRequestException, UnauthorizedException } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { TokenService } from './token.service';
import { PasswordHasherService } from '../credentials/password-hasher.service';
import { SanitizedLogger } from '../common/logger/sanitized.logger';
import { RegisterDto, LoginDto } from '../dto/auth.dto';
import { User } from './entities/user.entity';

@Injectable()
export class AuthService {
  constructor(
    @InjectRepository(User)
    private readonly usersRepository: Repository<User>,
    private readonly tokenService: TokenService,
    private readonly passwordHasher: PasswordHasherService,
    private readonly logger: SanitizedLogger,
  ) {}

  /**
   * Registers a new user with bcrypt-hashed password (cost 12+).
   * 
   * Security flow:
   * 1. Validate email and username are not already registered
   * 2. Hash password using PasswordHasherService (bcrypt, cost 12+)
   * 3. Save only the hash to the database (never plaintext)
   * 4. Return user info without password hash
   */
  async register(registerDto: RegisterDto): Promise<any> {
    const { username, email, password } = registerDto;

    // Check if user already exists (by username or email)
    const existingUser = await this.usersRepository.findOne({
      where: [
        { username },
        { email },
      ],
    });

    if (existingUser) {
      throw new BadRequestException(
        existingUser.username === username
          ? 'Username already taken'
          : 'Email already registered',
      );
    }

    // Hash password using bcrypt with cost 12+ (via PasswordHasherService)
    const passwordHash = await this.passwordHasher.hashPassword(password);

    // Create and save user entity (only hash, never plaintext)
    const user = this.usersRepository.create({
      username,
      email,
      passwordHash,
    });

    const savedUser = await this.usersRepository.save(user);

    this.logger.log(`User registered successfully: ${savedUser.username}`);

    // Return user info without password hash
    return {
      id: savedUser.id,
      username: savedUser.username,
      email: savedUser.email,
      createdAt: savedUser.createdAt,
    };
  }

  /**
   * Authenticates user by verifying password against stored hash.
   * 
   * Security flow:
   * 1. Find user by username
   * 2. Fetch password hash (explicitly, since select: false)
   * 3. Verify plaintext password against hash using bcrypt.compare()
   * 4. Return tokens if valid
   */
  async login(loginDto: LoginDto): Promise<any> {
    const { username, password } = loginDto;

    // Find user (password hash is not selected by default)
    const user = await this.usersRepository
      .createQueryBuilder('user')
      .addSelect('user.passwordHash')
      .where('user.username = :username', { username })
      .getOne();

    if (!user) {
      throw new UnauthorizedException('Invalid credentials');
    }

    // Verify password using bcrypt.compare() (constant-time comparison)
    const isValid = await this.passwordHasher.verifyPassword(
      password,
      user.passwordHash,
    );

    if (!isValid) {
      throw new UnauthorizedException('Invalid credentials');
    }

    this.logger.log(`User logged in successfully: ${user.username}`);

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