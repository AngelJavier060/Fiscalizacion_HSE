import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { EmpresaService } from '../../../../core/services/empresa.service';
import { VigenciaStoreService, VigenciaInfo } from '../../../../core/services/vigencia-store.service';
import { Empresa } from '../../../../core/models/empresa.model';

import { SuperAdminSidebarComponent } from '../../shared/super-admin-sidebar/super-admin-sidebar.component';

@Component({
  selector: 'app-empresa-list',
  standalone: true,
  imports: [CommonModule, RouterModule, SuperAdminSidebarComponent],
  templateUrl: './empresa-list.component.html',
  styleUrls: ['./empresa-list.component.scss'],
})
export class EmpresaListComponent implements OnInit {
  empresas: Empresa[] = [];
  loading = true;

  constructor(
    private empresaService: EmpresaService,
    private vigencia: VigenciaStoreService
  ) {}

  vigenciaInfo(empresa: Empresa): VigenciaInfo {
    return this.vigencia.estado(empresa.vigenciaDesde, empresa.vigenciaHasta);
  }

  ngOnInit(): void {
    this.cargarEmpresas();
  }

  cargarEmpresas(): void {
    this.loading = true;
    this.empresaService.listar(0, 100).subscribe({
      next: (response) => {
        this.empresas = response.content || [];
        this.loading = false;
      },
      error: () => {
        this.loading = false;
      },
    });
  }

  toggleActivo(empresa: Empresa): void {
    this.empresaService.toggleActivo(empresa.id).subscribe({
      next: (actualizada) => {
        const index = this.empresas.findIndex(e => e.id === empresa.id);
        if (index !== -1) {
          this.empresas[index] = actualizada;
        }
      },
    });
  }

  eliminar(empresa: Empresa): void {
    if (confirm(`¿Eliminar la empresa "${empresa.nombre}"?`)) {
      this.empresaService.eliminar(empresa.id).subscribe({
        next: () => {
          this.empresas = this.empresas.filter(e => e.id !== empresa.id);
        },
      });
    }
  }
}
