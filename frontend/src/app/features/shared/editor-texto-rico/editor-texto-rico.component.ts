import { Component, Input, Output, EventEmitter, forwardRef, AfterViewInit, OnDestroy, ElementRef, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ControlValueAccessor, NG_VALUE_ACCESSOR, FormsModule } from '@angular/forms';

@Component({
  selector: 'app-editor-texto-rico',
  standalone: true,
  imports: [CommonModule, FormsModule],
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => EditorTextoRicoComponent),
      multi: true,
    },
  ],
  template: `
    <div class="editor-wrapper" [class.focused]="focused" [class.disabled]="disabled" [class.embedded]="embedded">
      <!-- Toolbar -->
      <div class="editor-toolbar" (mousedown)="onToolbarMouseDown($event)">
        <div class="toolbar-scroll">
          <div class="toolbar-group" aria-label="Historial">
            <button type="button" class="tb-btn" (click)="exec('undo')" title="Deshacer (Ctrl+Z)">
              <svg class="tb-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M12.5 8c-2.65 0-5.05.99-6.9 2.6L2 7v9h9l-3.62-3.62c1.39-1.16 3.16-1.88 5.12-1.88 3.54 0 6.55 2.31 7.6 5.5l2.37-.78C21.08 11.03 17.15 8 12.5 8z"/></svg>
            </button>
            <button type="button" class="tb-btn" (click)="exec('redo')" title="Rehacer (Ctrl+Shift+Z)">
              <svg class="tb-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M18.4 10.6C16.55 8.99 14.15 8 11.5 8c-4.65 0-8.58 3.03-9.96 7.22L3.9 16c1.05-3.19 4.05-5.5 7.6-5.5 1.95 0 3.73.72 5.12 1.88L13 16h9V7l-3.6 3.6z"/></svg>
            </button>
          </div>

          <div class="toolbar-divider" aria-hidden="true"></div>

          <div class="toolbar-group" aria-label="Formato de texto">
            <button type="button" class="tb-btn" (click)="exec('bold')" [class.active]="formatState.bold" title="Negrita (Ctrl+B)">
              <svg class="tb-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M15.6 10.79c.97-.67 1.65-1.77 1.65-2.79 0-2.26-1.75-4-4-4H7v14h7.04c2.09 0 3.71-1.69 3.71-3.79 0-1.52-.86-2.82-2.15-3.42zM10 6.5h3c.83 0 1.5.67 1.5 1.5s-.67 1.5-1.5 1.5h-3v-3zm3.5 9H10v-3h3.5c.83 0 1.5.67 1.5 1.5s-.67 1.5-1.5 1.5z"/></svg>
            </button>
            <button type="button" class="tb-btn" (click)="exec('italic')" [class.active]="formatState.italic" title="Cursiva (Ctrl+I)">
              <svg class="tb-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M10 4v3h2.21l-3.42 8H6v3h8v-3h-2.21l3.42-8H18V4h-8z"/></svg>
            </button>
            <button type="button" class="tb-btn" (click)="exec('underline')" [class.active]="formatState.underline" title="Subrayado (Ctrl+U)">
              <svg class="tb-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M12 17c3.31 0 6-2.69 6-6V3h-2.5v8c0 1.93-1.57 3.5-3.5 3.5S8.5 12.93 8.5 11V3H6v8c0 3.31 2.69 6 6 6zm-7 3v2h14v-2H5z"/></svg>
            </button>
            <button type="button" class="tb-btn" (click)="exec('strikeThrough')" [class.active]="formatState.strike" title="Tachado">
              <svg class="tb-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M10 19h4v-3h-4v3zM5 4v3h5v12h4V7h5V4H5z"/></svg>
            </button>
          </div>

          <div class="toolbar-divider" aria-hidden="true"></div>

          <div class="toolbar-group" aria-label="Tamaño de letra">
            <label class="tb-select-wrap">
              <span class="sr-only">Tamaño de letra</span>
              <select class="tb-select tb-select-sm" [ngModel]="fontSizePx" (ngModelChange)="applyFontSize($event)">
                <option [ngValue]="14">14 px</option>
                <option [ngValue]="15">15 px</option>
                <option [ngValue]="16">16 px</option>
                <option [ngValue]="18">18 px</option>
              </select>
            </label>
            <button type="button" class="tb-heading" (click)="normalizarTamanoDocumento()" title="Mismo tamaño en todo el texto (quita tamaños mezclados)">
              Uniforme
            </button>
          </div>

          <div class="toolbar-divider" aria-hidden="true"></div>

          <div class="toolbar-group toolbar-headings" aria-label="Títulos para el índice">
            <span class="toolbar-mini-label">Índice:</span>
            @if (selectedBlockCount > 1) {
              <span class="tb-selection-badge">{{ selectedBlockCount }} líneas</span>
            }
            <button type="button" class="tb-heading" [class.active]="!blockTagMixed && currentBlockTag === 'H1'"
                    (mousedown)="onHeadingMouseDown($event, 'H1')" title="H1 — nombre del libro (solo uno, ej. Estándares que Salvan Vidas)">
              H1 Libro
            </button>
            <button type="button" class="tb-heading" [class.active]="!blockTagMixed && currentBlockTag === 'H2'"
                    (mousedown)="onHeadingMouseDown($event, 'H2')" title="H2 — cada estándar ESV (trabajo en caliente, espacios confinados…)">
              H2 Estándar
            </button>
            <button type="button" class="tb-heading" [class.active]="!blockTagMixed && currentBlockTag === 'H3'"
                    (mousedown)="onHeadingMouseDown($event, 'H3')" title="H3 — Objetivo, Alcance, Requisitos…">
              H3 Apartado
            </button>
            <button type="button" class="tb-heading" [class.active]="!blockTagMixed && currentBlockTag === 'P'"
                    (mousedown)="onHeadingMouseDown($event, 'P')" title="Párrafo normal">
              Párrafo
            </button>
          </div>

          <div class="toolbar-divider" aria-hidden="true"></div>

          <div class="toolbar-group" aria-label="Tipo de bloque">
            <label class="tb-select-wrap">
              <span class="sr-only">Tipo de bloque</span>
              <select class="tb-select" [ngModel]="blockTagMixed ? 'MIX' : currentBlockTag" (ngModelChange)="applyBlockType($event)">
                @if (blockTagMixed) {
                  <option value="MIX" disabled>Varios tipos ({{ selectedBlockCount }})</option>
                }
                <option value="P">Párrafo</option>
                <option value="H1">H1 — Libro / documento</option>
                <option value="H2">H2 — Estándar ESV</option>
                <option value="H3">Título 3</option>
              </select>
            </label>
          </div>

          <div class="toolbar-divider" aria-hidden="true"></div>

          <div class="toolbar-group" aria-label="Listas">
            <button type="button" class="tb-btn" (click)="exec('insertUnorderedList')" [class.active]="formatState.ul" title="Lista con viñetas">
              <svg class="tb-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M4 10.5c-.83 0-1.5.67-1.5 1.5s.67 1.5 1.5 1.5 1.5-.67 1.5-1.5-.67-1.5-1.5-1.5zm0-6c-.83 0-1.5.67-1.5 1.5S3.17 7.5 4 7.5 5.5 6.83 5.5 6 4.83 4.5 4 4.5zm0 12c-.83 0-1.5.68-1.5 1.5s.68 1.5 1.5 1.5 1.5-.68 1.5-1.5-.67-1.5-1.5-1.5zM7 19h14v-2H7v2zm0-6h14v-2H7v2zm0-8v2h14V5H7z"/></svg>
            </button>
            <button type="button" class="tb-btn" (click)="exec('insertOrderedList')" [class.active]="formatState.ol" title="Lista numerada">
              <svg class="tb-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M2 17h2v.5H3v1h1v.5H2v1h3v-4H2v1zm1-9h1V4H2v1h1v3zm-1 3h1.8L2 13.1v.9h3v-1H3.2L5 10.9V10H2v1zm5-6v2h14V5H7zm0 14h14v-2H7v2zm0-6h14v-2H7v2z"/></svg>
            </button>
          </div>

          <div class="toolbar-divider" aria-hidden="true"></div>

          <div class="toolbar-group" aria-label="Alineación">
            <button type="button" class="tb-btn" (click)="exec('justifyLeft')" [class.active]="formatState.align === 'left'" title="Alinear izquierda">
              <svg class="tb-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M15 15H3v2h12v-2zm0-8H3v2h12V7zM3 13h18v-2H3v2zm0 8h18v-2H3v2zM3 3v2h18V3H3z"/></svg>
            </button>
            <button type="button" class="tb-btn" (click)="exec('justifyCenter')" [class.active]="formatState.align === 'center'" title="Centrar">
              <svg class="tb-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M7 15v2h10v-2H7zm-4 6h18v-2H3v2zm0-8h18v-2H3v2zm4-6v2h10V7H7zM3 3v2h18V3H3z"/></svg>
            </button>
            <button type="button" class="tb-btn" (click)="exec('justifyRight')" [class.active]="formatState.align === 'right'" title="Alinear derecha">
              <svg class="tb-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M3 21h18v-2H3v2zm6-4h12v-2H9v2zm-6-4h18v-2H3v2zm6-4h12V7H9v2zM3 3v2h18V3H3z"/></svg>
            </button>
            <button type="button" class="tb-btn" (click)="exec('justifyFull')" [class.active]="formatState.align === 'justify'" title="Justificar">
              <svg class="tb-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M3 21h18v-2H3v2zm0-4h18v-2H3v2zm0-4h18v-2H3v2zm0-4h18V7H3v2zm0-6v2h18V3H3z"/></svg>
            </button>
          </div>

          <div class="toolbar-divider" aria-hidden="true"></div>

          <div class="toolbar-group" aria-label="Insertar">
            <button type="button" class="tb-btn" (click)="exec('insertHorizontalRule')" title="Insertar línea separadora">
              <svg class="tb-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M4 11h16v2H4z"/></svg>
            </button>
            <button type="button" class="tb-btn" (click)="insertLink()" title="Insertar enlace">
              <svg class="tb-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M3.9 12c0-1.71 1.39-3.1 3.1-3.1h4V7H7c-2.76 0-5 2.24-5 5s2.24 5 5 5h4v-1.9H7c-1.71 0-3.1-1.39-3.1-3.1zM8 13h8v-2H8v2zm9-6h-4v1.9h4c1.71 0 3.1 1.39 3.1 3.1s-1.39 3.1-3.1 3.1h-4V17h4c2.76 0 5-2.24 5-5s-2.24-5-5-5z"/></svg>
            </button>
            <button type="button" class="tb-btn" (click)="exec('removeFormat')" title="Limpiar formato">
              <svg class="tb-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M3.27 5 2 6.27l6.97 6.97L6.5 19h3l1.57-3.66L16.73 21 18 19.73 3.55 5.27 3.27 5zM6 5v.01H6.01V5H6z"/></svg>
            </button>
          </div>

          <div class="toolbar-divider" aria-hidden="true"></div>

          <div class="toolbar-group" aria-label="Mover bloque">
            <button type="button" class="tb-btn" (click)="moverBloque('up')" title="Mover bloque arriba (Ctrl+Shift+↑)">
              <svg class="tb-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M4 12l1.41 1.41L11 7.83V20h2V7.83l5.58 5.59L20 12l-8-8-8 8z"/></svg>
            </button>
            <button type="button" class="tb-btn" (click)="moverBloque('down')" title="Mover bloque abajo (Ctrl+Shift+↓)">
              <svg class="tb-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M20 12l-1.41-1.41L13 17.17V4h-2v13.17l-5.58-5.59L4 12l8 8 8-8z"/></svg>
            </button>
          </div>
        </div>
      </div>

      @if (embedded) {
        <div class="editor-hint" role="note">
          <span class="hint-icon">Tip</span>
          Títulos <strong>Principal</strong> / <strong>T2</strong> / <strong>T3</strong>:
          selecciona una o varias líneas y elige el tipo.
          Usa <strong>Uniforme</strong> si ves letras de distinto tamaño.
        </div>
      }

      <!-- Editor Area (SIN [innerHTML] para NO pisar el cursor) -->
      <div
        #editorEl
        class="editor-content"
        contenteditable="true"
        (input)="onInput()"
        (keydown)="onKeydown($event)"
        (paste)="onPaste($event)"
        (focus)="onFocus()"
        (blur)="onBlur()"
        (mouseup)="onSelectionChange()"
        (keyup)="onSelectionChange()"
        [attr.spellcheck]="spellcheck"
        [attr.data-placeholder]="placeholder"
      ></div>

      <!-- Footer -->
      @if (showFooter) {
        <div class="editor-footer">
          <div class="footer-left">
            <span class="ms-icon footer-icon" aria-hidden="true">text_fields</span>
            <span class="char-count">
              {{ charCount }} caracteres
              <span class="word-count">· {{ wordCount }} palabras</span>
            </span>
          </div>
          <div class="footer-right">
            @if (lastSaved) {
              <span class="save-indicator">
                <span class="ms-icon check-icon" aria-hidden="true">check_circle</span>
                Guardado · {{ lastSaved }}
              </span>
            }
            <button type="button" class="btn-save" (click)="onSave.emit(getContent())" [disabled]="disabled">
              <span class="ms-icon" aria-hidden="true">save</span>
              Guardar
            </button>
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    :host { display: flex; flex-direction: column; height: 100%; min-height: 0; }

    .sr-only {
      position: absolute;
      width: 1px;
      height: 1px;
      padding: 0;
      margin: -1px;
      overflow: hidden;
      clip: rect(0, 0, 0, 0);
      white-space: nowrap;
      border: 0;
    }

    .editor-wrapper {
      display: flex;
      flex-direction: column;
      height: 100%;
      min-height: 0;
      background: #ffffff;
      border-radius: 12px;
      border: 1px solid #dce9ff;
      overflow: hidden;
      transition: border-color 0.2s, box-shadow 0.2s;
      box-shadow: 0 1px 3px rgba(11, 28, 48, 0.04);
    }

    .editor-wrapper.embedded {
      border-radius: 10px;
      border-color: #c2c6d2;
      box-shadow: none;
    }

    .editor-hint {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 0.35rem 0.5rem;
      padding: 0.45rem 0.75rem;
      font-size: 11px;
      line-height: 1.45;
      color: #424751;
      background: #eff4ff;
      border-bottom: 1px solid #dce9ff;
      .hint-icon {
        color: #00356a;
        font-weight: 700;
        flex-shrink: 0;
        background: #d6e3ff;
        border-radius: 999px;
        width: 1.1rem;
        height: 1.1rem;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        font-size: 10px;
      }
      strong { color: #00356a; font-weight: 700; background: #d6e3ff; padding: 0 0.35rem; border-radius: 4px; }
    }

    .editor-wrapper.focused {
      border-color: #00356a;
      box-shadow: 0 0 0 3px rgba(0, 53, 106, 0.1);
    }

    .editor-wrapper.disabled { opacity: 0.6; pointer-events: none; }

    .editor-toolbar {
      flex-shrink: 0;
      background: #f8f9ff;
      border-bottom: 1px solid #dce9ff;
      user-select: none;
    }

    .toolbar-scroll {
      display: flex;
      align-items: center;
      gap: 0.15rem;
      padding: 0.5rem 0.65rem;
      overflow-x: auto;
      overflow-y: hidden;
      scrollbar-width: thin;
    }

    .toolbar-scroll::-webkit-scrollbar { height: 4px; }
    .toolbar-scroll::-webkit-scrollbar-thumb {
      background: #c2c6d2;
      border-radius: 999px;
    }

    .toolbar-group {
      display: flex;
      align-items: center;
      gap: 0.15rem;
      flex-shrink: 0;
    }

    .toolbar-divider {
      width: 1px;
      height: 24px;
      background: #c2c6d2;
      margin: 0 0.2rem;
      flex-shrink: 0;
    }

    .tb-btn {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 32px;
      height: 32px;
      padding: 0;
      background: transparent;
      border: 1px solid transparent;
      border-radius: 8px;
      color: #424751;
      cursor: pointer;
      transition: background 0.12s, color 0.12s, border-color 0.12s;
      flex-shrink: 0;
    }

    .tb-btn:hover {
      background: #e5eeff;
      color: #0b1c30;
    }

    .tb-btn.active {
      background: rgba(0, 53, 106, 0.1);
      border-color: rgba(0, 53, 106, 0.18);
      color: #00356a;
    }

    .tb-select-wrap { display: flex; align-items: center; }

    .toolbar-headings {
      gap: 0.2rem;
      padding: 0.15rem 0.25rem;
      background: #eff4ff;
      border-radius: 8px;
      border: 1px solid #dce9ff;
    }

    .toolbar-mini-label {
      font-size: 10px;
      font-weight: 700;
      color: #00356a;
      text-transform: uppercase;
      letter-spacing: 0.03em;
      padding: 0 0.15rem 0 0.25rem;
      white-space: nowrap;
    }

    .tb-selection-badge {
      font-size: 10px;
      font-weight: 600;
      color: #00356a;
      background: #e8f0fa;
      border: 1px solid #c5d9ef;
      border-radius: 999px;
      padding: 2px 8px;
      white-space: nowrap;
    }

    .tb-heading {
      height: 28px;
      padding: 0 0.55rem;
      border: 1px solid transparent;
      border-radius: 6px;
      background: transparent;
      font-size: 11px;
      font-weight: 700;
      font-family: 'Hanken Grotesk', sans-serif;
      color: #424751;
      cursor: pointer;
      white-space: nowrap;
      transition: background 0.12s, color 0.12s, border-color 0.12s;
    }

    .tb-heading:hover {
      background: #ffffff;
      border-color: #c2c6d2;
      color: #00356a;
    }

    .tb-heading.active {
      background: #00356a;
      border-color: #00356a;
      color: #ffffff;
    }

    .tb-select {
      height: 32px;
      min-width: 168px;
      border: 1px solid #c2c6d2;
      border-radius: 8px;
      background: #ffffff;
      font-size: 12px;
      font-family: 'Hanken Grotesk', sans-serif;
      color: #424751;
      cursor: pointer;
      outline: none;
      padding: 0 0.55rem;
    }

    .tb-select-sm {
      min-width: 72px;
      width: 72px;
    }

    .tb-btn .tb-icon {
      width: 18px;
      height: 18px;
      display: block;
      fill: currentColor;
      pointer-events: none;
      flex-shrink: 0;
    }

    .tb-select:hover { border-color: #727782; }
    .tb-select:focus {
      border-color: #00356a;
      box-shadow: 0 0 0 2px rgba(0, 53, 106, 0.1);
    }

    .footer-icon { font-size: 16px; color: #727782; }

    .editor-content {
      flex: 1;
      min-height: 0;
      padding: 1.25rem 1.5rem;
      overflow-y: auto;
      overflow-x: hidden;
      overscroll-behavior: contain;
      -webkit-overflow-scrolling: touch;
      outline: none;
      font-family: 'Hanken Grotesk', 'Inter', sans-serif;
      font-size: 15px;
      line-height: 1.65;
      color: #0b1c30;
      min-height: 200px;
      cursor: text;
      background: #ffffff;
    }

    .editor-wrapper.embedded .editor-content {
      font-size: 15px;
      overscroll-behavior: contain;
    }

    .editor-wrapper.embedded {
      height: 100%;
      max-height: 100%;
    }

    .editor-wrapper.embedded .editor-content h1,
    .editor-wrapper.embedded .editor-content h2,
    .editor-wrapper.embedded .editor-content h3,
    .editor-wrapper.embedded .editor-content h4,
    .editor-wrapper.embedded .editor-content p,
    .editor-wrapper.embedded .editor-content li {
      font-size: inherit;
      line-height: inherit;
    }

    .editor-wrapper.embedded .editor-content h1,
    .editor-wrapper.embedded .editor-content h2 {
      font-weight: 700;
      margin: 0.85em 0 0.35em;
    }

    .editor-wrapper.embedded .editor-content h3,
    .editor-wrapper.embedded .editor-content h4 {
      font-weight: 600;
      margin: 0.65em 0 0.25em;
    }

    .editor-content [style*="text-align: justify"],
    .editor-content [style*="text-align:justify"] {
      text-align: justify !important;
    }

    .editor-content section.doc-apartado {
      margin: 1.25rem 0;
      padding-bottom: 0.75rem;
      border-bottom: 1px dashed #dce9ff;
    }

    .editor-content section.doc-apartado:last-child {
      border-bottom: none;
    }

    .editor-wrapper.embedded .editor-content p {
      font-weight: 400;
      margin: 0.45em 0;
    }

    .editor-content:empty::before {
      content: attr(data-placeholder);
      color: #727782;
      pointer-events: none;
    }

    .editor-content h1 { font-size: 1.5rem; font-weight: 800; color: #0b1c30; margin: 1.2em 0 0.5em; line-height: 1.3; }
    .editor-content h2 { font-size: 1.25rem; font-weight: 700; color: #0b1c30; margin: 1em 0 0.4em; line-height: 1.35; }
    .editor-content h3 { font-size: 1.08rem; font-weight: 600; color: #0b1c30; margin: 0.8em 0 0.3em; line-height: 1.4; }
    .editor-content h4 { font-size: 1rem; font-weight: 600; color: #424751; margin: 0.7em 0 0.25em; line-height: 1.4; }
    .editor-content .doc-indice {
      background: #eff4ff;
      border: 1px solid #dce9ff;
      border-radius: 10px;
      padding: 1rem 1.15rem;
      margin: 0 0 1.25rem;
    }
    .editor-content .doc-indice-nota {
      font-size: 0.78rem;
      color: #727782;
      margin: 0.25rem 0 0.75rem;
    }
    .editor-content .doc-indice-lista {
      margin: 0;
      padding-left: 1.25rem;
      columns: 2;
      column-gap: 1.5rem;
    }
    .editor-content .doc-indice-lista li {
      margin: 0.2rem 0;
      font-size: 0.82rem;
      line-height: 1.45;
      break-inside: avoid;
    }
    .editor-content .doc-indice-lista .indice-nivel-1 { margin-left: 0.75rem; }
    .editor-content .doc-indice-lista .indice-nivel-2 { margin-left: 1.25rem; }
    .editor-content .doc-indice-lista .indice-nivel-3 { margin-left: 1.75rem; }
    .editor-content .doc-seccion { margin-bottom: 1.5rem; }
    .editor-content .doc-intro { margin-bottom: 1.25rem; }
    .editor-content p { margin: 0.55em 0; }
    .editor-content ul, .editor-content ol { margin: 0.45em 0; padding-left: 1.5em; }
    .editor-content li { margin: 0.15em 0; }
    .editor-content a { color: #00356a; text-decoration: underline; }

    .editor-content .tts-title-active {
      background: #ffe082;
      color: #1a1a1a;
      border-radius: 4px;
      padding: 0.12rem 0.4rem;
      box-shadow: 0 0 0 2px rgba(245, 158, 11, 0.45);
      transition: background 0.2s ease;
    }

    .editor-content .tts-reading-line {
      position: relative;
    }

    .editor-content .tts-reading-line::before {
      content: '';
      position: absolute;
      left: -10px;
      top: 0.1em;
      bottom: 0.1em;
      width: 4px;
      background: linear-gradient(180deg, #f59e0b, #fbbf24);
      border-radius: 3px;
      box-shadow: 0 0 8px rgba(245, 158, 11, 0.45);
    }

    .editor-content mark.tts-cursor {
      background: #f59e0b;
      color: #fff;
      border-radius: 2px;
      padding: 0 1px;
      font-weight: 700;
      letter-spacing: 0.02em;
      box-shadow: 0 0 6px rgba(245, 158, 11, 0.55);
    }

    .editor-content blockquote {
      border-left: 3px solid #00356a;
      margin: 0.6em 0;
      padding: 0.4em 1em;
      color: #424751;
      background: #f8f9ff;
      border-radius: 0 8px 8px 0;
    }
    .editor-content pre {
      background: #eff4ff;
      padding: 0.75em 1em;
      border-radius: 8px;
      font-family: 'JetBrains Mono', monospace;
      font-size: 0.82rem;
      overflow-x: auto;
    }

    .editor-footer {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 0.45rem 0.85rem;
      background: #f8f9ff;
      border-top: 1px solid #dce9ff;
      flex-shrink: 0;
    }

    .footer-left { display: flex; align-items: center; gap: 0.35rem; }
    .footer-icon { font-size: 16px; color: #727782; }
    .char-count { font-size: 0.72rem; color: #727782; }
    .word-count { color: #727782; opacity: 0.85; }
    .footer-right { display: flex; align-items: center; gap: 0.5rem; }
    .save-indicator {
      display: flex;
      align-items: center;
      gap: 0.2rem;
      font-size: 0.68rem;
      color: #059669;
      font-weight: 500;
    }
    .check-icon { font-size: 14px; color: #059669; }
    .btn-save {
      display: flex;
      align-items: center;
      gap: 0.3rem;
      padding: 0.35rem 0.75rem;
      background: #00356a;
      color: #ffffff;
      border: none;
      border-radius: 8px;
      font-size: 0.72rem;
      font-weight: 600;
      cursor: pointer;
      transition: all 0.15s;
      font-family: 'Hanken Grotesk', sans-serif;
    }
    .btn-save:hover:not(:disabled) { background: #004b93; }
    .btn-save:disabled { opacity: 0.45; cursor: not-allowed; }
    .btn-save .ms-icon { font-size: 16px; }
  `],
})
export class EditorTextoRicoComponent implements ControlValueAccessor, AfterViewInit, OnDestroy {
  @ViewChild('editorEl') editorEl!: ElementRef<HTMLDivElement>;

  @Input() placeholder = 'Escribe aquí...';
  @Input() spellcheck = true;
  @Input() disabled = false;
  @Input() lastSaved: string | null = null;
  @Input() embedded = false;
  @Input() showFooter = true;

  @Output() onSave = new EventEmitter<string>();
  @Output() onChange = new EventEmitter<string>();
  /** HTML actual al salir del editor (sincroniza índice sin esperar debounce). */
  @Output() onBlurContent = new EventEmitter<string>();

  focused = false;
  charCount = 0;
  wordCount = 0;
  internalValue = '';
  /** Etiqueta del bloque actual (P, H1, H2, H3) */
  currentBlockTag = 'P';
  /** Varios bloques seleccionados con distinto tipo */
  blockTagMixed = false;
  /** Cantidad de bloques (párrafos/títulos) en la selección */
  selectedBlockCount = 0;
  fontSizePx = 15;

  formatState = {
    bold: false,
    italic: false,
    underline: false,
    strike: false,
    h1: false,
    h2: false,
    h3: false,
    paragraph: false,
    ul: false,
    ol: false,
    align: 'left' as string,
  };

  private onChangeFn: (value: string) => void = () => {};
  private onTouchedFn: () => void = () => {};
  private mutationObserver: MutationObserver | null = null;
  /** Bandera: true mientras el usuario escribe, para NO pisar el DOM */
  private isUserInput = false;
  private emitTimer?: ReturnType<typeof setTimeout>;
  private formatStateFrame?: number;
  private readonly emitDelayMs = 450;

  ngAfterViewInit(): void {
    // Inicializar el DOM con el valor
    this.editorEl.nativeElement.innerHTML = this.internalValue;

    // Solo cambios estructurales (pegar, títulos); el texto se maneja en onInput.
    this.mutationObserver = new MutationObserver(() => this.scheduleFormatStateUpdate());
    this.mutationObserver.observe(this.editorEl.nativeElement, {
      childList: true,
      subtree: true,
    });
  }

  ngOnDestroy(): void {
    this.mutationObserver?.disconnect();
    if (this.emitTimer) clearTimeout(this.emitTimer);
    if (this.formatStateFrame) cancelAnimationFrame(this.formatStateFrame);
  }

  // ===== ControlValueAccessor =====
  writeValue(value: string): void {
    this.internalValue = value || '';
    if (this.editorEl && !this.isUserInput) {
      this.applyHtmlToEditor(this.internalValue);
    }
    this.updateCounts();
  }

  /** Actualiza el DOM del editor (p. ej. tras auto-estructurar). */
  setContent(html: string, emit = false): void {
    this.internalValue = html || '';
    if (this.editorEl) {
      this.applyHtmlToEditor(this.internalValue);
    }
    this.updateCounts();
    this.updateFormatState();
    if (emit) {
      this.onChangeFn(this.internalValue);
      this.onChange.emit(this.internalValue);
    }
  }

  private applyHtmlToEditor(html: string): void {
    if (!this.editorEl) return;
    const limpio = this.sanitizeHtml(html);
    const currentHtml = this.editorEl.nativeElement.innerHTML;
    if (currentHtml !== limpio) {
      this.editorEl.nativeElement.innerHTML = limpio;
    }
    this.editorEl.nativeElement.style.fontSize = `${this.fontSizePx}px`;
    if (this.embedded) {
      this.normalizarTamanoDocumento(false);
    }
  }

  private sanitizeHtml(html: string): string {
    if (!html) return '';
    return html
      .replace(/<font[^>]*>/gi, '')
      .replace(/<\/font>/gi, '')
      .replace(/\s*font-size:\s*[^;"']+;?/gi, '')
      .replace(/\s*line-height:\s*[^;"']+;?/gi, '');
  }

  applyFontSize(px: number): void {
    this.fontSizePx = px;
    if (!this.editorEl) return;
    this.editorEl.nativeElement.style.fontSize = `${px}px`;
    this.editorEl.nativeElement.focus();
    this.emitContent();
  }

  normalizarTamanoDocumento(emit = true): void {
    if (!this.editorEl) return;
    const root = this.editorEl.nativeElement;
    root.style.fontSize = `${this.fontSizePx}px`;
    root.style.lineHeight = '1.65';
    root.querySelectorAll('font').forEach((el) => {
      const span = document.createElement('span');
      span.innerHTML = el.innerHTML;
      el.replaceWith(span);
    });
    root.querySelectorAll<HTMLElement>('*').forEach((el) => {
      el.style.fontSize = '';
      el.style.lineHeight = '';
      if (el.getAttribute('style') === '') el.removeAttribute('style');
    });
    if (emit) {
      this.emitContent();
    }
  }

  registerOnChange(fn: (value: string) => void): void {
    this.onChangeFn = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouchedFn = fn;
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled = isDisabled;
    if (this.editorEl) {
      this.editorEl.nativeElement.contentEditable = isDisabled ? 'false' : 'true';
    }
  }

  // ===== Editor Commands =====
  exec(command: string, value?: string): void {
    this.editorEl.nativeElement.focus();

    const alignMap: Record<string, 'left' | 'center' | 'right' | 'justify'> = {
      justifyLeft: 'left',
      justifyCenter: 'center',
      justifyRight: 'right',
      justifyFull: 'justify',
    };
    if (alignMap[command]) {
      this.applyTextAlign(alignMap[command]);
      return;
    }

    document.execCommand(command, false, value || undefined);
    this.updateFormatState();
    this.emitContent();
  }

  /** Alineación en bloques (p/h): execCommand justifyFull suele fallar en contenteditable. */
  applyTextAlign(align: 'left' | 'center' | 'right' | 'justify'): void {
    if (!this.editorEl) return;
    this.editorEl.nativeElement.focus();

    let blocks = this.getBlocksInSelection();
    if (!blocks.length) {
      const block = this.ensureBlockAtCursor();
      if (block) blocks = [block];
    }
    if (!blocks.length) {
      this.editorEl.nativeElement.style.textAlign = align;
      this.formatState.align = align;
      this.emitContent();
      return;
    }

    for (const block of blocks) {
      block.style.textAlign = align;
    }

    this.formatState.align = align;
    this.updateFormatState();
    this.emitContent();
  }

  onBlockSelect(event: Event): void {
    const select = event.target as HTMLSelectElement;
    if (select.value === 'MIX') return;
    this.applyBlockType(select.value as BlockTag);
  }

  /** Aplica tipo de bloque sin perder la selección múltiple. */
  onHeadingMouseDown(event: MouseEvent, tag: BlockTag): void {
    event.preventDefault();
    this.applyBlockType(tag);
  }

  applyBlockType(tag: BlockTag | string): void {
    if (tag === 'MIX') return;
    const normalized = (tag || 'P').toUpperCase() as BlockTag;
    if (!['P', 'H1', 'H2', 'H3'].includes(normalized)) return;

    this.editorEl.nativeElement.focus();

    let blocks = this.getBlocksInSelection();
    if (!blocks.length) {
      const block = this.ensureBlockAtCursor();
      if (block) blocks = [block];
    }
    if (!blocks.length) return;

    const newTag = normalized.toLowerCase();
    const replacements: HTMLElement[] = [];

    for (const block of blocks) {
      if (block.tagName.toLowerCase() === newTag) {
        replacements.push(block);
        continue;
      }
      const replacement = document.createElement(newTag);
      replacement.innerHTML = block.innerHTML || '<br>';
      block.replaceWith(replacement);
      replacements.push(replacement);
    }

    if (replacements.length === 1) {
      this.placeCaretIn(replacements[0]);
    } else {
      this.selectBlockRange(replacements[0], replacements[replacements.length - 1]);
    }

    this.currentBlockTag = normalized;
    this.blockTagMixed = false;
    this.selectedBlockCount = replacements.length;
    this.updateFormatState();
    this.emitContent();
  }

  private readonly blockTags = new Set(['p', 'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'li', 'blockquote', 'pre']);

  private getBlocksInSelection(): HTMLElement[] {
    const root = this.editorEl?.nativeElement;
    if (!root) return [];

    const sel = window.getSelection();
    if (!sel || sel.rangeCount === 0 || !root.contains(sel.anchorNode)) return [];

    const range = sel.getRangeAt(0);
    if (range.collapsed) {
      const single = this.getActiveBlock();
      return single ? [single] : [];
    }

    const blocks: HTMLElement[] = [];
    const seen = new Set<HTMLElement>();

    const addBlock = (el: HTMLElement) => {
      if (!el || el === root || seen.has(el)) return;
      seen.add(el);
      blocks.push(el);
    };

    const resolveBlock = (node: Node | null): HTMLElement | null => {
      let el = node as HTMLElement | null;
      if (el?.nodeType === Node.TEXT_NODE) el = el.parentElement;
      while (el && el !== root) {
        const tag = el.tagName?.toLowerCase() || '';
        if (this.blockTags.has(tag)) return el;
        if (tag === 'div' && el.parentElement === root) return el;
        el = el.parentElement;
      }
      return null;
    };

    const walker = document.createTreeWalker(root, NodeFilter.SHOW_ELEMENT, {
      acceptNode: (node) => {
        const tag = (node as Element).tagName.toLowerCase();
        if (this.blockTags.has(tag)) return NodeFilter.FILTER_ACCEPT;
        if (tag === 'div' && (node as Element).parentElement === root) return NodeFilter.FILTER_ACCEPT;
        return NodeFilter.FILTER_SKIP;
      },
    });

    let node = walker.nextNode();
    while (node) {
      const el = node as HTMLElement;
      if (this.rangeIntersectsElement(range, el)) {
        if (el.tagName.toLowerCase() === 'div' && el.parentElement === root) {
          const children = Array.from(el.children).filter((c) =>
            this.blockTags.has(c.tagName.toLowerCase())
          ) as HTMLElement[];
          if (children.length) {
            children.forEach((c) => {
              if (this.rangeIntersectsElement(range, c)) addBlock(c);
            });
          } else {
            addBlock(el);
          }
        } else {
          addBlock(el);
        }
      }
      node = walker.nextNode();
    }

    if (!blocks.length) {
      const start = resolveBlock(range.startContainer);
      const end = resolveBlock(range.endContainer);
      if (start) addBlock(start);
      if (end && end !== start) addBlock(end);
    }

    blocks.sort((a, b) => {
      const pos = a.compareDocumentPosition(b);
      if (pos & Node.DOCUMENT_POSITION_FOLLOWING) return -1;
      if (pos & Node.DOCUMENT_POSITION_PRECEDING) return 1;
      return 0;
    });

    return blocks;
  }

  private rangeIntersectsElement(range: Range, el: HTMLElement): boolean {
    try {
      const elRange = document.createRange();
      elRange.selectNodeContents(el);
      return (
        range.compareBoundaryPoints(Range.END_TO_START, elRange) < 0 &&
        range.compareBoundaryPoints(Range.START_TO_END, elRange) > 0
      );
    } catch {
      return false;
    }
  }

  private selectBlockRange(start: HTMLElement, end: HTMLElement): void {
    const sel = window.getSelection();
    if (!sel) return;
    sel.removeAllRanges();
    const range = document.createRange();
    range.setStartBefore(start);
    range.setEndAfter(end);
    sel.addRange(range);
  }

  private getActiveBlock(): HTMLElement | null {
    const sel = window.getSelection();
    if (!sel?.anchorNode || !this.editorEl) return null;

    const blockTags = this.blockTags;
    let node: Node | null = sel.anchorNode;
    if (node.nodeType === Node.TEXT_NODE) node = node.parentNode;

    let el = node as HTMLElement | null;
    while (el && el !== this.editorEl.nativeElement) {
      const tag = el.tagName?.toLowerCase() || '';
      if (blockTags.has(tag)) return el;
      if (tag === 'div' && el.parentElement === this.editorEl.nativeElement) {
        const childBlocks = Array.from(el.children).filter((c) => blockTags.has(c.tagName.toLowerCase()));
        if (childBlocks.length === 1) return childBlocks[0] as HTMLElement;
        return el;
      }
      el = el.parentElement;
    }
    return null;
  }

  private ensureBlockAtCursor(): HTMLElement | null {
    const sel = window.getSelection();
    if (!sel || !this.editorEl) return null;

    const p = document.createElement('p');
    p.innerHTML = '<br>';

    if (sel.rangeCount > 0 && this.editorEl.nativeElement.contains(sel.anchorNode)) {
      const range = sel.getRangeAt(0);
      range.insertNode(p);
    } else {
      this.editorEl.nativeElement.appendChild(p);
    }

    this.placeCaretIn(p);
    return p;
  }

  private placeCaretIn(el: HTMLElement): void {
    const sel = window.getSelection();
    if (!sel) return;
    sel.removeAllRanges();
    const range = document.createRange();
    range.selectNodeContents(el);
    range.collapse(false);
    sel.addRange(range);
  }

  onPaste(event: ClipboardEvent): void {
    event.preventDefault();
    const text = event.clipboardData?.getData('text/plain') ?? '';
    if (!text) return;

    const lines = text.replace(/\r\n/g, '\n').split('\n');
    const html = lines
      .map((line) => {
        const t = line.trim();
        return t ? `<p>${this.escapeText(t)}</p>` : '<p><br></p>';
      })
      .join('');

    document.execCommand('insertHTML', false, html);
    this.emitContent();
    this.updateFormatState();
  }

  private escapeText(text: string): string {
    return text
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;');
  }

  insertLink(): void {
    const selection = window.getSelection();
    if (!selection || selection.isCollapsed) {
      document.execCommand('insertHTML', false, '<a href="https://">enlace</a>');
    } else {
      const url = prompt('URL del enlace:', 'https://');
      if (url) {
        document.execCommand('createLink', false, url);
      }
    }
    this.updateFormatState();
    this.emitContent();
  }

  /** Mueve el bloque donde está el cursor hacia arriba o abajo */
  moverBloque(direction: 'up' | 'down'): void {
    const sel = window.getSelection();
    if (!sel || !sel.anchorNode) return;

    let node = sel.anchorNode;
    if (node.nodeType === Node.TEXT_NODE) node = node.parentNode as Node;

    let block = node as HTMLElement;
    while (block && block !== this.editorEl.nativeElement) {
      const tag = block.tagName.toLowerCase();
      if (['p', 'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'div', 'blockquote', 'pre', 'ul', 'ol', 'li', 'hr'].includes(tag)) {
        break;
      }
      block = block.parentElement as HTMLElement;
    }
    if (!block || block === this.editorEl.nativeElement) return;

    const parent = block.parentElement as HTMLElement;
    const sibling = direction === 'up' ? block.previousElementSibling : block.nextElementSibling;
    if (!sibling) return;

    if (direction === 'up') {
      parent.insertBefore(block, sibling);
    } else {
      parent.insertBefore(sibling, block);
    }

    // Restaurar cursor
    sel.removeAllRanges();
    const range = document.createRange();
    range.setStart(block, 0);
    range.collapse(true);
    sel.addRange(range);
    block.focus();

    this.emitContent();
    this.updateFormatState();
  }

  getContent(): string {
    return this.stripMarcasLectura(this.editorEl?.nativeElement?.innerHTML || '');
  }

  private ultimoScrollTop = -1;

  /** Seguimiento TTS: título en amarillo, cursor corto y scroll automático en el editor. */
  seguirLectura(fragmentos: string[], indiceFragmento: number): void {
    this.limpiarSeguimientoLectura();
    if (!this.editorEl || indiceFragmento < 0 || indiceFragmento >= fragmentos.length) return;

    const fragmento = fragmentos[indiceFragmento]?.trim();
    if (!fragmento) return;

    const blocks = this.getBloquesEditor();
    const bloqueActivo = this.encontrarBloqueParaFragmento(blocks, fragmentos, indiceFragmento);
    if (!bloqueActivo) return;

    bloqueActivo.classList.add('tts-reading-line');
    this.resaltarTituloSeccion(blocks, bloqueActivo);

    const esTitulo = /^h[1-4]$/i.test(bloqueActivo.tagName);
    if (!esTitulo) {
      const offset = this.calcularOffsetFragmentos(fragmentos, indiceFragmento);
      if (!this.marcarCursorGlobal(offset, 3)) {
        this.marcarCursorCorto(bloqueActivo, fragmento, 3);
      }
    }

    this.desplazarAlSeguimiento(bloqueActivo);
  }

  limpiarSeguimientoLectura(): void {
    if (!this.editorEl) return;
    const root = this.editorEl.nativeElement;
    root.querySelectorAll('mark.tts-cursor, mark.tts-current').forEach((mark) => {
      const parent = mark.parentNode;
      if (!parent) return;
      while (mark.firstChild) parent.insertBefore(mark.firstChild, mark);
      mark.remove();
    });
    root.querySelectorAll('.tts-title-active, .tts-reading-line').forEach((el) => {
      el.classList.remove('tts-title-active', 'tts-reading-line');
    });
    this.ultimoScrollTop = -1;
  }

  private getBloquesEditor(): HTMLElement[] {
    if (!this.editorEl) return [];
    return Array.from(
      this.editorEl.nativeElement.querySelectorAll('p, h1, h2, h3, h4, li')
    ) as HTMLElement[];
  }

  private normalizarBusqueda(texto: string): string {
    return (texto || '').replace(/\s+/g, ' ').trim().toLowerCase();
  }

  private encontrarBloqueParaFragmento(
    blocks: HTMLElement[],
    fragmentos: string[],
    indice: number
  ): HTMLElement | null {
    const fragmento = fragmentos[indice]?.trim();
    if (!fragmento || !blocks.length) return null;

    const clave = this.normalizarBusqueda(fragmento.slice(0, Math.min(48, fragmento.length)));
    if (clave.length >= 8) {
      for (const block of blocks) {
        const txt = this.normalizarBusqueda(block.innerText || '');
        if (txt.includes(clave) || clave.includes(txt.slice(0, Math.min(40, txt.length)))) {
          return block;
        }
      }
    }

    let offset = 0;
    for (let i = 0; i < indice; i++) {
      offset += (fragmentos[i]?.length || 0) + 1;
    }

    let pos = 0;
    for (const block of blocks) {
      const len = this.normalizarBusqueda(block.innerText || '').length + 1;
      if (offset < pos + len) return block;
      pos += len;
    }

    return blocks[Math.min(indice, blocks.length - 1)] ?? null;
  }

  private calcularOffsetFragmentos(fragmentos: string[], indice: number): number {
    let offset = 0;
    for (let i = 0; i < indice; i++) {
      offset += (fragmentos[i]?.trim().length || 0) + 1;
    }
    return offset;
  }

  private marcarCursorGlobal(offset: number, longitud = 3): boolean {
    if (!this.editorEl) return false;
    const root = this.editorEl.nativeElement;
    const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
    const nodos: Text[] = [];
    let n: Node | null;
    while ((n = walker.nextNode())) nodos.push(n as Text);

    const total = nodos.reduce((acc, t) => acc + (t.textContent?.length || 0), 0);
    const inicio = Math.min(Math.max(0, offset), Math.max(0, total - 1));
    const fin = Math.min(longitud, total - inicio);
    if (fin <= 0) return false;

    const mapa = this.mapaOffsetTexto(nodos, inicio, fin);
    if (!mapa) return false;

    const range = document.createRange();
    range.setStart(mapa.startNode, mapa.startOffset);
    range.setEnd(mapa.endNode, mapa.endOffset);

    const mark = document.createElement('mark');
    mark.className = 'tts-cursor';
    try {
      range.surroundContents(mark);
    } catch {
      const contents = range.extractContents();
      mark.appendChild(contents);
      range.insertNode(mark);
    }
    return true;
  }

  private desplazarAlSeguimiento(bloqueActivo: HTMLElement): void {
    if (!this.editorEl) return;
    const container = this.editorEl.nativeElement;
    const cursor = container.querySelector('mark.tts-cursor') as HTMLElement | null;
    const ancla = cursor ?? bloqueActivo;
    const cRect = container.getBoundingClientRect();
    const aRect = ancla.getBoundingClientRect();
    const offsetTop = aRect.top - cRect.top + container.scrollTop;
    const margen = container.clientHeight * 0.28;
    const objetivo = Math.max(0, offsetTop - margen);

    if (Math.abs(objetivo - this.ultimoScrollTop) < 20 && Math.abs(objetivo - container.scrollTop) < 40) {
      return;
    }
    this.ultimoScrollTop = objetivo;
    container.scrollTo({ top: objetivo, behavior: 'smooth' });
  }

  private resaltarTituloSeccion(blocks: HTMLElement[], bloqueActivo: HTMLElement): void {
    let tituloActivo: HTMLElement | null = null;
    for (const block of blocks) {
      if (/^h[1-4]$/i.test(block.tagName)) tituloActivo = block;
      if (block === bloqueActivo) break;
    }
    tituloActivo?.classList.add('tts-title-active');
  }

  private marcarCursorCorto(root: HTMLElement, fragmento: string, longitud = 3): void {
    const busqueda = fragmento.trim();
    if (!busqueda) return;

    const clave = busqueda.slice(0, Math.min(24, busqueda.length));
    const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
    const nodos: Text[] = [];
    let n: Node | null;
    while ((n = walker.nextNode())) nodos.push(n as Text);

    const completo = nodos.map((t) => t.textContent || '').join('');
    const idx = completo.toLowerCase().indexOf(clave.slice(0, Math.min(16, clave.length)).toLowerCase());
    const inicio = idx >= 0 ? idx : 0;
    const fin = Math.min(longitud, completo.length - inicio);
    if (fin <= 0) return;

    const mapa = this.mapaOffsetTexto(nodos, inicio, fin);
    if (!mapa) return;

    const range = document.createRange();
    range.setStart(mapa.startNode, mapa.startOffset);
    range.setEnd(mapa.endNode, mapa.endOffset);

    const mark = document.createElement('mark');
    mark.className = 'tts-cursor';
    try {
      range.surroundContents(mark);
    } catch {
      const contents = range.extractContents();
      mark.appendChild(contents);
      range.insertNode(mark);
    }
  }

  private mapaOffsetTexto(nodos: Text[], inicio: number, longitud: number) {
    let pos = 0;
    let startNode: Text | null = null;
    let startOffset = 0;

    for (const n of nodos) {
      const len = n.textContent?.length || 0;
      if (startNode === null && inicio <= pos + len) {
        startNode = n;
        startOffset = Math.max(0, inicio - pos);
      }
      if (startNode && inicio + longitud <= pos + len) {
        return {
          startNode,
          startOffset,
          endNode: n,
          endOffset: Math.max(0, inicio + longitud - pos),
        };
      }
      pos += len;
    }
    return null;
  }

  private stripMarcasLectura(html: string): string {
    if (!html || (!html.includes('tts-cursor') && !html.includes('tts-current'))) return html;
    const tmp = document.createElement('div');
    tmp.innerHTML = html;
    tmp.querySelectorAll('mark.tts-cursor, mark.tts-current').forEach((mark) => {
      const text = document.createTextNode(mark.textContent || '');
      mark.replaceWith(text);
    });
    return tmp.innerHTML;
  }

  /** Sincroniza de inmediato el HTML (p. ej. antes de lectura por voz). */
  flushContent(): string {
    if (this.emitTimer) {
      clearTimeout(this.emitTimer);
      this.emitTimer = undefined;
    }
    this.flushEmit();
    return this.getContent();
  }

  clear(): void {
    if (this.editorEl) {
      this.editorEl.nativeElement.innerHTML = '';
    }
    this.internalValue = '';
    this.updateCounts();
    this.emitContent();
  }

  /** Throttle de estado de toolbar (usado desde la plantilla). */
  onSelectionChange(): void {
    this.scheduleFormatStateUpdate();
  }

  private scheduleFormatStateUpdate(): void {
    if (this.formatStateFrame) return;
    this.formatStateFrame = requestAnimationFrame(() => {
      this.formatStateFrame = undefined;
      this.updateFormatState();
      if (this.showFooter) this.updateCounts();
    });
  }

  private scheduleEmit(): void {
    if (this.emitTimer) clearTimeout(this.emitTimer);
    this.emitTimer = setTimeout(() => this.flushEmit(), this.emitDelayMs);
  }

  private flushEmit(): void {
    this.emitTimer = undefined;
    const content = this.getContent();
    this.internalValue = content;
    this.isUserInput = true;
    this.onChangeFn(content);
    this.onChange.emit(content);
    this.isUserInput = false;
    if (this.showFooter) this.updateCounts();
  }

  private emitContent(): void {
    this.flushEmit();
  }

  onInput(): void {
    this.scheduleEmit();
  }

  onKeydown(event: KeyboardEvent): void {
    if ((event.ctrlKey || event.metaKey) && event.altKey && /^[123]$/.test(event.key)) {
      event.preventDefault();
      const map: Record<string, BlockTag> = { '1': 'H1', '2': 'H2', '3': 'H3' };
      this.applyBlockType(map[event.key]);
      return;
    }
    // Ctrl+S para guardar
    if ((event.ctrlKey || event.metaKey) && event.key === 's') {
      event.preventDefault();
      this.onSave.emit(this.getContent());
    }
    // Ctrl+Shift+↑ o ↓ para mover bloque
    if ((event.ctrlKey || event.metaKey) && event.shiftKey && event.key === 'ArrowUp') {
      event.preventDefault();
      this.moverBloque('up');
    }
    if ((event.ctrlKey || event.metaKey) && event.shiftKey && event.key === 'ArrowDown') {
      event.preventDefault();
      this.moverBloque('down');
    }
  }

  onFocus(): void {
    this.focused = true;
  }

  onBlur(): void {
    this.focused = false;
    if (this.emitTimer) clearTimeout(this.emitTimer);
    this.flushEmit();
    this.onBlurContent.emit(this.getContent());
    this.onTouchedFn();
  }

  onToolbarMouseDown(event: MouseEvent): void {
    const target = event.target as HTMLElement;
    if (target.closest('select, option, input, textarea, label.tb-select-wrap')) {
      return;
    }
    event.preventDefault();
    this.editorEl.nativeElement.focus();
  }

  updateFormatState(): void {
    const blocks = this.getBlocksInSelection();
    this.selectedBlockCount = blocks.length || (this.getActiveBlock() ? 1 : 0);

    const tags = new Set(
      blocks.map((b) => {
        const t = b.tagName.toUpperCase();
        return t === 'H4' || t === 'H5' || t === 'H6' ? 'H3' : t;
      })
    );

    if (tags.size === 1) {
      const only = [...tags][0];
      this.currentBlockTag = (only === 'DIV' ? 'P' : only) as BlockTag;
      this.blockTagMixed = false;
    } else if (tags.size > 1) {
      this.blockTagMixed = true;
    } else {
      this.blockTagMixed = false;
    }

    const activeBlock = blocks[0] ?? this.getActiveBlock();
    const tag = activeBlock?.tagName?.toUpperCase()
      || (document.queryCommandValue('formatBlock') || 'p').replace(/[<>]/g, '').toUpperCase();
    this.formatState = {
      bold: document.queryCommandState('bold'),
      italic: document.queryCommandState('italic'),
      underline: document.queryCommandState('underline'),
      strike: document.queryCommandState('strikeThrough'),
      h1: !this.blockTagMixed && this.currentBlockTag === 'H1',
      h2: !this.blockTagMixed && this.currentBlockTag === 'H2',
      h3: !this.blockTagMixed && this.currentBlockTag === 'H3',
      paragraph: !this.blockTagMixed && (this.currentBlockTag === 'P' || tag === 'DIV'),
      ul: document.queryCommandState('insertUnorderedList'),
      ol: document.queryCommandState('insertOrderedList'),
      align: this.detectAlignment(),
    };

    if (!this.blockTagMixed) {
      if (this.formatState.h1) this.currentBlockTag = 'H1';
      else if (this.formatState.h2) this.currentBlockTag = 'H2';
      else if (this.formatState.h3) this.currentBlockTag = 'H3';
      else this.currentBlockTag = 'P';
    }
  }

  private detectAlignment(): string {
    const block = this.getActiveBlock();
    const inline = block?.style?.textAlign;
    if (inline && ['left', 'center', 'right', 'justify'].includes(inline)) {
      return inline;
    }
    if (document.queryCommandState('justifyFull')) return 'justify';
    if (document.queryCommandState('justifyCenter')) return 'center';
    if (document.queryCommandState('justifyRight')) return 'right';
    if (document.queryCommandState('justifyLeft')) return 'left';
    return 'left';
  }

  private updateCounts(): void {
    if (!this.showFooter || !this.editorEl) return;
    const text = this.editorEl.nativeElement.innerText || '';
    this.charCount = text.length;
    this.wordCount = text.trim() ? text.trim().split(/\s+/).length : 0;
  }
}

type BlockTag = 'P' | 'H1' | 'H2' | 'H3';
