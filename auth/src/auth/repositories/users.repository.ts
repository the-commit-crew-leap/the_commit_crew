import { Injectable } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { User } from '../entities/user.entity';
import { Credential } from '../entities/credential.entity';

@Injectable()
export class UsersRepository {
  constructor(
    @InjectRepository(User)
    private userRepository: Repository<User>,
    @InjectRepository(Credential)
    private credentialRepository: Repository<Credential>,
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

  async findById(userId: number): Promise<User | null> {
    return this.userRepository.findOne({
      where: { user_id: userId },
      relations: { credential: true },
    });
  }

  async createUser(data: {
    username: string;
    email: string;
    passwordHash: string;
  }): Promise<User> {
    // Create user
    const user = this.userRepository.create({
      username: data.username,
      email: data.email,
      status: 'ACTIVE',
    });

    const savedUser = await this.userRepository.save(user);

    // Create credential
    const credential = this.credentialRepository.create({
      user_id: savedUser.user_id,
      password_hash: data.passwordHash,
      algorithm: 'bcrypt',
      failed_login_attempts: 0,
    });

    await this.credentialRepository.save(credential);

    // Return user with credential
    return (await this.findByUsernameWithCredential(data.username)) as User;
  }

  async updateLastLogin(userId: number): Promise<void> {
    await this.credentialRepository.update(
      { user_id: userId },
      { last_login: new Date() },
    );
  }

  async incrementFailedLoginAttempts(userId: number): Promise<void> {
    await this.credentialRepository.increment(
      { user_id: userId },
      'failed_login_attempts',
      1,
    );

    // Lock account after 5 failed attempts for 15 minutes
    const credential = await this.credentialRepository.findOne({
      where: { user_id: userId },
    });

    if (credential && credential.failed_login_attempts >= 5) {
      const lockedUntil = new Date();
      lockedUntil.setMinutes(lockedUntil.getMinutes() + 15);

      await this.credentialRepository.update(
        { user_id: userId },
        { locked_until: lockedUntil },
      );
    }
  }

  async resetFailedLoginAttempts(userId: number): Promise<void> {
    await this.credentialRepository.update(
      { user_id: userId },
      { 
        failed_login_attempts: 0, 
        locked_until: null as any,
      },
    );
  }
}
