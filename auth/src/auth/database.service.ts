import { Injectable, Logger } from '@nestjs/common';
import * as pg from 'pg';

@Injectable()
export class DatabaseService {
  private readonly logger = new Logger(DatabaseService.name);
  private tradingDbPool: pg.Pool;

  constructor() {
    this.tradingDbPool = new pg.Pool({
      host: process.env.TRADING_DB_HOST,
      port: parseInt(process.env.TRADING_DB_PORT || '5432'),
      user: process.env.TRADING_DB_USER ,
      password: process.env.TRADING_DB_PASSWORD,
      database: process.env.TRADING_DB_NAME,
    });

    this.tradingDbPool.on('error', (err: Error) => {
      this.logger.error('Unexpected error on trading db idle client', err);
    });
  }

    async createAccount(accountId: string, holderName: string): Promise<boolean> {
    const client = await this.tradingDbPool.connect();
    try {
      await client.query('BEGIN');
      
      const query = `
        INSERT INTO accounts (account_id, holder_name, cash_balance, status)
        VALUES ($1, $2, $3, $4)
      `;
      
      await client.query(query, [accountId, holderName, 0.00, 'ACTIVE']);
      await client.query('COMMIT');
      
      this.logger.log(`Account created in trading-db: ${accountId}`);
      return true;
    } catch (error: unknown) {
      await client.query('ROLLBACK');
      const errorMessage = error instanceof Error ? error.message : 'Unknown error';
      this.logger.error(`Failed to create account in trading-db: ${errorMessage}`);
      throw error;
    } finally {
      client.release();
    }
  }

  async closeAccount(accountId: string): Promise<boolean> {
    const client = await this.tradingDbPool.connect();
    try {
      const query = `
        UPDATE accounts 
        SET status = $1
        WHERE account_id = $2
      `;
      
      const result = await client.query(query, ['CLOSED', accountId]);
      
      if (result.rowCount === 0) {
        throw new Error(`Account not found: ${accountId}`);
      }
      
      this.logger.log(`Account closed in trading-db: ${accountId}`);
      return true;
    } catch (error: unknown) {
      const errorMessage = error instanceof Error ? error.message : 'Unknown error';
      this.logger.error(`Failed to close account in trading-db: ${errorMessage}`);
      throw error;
    } finally {
      client.release();
    }
  }

  async getConnection() {
    return this.tradingDbPool;
  }

  async closeConnection() {
    await this.tradingDbPool.end();
  }
}