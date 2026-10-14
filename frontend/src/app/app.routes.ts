import { Routes } from '@angular/router';
import { authGuard } from './auth/auth.guard';
import { DashboardComponent } from './pages/dashboard/dashboard.component';
import { LoginComponent } from './pages/login/login.component';
import { RegisterComponent } from './pages/register/register.component';
import { NotFoundComponent } from './pages/not-found/not-found.component';
import { OrderHistoryComponent } from './pages/order-history/order-history.component';

export const routes: Routes = [
  { path: 'login', component: LoginComponent },
  { path: 'register', component: RegisterComponent },
  { path: 'orders', component: OrderHistoryComponent },
  { path: '', component: DashboardComponent, canActivate: [authGuard] },
  { path: '**', component: NotFoundComponent }
];