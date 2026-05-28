import { Component, input, output, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TituloDetectado } from '../../../core/services/pdf-title.service';
import { TtsSimpleService } from '../../../core/services/tts-simple.service';

@Component({
  selector: 'app-title-list',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './title-list.component.html',
  styleUrls: ['./title-list.component.scss'],
})
export class TitleListComponent {
  ttsService = inject(TtsSimpleService);

  titulos = input<TituloDetectado[]>([]);
  tituloClic = output<{ titulo: TituloDetectado; indice: number }>();

  onClicTitulo(titulo: TituloDetectado, indice: number): void {
    this.tituloClic.emit({ titulo, indice });
  }

  /** Determina si un título es el que se está leyendo actualmente */
  esTituloActivo(indice: number): boolean {
    return this.ttsService.estado().activo && this.ttsService.estado().indiceTitulo === indice;
  }

  /** Obtener el ícono según el índice (para dar variedad visual) */
  getIcono(indice: number): string {
    const iconos = ['🎯', '📌', '⭐', '🔖', '📍', '🎬', '📀', '🎪'];
    return iconos[indice % iconos.length];
  }
}
