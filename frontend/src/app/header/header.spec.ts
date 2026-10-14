import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TCC_HeaderComponent } from './header.component';

describe('TCC_HeaderComponent', () => {
  let component: TCC_HeaderComponent;
  let fixture: ComponentFixture<TCC_HeaderComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TCC_HeaderComponent]
    })
      .compileComponents();

    fixture = TestBed.createComponent(TCC_HeaderComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
