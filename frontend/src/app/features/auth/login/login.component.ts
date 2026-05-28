import { Component, EventEmitter, Input, Output } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.scss'],
  host: {
    '[class.login--embed]': 'embedMode',
  },
})
export class LoginComponent {
  /** Login dentro del panel de la landing (sin pantalla completa oscura). */
  @Input() embedMode = false;
  @Output() cerrar = new EventEmitter<void>();
  @Output() loginExitoso = new EventEmitter<void>();
  loginForm: FormGroup;
  loading = false;
  errorMessage = '';
  /** true si el usuario entró desde «Acceder» en la landing (siempre mostrar formulario). */
  desdeLanding = false;
  sesionActiva = false;

  constructor(
    private fb: FormBuilder,
    private authService: AuthService,
    private router: Router,
    private route: ActivatedRoute
  ) {
    this.loginForm = this.fb.group({
      email: ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required, Validators.minLength(6)]],
    });

    this.desdeLanding = this.route.snapshot.queryParamMap.get('from') === 'landing';
    this.sesionActiva = this.authService.isAuthenticated();

    // Solo saltar al panel si ya hay sesión y NO vino desde la landing
    if (this.sesionActiva && !this.desdeLanding) {
      this.redirigirSegunRol();
    }
  }

  continuarAlSistema(): void {
    this.redirigirSegunRol();
  }

  cerrarSesionYQuedarse(): void {
    localStorage.removeItem('token');
    localStorage.removeItem('usuario');
    this.sesionActiva = false;
    this.router.navigate(['/auth/login'], { queryParams: { from: 'landing' } });
  }

  onSubmit(): void {
    if (this.loginForm.invalid) return;

    this.loading = true;
    this.errorMessage = '';

    this.authService.login(this.loginForm.value).subscribe({
      next: () => {
        this.loginExitoso.emit();
        this.redirigirSegunRol();
      },
      error: (err) => {
        this.loading = false;
        this.errorMessage = err.error?.mensaje || 'Error al iniciar sesión';
      },
      complete: () => {
        this.loading = false;
      },
    });
  }

  private redirigirSegunRol(): void {
    const rol = this.authService.getUserRole();
    switch (rol) {
      case 'SUPER_ADMIN':
        this.router.navigate(['/super-admin/dashboard']);
        break;
      case 'ADMIN_EMPRESA':
        this.router.navigate(['/admin-empresa/dashboard']);
        break;
      case 'USUARIO':
        this.router.navigate(['/usuario/dashboard']);
        break;
      default:
        this.router.navigate(['/auth/login']);
    }
  }
}
