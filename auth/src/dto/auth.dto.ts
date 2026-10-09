import { ApiProperty } from '@nestjs/swagger';
import { 
  IsString, 
  IsEmail, 
  MinLength, 
  MaxLength, 
  Matches, 
  IsNotEmpty,
  IsNumber,
  IsPositive,
  IsOptional,
  IsJWT 
} from 'class-validator';

export class RegisterDto {
  @ApiProperty({
    description: 'Unique username',
    example: 'john_doe',
    minLength: 3,
    maxLength: 30,
    pattern: '^[a-zA-Z0-9_-]+$'
  })
  @IsNotEmpty()
  @IsString()
  @MinLength(3)
  @MaxLength(30)
  @Matches(/^[a-zA-Z0-9_-]+$/, {
    message: 'username must contain only letters, numbers, underscores, or hyphens'
  })
  username: string;

  @ApiProperty({
    description: 'User email address',
    example: 'john@example.com'
  })
  @IsNotEmpty()
  @IsEmail()
  email: string;

  @ApiProperty({
    description: 'User full name',
    example: 'John Doe'
  })
  @IsNotEmpty()
  @IsString()
  @MinLength(1)
  @MaxLength(255)
  fullName: string;

  @ApiProperty({
    description: 'Password (must contain uppercase, lowercase, number, and special character)',
    example: 'SecurePass123!'
  })
  @IsNotEmpty()
  @IsString()
  @MinLength(8)
  @Matches(/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&])[a-zA-Z\d@$!%*?&]+$/, {
    message: 'password must contain uppercase, lowercase, number, and special character'
  })
  password: string;
}

export class LoginDto {
  @ApiProperty({
    description: 'Username or email',
    example: 'john_doe'
  })
  @IsNotEmpty()
  @IsString()
  username: string;

  @ApiProperty({
    description: 'User password',
    example: 'SecurePass123!'
  })
  @IsNotEmpty()
  @IsString()
  password: string;
}

export class RefreshDto {
  @ApiProperty({
    description: 'Refresh token received from login/register',
    example: 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...'
  })
  @IsNotEmpty()
  @IsString()
  @IsJWT()
  refreshToken: string;
}

export class ValidateDto {
  @ApiProperty({
    description: 'Access token to validate',
    example: 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...'
  })
  @IsNotEmpty()
  @IsString()
  @IsJWT()
  token: string;
}

export class AuthResponseDto {
  @ApiProperty({
    description: 'JWT access token',
    example: 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...'
  })
  @IsNotEmpty()
  @IsString()
  accessToken: string;

  @ApiProperty({
    description: 'Refresh token (optional, only in login/register)',
    example: 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...',
    required: false
  })
  @IsOptional()
  @IsString()
  refreshToken?: string;

  @ApiProperty({
    description: 'Token expiration time in seconds',
    example: 3600
  })
  @IsNumber()
  @IsPositive()
  expiresIn: number;
}