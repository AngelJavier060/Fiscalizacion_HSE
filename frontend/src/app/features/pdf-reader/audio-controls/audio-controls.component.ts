import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TtsSimpleService } from '../../../core/services/tts-simple.service';

@Component({
  selector: 'app-audio-controls',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './audio-controls.component.html',
  styleUrls: ['./audio-controls.component.scss'],
})
export class AudioControlsComponent {
  ttsService = inject(TtsSimpleService);

  /** Reproducir/reanudar el título seleccionado */
  onPlay(): void {
    if (this.ttsService.estado().pausado) {
      this.ttsService.resume();
    }
    // Si no hay nada activo, no hace nada (el usuario debe seleccionar un título primero)
  }

  /** Pausar la lectura actual */
  onPause(): void {
    this.ttsService.pause();
  }

  /** Detener completamente la lectura */
  onStop(): void {
    this.ttsService.stop();
  }

  get estado() {
    return this.ttsService.estado;
  }
}
