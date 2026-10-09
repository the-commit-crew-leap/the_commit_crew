import { Entity, PrimaryColumn, Column, ManyToOne, CreateDateColumn, JoinColumn } from 'typeorm';
import { User } from './user.entity';

@Entity('user_trading_accounts')
export class UserTradingAccount {
  @PrimaryColumn({ type: 'bigint', name: 'user_id', unique: true })
  userId: number;

  @PrimaryColumn({ type: 'varchar', length: 32, name: 'account_id', unique: true })
  accountId: string;

  @Column({ 
    type: 'varchar', 
    length: 20,
    enum: ['CLIENT', 'ADVISOR'],
    name: 'role'
  })
  role: string;

  @CreateDateColumn({ name: 'created_at' })
  createdAt: Date;

  @ManyToOne(() => User, user => user.tradingAccounts, { onDelete: 'CASCADE' })
  @JoinColumn({ name: 'user_id' })
  user: User;
}