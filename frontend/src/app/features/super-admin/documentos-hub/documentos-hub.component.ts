import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { EmpresaService } from '../../../core/services/empresa.service';
import { Empresa } from '../../../core/models/empresa.model';
import { SuperAdminSidebarComponent } from '../shared/super-admin-sidebar/super-admin-sidebar.component';

@Component({
  selector: 'app-documentos-hub',
  standalone: true,
  imports: [CommonModule, RouterModule, SuperAdminSidebarComponent],
  templateUrl: './documentos-hub.component.html',
  styleUrls: ['./documentos-hub.component.scss'],
})
export class DocumentosHubComponent implements OnInit {
  empresas: Empresa[] = [];
  loading = true;

  constructor(private empresaService: EmpresaService) {}

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
    this.loading = true;
    this.empresaService.listar(0, 200).subscribe({
      next: (response) => {
        this.empresas = response.content || [];
        this.loading = false;
      },
      error: () => (this.loading = false),
    });
  }
}
