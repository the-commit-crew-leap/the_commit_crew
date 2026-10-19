import { Injectable } from '@nestjs/common';
import { PassportStrategy } from '@nestjs/passport';
import { ExtractJwt, Strategy } from 'passport-jwt';
import { JWT_ISSUER } from '../constants';

@Injectable()
export class JwtStrategy extends PassportStrategy(Strategy) {
  constructor() {
    super({
      jwtFromRequest: ExtractJwt.fromAuthHeaderAsBearerToken(),
      ignoreExpiration: false,
      issuer: JWT_ISSUER,
      secretOrKey: process.env.JWT_SECRET || 'your-secret-key-min-32-chars',
    });
  }

  validate(payload: any) {
    return { userId: payload.sub, username: payload.username };
  }
}