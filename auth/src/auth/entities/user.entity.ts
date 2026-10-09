import { Entity, PrimaryGeneratedColumn, Column, CreateDateColumn, UpdateDateColumn, OneToMany } from 'typeorm';
import { UserTradingAccount } from './user-trading-account.entity';

@Entity('users')
export class User {
  @PrimaryGeneratedColumn('identity', { type: 'bigint', name: 'user_id' })
  userId: number;

  @Column({ type: 'varchar', length: 64, unique: true, name: 'username' })
  username: string;

  @Column({ type: 'varchar', length: 255, unique: true, name: 'email' })
  email: string;

  @Column({ type: 'varchar', length: 255, nullable: true, name: 'full_name' })
  fullName: string;

  @Column({ 
    type: 'varchar', 
    length: 20, 
    default: 'ACTIVE',
    enum: ['ACTIVE', 'DELETED', 'SUSPENDED'],
    name: 'status'
  })
  status: string;

  @CreateDateColumn({ name: 'created_at' })
  createdAt: Date;

  @UpdateDateColumn({ name: 'updated_at' })
  updatedAt: Date;

  @OneToMany(() => UserTradingAccount, uta => uta.user)
  tradingAccounts: UserTradingAccount[];
}

