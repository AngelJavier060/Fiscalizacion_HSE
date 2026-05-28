import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { finalize } from 'rxjs';
import { SuperAdminSidebarComponent } from '../shared/super-admin-sidebar/super-admin-sidebar.component';
import { UsuarioService } from '../../../core/services/usuario.service';
import { EmpresaService } from '../../../core/services/empresa.service';
import { AuthService } from '../../../core/services/auth.service';
import { VigenciaStoreService, VigenciaInfo } from '../../../core/services/vigencia-store.service';
import { PermisosService, ModoAcceso, RolId, Matriz } from '../../../core/services/permisos.service';
import { Usuario, UsuarioRequest } from '../../../core/models/usuario.model';
import { Empresa } from '../../../core/models/empresa.model';

@Component({
  selector: 'app-usuarios-list',
  standalone: true,
  imports: [CommonModule, RouterModule, ReactiveFormsModule, SuperAdminSidebarComponent],
  templateUrl: './usuarios-list.component.html',
  styleUrls: ['./usuarios-list.component.scss'],
})
export class UsuariosListComponent implements OnInit {
  usuarios: Usuario[] = [];
  empresas: Empresa[] = [];
  loading = true;
  modalAbierto = false;
  submitting = false;
  editingUser: Usuario | null = null;
  errorMsg: string | null = null;
  form: FormGroup;

  // Accesos a módulos del usuario que se está editando/creando
  accesoModo: ModoAcceso = 'rol';
  accesoModulos: Record<string, boolean> = {};
  /** Matriz rol × módulo cargada una vez desde el backend */
  matriz: Matriz = {};

  constructor(
    private fb: FormBuilder,
    private usuarioService: UsuarioService,
    private empresaService: EmpresaService,
    private authService: AuthService,
    private vigencia: VigenciaStoreService,
    public permisos: PermisosService
  ) {
    this.form = this.fb.group({
      nombre: ['', Validators.required],
      email: ['', [Validators.required, Validators.email]],
      password: [''],
      rol: ['USUARIO', Validators.required],
      empresaId: [null as number | null],
      vigenciaDesde: [null as string | null],
      vigenciaHasta: [null as string | null],
    });
  }

  ngOnInit(): void {
    this.matriz = this.permisos.matrizPorDefecto();
    this.permisos.getMatriz().subscribe({
      next: (m) => (this.matriz = m),
      error: () => {},
    });
    this.cargarEmpresas();
    this.cargar();
  }

  get empresasActivas(): Empresa[] {
    return this.empresas.filter((e) => e.activa);
  }

  get miUsuarioId(): number | null {
    return this.authService.getUserData()?.id ?? null;
  }

  cargarEmpresas(): void {
    this.empresaService.listar(0, 500).subscribe({
      next: (r) => (this.empresas = r.content || []),
      error: () => {},
    });
  }

  cargar(): void {
    this.loading = true;
    this.usuarioService.listar(0, 500).subscribe({
      next: (r) => {
        this.usuarios = r.content || [];
        this.loading = false;
      },
      error: () => (this.loading = false),
    });
  }

  abrirNuevo(): void {
    this.editingUser = null;
    this.errorMsg = null;
    this.form.reset({
      nombre: '',
      email: '',
      password: '',
      rol: 'USUARIO',
      empresaId: null,
      vigenciaDesde: null,
      vigenciaHasta: null,
    });
    this.form.get('password')?.setValidators([Validators.required, Validators.minLength(6)]);
    this.form.get('password')?.updateValueAndValidity();
    this.form.get('rol')?.enable();
    this.form.get('empresaId')?.enable();
    this.setEmpresaValidators(true);
    this.accesoModo = 'rol';
    this.accesoModulos = this.permisos.modulosDeRol(this.matriz, 'USUARIO');
    this.modalAbierto = true;
  }

  abrirEditar(u: Usuario): void {
    this.editingUser = u;
    this.errorMsg = null;
    this.form.patchValue({
      nombre: u.nombre,
      email: u.email,
      password: '',
      rol: u.rol,
      empresaId: u.empresaId,
      vigenciaDesde: u.accesoDesde ?? null,
      vigenciaHasta: u.accesoHasta ?? null,
    });
    this.form.get('password')?.clearValidators();
    this.form.get('password')?.updateValueAndValidity();
    if (u.rol === 'SUPER_ADMIN') {
      this.form.get('rol')?.disable();
      this.form.get('empresaId')?.disable();
      this.setEmpresaValidators(false);
    } else {
      this.form.get('rol')?.enable();
      this.form.get('empresaId')?.enable();
      this.setEmpresaValidators(true);
    }
    // accesos a módulos: por defecto los del rol, luego se sobreescribe con la respuesta
    this.accesoModo = 'rol';
    this.accesoModulos = this.permisos.modulosDeRol(this.matriz, u.rol as RolId);
    if (u.rol !== 'SUPER_ADMIN') {
      this.permisos.getAcceso(u.id).subscribe({
        next: (acc) => {
          this.accesoModo = acc.modo;
          this.accesoModulos = acc.modulos;
        },
        error: () => {},
      });
    }
    this.modalAbierto = true;
  }

  // ── Accesos a módulos ──────────────────────────────────────────────
  get rolSeleccionado(): RolId {
    return (this.form.get('rol')?.value as RolId) || 'USUARIO';
  }

  setAccesoModo(modo: ModoAcceso): void {
    this.accesoModo = modo;
    if (modo === 'custom') {
      // parte de lo que hereda del rol y a partir de ahí se ajusta
      this.accesoModulos = this.permisos.modulosDeRol(this.matriz, this.rolSeleccionado);
    }
  }

  /** Al cambiar el rol, si está heredando, refresca la vista de módulos base */
  onRolChange(): void {
    if (this.accesoModo === 'rol') {
      this.accesoModulos = this.permisos.modulosDeRol(this.matriz, this.rolSeleccionado);
    }
  }

  toggleModuloAcceso(moduloId: string): void {
    if (this.accesoModo !== 'custom') {
      return;
    }
    this.accesoModulos[moduloId] = !this.accesoModulos[moduloId];
  }

  contarModulosActivos(): number {
    return Object.values(this.accesoModulos).filter(Boolean).length;
  }

  private setEmpresaValidators(required: boolean): void {
    const c = this.form.get('empresaId');
    if (required) {
      c?.setValidators([Validators.required]);
    } else {
      c?.clearValidators();
    }
    c?.updateValueAndValidity();
  }

  cerrarModal(): void {
    this.modalAbierto = false;
    this.editingUser = null;
    this.errorMsg = null;
    this.form.get('rol')?.enable();
    this.form.get('empresaId')?.enable();
  }

  guardar(): void {
    this.errorMsg = null;
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.errorMsg =
        'Revise los campos marcados. La contraseña nueva debe tener al menos 6 caracteres; el acceso al sistema se hace con el correo indicado.';
      return;
    }
    const raw = this.form.getRawValue();
    this.submitting = true;

    const esSuperAdmin = this.editingUser?.rol === 'SUPER_ADMIN';

    if (this.editingUser) {
      const req: UsuarioRequest = {
        nombre: raw.nombre,
        email: raw.email,
        rol: raw.rol,
        empresaId: raw.empresaId ?? undefined,
        accesoDesde: raw.vigenciaDesde || null,
        accesoHasta: raw.vigenciaHasta || null,
      };
      if (raw.password?.trim()) {
        req.password = raw.password;
      }
      const idEdit = this.editingUser.id;
      this.usuarioService
        .actualizar(idEdit, req)
        .pipe(finalize(() => (this.submitting = false)))
        .subscribe({
          next: () => {
            if (esSuperAdmin) {
              this.cerrarModal();
              this.cargar();
              return;
            }
            this.guardarAccesos(idEdit);
          },
          error: (e: HttpErrorResponse) => this.setError(e),
        });
    } else {
      const req: UsuarioRequest = {
        nombre: raw.nombre,
        email: raw.email,
        password: raw.password,
        rol: raw.rol,
        empresaId: raw.empresaId ?? undefined,
        accesoDesde: raw.vigenciaDesde || null,
        accesoHasta: raw.vigenciaHasta || null,
      };
      this.usuarioService
        .crear(req)
        .pipe(finalize(() => (this.submitting = false)))
        .subscribe({
          next: (creado: Usuario) => {
            if (creado?.id != null) {
              this.guardarAccesos(creado.id);
            } else {
              this.cerrarModal();
              this.cargar();
            }
          },
          error: (e: HttpErrorResponse) => this.setError(e),
        });
    }
  }

  /** Persiste los accesos a módulos del usuario y refresca la lista. */
  private guardarAccesos(userId: number): void {
    this.permisos
      .guardarAcceso(userId, { modo: this.accesoModo, modulos: this.accesoModulos })
      .subscribe({
        next: () => {
          this.cerrarModal();
          this.cargar();
        },
        error: () => {
          // el usuario se guardó; solo falló el detalle de accesos
          this.cerrarModal();
          this.cargar();
        },
      });
  }

  private setError(e: HttpErrorResponse): void {
    const body = e.error;
    if (body && typeof body === 'object' && 'mensaje' in body) {
      const api = body as { mensaje?: string; errores?: string[] };
      let m = api.mensaje || 'Error al guardar';
      if (Array.isArray(api.errores) && api.errores.length > 0) {
        m = m + ' ' + api.errores.join(' · ');
      }
      this.errorMsg = m;
      return;
    }
    if (typeof body === 'string' && body.length > 0) {
      this.errorMsg = body;
      return;
    }
    this.errorMsg = e.message || 'Error al guardar. Compruebe que el backend esté en marcha.';
  }

  errorCampo(nombre: string): string | null {
    const c = this.form.get(nombre);
    if (!c || !c.errors || !c.touched) {
      return null;
    }
    if (c.errors['required']) {
      return 'Obligatorio';
    }
    if (c.errors['email']) {
      return 'Use un correo válido (ej. usuario@gmail.com). El correo es su usuario de acceso.';
    }
    if (c.errors['minlength']) {
      const min = (c.errors['minlength'] as { requiredLength: number }).requiredLength;
      return `Mínimo ${min} caracteres`;
    }
    return null;
  }

  eliminar(u: Usuario): void {
    if (u.rol === 'SUPER_ADMIN') {
      return;
    }
    if (!confirm(`¿Eliminar al usuario "${u.nombre}" (${u.email})?`)) {
      return;
    }
    this.usuarioService.eliminar(u.id).subscribe({
      next: () => this.cargar(),
      error: (err: HttpErrorResponse) => {
        const m =
          err.error && typeof err.error === 'object' && 'mensaje' in err.error
            ? (err.error as { mensaje: string }).mensaje
            : 'No se pudo eliminar';
        alert(m);
      },
    });
  }

  toggleActivo(u: Usuario): void {
    this.usuarioService.toggleActivo(u.id).subscribe({
      next: (act) => {
        const i = this.usuarios.findIndex((x) => x.id === u.id);
        if (i >= 0) {
          this.usuarios[i] = act;
        }
      },
      error: (err: HttpErrorResponse) => {
        const m =
          err.error && typeof err.error === 'object' && 'mensaje' in err.error
            ? (err.error as { mensaje: string }).mensaje
            : 'Error al cambiar estado';
        alert(m);
      },
    });
  }

  puedeEliminar(u: Usuario): boolean {
    if (u.rol === 'SUPER_ADMIN') {
      return false;
    }
    if (this.miUsuarioId !== null && u.id === this.miUsuarioId) {
      return false;
    }
    return true;
  }

  etiquetaRol(rol: string): string {
    switch (rol) {
      case 'SUPER_ADMIN':
        return 'Super Admin';
      case 'ADMIN_EMPRESA':
        return 'Admin empresa';
      case 'USUARIO':
        return 'Usuario';
      default:
        return rol;
    }
  }

  claseRol(rol: string): string {
    switch (rol) {
      case 'SUPER_ADMIN':
        return 'rol-sa';
      case 'ADMIN_EMPRESA':
        return 'rol-ae';
      case 'USUARIO':
        return 'rol-u';
      default:
        return '';
    }
  }

  vigenciaInfo(u: Usuario): VigenciaInfo {
    return this.vigencia.estado(u.accesoDesde, u.accesoHasta);
  }

  tieneAccesoPersonalizado(u: Usuario): boolean {
    return !!u.accesosPersonalizados;
  }
}
