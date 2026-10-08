import { Module, MiddlewareConsumer, NestModule } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { JwtModule } from '@nestjs/jwt';
import { PassportModule } from '@nestjs/passport';
import { AuthService } from './auth.service';
import { AuthController } from './auth.controller';
import { TokenService } from './token.service';
import { JwtStrategy } from './strategies/jwt.strategy';
import { User } from './entities/user.entity';
import { Credential } from './entities/credential.entity';
import { UsersRepository } from './repositories/users.repository';
import { SecureLoggerService } from '../common/services/secure-logger.service';
import { RequestSanitizerMiddleware } from '../common/middleware/request-sanitizer.middleware';

@Module({
  imports: [
    TypeOrmModule.forFeature([User, Credential]),
    PassportModule,
    JwtModule.register({
      secret: process.env.JWT_SECRET || 'your-secret-key-min-32-chars',
      signOptions: { expiresIn: '15m' },
    }),
  ],
  providers: [AuthService, TokenService, JwtStrategy, UsersRepository, SecureLoggerService],
  controllers: [AuthController],
  exports: [AuthService, TokenService, SecureLoggerService],
})
export class AuthModule implements NestModule {
  configure(consumer: MiddlewareConsumer) {
    consumer
      .apply(RequestSanitizerMiddleware)
      .forRoutes('api/auth');
  }
}