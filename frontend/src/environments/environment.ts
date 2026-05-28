export const environment = {
  production: false,
  // Los controladores REST usan @RequestMapping("/api/..."); con context-path /api/v1 la URL base es /api/v1/api
  apiUrl: 'http://localhost:8080/api/v1/api',
  apiAuth: 'http://localhost:8080/api/v1/auth',
};
