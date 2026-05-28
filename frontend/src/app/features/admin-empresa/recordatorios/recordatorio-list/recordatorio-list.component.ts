import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { AuthService } from '../../../../core/services/auth.service';
import { environment } from '../../../../../environments/environment';

@Component({
  selector: 'app-recordatorio-list',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './recordatorio-list.component.html',
  styleUrls: ['./recordatorio-list.component.scss'],
})
export class RecordatorioListComponent implements OnInit {
  recordatorios: any[] = [];
  loading = true;
  user: any;
  empresaId: number | null = null;

  constructor(
    private http: HttpClient,
    private authService: AuthService
  ) {}

  ngOnInit(): void {
    this.user = this.authService.getUserData();
    this.empresaId = this.user?.empresaId;
    if (this.empresaId) this.cargarRecordatorios();
  }

  cargarRecordatorios(): void {
    this.loading = true;
    this.http.get(`${environment.apiUrl}/recordatorios/empresa/${this.empresaId}`)
      .subscribe({
        next: (res: any) => {
          this.recordatorios = res.content || [];
          this.loading = false;
        },
        error: () => this.loading = false,
      });
  }

  toggleActivo(r: any): void {
    this.http.patch(`${environment.apiUrl}/recordatorios/${r.id}/toggle-activo`, {})
      .subscribe({
        next: () => {
          r.activo = !r.activo;
        },
      });
  }

  eliminar(r: any): void {
    if (!confirm(`¿Eliminar recordatorio "${r.titulo}"?`)) return;
    this.http.delete(`${environment.apiUrl}/recordatorios/${r.id}`)
      .subscribe({
        next: () => {
          this.recordatorios = this.recordatorios.filter(rec => rec.id !== r.id);
        },
      });
  }

  getRecurrenciaLabel(tipo: string): string {
    const labels: any = {
      'ONE_TIME': '🕐 Una vez',
      'DAILY': '📅 Diario',
      'WEEKLY': '📅 Semanal',
      'MONTHLY': '📅 Mensual',
      'CUSTOM': '📅 Personalizado',
    };
    return labels[tipo] || tipo;
  }

  getProximaEjecucion(proxima: string): string {
    if (!proxima) return '—';
    const fecha = new Date(proxima);
    const ahora = new Date();
    const diffMs = fecha.getTime() - ahora.getTime();
    const diffHoras = Math.floor(diffMs / 3600000);

    if (diffHoras < 0) return '🟡 Vencido';
    if (diffHoras < 24) return `🔴 Hoy (${fecha.toLocaleTimeString()})`;
    if (diffHoras < 48) return `🟠 Mañana`;
    return `🟢 ${fecha.toLocaleDateString()}`;
  }

  logout(): void {
    this.authService.logout();
  }
}
