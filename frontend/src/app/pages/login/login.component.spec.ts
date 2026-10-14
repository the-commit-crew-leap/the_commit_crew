import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { of, throwError } from 'rxjs';

import { LoginComponent } from './login.component';
import { AuthSessionService } from '../../auth/auth-session.service';

describe('LoginComponent', () => {
  let fixture: ComponentFixture<LoginComponent>;
  let component: LoginComponent;
  let authSession: jest.Mocked<AuthSessionService>;
  let router: jest.Mocked<Router>;
  let queryParams: Record<string, string | null>;

  beforeEach(async () => {
    queryParams = {};

    authSession = {
      login: jest.fn()
    } as unknown as jest.Mocked<AuthSessionService>;

    router = {
      navigateByUrl: jest.fn()
    } as unknown as jest.Mocked<Router>;

    await TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [
        { provide: AuthSessionService, useValue: authSession },
        { provide: Router, useValue: router },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              queryParamMap: {
                get: (key: string) => queryParams[key] ?? null
              }
            }
          }
        }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(LoginComponent);
    component = fixture.componentInstance;
  });

  it('rejects empty credentials', () => {
    component.submit();

    expect(component.form.invalid).toBe(true);
    expect(authSession.login).not.toHaveBeenCalled();
  });

  it('logs in and navigates to the requested URL', () => {
    queryParams['returnUrl'] = '/orders';
    authSession.login.mockReturnValue(of(void 0));

    component.form.setValue({
      username: 'alice',
      password: 'SecurePass123!'
    });

    component.submit();

    expect(authSession.login).toHaveBeenCalledWith({
      username: 'alice',
      password: 'SecurePass123!'
    });
    expect(router.navigateByUrl).toHaveBeenCalledWith('/orders');
    expect(component.isSubmitting).toBe(false);
  });

  it('displays the backend error', () => {
    authSession.login.mockReturnValue(
      throwError(() => ({
        error: { message: 'Invalid credentials' }
      }))
    );

    component.form.setValue({
      username: 'alice',
      password: 'wrong-password'
    });

    component.submit();

    expect(component.errorMessage).toBe('Invalid credentials');
    expect(component.isSubmitting).toBe(false);
  });

  it('shows the registration success message', () => {
    queryParams['registered'] = '1';

    fixture = TestBed.createComponent(LoginComponent);
    component = fixture.componentInstance;

    expect(component.infoMessage)
      .toBe('Account created successfully. Please sign in.');
  });

  it('shows the expired-session message', () => {
    queryParams['expired'] = '1';

    fixture = TestBed.createComponent(LoginComponent);
    component = fixture.componentInstance;

    expect(component.infoMessage)
      .toBe('Session expired. Please sign in again.');
  });
});