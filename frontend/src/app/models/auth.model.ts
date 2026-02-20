export interface LoginRequest {
  email: string;
  password: string;
}

export interface TokenResponse {
  accessToken: string;
  tokenType: string;
}

export interface User {
  id: number;
  name: string;
  email: string;
  role: 'USER' | 'ADMIN';
  enabled: boolean;
}

export interface CreateUserRequest {
  name: string;
  email: string;
  password: string;
  role: 'USER' | 'ADMIN';
}
