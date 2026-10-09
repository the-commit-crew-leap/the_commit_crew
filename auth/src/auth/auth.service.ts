import { Injectable, BadRequestException, UnauthorizedException, OnModuleInit, Logger } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import * as bcrypt from 'bcrypt';
import { v4 as uuidv4 } from 'uuid';
import { TokenService } from './token.service';
import { DatabaseService } from './database.service';
import { RegisterDto, LoginDto } from '../dto/auth.dto';
import { User } from './entities/user.entity';
import { Credential } from './entities/credential.entity';
import { UserTradingAccount } from './entities/user-trading-account.entity';

@Injectable()
export class AuthService implements OnModuleInit {
  private readonly logger = new Logger(AuthService.name);

  constructor(
    @InjectRepository(User) private userRepository: Repository<User>,
    @InjectRepository(Credential) private credentialRepository: Repository<Credential>,
    @InjectRepository(UserTradingAccount)
    private userTradingAccountRepository: Repository<UserTradingAccount>,
    private tokenService: TokenService,
    private databaseService: DatabaseService,
  ) {}

  async onModuleInit() {
    // Create default test user if it doesn't exist
    const testUserExists = await this.userRepository.findOne({
      where: { username: 'testuser' },
    });

    if (!testUserExists) {
      const hashedPassword = await bcrypt.hash('testpass', 10);
      const accountId = `ACC-${uuidv4().slice(0, 8).toUpperCase()}`;

      try {
        // Create account in trading database
        await this.databaseService.createAccount(accountId, 'Test User Account');

        // Create user in auth database
        const user = this.userRepository.create({
          username: 'testuser',
          email: 'test@example.com',
          fullName: 'Test User',
          status: 'ACTIVE',
        });

        const savedUser = await this.userRepository.save(user);

        // Create credential record for the user
        const credential = this.credentialRepository.create({
          userId: savedUser.userId,
          passwordHash: hashedPassword,
          algorithm: 'bcrypt',
        });

        await this.credentialRepository.save(credential);

        // Link user to account
        const userTrading = this.userTradingAccountRepository.create({
          userId: savedUser.userId,
          accountId,
          role: 'CLIENT',
        });

        await this.userTradingAccountRepository.save(userTrading);

        this.logger.log('Default test user created: testuser/testpass');
      } catch (error) {
        this.logger.error('Failed to create default test user', error);
      }
    }
  }

  async register(registerDto: RegisterDto): Promise<any> {
    const { username, email, password } = registerDto;

    // Check if user exists
    const existingUser = await this.userRepository.findOne({
      where: [{ username }, { email }],
    });

    if (existingUser) {
      throw new BadRequestException('User already exists');
    }

    // Hash password
    const hashedPassword = await bcrypt.hash(password, 10);

    // Generate unique account ID
    const accountId = `ACC-${uuidv4().slice(0, 8).toUpperCase()}`;

    try {
      // Create account in trading database FIRST
      await this.databaseService.createAccount(accountId, username);

      // Create user in auth database
      const user = this.userRepository.create({
        username,
        email,
        status: 'ACTIVE',
      });

      const savedUser = await this.userRepository.save(user);

      // Create credential record for the user
      const credential = this.credentialRepository.create({
        userId: savedUser.userId,
        passwordHash: hashedPassword,
        algorithm: 'bcrypt',
      });

      await this.credentialRepository.save(credential);

      // Link user to account with CLIENT role
      const userTrading = this.userTradingAccountRepository.create({
        userId: savedUser.userId,
        accountId,
        role: 'CLIENT',
      });

      await this.userTradingAccountRepository.save(userTrading);

      this.logger.log(`User registered: ${username} with account: ${accountId}`);

      return {
        id: savedUser.userId,
        username: savedUser.username,
        email: savedUser.email,
        accountId,
        createdAt: savedUser.createdAt,
      };
    } catch (error: unknown) {
      this.logger.error(`Registration failed for user ${username}:`, error);
      throw new BadRequestException('Registration failed. Please try again.');
    }
  }

  async login(loginDto: LoginDto): Promise<any> {
    const { username, password } = loginDto;

    // Find user with credential and trading accounts
    const user = await this.userRepository.findOne({
      where: { username },
      relations: { tradingAccounts: true },
    });

    if (!user) {
      throw new UnauthorizedException('Invalid credentials');
    }

    // Get credential for this user
    const credential = await this.credentialRepository.findOne({
      where: { userId: user.userId },
    });

    if (!credential) {
      throw new UnauthorizedException('Invalid credentials');
    }

    // Verify password
    const isValid = await bcrypt.compare(password, credential.passwordHash);
    if (!isValid) {
      throw new UnauthorizedException('Invalid credentials');
    }

    // Get primary trading account
    const primaryAccount = user.tradingAccounts?.[0];
    if (!primaryAccount) {
      throw new UnauthorizedException('No trading account linked to user');
    }

    // Generate tokens with accountId and role
    const tokens = this.tokenService.issue(
      username,
      primaryAccount.accountId,
      primaryAccount.role,
    );

    return {
      ...tokens,
      user: {
        id: user.userId,
        username: user.username,
        email: user.email,
        accountId: primaryAccount.accountId,
        role: primaryAccount.role,
      },
    };
  }

  async refresh(refreshToken: string): Promise<any> {
    if (!this.tokenService.verify(refreshToken)) {
      throw new UnauthorizedException('Invalid refresh token');
    }

    const decoded: any = this.tokenService.decode(refreshToken);
    const tokens = this.tokenService.issue(
      decoded.username,
      decoded.accountId,
      decoded.role,
    );

    return tokens;
  }

  async validate(token: string): Promise<any> {
    if (!this.tokenService.verify(token)) {
      throw new UnauthorizedException('Invalid token');
    }

    const decoded: any = this.tokenService.decode(token);
    return {
      username: decoded.username,
      accountId: decoded.accountId,
      role: decoded.role,
      valid: true,
    };
  }

  async deleteUser(userId: number): Promise<{ message: string }> {
    // Find the user and their trading account
    const user = await this.userRepository.findOne({
      where: { userId },
      relations: { tradingAccounts: true },
    });

    if (!user) {
      throw new BadRequestException('User not found');
    }

    // Get the associated account ID before deletion
    const accountId = user.tradingAccounts?.[0]?.accountId;

    try {
      // Delete credential record (cascades to user if configured)
      await this.credentialRepository.delete({ userId });

      // Delete user trading accounts (or they cascade)
      await this.userTradingAccountRepository.delete({ userId });

      // Delete the user
      await this.userRepository.delete({ userId });

      // Close the associated trading account (don't delete it)
      if (accountId) {
        await this.databaseService.closeAccount(accountId);
      }

      this.logger.log(`User deleted: ${user.username}, account closed: ${accountId}`);

      return { message: `User ${user.username} deleted and account ${accountId} closed` };
    } catch (error: unknown) {
      this.logger.error(`Failed to delete user ${userId}:`, error);
      throw new BadRequestException('Failed to delete user');
    }
  }
}