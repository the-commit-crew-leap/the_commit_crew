import { AuthController } from './auth.controller';

describe('AuthController', () => {
  let controller: AuthController;
  let authService: {
    register: jest.Mock;
    login: jest.Mock;
    refresh: jest.Mock;
    validate: jest.Mock;
    deleteUser: jest.Mock;
  };

  beforeEach(() => {
    authService = {
      register: jest.fn(),
      login: jest.fn(),
      refresh: jest.fn(),
      validate: jest.fn(),
      deleteUser: jest.fn(),
    };

    controller = new AuthController(authService as any);
  });

  it('registers a user and wraps the response', async () => {
    const registerRequest = {
      username: 'alice',
      email: 'alice@example.com',
      fullName: 'Alice',
      password: 'SecurePass123!',
    };
    const registerResult = { userId: 1, username: 'alice' };

    authService.register.mockResolvedValue(registerResult);

    await expect(controller.register(registerRequest as any)).resolves.toEqual({
      status: 'SUCCESS',
      data: registerResult,
    });

    expect(authService.register).toHaveBeenCalledWith(registerRequest);
  });

  it('logs in a user and wraps the response', async () => {
    const loginRequest = {
      username: 'alice',
      password: 'SecurePass123!',
    };
    const loginResult = {
      accessToken: 'access-token',
      refreshToken: 'refresh-token',
    };

    authService.login.mockResolvedValue(loginResult);

    await expect(controller.login(loginRequest as any)).resolves.toEqual({
      status: 'SUCCESS',
      data: loginResult,
    });

    expect(authService.login).toHaveBeenCalledWith(loginRequest);
  });

  it('refreshes a token and wraps the response', async () => {
    const refreshRequest = { refreshToken: 'refresh-token' };
    const refreshResult = { accessToken: 'new-access-token' };

    authService.refresh.mockResolvedValue(refreshResult);

    await expect(controller.refresh(refreshRequest as any)).resolves.toEqual({
      status: 'SUCCESS',
      data: refreshResult,
    });

    expect(authService.refresh).toHaveBeenCalledWith('refresh-token');
  });

  it('validates a token and wraps the response', async () => {
    const validateRequest = { token: 'access-token' };
    const validateResult = {
      username: 'alice',
      accountId: 'ACC-123',
      role: 'CLIENT',
      valid: true,
    };

    authService.validate.mockResolvedValue(validateResult);

    await expect(controller.validate(validateRequest as any)).resolves.toEqual({
      status: 'SUCCESS',
      data: validateResult,
    });

    expect(authService.validate).toHaveBeenCalledWith('access-token');
  });

  it('deletes a user and wraps the response', async () => {
    const deleteResult = { message: 'User deleted' };

    authService.deleteUser.mockResolvedValue(deleteResult);

    await expect(controller.deleteUser(42)).resolves.toEqual({
      status: 'SUCCESS',
      data: deleteResult,
    });

    expect(authService.deleteUser).toHaveBeenCalledWith(42);
  });

  it('returns a healthy status', () => {
    expect(controller.health()).toEqual({ status: 'ok' });
  });
});