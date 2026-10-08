import { AuthService } from "./auth.service";

describe("AuthService", () => {
  let service: AuthService;
  let mockTokenService: any;

  beforeEach(async () => {
    mockTokenService = {
        issue: jest.fn().mockResolvedValue({
            accessToken: "mock-access-token",
            refreshToken: "mock-refresh-token",
            expiresIn: 900
        }),
    };

    service = new AuthService(mockTokenService);
  });

  it("logs in and receives a valid access token and refresh token", async () => {
    await service.register({ 
      username: "carol", 
      email: "carol@example.com", 
      password: "SecurePass123!" 
    });
    const { accessToken, refreshToken } = await service.login({ 
      username: "carol", 
      password: "SecurePass123!" 
    });
    expect(typeof accessToken).toBe("string");
    expect(typeof refreshToken).toBe("string");
    expect(accessToken).not.toEqual(refreshToken);
  });

  it("rejects login with an incorrect password", async () => {
    await service.register({ 
      username: "carol", 
      email: "carol@example.com", 
      password: "SecurePass123!" 
    });
    await expect(service.login({ 
      username: "carol", 
      password: "wrong-password" 
    })).rejects.toThrow();
  });

  it("successfully registers a new user", async () => {
    const result = await service.register({ 
        username: "alice", 
        email: "alice@example.com", 
        password: "SecurePass123!" 
    });
    expect(result).toHaveProperty('id');
    expect(result.username).toBe("alice");
    expect(result.email).toBe("alice@example.com");
    expect(result).toHaveProperty('createdAt');
    });

    it("rejects duplicate user registration", async () => {
    await service.register({ 
        username: "bob", 
        email: "bob@example.com", 
        password: "SecurePass123!" 
    });
    await expect(service.register({ 
        username: "bob", 
        email: "bob2@example.com", 
        password: "SecurePass123!" 
    })).rejects.toThrow('User already exists');
    });

    it("rejects login for non-existent user", async () => {
    await expect(service.login({ 
        username: "nonexistent", 
        password: "SecurePass123!" 
    })).rejects.toThrow('Invalid credentials');
    });
});