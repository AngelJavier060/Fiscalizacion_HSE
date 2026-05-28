import { Pipe, PipeTransform } from '@angular/core';

import { DomSanitizer, SafeHtml } from '@angular/platform-browser';



/**

 * Markdown orientado al chat HSE: títulos, listas, citas, énfasis, enlaces/https e imágenes https.

 */

@Pipe({

  name: 'markdown',

  standalone: true,

})

export class MarkdownPipe implements PipeTransform {

  constructor(private sanitizer: DomSanitizer) {}



  transform(value: string | null | undefined): SafeHtml {

    if (value == null || value === '') {

      return this.sanitizer.bypassSecurityTrustHtml('');

    }



    let s = escapeHtml(value);



    s = replaceImages(s);

    s = replaceLinks(s);



    const lines = s.split(/\r?\n/);

    const parts: string[] = [];

    let ulOpen = false;

    let olOpen = false;

    let paragraphBuffer: string[] = [];



    const flushParagraph = (): void => {

      if (!paragraphBuffer.length) {

        return;

      }

      const body = paragraphBuffer.join('<br>').trim();

      if (body.length > 0) {

        parts.push(`<p class="ia-md-p">${inlineEmphasis(body)}</p>`);

      }

      paragraphBuffer = [];

    };



    const closeLists = (): void => {

      if (ulOpen) {

        parts.push('</ul>');

        ulOpen = false;

      }

      if (olOpen) {

        parts.push('</ol>');

        olOpen = false;

      }

    };



    for (let rawLine of lines) {

      const lineTrim = rawLine.trim();



      const empty = lineTrim === '';



      const hSharp = lineTrim.match(/^(#{1,4})\s+(.+)$/);

      const hr =

        /^(\*{3}|-{3}|_{3})$/.test(lineTrim) ||

        lineTrim === '---';

      const capsHeading =

        !hSharp &&

        lineTrim.length >= 12 &&

        lineTrim.length <= 120 &&

        /^[A-ZÁÉÍÓÚÑ0-9][A-ZÁÉÍÓÚÑ0-9\s\-–.,:()]+$/.test(lineTrim) &&

        lineTrim === lineTrim.toUpperCase();



      const ulItem = rawLine.match(/^\s*[-*]\s+(.+)$/);

      const olItem = rawLine.match(/^\s*\d+[.)]\s+(.+)$/);

      const bq =

        rawLine.match(/^\s*>\s?(.*)$/);



      if (empty) {

        flushParagraph();

        continue;

      }



      if (hSharp) {

        flushParagraph();

        closeLists();

        const level = hSharp[1].length;

        const title = inlineEmphasis(hSharp[2].trim());

        const tag =

          level <= 2 ? 'h2' : level === 3 ? 'h3' : 'h4';

        const cls =

          level <= 2 ? 'ia-md-h2' : level === 3 ? 'ia-md-h3' : 'ia-md-h4';

        parts.push(`<${tag} class="${cls}">${title}</${tag}>`);

        continue;

      }



      if (capsHeading) {

        flushParagraph();

        closeLists();

        parts.push(`<h2 class="ia-md-h2">${inlineEmphasis(lineTrim)}</h2>`);

        continue;

      }



      if (hr) {

        flushParagraph();

        closeLists();

        parts.push('<hr class="ia-md-hr"/>');

        continue;

      }



      if (ulItem) {

        flushParagraph();

        if (olOpen) {

          parts.push('</ol>');

          olOpen = false;

        }

        if (!ulOpen) {

          parts.push('<ul class="ia-md-ul">');

          ulOpen = true;

        }

        parts.push(`<li>${inlineEmphasis(ulItem[1].trim())}</li>`);

        continue;

      }



      if (olItem) {

        flushParagraph();

        if (ulOpen) {

          parts.push('</ul>');

          ulOpen = false;

        }

        if (!olOpen) {

          parts.push('<ol class="ia-md-ol">');

          olOpen = true;

        }

        parts.push(`<li>${inlineEmphasis(olItem[1].trim())}</li>`);

        continue;

      }



      if (bq) {

        flushParagraph();

        closeLists();

        const inner = bq[1] ? inlineEmphasis(bq[1].trim()) : '';

        parts.push(`<blockquote class="ia-md-bq">${inner}</blockquote>`);

        continue;

      }



      paragraphBuffer.push(lineTrim);

    }



    flushParagraph();

    closeLists();



    const html = `<article class="ia-md-root">${parts.join('\n')}</article>`;



    return this.sanitizer.bypassSecurityTrustHtml(html);

  }

}



function escapeHtml(raw: string): string {

  return raw

    .replace(/&/g, '&amp;')

    .replace(/</g, '&lt;')

    .replace(/>/g, '&gt;')

    .replace(/"/g, '&quot;');

}



/**

 * ![](https://...) — solo HTTPS; tamaño contenido dentro del bubble.

 */

function replaceImages(html: string): string {

  return html.replace(

    /!\[([^\]]*)]\((https:\/\/[a-zA-Z0-9._\-/:?#[\]@!$&'()*+,;=%]+\))/g,

    (_m, alt, url) => {

      const a = String(alt).replace(/"/g, '');

      return (

        `<figure class="ia-md-figure">` +

        `<img class="ia-md-img" src="${url}" alt="${escapeAttr(a)}" loading="lazy" referrerpolicy="no-referrer"/>` +

        (a ? `<figcaption class="ia-md-cap">${inlineEmphasis(a)}</figcaption>` : '') +

        '</figure>'

      );

    }

  );

}



function replaceLinks(html: string): string {

  return html.replace(

    /\[([^\]]+)]\((https:\/\/[a-zA-Z0-9._\-/:?#[\]@!$&'()*+,;=%]+)\)/g,

    (_m, text, url) => {

      const t = inlineEmphasis(String(text));

      return `<a class="ia-md-a" href="${url}" target="_blank" rel="noopener noreferrer">${t}</a>`;

    }

  );

}



function escapeAttr(s: string): string {

  return s.replace(/"/g, '&quot;');

}



function inlineEmphasis(text: string): string {

  let t = text;

  t = t.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>');

  t = t.replace(/__(.+?)__/g, '<strong>$1</strong>');

  t = t.replace(/\*([^*]+)\*/g, '<em>$1</em>');

  t = t.replace(/_([^_]+)_/g, '<em>$1</em>');

  t = t.replace(/`([^`]+)`/g, '<code class="ia-md-code">$1</code>');

  return t;

}
