import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { AppController } from './app.controller';
import { AppService } from './app.service';
import { AuthModule } from './auth/auth.module';
  
@Module({
  imports: [
    TypeOrmModule.forRoot({
      type: 'postgres',
      host: process.env.AUTH_DB_HOST || 'db',
      port: parseInt(process.env.AUTH_DB_PORT || '5432', 10),
      username: process.env.AUTH_DB_USER || 'postgres',
      password: process.env.AUTH_DB_PASSWORD || 'postgres',
      database: process.env.AUTH_DB_NAME || 'auth_db-dev',
      entities: [__dirname + '/**/*.entity{.ts,.js}'],
      synchronize: false,
      logging: false,
    }),
    AuthModule,
  ],
  controllers: [AppController],
  providers: [AppService],
})
export class AppModule {}
