interface BackendAuthError {
  status?: string;
  message?: string;
  data?: unknown;
}

interface BackendAppError {
  errorCode?: string;
  message?: string;
}

export function getAuthErrorMessage(error: unknown, fallback: string): string {
  if (!error || typeof error !== 'object') {
    return fallback;
  }

  const maybeHttpError = error as { error?: unknown };

  if (maybeHttpError.error && typeof maybeHttpError.error === 'object') {
    const authError = maybeHttpError.error as BackendAuthError;
    if (typeof authError.message === 'string' && authError.message.trim().length > 0) {
      return authError.message;
    }

    const appError = maybeHttpError.error as BackendAppError;
    if (typeof appError.message === 'string' && appError.message.trim().length > 0) {
      return appError.message;
    }
  }

  return fallback;
}