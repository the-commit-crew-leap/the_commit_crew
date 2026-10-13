import { Routes } from '@angular/router';
import { DashboardComponent } from './pages/dashboard/dashboard.component';
import { NotFoundComponent } from './pages/not-found/not-found.component';
import { OrderHistoryComponent } from './pages/order-history/order-history.component';

export const routes: Routes = [
  { path: '', component: DashboardComponent },
  { path: 'orders', component: OrderHistoryComponent },
  { path: '**', component: NotFoundComponent }
];