import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { JwtModule } from '@nestjs/jwt';
import { PassportModule } from '@nestjs/passport';
import { AuthService } from './auth.service';
import { AuthController } from './auth.controller';
import { TokenService } from './token.service';
import { JwtStrategy } from './strategies/jwt.strategy';
import { DatabaseService } from './database.service';
import { User } from './entities/user.entity';
import { UserTradingAccount } from './entities/user-trading-account.entity';
import { Credential } from './entities/credential.entity';

@Module({
  imports: [
    TypeOrmModule.forFeature([User, Credential, UserTradingAccount]),
    PassportModule,
    JwtModule.register({
      secret: process.env.JWT_SECRET || 'your-secret-key-min-32-chars',
      signOptions: { expiresIn: '15m' },
    }),
  ],
  providers: [AuthService, TokenService, JwtStrategy, DatabaseService],
  controllers: [AuthController],
  exports: [AuthService, TokenService],
})
export class AuthModule {}