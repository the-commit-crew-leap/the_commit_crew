import { Controller, Post, Body, Get, Delete, Param, ParseIntPipe } from '@nestjs/common';
import { 
  ApiTags, 
  ApiOperation, 
  ApiResponse, 
  ApiBody,
  ApiOkResponse,
  ApiBadRequestResponse,
  ApiUnauthorizedResponse,
  ApiConflictResponse,
  ApiNotFoundResponse
} from '@nestjs/swagger';
import { AuthService } from './auth.service';
import { RegisterDto, LoginDto, RefreshDto, ValidateDto, AuthResponseDto } from '../dto/auth.dto';

@ApiTags('auth')
@Controller('api/auth')
export class AuthController {
  constructor(private authService: AuthService) {}

  @Post('register')
  @ApiOperation({ summary: 'Register a new user' })
  @ApiBody({ type: RegisterDto })
  @ApiOkResponse({ 
    description: 'User successfully registered',
    type: AuthResponseDto 
  })
  @ApiBadRequestResponse({ description: 'Invalid input data' })
  @ApiConflictResponse({ description: 'User already exists' })
  async register(@Body() registerDto: RegisterDto) {
    return {
      status: 'SUCCESS',
      data: await this.authService.register(registerDto),
    };
  }

  @Post('login')
  @ApiOperation({ summary: 'Login with credentials' })
  @ApiBody({ type: LoginDto })
  @ApiOkResponse({ 
    description: 'Successfully logged in',
    type: AuthResponseDto 
  })
  @ApiUnauthorizedResponse({ description: 'Invalid credentials' })
  @ApiNotFoundResponse({ description: 'User not found' })
  async login(@Body() loginDto: LoginDto) {
    return {
      status: 'SUCCESS',
      data: await this.authService.login(loginDto),
    };
  }

  @Post('refresh')
  @ApiOperation({ summary: 'Refresh access token' })
  @ApiBody({ type: RefreshDto })
  @ApiOkResponse({ 
    description: 'Token successfully refreshed',
    type: AuthResponseDto 
  })
  @ApiUnauthorizedResponse({ description: 'Invalid or expired refresh token' })
  async refresh(@Body() refreshDto: RefreshDto) {
    return {
      status: 'SUCCESS',
      data: await this.authService.refresh(refreshDto.refreshToken),
    };
  }

  @Post('validate')
  @ApiOperation({ summary: 'Validate an access token' })
  @ApiBody({ type: ValidateDto })
  @ApiOkResponse({ 
    description: 'Token is valid',
    type: AuthResponseDto 
  })
  @ApiUnauthorizedResponse({ description: 'Invalid or expired token' })
  async validate(@Body() validateDto: ValidateDto) {
    return {
      status: 'SUCCESS',
      data: await this.authService.validate(validateDto.token),
    };
  }

  @Delete(':userId')
  @ApiOperation({ summary: 'Delete a user account' })
  @ApiOkResponse({ 
    description: 'User successfully deleted and trading account closed',
    schema: {
      properties: {
        status: { type: 'string', example: 'SUCCESS' },
        data: { 
          properties: {
            message: { type: 'string' }
          }
        }
      }
    }
  })
  @ApiBadRequestResponse({ description: 'User not found or deletion failed' })
  async deleteUser(@Param('userId', ParseIntPipe) userId: number) {
    return {
      status: 'SUCCESS',
      data: await this.authService.deleteUser(userId),
    };
  }

  @Get('health')
  @ApiOperation({ summary: 'Health check endpoint' })
  @ApiOkResponse({ 
    description: 'Service is healthy',
    schema: {
      properties: {
        status: { type: 'string', example: 'ok' }
      }
    }
  })
  health() {
    return { status: 'ok' };
  }
}