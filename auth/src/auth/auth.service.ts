import { Injectable, BadRequestException, UnauthorizedException, OnModuleInit } from '@nestjs/common';
import * as bcrypt from 'bcrypt';
import { TokenService } from './token.service';
import { RegisterDto, LoginDto } from '../dto/auth.dto';

@Injectable()
export class AuthService implements OnModuleInit {
  // Mock user storage (replace with database later)
  private users: Map<string, any> = new Map();

  constructor(private tokenService: TokenService) {}

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

    // Check if user exists
    if (this.users.has(username)) {
      throw new BadRequestException('User already exists');
    }

    // Hash password
    const hashedPassword = await bcrypt.hash(password, 10);

    // Store user
    const user = {
      id: this.users.size + 1,
      username,
      email,
      password: hashedPassword,
      createdAt: new Date(),
    };

    this.users.set(username, user);
    return { id: user.id, username, email, createdAt: user.createdAt };
  }

  async login(loginDto: LoginDto): Promise<any> {
    const { username, password } = loginDto;

    // Find user
    const user = this.users.get(username);
    if (!user) {
      throw new UnauthorizedException('Invalid credentials');
    }

    // Verify password
    const isValid = await bcrypt.compare(password, user.password);
    if (!isValid) {
      throw new UnauthorizedException('Invalid credentials');
    }

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