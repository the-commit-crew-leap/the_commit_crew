import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

import {
  ContainerComponent,
  DropdownComponent,
  DropdownDividerDirective,
  DropdownItemDirective,
  DropdownMenuDirective,
  DropdownToggleDirective,
  HeaderBrandComponent,
  HeaderComponent,
  HeaderDividerComponent,
  HeaderNavComponent,
  HeaderTextComponent,
  NavItemComponent,
  NavLinkDirective
} from '@coreui/angular';

@Component({
  selector: 'app-header',
  styleUrl: './header.css',
  templateUrl: './header.html',
  imports: [
    RouterLink,
    RouterLinkActive,
    ContainerComponent,
    DropdownComponent,
    DropdownDividerDirective,
    DropdownItemDirective,
    DropdownMenuDirective,
    DropdownToggleDirective,
    HeaderBrandComponent,
    HeaderComponent,
    HeaderDividerComponent,
    HeaderNavComponent,
    HeaderTextComponent,
    NavItemComponent,
    NavLinkDirective
  ]
})
export class TCC_HeaderComponent {

}
