import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

@Component({
  selector: 'app-header',
  styleUrl: './header.css',
  templateUrl: './header.html',
  imports: [
    RouterLink,
    RouterLinkActive
  ]
})
export class TCC_HeaderComponent {

}
