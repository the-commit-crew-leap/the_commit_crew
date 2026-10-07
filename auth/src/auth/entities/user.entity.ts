import { Entity, PrimaryGeneratedColumn, Column, CreateDateColumn, UpdateDateColumn, OneToOne, JoinColumn, Check } from 'typeorm';
import { Credential } from './credential.entity';

@Entity('users')
@Check(`status IN ('ACTIVE', 'DELETED', 'SUSPENDED')`)
export class User {
  @PrimaryGeneratedColumn({ type: 'bigint' })
  user_id: number;

  @Column({ type: 'varchar', length: 64, unique: true })
  username: string;

  @Column({ type: 'varchar', length: 255, unique: true })
  email: string;

  @Column({ type: 'varchar', length: 255, nullable: true })
  full_name: string;

  @Column({ type: 'varchar', length: 20, default: 'ACTIVE' })
  status: string;

  @CreateDateColumn({ type: 'timestamp' })
  created_at: Date;

  @UpdateDateColumn({ type: 'timestamp' })
  updated_at: Date;

  @OneToOne(() => Credential, (credential) => credential.user, { cascade: true })
  credential: Credential;
}