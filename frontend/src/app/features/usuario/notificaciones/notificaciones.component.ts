import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { AuthService } from '../../../core/services/auth.service';
import { environment } from '../../../../environments/environment';

@Component({
  selector: 'app-notificaciones',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './notificaciones.component.html',
  styleUrls: ['./notificaciones.component.scss'],
})
export class NotificacionesComponent implements OnInit {
  notificaciones: any[] = [];
  loading = true;
  user: any;

  constructor(
    private http: HttpClient,
    private authService: AuthService
  ) {}

  ngOnInit(): void {
    this.user = this.authService.getUserData();
    this.cargarNotificaciones();
  }

  cargarNotificaciones(): void {
    this.loading = true;
    this.http.get(`${environment.apiUrl}/notificaciones/bandeja`)
      .subscribe({
        next: (res: any) => {
          this.notificaciones = res.content || [];
          this.loading = false;
        },
        error: () => this.loading = false,
      });
  }

  marcarLeida(id: number): void {
    this.http.patch(`${environment.apiUrl}/notificaciones/${id}/leida`, {})
      .subscribe({
        next: () => {
          const n = this.notificaciones.find(n => n.id === id);
          if (n) {
            n.leida = true;
            n.fechaLectura = new Date().toISOString();
          }
        },
      });
  }

  marcarTodasLeidas(): void {
    this.http.post(`${environment.apiUrl}/notificaciones/marcar-todas-leidas`, {})
      .subscribe({
        next: () => {
          this.notificaciones.forEach(n => {
            n.leida = true;
            n.fechaLectura = new Date().toISOString();
          });
        },
      });
  }

  getTodasLeidas(): boolean {
    return this.notificaciones.every(n => n.leida);
  }

  getPendientes(): number {
    return this.notificaciones.filter(n => !n.leida).length;
  }

  /**
   * Reproduce el audio de una notificación en el teléfono/móvil
   */
  reproducirAudio(notificacion: any): void {
    if (!notificacion.tieneAudio) return;

    // Crear elemento de audio y reproducir
    const audioUrl = `${environment.apiUrl}/notificaciones/audio/${notificacion.id}`;
    const audio = new Audio(audioUrl);
    audio.play().catch(err => {
      console.warn('🔊 No se pudo reproducir audio:', err);
      // Fallback: mostrar mensaje
      alert(`🔊 Audio: ${notificacion.titulo}\n\nPara escuchar, abre el enlace:\n${audioUrl}`);
    });
  }

  logout(): void {
    this.authService.logout();
  }
}
