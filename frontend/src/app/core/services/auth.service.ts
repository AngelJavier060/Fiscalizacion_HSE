import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { Router } from '@angular/router';
import { environment } from '../../../environments/environment';
import { LoginRequest, LoginResponse, Usuario } from '../models/usuario.model';

@Injectable({
  providedIn: 'root',
})
export class AuthService {
  private apiUrl = environment.apiAuth;

  constructor(private http: HttpClient, private router: Router) {}

  login(credentials: LoginRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.apiUrl}/login`, credentials).pipe(
      tap((response) => {
        localStorage.setItem('token', response.token);
        localStorage.setItem('usuario', JSON.stringify(response));
      })
    );
  }

  logout(): void {
    localStorage.removeItem('token');
    localStorage.removeItem('usuario');
    this.router.navigate(['/auth/login']);
  }

  isAuthenticated(): boolean {
    const token = localStorage.getItem('token');
    if (!token) return false;

    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      const exp = payload.exp * 1000;
      return Date.now() < exp;
    } catch {
      return false;
    }
  }

  getUserRole(): string | null {
    const usuario = this.getUserData();
    return usuario?.rol || null;
  }

  /** Códigos de módulos habilitados para el usuario actual (del login). */
  getModulos(): string[] {
    const m = this.getUserData()?.modulos;
    return Array.isArray(m) ? m : [];
  }

  /**
   * True si el usuario tiene habilitado el módulo indicado.
   * Si el login no trajo lista de módulos (sesión antigua), se asume permitido
   * para no romper la navegación.
   */
  tieneModulo(codigo: string): boolean {
    const m = this.getUserData()?.modulos;
    if (!Array.isArray(m)) return true;
    return m.includes(codigo);
  }

  getUserData(): LoginResponse | null {
    const stored = localStorage.getItem('usuario');
    if (!stored) return null;
    try {
      return JSON.parse(stored);
    } catch {
      return null;
    }
  }

  getToken(): string | null {
    return localStorage.getItem('token');
  }
}
