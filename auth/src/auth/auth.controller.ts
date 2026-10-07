import { Controller, Post, Body, Get } from '@nestjs/common';
import { AuthService } from './auth.service';
import { RegisterDto, LoginDto, RefreshDto, ValidateDto } from '../dto/auth.dto';

@Controller('api/auth')
export class AuthController {
  constructor(private authService: AuthService) {}

  @Post('register')
  async register(@Body() registerDto: RegisterDto) {
    return {
      status: 'SUCCESS',
      data: await this.authService.register(registerDto),
    };
  }

  @Post('login')
  async login(@Body() loginDto: LoginDto) {
    return {
      status: 'SUCCESS',
      data: await this.authService.login(loginDto),
    };
  }

  @Post('refresh')
  async refresh(@Body() refreshDto: RefreshDto) {
    return {
      status: 'SUCCESS',
      data: await this.authService.refresh(refreshDto.refreshToken),
    };
  }

  @Post('validate')
  async validate(@Body() validateDto: ValidateDto) {
    return {
      status: 'SUCCESS',
      data: await this.authService.validate(validateDto.token),
    };
  }

    @Get('health')
  health() {
    return { status: 'ok' };
  }
}