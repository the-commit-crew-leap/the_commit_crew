import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { Router } from '@angular/router';
import { of } from 'rxjs';
import { signal } from '@angular/core';
import { AuthSessionService } from '../auth/auth-session.service';
import { TCC_HeaderComponent } from './header.component';

describe('TCC_HeaderComponent', () => {
  let component: TCC_HeaderComponent;
  let fixture: ComponentFixture<TCC_HeaderComponent>;
  let authSession: jest.Mocked<AuthSessionService>;
  let router: jest.Mocked<Router>;

  beforeEach(async () => {
    authSession = {
      accountId: signal('ACC-1001'),
      getAccountId: jest.fn().mockReturnValue('ACC-1001'),
      logout: jest.fn()
    } as unknown as jest.Mocked<AuthSessionService>;
    router = {
      navigate: jest.fn(),
      events: of({}),
      url: '/',
      createUrlTree: jest.fn(),
      serializeUrl: jest.fn()
    } as unknown as jest.Mocked<Router>;

    await TestBed.configureTestingModule({
      imports: [TCC_HeaderComponent],
      providers: [
        { provide: AuthSessionService, useValue: authSession },
        { provide: Router, useValue: router },
        { provide: ActivatedRoute, useValue: {} }
      ]
    })
      .compileComponents();

    fixture = TestBed.createComponent(TCC_HeaderComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('shows the current account and logs out', () => {
    expect(component.accountId()).toBe('ACC-1001');

    component.toggleAccountMenu();
    component.logout();

    expect(component.accountMenuOpen).toBe(false);
    expect(authSession.logout).toHaveBeenCalled();
    expect(router.navigate).toHaveBeenCalledWith(['/login']);
  });

  it('closes the account menu when clicking outside the header', () => {
    component.accountMenuOpen = true;

    component.closeAccountMenuOnOutsideClick(
      new MouseEvent('click', { bubbles: true }),
    );

    expect(component.accountMenuOpen).toBe(false);
  });
});
