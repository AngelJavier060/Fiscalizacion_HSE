import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-admin-empresa-dashboard',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.scss'],
})
export class DashboardComponent implements OnInit {
  user: any;

  constructor(private authService: AuthService) {}

  ngOnInit(): void {
    this.user = this.authService.getUserData();
  }

  /** True si el usuario tiene habilitado el módulo (según el login). */
  tieneModulo(codigo: string): boolean {
    return this.authService.tieneModulo(codigo);
  }

  logout(): void {
    this.authService.logout();
  }
}
