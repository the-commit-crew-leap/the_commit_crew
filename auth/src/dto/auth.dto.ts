export class RegisterDto {
  username: string;
  email: string;
  password: string;
}

export class LoginDto {
  username: string;
  password: string;
}

export class RefreshDto {
  refreshToken: string;
}

export class ValidateDto {
  token: string;
}

export class AuthResponseDto {
  accessToken: string;
  refreshToken?: string;
  expiresIn: number;
}