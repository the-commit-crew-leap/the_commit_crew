import { Injectable } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { User } from '../entities/user.entity';
import { Credential } from '../entities/credential.entity';
import { UserTradingAccount } from '../entities/user-trading-account.entity';

@Injectable()
export class UsersRepository {
  constructor(
    @InjectRepository(User)
    private userRepository: Repository<User>,
    @InjectRepository(Credential)
    private credentialRepository: Repository<Credential>,
    @InjectRepository(UserTradingAccount)
    private userTradingAccountRepository: Repository<UserTradingAccount>,
  ) {}

  async findByUsername(username: string): Promise<User | null> {
    return this.userRepository.findOne({
      where: { username },
    });
  }

  async findByUsernameWithCredential(username: string): Promise<User | null> {
    return this.userRepository.findOne({
      where: { username },
      relations: { credential: true },
    });
  }

  async findByUsernameWithCredentialAndTradingAccounts(username: string): Promise<User | null> {
    return this.userRepository.findOne({
      where: { username },
      relations: { credential: true, tradingAccounts: true },
    });
  }

  async findById(userId: number): Promise<User | null> {
    return this.userRepository.findOne({
      where: { userId },
      relations: { credential: true },
    });
  }

  async createUser(data: {
    username: string;
    email: string;
    fullName?: string;
    passwordHash: string;
  }): Promise<User> {
    // Create user
    const user = this.userRepository.create({
      username: data.username,
      email: data.email,
      fullName: data.fullName,
      status: 'ACTIVE',
    });

    const savedUser = await this.userRepository.save(user);

    // Create credential
    const credential = this.credentialRepository.create({
      userId: savedUser.userId,
      passwordHash: data.passwordHash,
      algorithm: 'bcrypt',
      failedLoginAttempts: 0,
    });

    await this.credentialRepository.save(credential);

    // Return user with credential
    return (await this.findByUsernameWithCredential(data.username)) as User;
  }

  async linkUserToTradingAccount(userId: number, accountId: string, role: 'CLIENT' | 'ADVISOR'): Promise<void> {
    const userTradingAccount = this.userTradingAccountRepository.create({
      userId,
      accountId,
      role,
    });

    await this.userTradingAccountRepository.save(userTradingAccount);
  }

  async findTradingAccountByUserId(userId: number): Promise<UserTradingAccount | null> {
    return this.userTradingAccountRepository.findOne({
      where: { userId },
    });
  }

  async updateLastLogin(userId: number): Promise<void> {
    await this.credentialRepository.update(
      { userId },
      { lastLogin: new Date() },
    );
  }

  async incrementFailedLoginAttempts(userId: number): Promise<void> {
    await this.credentialRepository.increment(
      { userId },
      'failedLoginAttempts',
      1,
    );

    // Lock account after 5 failed attempts for 15 minutes
    const credential = await this.credentialRepository.findOne({
      where: { userId },
    });

    if (credential && credential.failedLoginAttempts >= 5) {
      const lockedUntil = new Date();
      lockedUntil.setMinutes(lockedUntil.getMinutes() + 15);

      await this.credentialRepository.update(
        { userId },
        { lockedUntil },
      );

      await this.userRepository.update(
        { userId },
        { status: 'SUSPENDED' },
      );
    }
  }

  async resetFailedLoginAttempts(userId: number): Promise<void> {
    await this.credentialRepository.update(
      { userId },
      { 
        failedLoginAttempts: 0, 
        lockedUntil: null as any,
      },
    );

    await this.userRepository.update(
      { userId, status: 'SUSPENDED' },
      { status: 'ACTIVE' },
    );
  }
}
