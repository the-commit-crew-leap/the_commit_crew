import { Injectable } from '@nestjs/common';
import { JwtService } from '@nestjs/jwt';

@Injectable()
export class TokenService {
  constructor(private jwtService: JwtService) {}

  issue(
    username: string,
    accountId?: string,
    role?: string
  ): { accessToken: string; refreshToken: string; expiresIn: number } {
    const payload = {
      username,
      sub: username,
      ...(accountId && { accountId }),
      ...(role && { role }),
    };
    
    const accessToken = this.jwtService.sign(payload, { expiresIn: '15m' });
    const refreshToken = this.jwtService.sign(payload, { expiresIn: '7d' });
    return {
      accessToken,
      refreshToken,
      expiresIn: 900, // 15 minutes in seconds
    };
  }

  verify(token: string): boolean {
    try {
      this.jwtService.verify(token);
      return true;
    } catch {
      return false;
    }
  }

  decode(token: string) {
    try {
      return this.jwtService.decode(token);
    } catch {
      return null;
    }
  }
}