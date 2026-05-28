import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { PdfTitleService, TituloDetectado } from '../../core/services/pdf-title.service';
import { TtsSimpleService } from '../../core/services/tts-simple.service';
import { PdfUploaderComponent } from './pdf-uploader/pdf-uploader.component';
import { TitleListComponent } from './title-list/title-list.component';
import { AudioControlsComponent } from './audio-controls/audio-controls.component';

@Component({
  selector: 'app-pdf-reader',
  standalone: true,
  imports: [
    CommonModule,
    PdfUploaderComponent,
    TitleListComponent,
    AudioControlsComponent,
  ],
  templateUrl: './pdf-reader.component.html',
  styleUrls: ['./pdf-reader.component.scss'],
})
export class PdfReaderComponent {
  pdfTitleService = inject(PdfTitleService);
  ttsService = inject(TtsSimpleService);

  /** Título seleccionado actualmente para leer */
  tituloSeleccionado: TituloDetectado | null = null;

  onTituloClic(titulo: TituloDetectado, indice: number): void {
    this.tituloSeleccionado = titulo;
    this.ttsService.speak(titulo.texto, indice);
  }

  onPdfProcesado(titulos: TituloDetectado[]): void {
    // Se selecciona el primer título automáticamente si hay
    if (titulos.length > 0) {
      this.tituloSeleccionado = titulos[0];
    }
  }

  onSubirNuevo(): void {
    this.tituloSeleccionado = null;
    this.ttsService.stop();
  }
}
