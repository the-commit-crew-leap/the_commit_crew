import * as dotenv from 'dotenv';
import * as path from 'path';
import * as fs from 'fs';

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

async function bootstrap() {
  const app = await NestFactory.create(AppModule);
  app.useGlobalFilters(new HttpExceptionFilter());

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

// Ensure logs directory exists
const logsDir = path.join(process.cwd(), 'logs');
if (!fs.existsSync(logsDir)) {
  fs.mkdirSync(logsDir);
}