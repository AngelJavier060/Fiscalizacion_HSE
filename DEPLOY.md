# Despliegue — Fiscalización HSE

Guía para levantar el sistema con Docker en **local** o **producción**, sin afectar el desarrollo tradicional (backend/frontend/app por separado).

---

## Mapa de puertos (evitar conflictos)

### En tu PC (estado detectado)

| Puerto | Servicio activo ahora | Uso HSE |
|--------|----------------------|---------|
| `5432` | PostgreSQL | ❌ No usar en Docker local |
| `5434` | PostgreSQL (dev HSE) | ✅ Dev sin Docker |
| `5435` | libre | ✅ Postgres Docker local |
| `8080` | Backend Spring (dev) | ❌ Usar `8082` en Docker local |
| `8081` | libre | ✅ Frontend Docker local |
| `8082` | libre | ✅ Backend Docker local |
| `4200` | Angular dev | Dev sin Docker |

### Por modo de ejecución

| Modo | PostgreSQL | Backend API | Frontend web |
|------|------------|-------------|--------------|
| **Dev sin Docker** | `localhost:5434` | `localhost:8080` | `localhost:4200` |
| **Docker local** | `localhost:5435` | `localhost:8082` | `localhost:8081` |
| **Docker VPS** | solo red interna | `localhost:8090` | `localhost:8005` |

> El `.env` local ya viene con `BACKEND_HOST_PORT=8082` para no chocar con el backend que tienes corriendo en `:8080`.

### Puertos en el VPS (todos tus programas)

| Proyecto | Frontend | Backend | Base de datos |
|----------|----------|---------|---------------|
| improvement-solutions | **8001** | **8082** | PG nativo `:5432` |
| pollos-chanchos | **8004** | **8088** | Docker interno |
| seguimiento-agenda | **4200** | **8081** | PG nativo `:5432` |
| trading-bot | **3001** | **5001** | Docker interno |
| matenimiento-clientes | override | **9088/9089** | Docker interno |
| **fiscalizacion-hse** | **8005** ✅ | **8090** ✅ | Docker interno |

Nginx del VPS apunta el dominio → `localhost:8005`. Ver plantilla `nginx-vps.example.conf`.

La **app móvil Flutter** no va en Docker:
- Desarrollo: `10.0.2.2:8080` (emulador Android)
- Producción: dominio HTTPS en `api_config.dart` → `Enviroment.production`

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
| Backend API | http://localhost:8082/api/v1 |
| Swagger | http://localhost:8082/api/v1/swagger-ui.html |
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

## 3. Docker — PRODUCCIÓN (VPS)

> **No despliegue automático.** Solo cuando el servidor esté listo.

1. Copie el proyecto al VPS (`git clone` o `scp`).
2. Copie la plantilla del servidor:
   ```bash
   cp .env.vps.example .env
   nano .env   # secretos reales, dominio CORS
   ```
3. Verifique que **8005** y **8090** estén libres en el VPS:
   ```bash
   ss -tlnp | grep -E '8005|8090'
   ```
4. Levante:
   ```bash
   docker compose --env-file .env -f docker-compose.yml -f docker-compose.prod.yml up -d --build
   ```
5. Configure Nginx con `nginx-vps.example.conf` (certificado Let's Encrypt).

URLs en el VPS (antes de dominio):

| Servicio | URL |
|----------|-----|
| Frontend | http://IP-SERVIDOR:8005 |
| Backend directo | http://IP-SERVIDOR:8090/api/v1 |
| Con dominio | https://hse.improvement-solution.com (ejemplo) |

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
.env.example                # Plantilla local
.env.vps.example            # Plantilla VPS (8005 / 8090)
nginx-vps.example.conf      # Nginx del servidor (como improvement/pollos)
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
