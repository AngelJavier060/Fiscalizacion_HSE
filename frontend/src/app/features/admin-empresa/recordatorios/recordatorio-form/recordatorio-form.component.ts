import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router } from '@angular/router';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { AuthService } from '../../../../core/services/auth.service';
import { environment } from '../../../../../environments/environment';

@Component({
  selector: 'app-recordatorio-form',
  standalone: true,
  imports: [CommonModule, RouterModule, ReactiveFormsModule],
  templateUrl: './recordatorio-form.component.html',
  styleUrls: ['./recordatorio-form.component.scss'],
})
export class RecordatorioFormComponent implements OnInit {
  form: FormGroup;
  user: any;
  empresaId: number | null = null;
  documentos: any[] = [];
  usuarios: any[] = [];
  enviando = false;

  tiposRecurrencia = [
    { value: 'ONE_TIME', label: '🕐 Una vez' },
    { value: 'DAILY', label: '📅 Diario' },
    { value: 'WEEKLY', label: '📅 Semanal' },
    { value: 'MONTHLY', label: '📅 Mensual' },
    { value: 'CUSTOM', label: '📅 Personalizado (cada X días)' },
  ];

  constructor(
    private fb: FormBuilder,
    private http: HttpClient,
    private router: Router,
    private authService: AuthService
  ) {
    this.form = this.fb.group({
      titulo: ['', Validators.required],
      descripcion: [''],
      tipoRecurrencia: ['DAILY'],
      intervaloDias: [1],
      diaSemana: [1],
      diaMes: [1],
      fechaInicio: [new Date().toISOString().split('T')[0], Validators.required],
      fechaFin: [''],
      horaRecordatorio: ['08:00'],
      incluirAudio: [false],
      mensajePersonalizado: [''],
      documentoId: [null],
      destinatarioIds: [[]],
    });
  }

  ngOnInit(): void {
    this.user = this.authService.getUserData();
    this.empresaId = this.user?.empresaId;
    if (this.empresaId) {
      this.cargarDocumentos();
      this.cargarUsuarios();
    }
  }

  cargarDocumentos(): void {
    this.http.get(`${environment.apiUrl}/documentos/empresa/${this.empresaId}`)
      .subscribe((res: any) => {
        this.documentos = res.content || [];
      });
  }

  cargarUsuarios(): void {
    this.http.get(`${environment.apiUrl}/usuarios/empresa/${this.empresaId}`)
      .subscribe((res: any) => {
        this.usuarios = res.content || res || [];
      });
  }

  toggleDestinatario(usuarioId: number): void {
    const ids: number[] = this.form.get('destinatarioIds')?.value || [];
    const index = ids.indexOf(usuarioId);
    if (index >= 0) {
      ids.splice(index, 1);
    } else {
      ids.push(usuarioId);
    }
    this.form.patchValue({ destinatarioIds: [...ids] });
  }

  onSubmit(): void {
    if (this.form.invalid || !this.empresaId) return;

    this.enviando = true;
    const data = {
      ...this.form.value,
      empresaId: this.empresaId,
    };

    this.http.post(`${environment.apiUrl}/recordatorios`, data)
      .subscribe({
        next: () => {
          this.router.navigate(['/admin-empresa/recordatorios']);
        },
        error: () => this.enviando = false,
        complete: () => this.enviando = false,
      });
  }

  logout(): void {
    this.authService.logout();
  }
}
