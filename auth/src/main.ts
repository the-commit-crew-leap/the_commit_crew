import * as dotenv from 'dotenv';
import * as path from 'path';

// Load environment variables BEFORE importing anything else
const envFile = process.env.NODE_ENV === 'prod' ? '.env.prod' : '.env.dev';
const envPath = path.resolve(process.cwd(), '..', envFile);
console.log(`Loading environment variables from: ${envPath}`);
const result = dotenv.config({
  path: envPath,
});
if (result.error) {
  console.warn(`Warning: Could not load .env file from ${envPath}`);
} else {
  console.log(`Successfully loaded .env file from ${envPath}`);
  console.log(`Database host: ${process.env.POSTGRES_HOST}`);
  console.log(`Database port: ${process.env.POSTGRES_PORT}`);
  console.log(`Auth DB: ${process.env.AUTH_DB}`);
}

import { NestFactory } from '@nestjs/core';
import { SwaggerModule, DocumentBuilder } from '@nestjs/swagger';
import { AppModule } from './app.module';
import { HttpExceptionFilter } from './common/exceptions.filter';
import { ValidationPipe } from '@nestjs/common';

async function bootstrap() {
  const app = await NestFactory.create(AppModule);

  const corsOrigins = (process.env.CORS_ORIGINS || 'http://localhost:4300,http://127.0.0.1:4300')
    .split(',')
    .map((origin) => origin.trim())
    .filter(Boolean);

  app.enableCors({
    origin: corsOrigins,
    methods: ['GET', 'HEAD', 'PUT', 'PATCH', 'POST', 'DELETE', 'OPTIONS'],
    allowedHeaders: ['Content-Type', 'Authorization'],
  });

  app.useGlobalFilters(new HttpExceptionFilter());
  app.useGlobalPipes(new ValidationPipe({
    whitelist: true,
    forbidNonWhitelisted: true
  }));

  // Swagger configuration
  const config = new DocumentBuilder()
    .setTitle('The Commit Crew - Auth API')
    .setDescription('Authentication service API')
    .setVersion('1.0.0')
    .addBearerAuth()
    .build();

  const document = SwaggerModule.createDocument(app, config);
  
  // Serve Swagger UI
  SwaggerModule.setup('api/docs', app, document);

  await app.listen(3000, '0.0.0.0');
  console.log('Auth service running on http://0.0.0.0:3000');
  console.log('Swagger docs available at http://0.0.0.0:3000/api/docs');
  console.log('OpenAPI JSON: http://0.0.0.0:3000/api/docs-json');
}

bootstrap();

// TODO: Re-enable logging to file once file permissions are fixed in Docker
// Ensure logs directory exists
// const logsDir = path.join(process.cwd(), 'logs');
// if (!fs.existsSync(logsDir)) {
//   fs.mkdirSync(logsDir);
// }