import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { AdminEmpresaSidebarComponent } from '../shared/admin-empresa-sidebar/admin-empresa-sidebar.component';

@Component({
  selector: 'app-admin-roles-panel',
  standalone: true,
  imports: [CommonModule, RouterModule, AdminEmpresaSidebarComponent],
  templateUrl: './admin-roles-panel.component.html',
  styleUrls: ['./admin-roles-panel.component.scss'],
})
export class AdminRolesPanelComponent {
  constructor(public auth: AuthService) {}
}
