import { Entity, PrimaryGeneratedColumn, Column, CreateDateColumn, UpdateDateColumn, Unique } from 'typeorm';

/**
 * User entity for storing user credentials.
 * 
 * Security notes:
 * - passwordHash is never selected by default (select: false)
 * - Use explicit query to fetch when needed (e.g., during login)
 * - Plaintext passwords are never stored
 * - Passwords are hashed with bcrypt at cost 12+
 */
@Entity('users')
@Unique(['username'])
@Unique(['email'])
export class User {
  @PrimaryGeneratedColumn('increment')
  id: number;

  @Column({ type: 'varchar', length: 255 })
  username: string;

  @Column({ type: 'varchar', length: 255 })
  email: string;

  /**
   * Bcrypt password hash (not plaintext).
   * Never selected by default to prevent accidental exposure.
   * Format: $2b$cost$salt$hash
   * Example: $2b$12$N9qo8uLOickgx2ZMRZoMyeIjZAgcg7b3XeVgVQ2pTgabNbFZKE7Be
   */
  @Column({ name: 'password_hash', type: 'varchar', length: 255, select: false })
  passwordHash: string;

  @CreateDateColumn({ name: 'created_at' })
  createdAt: Date;

  @UpdateDateColumn({ name: 'updated_at' })
  updatedAt: Date;
}