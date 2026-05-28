import { Component, HostListener, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';

@Component({
  selector: 'app-landing-page',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './landing-page.component.html',
  styleUrl: './landing-page.component.scss',
})
export class LandingPageComponent {
  headerCompact = signal(false);

  constructor(private router: Router) {}

  @HostListener('window:scroll')
  onScroll(): void {
    this.headerCompact.set(window.scrollY > 20);
  }

  /** Pantalla de login completa; from=landing evita entrar directo si hay sesión guardada */
  irAlLogin(): void {
    this.router.navigate(['/auth/login'], { queryParams: { from: 'landing' } });
  }
}
