import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { of, throwError } from 'rxjs';

import { RegisterComponent } from './register.component';
import { AuthSessionService } from '../../auth/auth-session.service';
import { RegisterResponse } from '../../auth/auth.models';

describe('RegisterComponent', () => {
  let fixture: ComponentFixture<RegisterComponent>;
  let component: RegisterComponent;
  let authSession: jest.Mocked<AuthSessionService>;
  let router: jest.Mocked<Router>;

  beforeEach(async () => {
    authSession = {
      register: jest.fn()
    } as unknown as jest.Mocked<AuthSessionService>;

    router = {
      navigate: jest.fn()
    } as unknown as jest.Mocked<Router>;

    await TestBed.configureTestingModule({
      imports: [RegisterComponent],
      providers: [
        { provide: AuthSessionService, useValue: authSession },
        { provide: Router, useValue: router },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              queryParamMap: {
                get: () => null
              }
            }
          }
        }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(RegisterComponent);
    component = fixture.componentInstance;
  });

  it('rejects invalid registration data', () => {
    component.submit();

    expect(component.form.invalid).toBe(true);
    expect(authSession.register).not.toHaveBeenCalled();
  });

  it('registers and redirects to login', () => {
    authSession.register.mockReturnValue(
      of<RegisterResponse>({
        id: "100",
        username: 'alice',
        email: 'alice@example.com',
        accountId: 'ACC-12345678',
        createdAt: '2026-10-13T12:00:00.000Z'
      }),
    );

    component.form.setValue({
      username: 'alice',
      email: 'alice@example.com',
      fullName: 'Alice Smith',
      password: 'SecurePass123!'
    });

    component.submit();

    expect(authSession.register).toHaveBeenCalledWith({
      username: 'alice',
      email: 'alice@example.com',
      fullName: 'Alice Smith',
      password: 'SecurePass123!'
    });

    expect(router.navigate).toHaveBeenCalledWith(['/login'], {
      queryParams: { registered: '1' }
    });
    expect(component.isSubmitting).toBe(false);
  });

  it('displays a backend registration error', () => {
    authSession.register.mockReturnValue(
      throwError(() => ({
        error: { message: 'User already exists' }
      }))
    );

    component.form.setValue({
      username: 'alice',
      email: 'alice@example.com',
      fullName: 'Alice Smith',
      password: 'SecurePass123!'
    });

    component.submit();

    expect(component.hasSubmitError).toBe(true);
    expect(component.errorMessage).toBe('User already exists');
    expect(component.isSubmitting).toBe(false);
  });

  it('displays a fallback error when no backend message exists', () => {
    authSession.register.mockReturnValue(
      throwError(() => new Error('Network error'))
    );

    component.form.setValue({
      username: 'alice',
      email: 'alice@example.com',
      fullName: 'Alice Smith',
      password: 'SecurePass123!'
    });

    component.submit();

    expect(component.hasSubmitError).toBe(true);
    expect(component.errorMessage)
      .toBe('Could not register. Please try again.');
    expect(component.isSubmitting).toBe(false);
  });
});