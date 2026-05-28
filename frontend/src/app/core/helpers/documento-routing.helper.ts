import { ActivatedRoute } from '@angular/router';
import { AuthService } from '../services/auth.service';

export interface DocumentoContext {
  empresaId: number | null;
  esRutaSuperAdmin: boolean;
}

/** empresaId en URL (/super-admin/empresas/:id/documentos) o del usuario ADMIN_EMPRESA */
export function buildDocumentoContext(
  route: ActivatedRoute,
  auth: AuthService
): DocumentoContext {
  const desdeUrl = route.snapshot.paramMap.get('empresaId');
  if (desdeUrl) {
    return { empresaId: +desdeUrl, esRutaSuperAdmin: true };
  }
  const u = auth.getUserData();
  return { empresaId: u?.empresaId ?? null, esRutaSuperAdmin: false };
}

export interface DocumentoLinks {
  sidebarInicio: (string | number)[];
  documentos: (string | number)[];
  subir: (string | number)[];
  detalle: (id: number) => (string | number)[];
  /** FISCALIZA-AI: modo empresa (admin) o por empresa en super admin */
  ia: (string | number)[];
}

export function documentNavigationLinks(ctx: DocumentoContext): DocumentoLinks {
  const e = ctx.empresaId;
  if (ctx.esRutaSuperAdmin && e != null) {
    const base = ['/super-admin', 'empresas', e] as const;
    return {
      sidebarInicio: ['/super-admin', 'empresas'],
      documentos: [...base, 'documentos'],
      subir: [...base, 'documentos', 'subir'],
      detalle: (id: number) => [...base, 'documentos', id],
      ia: [...base, 'ia'],
    };
  }
  return {
    sidebarInicio: ['/admin-empresa', 'dashboard'],
    documentos: ['/admin-empresa', 'documentos'],
    subir: ['/admin-empresa', 'documentos', 'subir'],
    detalle: (id: number) => ['/admin-empresa', 'documentos', id],
    ia: ['/admin-empresa', 'ia'],
  };
}
