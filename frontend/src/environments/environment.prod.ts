const apiHost = typeof window === 'undefined' ? 'localhost' : window.location.hostname;

export const environment = {
  production: true,
  bypassAuth: false,
  authApiBaseUrl: `http://${apiHost}:8082/api/auth`,
  appApiBaseUrl: `http://${apiHost}:8081`,
  orderApiBaseUrl: `http://${apiHost}:8081/api/v1`
};