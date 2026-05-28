import { Component, output, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { PdfTitleService, TituloDetectado } from '../../../core/services/pdf-title.service';

@Component({
  selector: 'app-pdf-uploader',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './pdf-uploader.component.html',
  styleUrls: ['./pdf-uploader.component.scss'],
})
export class PdfUploaderComponent {
  pdfTitleService = inject(PdfTitleService);

  /** Emite cuando se han procesado los títulos del PDF */
  pdfProcesado = output<TituloDetectado[]>();
  /** Emite cuando el usuario quiere subir un nuevo PDF */
  subirNuevo = output<void>();

  dragOver = false;

  async onFileSelected(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      await this.procesarArchivo(input.files[0]);
    }
    // Resetear el input para permitir subir el mismo archivo otra vez
    input.value = '';
  }

  async onDrop(event: DragEvent): Promise<void> {
    event.preventDefault();
    this.dragOver = false;

    if (event.dataTransfer && event.dataTransfer.files.length > 0) {
      await this.procesarArchivo(event.dataTransfer.files[0]);
    }
  }

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    this.dragOver = true;
  }

  onDragLeave(event: DragEvent): void {
    event.preventDefault();
    this.dragOver = false;
  }

  private async procesarArchivo(file: File): Promise<void> {
    // Validar que sea PDF
    if (!file.name.toLowerCase().endsWith('.pdf')) {
      this.pdfTitleService.error.set('Solo se aceptan archivos .pdf');
      return;
    }

    // Si ya hay títulos cargados, emitir señal de "subir nuevo"
    if (this.pdfTitleService.titulos().length > 0) {
      this.subirNuevo.emit();
    }

    try {
      const titulos = await this.pdfTitleService.procesarPdf(file);
      if (titulos.length > 0) {
        this.pdfProcesado.emit(titulos);
      }
    } catch {
      // El error ya se guarda en pdfTitleService.error()
    }
  }
}
