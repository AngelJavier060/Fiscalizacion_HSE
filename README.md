# 🏛️ Fiscalización HSE

**Sistema de Gestión de Normativas HSE (Health, Safety & Environment)**

Plataforma multiempresa para la gestión, consulta y fiscalización de normativas de Salud, Seguridad y Medio Ambiente.

---

## 📋 Arquitectura

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│   Frontend      │     │   Backend       │     │   Base de Datos │
│   Angular 18    │────>│   Spring Boot 3 │────>│   PostgreSQL 16 │
│   (Standalone)  │     │   + JWT + JPA   │     │                 │
└─────────────────┘     └─────────────────┘     └─────────────────┘
```

## 🚀 Inicio Rápido (Con Docker — local)

```bash
# 1. Copiar variables de entorno
cp .env.example .env   # Windows: copy .env.example .env

# 2. Iniciar servicios (puertos seguros: PG 5435, web 8081, API 8080)
docker compose --env-file .env -f docker-compose.yml -f docker-compose.local.yml up -d --build

# 3. Acceder
# Frontend:  http://localhost:8081
# Backend:   http://localhost:8080/api/v1
# Swagger:   http://localhost:8080/api/v1/swagger-ui.html
```

> Guía completa de despliegue local y producción: **[DEPLOY.md](./DEPLOY.md)**

## 🧪 Inicio Rápido (Desarrollo Local — sin Docker)

### Backend
```bash
cd backend
mvn spring-boot:run
# PostgreSQL local en puerto 5434 (application.yml)
```

### Frontend
```bash
cd frontend
npm install
ng serve
# http://localhost:4200
```

### App móvil (Flutter)
```bash
cd app
flutter pub get
flutter run
```

## 👤 Credenciales por Defecto

| Rol | Email | Contraseña |
|-----|-------|-----------|
| **Super Admin** | admin@fiscalizacionhse.com | AdminHSE2024! |

## 📐 Estructura del Proyecto

```
fiscalizacion-hse/
├── backend/          # Spring Boot API REST
│   ├── src/
│   └── pom.xml
├── frontend/         # Angular 18 Standalone
│   ├── src/
│   └── package.json
├── app/              # App móvil Flutter (Android/iOS)
│   ├── lib/
│   └── pubspec.yaml
├── docker-compose.yml
├── docker-compose.local.yml
├── docker-compose.prod.yml
├── .env.example
└── DEPLOY.md
```

## 🗺️ Roadmap

| Fase | Descripción | Estado |
|------|-------------|--------|
| **Fase 1** | Core + Roles + Multiempresa + Auth JWT | ✅ Completo |
| **Fase 2** | Documentos PDF + Traducción EN→ES + Puntos Clave | 📅 Pendiente |
| **Fase 3** | Recordatorios + Notificaciones + Scheduler | 📅 Pendiente |
| **Fase 4** | IA + Búsqueda Inteligente + Embeddings | 📅 Pendiente |
| **Fase 5** | App Móvil Flutter (lectura, TTS, FISCALIZA-AI) | 🚧 En desarrollo |
