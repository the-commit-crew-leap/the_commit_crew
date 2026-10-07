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