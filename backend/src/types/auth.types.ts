export interface TokenPayload {
  userId: string;
  email: string;
}

export interface UserDto {
  id: string;
  email: string;
  createdAt: string;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  user: {
    id: string;
    email: string;
  };
}

export interface TokenPair {
  accessToken: string;
  refreshToken: string;
}

declare module 'fastify' {
  interface FastifyRequest {
    user?: TokenPayload;
  }
}
