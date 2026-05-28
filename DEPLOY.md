# Despliegue — Fiscalización HSE

Guía para levantar el sistema con Docker en **local** o **producción**, sin afectar el desarrollo tradicional (backend/frontend/app por separado).

---

## Mapa de puertos (evitar conflictos)

| Modo | PostgreSQL | Backend API | Frontend web | Notas |
|------|------------|-------------|--------------|-------|
| **Dev sin Docker** | `localhost:5434` | `localhost:8080` | `localhost:4200` | Config actual de `application.yml` |
| **Docker local** | `localhost:5435` | `localhost:8080` | `localhost:8081` | No usa puerto 80 ni 5432 en el host |
| **Docker producción** | *(solo red interna)* | *(solo red interna)* | `host:80` | Nginx hace proxy a backend |

> Si el backend en `:8080` ya está corriendo fuera de Docker, deténgalo antes de levantar el contenedor backend, o cambie `BACKEND_HOST_PORT` en `.env` (ej. `8082`).

La **app móvil Flutter** no va en Docker. Sigue apuntando a `api_config.dart`:
- Desarrollo: `10.0.2.2:8080` (emulador Android)
- Producción futura: dominio HTTPS en `Enviroment.production`

---

## Requisitos

- Docker Desktop 4.x+ (Windows/Mac) o Docker Engine + Compose v2
- Git (para clonar / versionar)
- **No** se requiere Node, Java ni PostgreSQL instalados en el host si usa Docker

---

## 1. Configuración inicial

```powershell
cd "d:\PROGRAMAS 2025 CONTRUCCION\Fiscalizacion HSE"

# Copiar plantilla de variables (NO commitear .env)
copy .env.example .env

# Editar .env: passwords, JWT_SECRET, API keys opcionales
notepad .env
```

Variables críticas en `.env`:

| Variable | Descripción |
|----------|-------------|
| `JWT_SECRET` | Secreto Base64 largo para firmar JWT |
| `POSTGRES_PASSWORD` / `DB_PASSWORD` | Deben coincidir |
| `SUPER_ADMIN_PASSWORD` | Contraseña del admin inicial |
| `DEEPSEEK_API_KEY` | IA (opcional en local) |
| `CORS_ALLOWED_ORIGINS` | Dominios permitidos en producción |

---

## 2. Docker — entorno LOCAL

```powershell
docker compose --env-file .env -f docker-compose.yml -f docker-compose.local.yml up -d --build
```

URLs:

| Servicio | URL |
|----------|-----|
| Frontend | http://localhost:8081 |
| Backend API | http://localhost:8080/api/v1 |
| Swagger | http://localhost:8080/api/v1/swagger-ui.html |
| PostgreSQL (desde host) | `localhost:5435` |

Ver logs:

```powershell
docker compose -f docker-compose.yml -f docker-compose.local.yml logs -f backend
```

Detener:

```powershell
docker compose -f docker-compose.yml -f docker-compose.local.yml down
```

---

## 3. Docker — PRODUCCIÓN (servidor)

> **Solo cuando el servidor esté listo.** No despliegue automáticamente desde GitHub.

1. Copie el proyecto al servidor (git clone o rsync).
2. Configure `.env` con secretos **reales** (nunca los valores de ejemplo).
3. Ajuste en `.env`:
   ```env
   SPRING_PROFILES_ACTIVE=prod
   FRONTEND_HOST_PORT=80
   CORS_ALLOWED_ORIGINS=https://tu-dominio.com
   ```
4. Levante:

```bash
docker compose --env-file .env -f docker-compose.yml -f docker-compose.prod.yml up -d --build
```

En producción:
- PostgreSQL y backend **no** se exponen al host.
- Nginx sirve Angular y hace proxy `/api/` → backend interno.
- Coloque un reverse proxy externo (Caddy, Nginx, Cloudflare) para HTTPS si aplica.

---

## 4. Desarrollo tradicional (sin Docker)

Sigue funcionando igual que antes:

```powershell
# Terminal 1 — Backend (PostgreSQL local en 5434)
cd backend
mvn spring-boot:run

# Terminal 2 — Frontend
cd frontend
npm install
ng serve

# Terminal 3 — App móvil
cd app
flutter run
```

Perfil `local` carga `application-local.yml` (gitignored) para claves DeepSeek.

---

## 5. Estructura Docker

```
docker-compose.yml          # Servicios base (postgres, backend, frontend)
docker-compose.local.yml    # Puertos 5435 / 8080 / 8081
docker-compose.prod.yml     # Solo frontend:80, perfil prod, restart policy
.env.example                # Plantilla (versionada)
.env                        # Secretos reales (NO versionar)
backend/Dockerfile          # Maven multi-stage → JRE 17
frontend/Dockerfile         # Node build + nginx
frontend/nginx.conf         # Proxy /api/ → backend:8080
```

---

## 6. Subir código a GitHub (respaldo, NO producción)

El repositorio guarda código fuente. **No despliega** a producción automáticamente.

```powershell
git init
git add .
git commit -m "Initial commit: Fiscalización HSE (backend, frontend, app móvil)"

# Crear repo vacío en GitHub → luego:
git remote add origin https://github.com/TU_USUARIO/fiscalizacion-hse.git
git branch -M main
git push -u origin main
```

Archivos que **nunca** deben subirse:
- `.env`
- `application-local.yml`
- `backend/target/`, `node_modules/`, `app/build/`

---

## 7. Checklist pre-producción

- [ ] `.env` con secretos únicos (JWT, DB, admin)
- [ ] `CORS_ALLOWED_ORIGINS` con dominio real HTTPS
- [ ] `SUPER_ADMIN_PASSWORD` cambiada
- [ ] Backup de volumen `postgres_data`
- [ ] Backup de volumen `uploads_data` (PDFs)
- [ ] Firewall: solo puertos 80/443 públicos
- [ ] Certificado SSL (Let's Encrypt / Cloudflare)
- [ ] App móvil: cambiar `api_config.dart` a `Enviroment.production`

---

## Solución de problemas

**Puerto 8080 en uso**
```powershell
netstat -ano | findstr :8080
# Detener el proceso o cambiar BACKEND_HOST_PORT=8082 en .env
```

**Backend no conecta a Postgres**
- Verifique que `DB_PASSWORD` = `POSTGRES_PASSWORD` en `.env`
- Espere el healthcheck: `docker compose ps`

**Frontend en Docker llama a localhost:8080**
- Verifique que el build use `environment.prod.ts` (fileReplacements en `angular.json`)
- Reconstruya: `docker compose ... up -d --build frontend`

**CORS desde Angular dev (4200)**
- Incluya `http://localhost:4200` en `CORS_ALLOWED_ORIGINS`
