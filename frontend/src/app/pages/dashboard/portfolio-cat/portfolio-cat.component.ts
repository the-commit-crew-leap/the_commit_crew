import { CommonModule } from '@angular/common';
import { Component, Input } from '@angular/core';

type PortfolioCatState = 'skinny' | 'base' | 'fat';

@Component({
  selector: 'app-portfolio-cat',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './portfolio-cat.component.html',
  styleUrl: './portfolio-cat.component.css'
})
export class PortfolioCatComponent {
  @Input() unrealizedPnl = 0;

  get state(): PortfolioCatState {
    if (this.unrealizedPnl > 100) {
      return 'fat';
    }

    if (this.unrealizedPnl < -100) {
      return 'skinny';
    }

    return 'base';
  }

  get stateLabel(): string {
    return {
      skinny: 'Skinny · Loss',
      base: 'Base · Neutral',
      fat: 'Fat · Profit'
    }[this.state];
  }

  get stateThreshold(): string {
    return {
      skinny: '< −$100',
      base: '±$100',
      fat: '> +$100'
    }[this.state];
  }

  get imagePath(): string {
    return `/assets/${this.state}.png`;
  }

  formatCurrency(value: number): string {
    return `${value < 0 ? '-' : ''}$${Math.abs(value).toLocaleString('en-US', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2
    })}`;
  }
}
