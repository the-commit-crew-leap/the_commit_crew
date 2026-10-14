import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { TCC_HeaderComponent } from './header/header.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, TCC_HeaderComponent],
  template: `
  <app-header></app-header>
  <router-outlet></router-outlet>
  `,
  styles: []
})
export class AppComponent {
}