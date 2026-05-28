import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, ActivatedRoute, Router } from '@angular/router';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { EmpresaService } from '../../../../core/services/empresa.service';
import { SuperAdminSidebarComponent } from '../../shared/super-admin-sidebar/super-admin-sidebar.component';

@Component({
  selector: 'app-empresa-form',
  standalone: true,
  imports: [CommonModule, RouterModule, ReactiveFormsModule, SuperAdminSidebarComponent],
  templateUrl: './empresa-form.component.html',
  styleUrls: ['./empresa-form.component.scss'],
})
export class EmpresaFormComponent implements OnInit {
  form: FormGroup;
  editing = false;
  empresaId: number | null = null;
  loading = false;
  submitting = false;

  constructor(
    private fb: FormBuilder,
    private empresaService: EmpresaService,
    private route: ActivatedRoute,
    private router: Router
  ) {
    this.form = this.fb.group({
      nombre: ['', Validators.required],
      ruc: [''],
      direccion: [''],
      email: [''],
      telefono: [''],
      vigenciaDesde: [null as string | null],
      vigenciaHasta: [null as string | null],
    });
  }

  ngOnInit(): void {
    this.empresaId = this.route.snapshot.params['id'];
    if (this.empresaId) {
      this.editing = true;
      this.cargarEmpresa();
    }
  }

  cargarEmpresa(): void {
    if (!this.empresaId) return;
    this.loading = true;
    this.empresaService.obtener(this.empresaId).subscribe({
      next: (empresa) => {
        this.form.patchValue({
          nombre: empresa.nombre,
          ruc: empresa.ruc,
          direccion: empresa.direccion,
          email: empresa.email,
          telefono: empresa.telefono,
          vigenciaDesde: empresa.vigenciaDesde ?? null,
          vigenciaHasta: empresa.vigenciaHasta ?? null,
        });
        this.loading = false;
      },
      error: () => this.loading = false,
    });
  }

  onSubmit(): void {
    if (this.form.invalid) return;
    this.submitting = true;

    const raw = this.form.value;

    const request = {
      nombre: raw.nombre,
      ruc: raw.ruc,
      direccion: raw.direccion,
      email: raw.email,
      telefono: raw.telefono,
      vigenciaDesde: raw.vigenciaDesde || null,
      vigenciaHasta: raw.vigenciaHasta || null,
    };

    const action = this.editing
      ? this.empresaService.actualizar(this.empresaId!, request)
      : this.empresaService.crear(request);

    action.subscribe({
      next: () => {
        this.router.navigate(['/super-admin/empresas']);
      },
      error: () => {
        this.submitting = false;
      },
      complete: () => {
        this.submitting = false;
      },
    });
  }
}
