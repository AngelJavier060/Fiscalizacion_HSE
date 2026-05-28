import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-admin-roles-panel',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './admin-roles-panel.component.html',
  styleUrls: ['./admin-roles-panel.component.scss'],
})
export class AdminRolesPanelComponent {
  constructor(public auth: AuthService) {}
}
