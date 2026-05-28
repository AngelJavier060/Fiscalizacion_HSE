export interface SeccionTexto {
  nivel: string;
  titulo: string;
  indiceInicio: number;
  indiceFin: number;
  profundidad: number;
  /** Texto plano listo para TTS (solo en secciones del editor) */
  textoLectura?: string;
}

export interface ResultadoEstructura {
  textoConSaltos: string;
  indice: SeccionTexto[];
  seccionesNav: SeccionTexto[];
  html: string;
}

/** Prepara saltos de línea en texto PDF extraído como bloque continuo. */
export function prepararTextoConSaltos(texto: string): string {
  if (!texto?.trim()) return '';

  let t = texto.replace(/\r\n/g, '\n').replace(/\r/g, '\n');
  t = t.replace(/([\p{L}])-\s*\n\s*([\p{L}])/gmu, '$1$2');

  t = t.replace(/(?<=\S)\s+(\d{1,2}\.\s+EST[ÁA]NDAR\s+DE\s+)/gi, '\n\n$1');
  t = t.replace(/(?<=[.;!?])\s+(EST[ÁA]NDAR\s+DE\s+)/gi, '\n\n$1');
  t = t.replace(/(?<=[^\n])\s+(EST[ÁA]NDAR\s+DE\s+)/gi, '\n\n$1');
  t = t.replace(/(?<!\n)(\d{1,2}\.\s+EST[ÁA]NDAR\s+DE\s+)/gi, '\n\n$1');
  t = t.replace(/(?<!\n)(EST[ÁA]NDAR\s+DE\s+)/gi, '\n\n$1');
  t = t.replace(/(?<![\d.])(\d+\.\d+(?:\.\d+)*)\s+(?=[A-ZÁÉÍÓÚÑ"(])/g, '\n\n$1 ');
  t = t.replace(/\s+((?:CAP[ÍI]TULO|SECCI[ÓO]N)\s+[\dIVXLC]+)/gi, '\n\n$1');
  t = t.replace(/(?<=[.;!?])\s+(?=[A-ZÁÉÍÓÚÑ"(])/g, '\n');

  t = t.replace(/[ \t]+/g, ' ');
  t = t.replace(/^\s+/gm, '');
  t = t.replace(/\n{3,}/g, '\n\n');
  return quitarArtefactosPagina(t.trim());
}

/** Elimina números de página y artefactos típicos del PDF extraído. */
export function quitarArtefactosPagina(texto: string): string {
  if (!texto?.trim()) return '';
  let t = texto.replace(/\r\n/g, '\n').replace(/\r/g, '\n');
  t = t.replace(/^\s*\d{1,4}\s*$/gm, '');
  t = t.replace(/^\s*(?:p[áa]gina|page)\s+\d+\s*(?:de|of)\s*\d+\s*$/gim, '');
  t = t.replace(/^\s*-\s*\d+\s*-\s*$/gm, '');
  t = t.replace(/\n{3,}/g, '\n\n');
  return t.trim();
}

/** Texto listo para leer: quita solo basura de PDF, conserva números y saltos de sección. */
export function normalizarTextoLectura(texto: string): string {
  if (!texto?.trim()) return '';
  if (!texto.includes('\n\n')) {
    return quitarArtefactosPagina(texto.replace(/[ \t]+/g, ' ').trim());
  }
  return texto
    .split(/\n\n+/)
    .map((parte) => quitarArtefactosPagina(parte.replace(/[ \t]+/g, ' ').trim()))
    .filter(Boolean)
    .join('\n\n');
}

/** @deprecated Usar normalizarTextoLectura para TTS fluido */
export function limpiarTextoParaVoz(texto: string): string {
  return normalizarTextoLectura(texto);
}

/** Título para lectura: conserva numeración (1. ESTÁNDAR…). */
export function limpiarTituloParaVoz(titulo: string): string {
  return (titulo || '').replace(/\s+/g, ' ').trim();
}

function esBloqueIndice(s: SeccionTexto): boolean {
  if (s.nivel === 'IDX') return true;
  const t = (s.titulo || '').trim();
  if (/^índice$/i.test(t)) return true;
  const c = s.textoLectura || '';
  const refs = c.match(/\d+\.\s+ESTÁNDAR/gi) || [];
  return refs.length >= 3 && c.length < 1200;
}

/** Texto TTS de una sección del editor (no PDF crudo). */
export function construirTextoLecturaSeccion(
  s: SeccionTexto,
  opts?: { soloCuerpo?: boolean }
): string {
  const titulo = limpiarTituloParaVoz(s.titulo);
  const cuerpo = normalizarTextoLectura(s.textoLectura || '');
  if (!titulo && !cuerpo) return '';
  if (opts?.soloCuerpo) return cuerpo || titulo;
  if (!cuerpo) return titulo;
  if (!titulo || /^introducci[oó]n$/i.test(titulo)) return cuerpo;
  const pref = titulo.toLowerCase().slice(0, 18);
  if (pref.length >= 8 && cuerpo.toLowerCase().startsWith(pref)) return cuerpo;
  // Doble salto = pausa larga entre título y cuerpo en TTS
  return `${titulo}.\n\n${cuerpo}`;
}

/** Documento completo para TTS: solo secciones del editor, sin índice ni números de página. */
export function construirTextoLecturaDocumento(secciones: SeccionTexto[]): string {
  const partes: string[] = [];
  for (const s of secciones) {
    if (esBloqueIndice(s)) continue;
    const frag = construirTextoLecturaSeccion(s);
    if (frag.trim()) partes.push(frag);
  }
  // Separador de sección → pausa larga en el servicio de voz
  return partes.join('\n\n');
}

function escapeHtml(s: string): string {
  return s
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function finDeLinea(texto: string, pos: number): number {
  const idx = texto.indexOf('\n', Math.max(0, pos));
  return idx >= 0 ? idx + 1 : texto.length;
}

function parrafosHtml(texto: string): string {
  if (!texto?.trim()) return '';

  let bloques: string[];
  if (texto.includes('\n\n')) bloques = texto.split(/\n\n+/);
  else if (texto.includes('\n')) bloques = texto.split(/\n+/);
  else bloques = texto.split(/(?<=[.;!?])\s+(?=[A-ZÁÉÍÓÚÑ"(])/);

  const out: string[] = [];
  let acum = '';

  for (const bloque of bloques) {
    const linea = bloque.trim().replace(/\s*\n\s*/g, ' ');
    if (!linea) continue;

    if (linea.length < 140 && !linea.endsWith('.') && !linea.endsWith(':')) {
      if (acum) {
        out.push(`<p>${escapeHtml(acum.trim())}</p>`);
        acum = '';
      }
      out.push(`<p>${escapeHtml(linea)}</p>`);
      continue;
    }

    acum = acum ? `${acum} ${linea}` : linea;
    if (acum.length >= 420) {
      out.push(`<p>${escapeHtml(acum.trim())}</p>`);
      acum = '';
    }
  }
  if (acum) out.push(`<p>${escapeHtml(acum.trim())}</p>`);
  return out.join('\n') + (out.length ? '\n' : '');
}

/** H1 = libro · H2 = cada estándar · H3/H4 = apartados internos. */
function etiquetaHtml(sec: SeccionTexto): string {
  const n = (sec.nivel || '').toUpperCase();
  const titulo = (sec.titulo || '').trim();
  const tituloUp = titulo.toUpperCase();

  if (n === 'DOC' || /^ESTÁNDARES\s+QUE\s+SALVAN/i.test(tituloUp)) {
    return 'h1';
  }
  if (
    n === 'EST' ||
    n === 'IDX' ||
    n === 'CAP' ||
    tituloUp.includes('ESTÁNDAR DE') ||
    /^\d+\.\s+ESTÁNDAR/i.test(titulo)
  ) {
    return 'h2';
  }
  if (
    n === 'SUB' ||
    /^(OBJETIVO|ALCANCE|REQUISITOS|CONTROLES|CAPACITACIÓN|DEFINICIONES)/i.test(titulo)
  ) {
    return 'h3';
  }
  if (n === 'NUM' && sec.profundidad <= 0) return 'h2';
  if (n === 'NUM' && sec.profundidad === 1) return 'h3';
  if (sec.profundidad <= 1) return 'h3';
  return 'h4';
}

function normalizarTitulo(t: string): string {
  return t
    .replace(/^\d+\.\s+/, '')
    .replace(/\s+\d{1,4}\s*$/, '')
    .replace(/\s+/g, ' ')
    .trim()
    .toLowerCase();
}

/** Quita líneas iniciales que repiten el título (evita leerlo dos veces). */
function limpiarCuerpoSeccion(cuerpo: string, titulo: string): string {
  if (!cuerpo?.trim()) return cuerpo;
  const core = normalizarTitulo(titulo);
  const lines = cuerpo.split('\n');
  while (lines.length) {
    const line = lines[0].trim();
    if (!line) {
      lines.shift();
      continue;
    }
    const lineCore = normalizarTitulo(line);
    if (lineCore === core || core.includes(lineCore) || lineCore.includes(core)) {
      lines.shift();
      continue;
    }
    break;
  }
  return lines.join('\n').trim();
}

function generarIntroHtml(intro: string): string {
  if (!intro?.trim()) return '';
  const lineas = intro.split(/\n+/).map((l) => l.trim()).filter(Boolean);
  const out: string[] = [];
  for (const linea of lineas) {
    if (/^ESTÁNDARES\s/i.test(linea) && linea.length < 80) {
      out.push(`<h1>${escapeHtml(linea)}</h1>`);
    } else if (/^\d+\.\s+ESTÁNDAR/i.test(linea)) {
      const titulo = linea.replace(/\s+\d{1,4}\s*$/, '').trim();
      out.push(`<h2>${escapeHtml(titulo)}</h2>`);
    } else {
      out.push(`<p>${escapeHtml(linea)}</p>`);
    }
  }
  return out.join('\n');
}

function deduplicarEncabezados(
  encs: { titulo: string; pos: number; end: number; tipo: string; prof: number }[]
): typeof encs {
  encs.sort((a, b) => a.pos - b.pos);
  return encs.filter((e, i, all) => {
    if (e.tipo !== 'EST') return true;
    const core = normalizarTitulo(e.titulo);
    return !all.some(
      (o, j) =>
        j !== i &&
        o.tipo === 'IDX' &&
        normalizarTitulo(o.titulo) === core
    );
  });
}
/** Secciones con cuerpo real (no solo líneas del índice al inicio del PDF). */
function seccionesPrincipalesCuerpo(todas: SeccionTexto[]): SeccionTexto[] {
  const porEstandar = todas.filter((s) => {
    const titulo = (s.titulo || '').toUpperCase();
    if (s.nivel === 'EST') return true;
    if (titulo.includes('ESTÁNDAR DE')) return true;
    return false;
  });
  if (porEstandar.length >= 2) {
    return deduplicarPorTitulo(porEstandar);
  }

  const conCuerpo = todas.filter((s) => {
    const largo = Math.max(0, s.indiceFin - s.indiceInicio);
    const esEstandar =
      /^\d+\.\s+ESTÁNDAR/i.test(s.titulo) ||
      s.titulo.toUpperCase().includes('ESTÁNDAR DE');
    return esEstandar && largo > 600;
  });
  if (conCuerpo.length >= 2) {
    return deduplicarPorTitulo(conCuerpo);
  }

  const vistos = new Set<string>();
  return deduplicarPorTitulo(
    todas.filter((s) => {
      const titulo = s.titulo.toUpperCase();
      if (!titulo.includes('ESTÁNDAR')) return false;
      const key = normalizarTitulo(s.titulo);
      if (vistos.has(key)) return false;
      vistos.add(key);
      return true;
    })
  );
}

function slugApartado(titulo: string): string {
  return normalizarTitulo(titulo)
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-|-$/g, '')
    .slice(0, 80) || 'apartado';
}

/** Convierte HTML del editor a texto plano conservando saltos en títulos. */
export function textoPlanoConSaltosDesdeHtml(html: string): string {
  if (!html?.trim()) return '';
  if (typeof DOMParser === 'undefined') return html.replace(/<[^>]+>/g, '\n');

  const doc = new DOMParser().parseFromString(html, 'text/html');
  doc.body.querySelectorAll('.doc-indice').forEach((el) => el.remove());

  const lineas: string[] = [];
  const hijos = doc.body.querySelectorAll('section.doc-apartado, section.doc-seccion, h1, h2, h3, h4, p, li');
  const usarHijos = hijos.length > 0 ? Array.from(hijos) : Array.from(doc.body.children);

  for (const el of usarHijos) {
    const tag = el.tagName.toLowerCase();
    const t = (el.textContent || '').replace(/\s+/g, ' ').trim();
    if (!t || /^\d{1,4}$/.test(t)) continue;
    if (/^h[1-4]$/.test(tag)) {
      lineas.push('', t, '');
    } else if (tag === 'section') {
      const h = el.querySelector('h1,h2,h3,h4');
      const ht = (h?.textContent || '').replace(/\s+/g, ' ').trim();
      if (ht) lineas.push('', ht, '');
      el.querySelectorAll('p, li').forEach((p) => {
        const pt = (p.textContent || '').replace(/\s+/g, ' ').trim();
        if (pt && pt.length > 2) lineas.push(pt);
      });
    } else {
      lineas.push(t);
    }
  }

  if (!lineas.length) {
    return prepararTextoConSaltos((doc.body.textContent || '').trim());
  }
  return prepararTextoConSaltos(lineas.join('\n'));
}

/** Mejor fuente para «Estructurar libro»: PDF plano o HTML ya editado. */
export function resolverTextoBaseParaEstructura(textoCompleto?: string, htmlEditor?: string): string {
  let base = (textoCompleto || '').trim();
  if (base.includes('<') && (base.includes('<p') || base.includes('<h'))) {
    base = textoPlanoConSaltosDesdeHtml(base);
  }

  const html = (htmlEditor || '').trim();
  if (html && htmlTieneEstructura(html)) {
    const desdeEditor = textoPlanoConSaltosDesdeHtml(html);
    if (desdeEditor.length > base.length * 0.4) {
      base = desdeEditor;
    }
  } else if (!base && html) {
    base = textoPlanoConSaltosDesdeHtml(html);
  }

  return prepararTextoConSaltos(base);
}

/** Detecta estándares en bloque continuo (PDF sin saltos de línea). */
function detectarEstandaresEnBloque(texto: string): SeccionTexto[] {
  const re =
    /(\d{1,2}\.\s+EST[ÁA]NDAR\s+DE\s+[\p{L}ÁÉÍÓÚÑ\s]{4,95}?)(?=\s+\d{1,2}\.\s+EST[ÁA]NDAR|\s+OBJETIVO\s|\s+ALCANCE\s|\s+REQUISITOS\s|$)/giu;
  const hits: { titulo: string; pos: number }[] = [];
  let m: RegExpExecArray | null;
  while ((m = re.exec(texto)) !== null) {
    const titulo = m[1].trim().replace(/\s+/g, ' ');
    if (titulo.length >= 12) {
      hits.push({ titulo, pos: m.index });
    }
  }
  if (hits.length < 2) return [];

  return hits.map((h, i) => ({
    nivel: 'EST',
    titulo: h.titulo,
    indiceInicio: h.pos,
    indiceFin: i + 1 < hits.length ? hits[i + 1].pos : texto.length,
    profundidad: 0,
  }));
}

function deduplicarPorTitulo(secciones: SeccionTexto[]): SeccionTexto[] {
  const vistos = new Set<string>();
  const out: SeccionTexto[] = [];
  for (const s of secciones.sort((a, b) => a.indiceInicio - b.indiceInicio)) {
    const key = normalizarTitulo(s.titulo);
    if (!key || vistos.has(key)) continue;
    vistos.add(key);
    out.push(s);
  }
  return out;
}

function detectarEncabezados(texto: string): SeccionTexto[] {
  const encs: { titulo: string; pos: number; end: number; tipo: string; prof: number }[] = [];

  const patrones: { re: RegExp; map: (m: RegExpExecArray) => { titulo: string; prof: number; tipo: string } }[] = [
    {
      re: /^\s*(\d+)\.\s+(EST[ÁA]NDAR\s+DE\s+.+?)(?:\s+\d{1,4})?\s*$/gim,
      map: (m) => ({
        titulo: `${m[1]}. ${m[2].trim().replace(/\s+\d{1,4}\s*$/, '')}`,
        prof: 0,
        tipo: 'EST',
      }),
    },
    {
      re: /^\s*(ESTÁNDARES\s+QUE\s+SALVAN\s+VIDAS)\s*$/gim,
      map: (m) => ({ titulo: m[1].trim(), prof: 0, tipo: 'DOC' }),
    },
    {
      re: /^\s*(EST[ÁA]NDAR\s+DE\s+.+?)(?:\s+\d{1,4})?\s*$/gim,
      map: (m) => ({ titulo: m[1].trim().replace(/\s+\d{1,4}\s*$/, ''), prof: 0, tipo: 'EST' }),
    },
    {
      re: /^\s*(\d+(?:\.\d+){1,3})\s+(.{3,180})\s*$/gm,
      map: (m) => ({
        titulo: `${m[1]} ${m[2].trim()}`,
        prof: m[1].split('.').length - 1,
        tipo: 'NUM',
      }),
    },
    {
      re: /^\s*((?:CAP[ÍI]TULO|SECCI[ÓO]N)\s+[\dIVXLC]+\.?\s*.{3,180})\s*$/gim,
      map: (m) => ({ titulo: m[1].trim(), prof: 0, tipo: 'CAP' }),
    },
  ];

  for (const { re, map } of patrones) {
    re.lastIndex = 0;
    let m: RegExpExecArray | null;
    while ((m = re.exec(texto)) !== null) {
      const mapped = map(m);
      const dup = encs.some((e) => Math.abs(e.pos - m!.index) < 35 || e.titulo.toLowerCase() === mapped.titulo.toLowerCase());
      if (!dup) encs.push({ titulo: mapped.titulo, pos: m.index, end: m.index + m[0].length, tipo: mapped.tipo, prof: mapped.prof });
    }
  }

  encs.sort((a, b) => a.pos - b.pos);
  const unicos = deduplicarEncabezados(encs);

  let resultado = unicos.map((e, i) => ({
    nivel: e.tipo === 'NUM' ? e.titulo.split(/\s+/)[0] : e.tipo,
    titulo: e.titulo,
    indiceInicio: e.pos,
    indiceFin: i + 1 < unicos.length ? unicos[i + 1].pos : texto.length,
    profundidad: e.prof,
  }));

  const estandares = resultado.filter((s) => s.titulo.toUpperCase().includes('ESTÁNDAR'));
  if (estandares.length < 2) {
    const inline = detectarEstandaresEnBloque(texto);
    if (inline.length >= 2) resultado = inline;
  }

  return resultado;
}

function filtrarSeccionesNav(todas: SeccionTexto[]): SeccionTexto[] {
  return todas
    .filter((s) => {
      const n = (s.nivel || '').toUpperCase();
      if (n === 'IDX' || n === 'EST' || n === 'CAP') return true;
      if (s.titulo.toUpperCase().includes('ESTÁNDAR DE')) return true;
      return s.profundidad <= 2 && s.indiceFin - s.indiceInicio > 120;
    })
    .slice(0, 120);
}

function filtrarSeccionesCuerpo(todas: SeccionTexto[]): SeccionTexto[] {
  return seccionesPrincipalesCuerpo(todas);
}

function generarHtml(texto: string, indice: SeccionTexto[], _cuerpo: SeccionTexto[]): string {
  const secciones = seccionesPrincipalesCuerpo(indice.length ? indice : _cuerpo);

  if (!secciones.length) {
    return parrafosHtml(texto);
  }

  const partes: string[] = [];
  const firstStart = secciones[0].indiceInicio;
  if (firstStart > 0) {
    partes.push(`<section class="doc-intro">\n${generarIntroHtml(texto.slice(0, firstStart))}</section>\n`);
  }

  for (let i = 0; i < secciones.length; i++) {
    const sec = secciones[i];
    const nextStart = i + 1 < secciones.length ? secciones[i + 1].indiceInicio : texto.length;
    const bodyStart = finDeLinea(texto, sec.indiceInicio);
    const bodyEnd = Math.min(nextStart, texto.length);
    const cuerpo = limpiarCuerpoSeccion(texto.slice(bodyStart, bodyEnd).trim(), sec.titulo);
    const tag = etiquetaHtml(sec);
    const id = slugApartado(sec.titulo);
    partes.push(`<section class="doc-apartado doc-seccion" data-apartado-id="${escapeHtml(id)}" data-nivel="${escapeHtml(sec.nivel || 'EST')}">
<${tag}>${escapeHtml(sec.titulo.replace(/\s+\d{1,4}\s*$/, ''))}</${tag}>
${parrafosHtml(cuerpo)}
</section>
`);
  }
  return partes.join('\n').trim();
}

/** Extrae el índice y secciones de lectura desde el HTML del editor (h1–h4 + párrafos). */
export function extraerSeccionesDesdeEditor(html: string): SeccionTexto[] {
  if (!html?.trim() || typeof DOMParser === 'undefined') return [];

  const doc = new DOMParser().parseFromString(html, 'text/html');
  doc.body.querySelectorAll('.doc-indice').forEach((el) => el.remove());

  const elementos: Element[] = [];
  const walker = doc.createTreeWalker(doc.body, NodeFilter.SHOW_ELEMENT, {
    acceptNode(node) {
      const tag = (node as Element).tagName.toLowerCase();
      if (['h1', 'h2', 'h3', 'h4', 'p', 'li'].includes(tag)) {
        return NodeFilter.FILTER_ACCEPT;
      }
      return NodeFilter.FILTER_SKIP;
    },
  });
  let n = walker.nextNode();
  while (n) {
    elementos.push(n as Element);
    n = walker.nextNode();
  }

  const secciones: SeccionTexto[] = [];
  let actual: SeccionTexto | null = null;
  const intro: string[] = [];

  const cerrar = () => {
    if (!actual) return;
    const texto = (actual.textoLectura || '').trim();
    if (texto.length >= 20 || actual.titulo.length >= 8) {
      actual.textoLectura = texto;
      actual.indiceInicio = secciones.length;
      actual.indiceFin = secciones.length + 1;
      secciones.push(actual);
    }
    actual = null;
  };

  for (const el of elementos) {
    const tag = el.tagName.toLowerCase();
    const texto = (el.textContent || '').replace(/\s+/g, ' ').trim();
    if (!texto) continue;

    if (['h1', 'h2', 'h3', 'h4'].includes(tag)) {
      cerrar();
      const prof = tag === 'h1' ? 0 : tag === 'h2' ? 1 : tag === 'h3' ? 2 : 3;
      actual = {
        nivel: tag.toUpperCase(),
        titulo: limpiarTituloParaVoz(texto) || texto.trim(),
        profundidad: prof,
        indiceInicio: 0,
        indiceFin: 0,
        textoLectura: '',
      };
    } else if (actual) {
      const limpio = normalizarTextoLectura(texto);
      if (!limpio || /^\d{1,4}$/.test(limpio)) continue;
      const titleCore = normalizarTitulo(actual.titulo);
      const lineCore = normalizarTitulo(limpio);
      if (lineCore === titleCore || titleCore.includes(lineCore) || lineCore.includes(titleCore)) {
        continue;
      }
      actual.textoLectura = actual.textoLectura ? `${actual.textoLectura} ${limpio}` : limpio;
    } else {
      const limpio = normalizarTextoLectura(texto);
      if (limpio && !/^\d{1,4}$/.test(limpio)) intro.push(limpio);
    }
  }
  cerrar();

  if (intro.length) {
    secciones.unshift({
      nivel: 'INT',
      titulo: 'Introducción',
      profundidad: 0,
      indiceInicio: 0,
      indiceFin: 1,
      textoLectura: normalizarTextoLectura(intro.join(' ')),
    });
  }

  return secciones;
}

/** Normaliza títulos para emparejar índice lateral ↔ encabezados del editor. */
export function normalizarTituloLectura(t: string): string {
  return (t || '')
    .normalize('NFD')
    .replace(/\p{M}/gu, '')
    .replace(/^\d+(?:\.\d+)*\.?\s*/, '')
    .replace(/^(?:estandar\s+de|capitulo|seccion)\s+/i, '')
    .replace(/\s+\d{1,4}$/, '')
    .replace(/\s+/g, ' ')
    .trim()
    .toLowerCase();
}

/** Busca una sección ya parseada (evita re-leer todo el HTML). */
export function buscarSeccionEnLista(secciones: SeccionTexto[], tituloRef: string): SeccionTexto | undefined {
  const clave = normalizarTituloLectura(tituloRef);
  if (!clave || !secciones.length) return undefined;

  const exacta = secciones.find((s) => normalizarTituloLectura(s.titulo) === clave);
  if (exacta) return exacta;

  return secciones.find((s) => {
    const nt = normalizarTituloLectura(s.titulo);
    return nt.includes(clave) || clave.includes(nt);
  });
}

/** Busca una sección en el HTML actual del editor (fuente única para TTS). */
export function buscarSeccionEnHtmlEditor(html: string, tituloRef: string): SeccionTexto | undefined {
  return buscarSeccionEnLista(extraerSeccionesDesdeEditor(html), tituloRef);
}

/** Texto plano del HTML del editor (sin usar textoCompleto del API). */
export function textoPlanoDesdeHtml(html: string): string {
  if (!html?.trim()) return '';
  if (typeof DOMParser !== 'undefined') {
    const doc = new DOMParser().parseFromString(html, 'text/html');
    doc.body.querySelectorAll('.doc-indice').forEach((el) => el.remove());
    return (doc.body.textContent || '').replace(/\s+/g, ' ').trim();
  }
  const div = document.createElement('div');
  div.innerHTML = html;
  return (div.textContent || div.innerText || '').replace(/\s+/g, ' ').trim();
}

/** Índice lateral: títulos del editor (H1–H3) y estándares detectados. */
export function filtrarIndiceSidebar(secciones: SeccionTexto[]): SeccionTexto[] {
  const vistos = new Set<string>();
  return secciones.filter((s) => {
    const t = s.titulo.trim();
    if (t.length < 5) return false;
    const key = t.toLowerCase();
    if (vistos.has(key)) return false;
    vistos.add(key);

    if (['H1', 'H2', 'H3', 'INT'].includes(s.nivel)) {
      return s.nivel === 'H1' || (s.textoLectura?.length || 0) >= 15;
    }
    if (s.nivel === 'DOC' || /^ESTÁNDARES\s/i.test(t)) return true;
    if (t.toUpperCase().includes('ESTÁNDAR')) return true;
    if (/^\d+\.\s+ESTÁNDAR/i.test(t)) return true;
    return s.profundidad <= 1 && (s.textoLectura?.length || 0) >= 60;
  });
}

/** Estructura texto plano en HTML con índice y párrafos (fallback si el API no lo entrega). */
export function estructurarTextoDocumento(textoPlano: string, seccionesApi: SeccionTexto[] = []): ResultadoEstructura {
  const textoConSaltos = prepararTextoConSaltos(textoPlano);
  let indice = detectarEncabezados(textoConSaltos);

  if (indice.length < 3 && seccionesApi.length > 0) {
    indice = seccionesApi.map((s) => ({ ...s }));
  }

  const seccionesNav = filtrarSeccionesNav(indice);
  const seccionesCuerpo = filtrarSeccionesCuerpo(indice);
  const html = generarHtml(textoConSaltos, indice, seccionesCuerpo);

  return { textoConSaltos, indice, seccionesNav, html };
}

/** Quita el bloque de índice embebido (el índice va solo en el panel lateral). */
export function quitarIndiceDelHtml(html: string): string {
  if (!html?.trim() || !html.includes('doc-indice')) return html;
  if (typeof DOMParser === 'undefined') return html;
  const doc = new DOMParser().parseFromString(html, 'text/html');
  doc.body.querySelectorAll('.doc-indice').forEach((el) => el.remove());
  return doc.body.innerHTML.trim();
}

export function htmlTieneEstructura(html: string): boolean {
  if (!html?.trim()) return false;
  const heads = (html.match(/<h[1-4][\s>]/gi) || []).length;
  return heads >= 2;
}

/**
 * Estructura tipo «libro Word» en el panel derecho: H1 título ESV, H2 por estándar, H3 subapartados.
 */
export function estructurarLibroEsv(textoPlano: string, tituloDocumento?: string): ResultadoEstructura {
  const textoConSaltos = prepararTextoConSaltos(textoPlano);
  let indice = detectarEncabezados(textoConSaltos);
  indice = detectarSubseccionesEsv(textoConSaltos, indice);

  const seccionesNav = filtrarSeccionesNav(indice);
  const seccionesCuerpo = filtrarSeccionesCuerpo(indice);
  let html = generarHtml(textoConSaltos, indice, seccionesCuerpo);

  if (tituloDocumento?.trim() && !html.includes('<h1')) {
    html = `<h1>${escapeHtml(tituloDocumento.trim())}</h1>\n` + html;
  }

  return { textoConSaltos, indice, seccionesNav, html };
}

function detectarSubseccionesEsv(texto: string, base: SeccionTexto[]): SeccionTexto[] {
  const extra: { titulo: string; pos: number; end: number; tipo: string; prof: number }[] = [];
  const re = /^\s*((?:OBJETIVO|ALCANCE|REQUISITOS(?:\s+DE\s+.+)?|CONTROLES(?:\s+DEL\s+ESTÁNDAR)?|CAPACITACIÓN)(?:\s+\([^)]+\))?)\s*$/gim;
  let m: RegExpExecArray | null;
  while ((m = re.exec(texto)) !== null) {
    const dup = base.some((e) => Math.abs(e.indiceInicio - m!.index) < 20);
    if (!dup) {
      extra.push({
        titulo: m[1].trim(),
        pos: m.index,
        end: m.index + m[0].length,
        tipo: 'SUB',
        prof: 2,
      });
    }
  }
  if (!extra.length) return base;

  const merged = [
    ...base.map((s) => ({
      titulo: s.titulo,
      pos: s.indiceInicio,
      end: s.indiceInicio + 1,
      tipo: s.nivel,
      prof: s.profundidad,
    })),
    ...extra,
  ].sort((a, b) => a.pos - b.pos);

  return merged.map((e, i) => ({
    nivel: e.tipo,
    titulo: e.titulo,
    indiceInicio: e.pos,
    indiceFin: i + 1 < merged.length ? merged[i + 1].pos : texto.length,
    profundidad: e.prof,
  }));
}

/** Fuerza reestructuración desde texto plano (ignora HTML previo). */
export function forzarEstructuraDesdeTexto(textoPlano: string, tituloDocumento?: string): ResultadoEstructura {
  return estructurarLibroEsv(textoPlano, tituloDocumento);
}

/** Usa la respuesta del API tal cual; solo estructura si el backend no entregó HTML. */
export function normalizarRespuestaTexto(resp: any, tituloDocumento?: string): any {
  if (!resp) return resp;
  if (tituloDocumento?.trim()) {
    resp = { ...resp, tituloDocumento: tituloDocumento.trim() };
  }
  const limpiar = (html: string) => quitarIndiceDelHtml(html || '');
  const editor = limpiar(resp.textoEditor || resp.textoEstructurado || '');

  if (editor && htmlTieneEstructura(editor)) {
    return {
      ...resp,
      textoEditor: editor,
      textoEstructurado: limpiar(resp.textoEstructurado || editor),
      indice: resp.indice || resp.secciones || [],
    };
  }
  return enriquecerRespuestaTexto(resp);
}

export function respuestaTextoNecesitaEstructura(resp: any): boolean {
  if (!resp?.textoCompleto?.trim()) return false;
  if (resp.textoEstructurado && htmlTieneEstructura(resp.textoEstructurado)) return false;
  if (resp.textoEditor && htmlTieneEstructura(resp.textoEditor)) return false;
  return true;
}

export function enriquecerRespuestaTexto(resp: any): any {
  if (!resp) return resp;

  const limpiar = (html: string) => quitarIndiceDelHtml(html || '');

  if (!respuestaTextoNecesitaEstructura(resp)) {
    return {
      ...resp,
      indice: resp.indice || resp.secciones || [],
      textoEstructurado: limpiar(resp.textoEstructurado),
      textoEditor: limpiar(resp.textoEditor || resp.textoEstructurado),
    };
  }

  const titulo = resp.tituloDocumento || resp.titulo || 'ESTÁNDARES QUE SALVAN VIDAS';
  const base = resolverTextoBaseParaEstructura(
    resp.textoCompleto,
    resp.textoEditor || resp.textoEstructurado
  );
  const resultado = estructurarLibroEsv(base, titulo);
  const html = limpiar(resultado.html);

  return {
    ...resp,
    textoCompleto: resultado.textoConSaltos,
    indice: resultado.indice,
    secciones: resultado.seccionesNav.length ? resultado.seccionesNav : resp.secciones,
    textoEstructurado: html,
    textoEditor: html,
  };
}
