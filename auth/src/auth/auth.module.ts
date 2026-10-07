import { Module } from '@nestjs/common';
import { JwtModule } from '@nestjs/jwt';
import { PassportModule } from '@nestjs/passport';
import { TypeOrmModule } from '@nestjs/typeorm';
import { AuthService } from './auth.service';
import { AuthController } from './auth.controller';
import { TokenService } from './token.service';
import { JwtStrategy } from './strategies/jwt.strategy';
import { User } from './entities/user.entity';
import { CredentialsModule } from '../credentials/credentials.module';

@Module({
  imports: [
    PassportModule,
    JwtModule.register({
      secret: process.env.JWT_SECRET || 'your-secret-key-min-32-chars',
      signOptions: { expiresIn: '15m' },
    }),
    TypeOrmModule.forFeature([User]),
    CredentialsModule,
  ],
  providers: [AuthService, TokenService, JwtStrategy],
  controllers: [AuthController],
  exports: [AuthService, TokenService],
})
export class AuthModule {}