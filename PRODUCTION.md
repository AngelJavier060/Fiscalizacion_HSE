# 🚀 Producción — Fiscalización HSE

Guía paso a paso para desplegar en el **VPS** (mismo servidor que improvement-solutions, pollos, agenda, etc.).

**Repositorio:** https://github.com/AngelJavier060/Fiscalizacion_HSE

---

## Mapa de puertos en el VPS

| Proyecto | Frontend | Backend | DB |
|----------|----------|---------|-----|
| improvement-solutions | 8001 | 8082 | PG nativo |
| pollos-chanchos | 8004 | 8088 | Docker interno |
| seguimiento-agenda | 4200 | 8081 | PG nativo |
| trading-bot | 3001 | 5001 | Docker interno |
| matenimiento-clientes | — | 9088/9089 | Docker interno |
| **fiscalizacion-hse** | **8005** | **8090** | Docker interno |

> PostgreSQL de HSE **no** se expone al host. Solo el contenedor backend accede por red Docker.

---

## Checklist antes de desplegar

- [ ] DNS: `hse.improvement-solution.com` → IP del VPS
- [ ] Puertos **8005** y **8090** libres en el servidor
- [ ] Docker instalado en el VPS
- [ ] `.env` con secretos reales (no los de ejemplo)
- [ ] Contraseña admin cambiada
- [ ] `DEEPSEEK_API_KEY` si usarás IA en producción

---

## Paso 1 — Conectar al VPS

```bash
ssh tu_usuario@IP-DEL-SERVIDOR
```

---

## Paso 2 — Verificar puertos libres

```bash
ss -tlnp | grep -E '8005|8090'
# No debe mostrar nada. Si hay conflicto, revise DEPLOY.md.
```

---

## Paso 3 — Desplegar con script automático

```bash
curl -fsSL https://raw.githubusercontent.com/AngelJavier060/Fiscalizacion_HSE/main/scripts/deploy-vps.sh -o /tmp/deploy-hse.sh
chmod +x /tmp/deploy-hse.sh
/tmp/deploy-hse.sh
```

**Primera vez:** el script crea `/opt/fiscalizacion-hse`, clona el repo y genera `.env`.  
Debe editarlo:

```bash
nano /opt/fiscalizacion-hse/.env
```

Valores mínimos a cambiar:

```env
POSTGRES_PASSWORD=una_clave_larga_y_unica
DB_PASSWORD=la_misma_que_POSTGRES_PASSWORD
JWT_SECRET=base64_largo_minimo_256_bits
SUPER_ADMIN_PASSWORD=clave_admin_segura
CORS_ALLOWED_ORIGINS=https://hse.improvement-solution.com
DEEPSEEK_API_KEY=sk-...   # si usa IA
```

Vuelva a ejecutar el script:

```bash
/tmp/deploy-hse.sh
```

---

## Paso 4 — Despliegue manual (alternativa)

```bash
sudo mkdir -p /opt/fiscalizacion-hse
sudo chown $USER:$USER /opt/fiscalizacion-hse
git clone https://github.com/AngelJavier060/Fiscalizacion_HSE.git /opt/fiscalizacion-hse
cd /opt/fiscalizacion-hse
cp .env.vps.example .env
nano .env

docker compose --env-file .env -f docker-compose.yml -f docker-compose.prod.yml up -d --build
```

---

## Paso 5 — Probar sin dominio (IP + puerto)

| Servicio | URL |
|----------|-----|
| Frontend | `http://IP-SERVIDOR:8005` |
| Login API | `http://IP-SERVIDOR:8090/api/v1/auth/login` |
| Swagger | `http://IP-SERVIDOR:8090/api/v1/swagger-ui.html` |

Credenciales iniciales (cámbielas en `.env` antes del primer deploy):

- Email: `admin@fiscalizacionhse.com`
- Password: la definida en `SUPER_ADMIN_PASSWORD`

---

## Paso 6 — Nginx + HTTPS (dominio)

1. Apunte el DNS `hse.improvement-solution.com` a la IP del VPS.
2. En el servidor:

```bash
sudo cp /opt/fiscalizacion-hse/nginx-vps.example.conf /etc/nginx/sites-available/hse.improvement-solution.com
sudo ln -sf /etc/nginx/sites-available/hse.improvement-solution.com /etc/nginx/sites-enabled/
sudo certbot certonly --nginx -d hse.improvement-solution.com
sudo nginx -t && sudo systemctl reload nginx
```

O use el script:

```bash
chmod +x /opt/fiscalizacion-hse/scripts/setup-ssl-hse.sh
/opt/fiscalizacion-hse/scripts/setup-ssl-hse.sh
```

URL final: **https://hse.improvement-solution.com**

---

## Paso 7 — App móvil (Flutter)

En `app/lib/config/api_config.dart`, cuando publique la app:

```dart
static const Enviroment _env = Enviroment.production;
```

URLs de producción (ya configuradas):

```dart
Enviroment.production: 'https://hse.improvement-solution.com/api/v1',
```

Compile APK release:

```bash
cd app
flutter build apk --release
```

---

## Comandos útiles en producción

```bash
cd /opt/fiscalizacion-hse

# Ver logs
docker compose -f docker-compose.yml -f docker-compose.prod.yml logs -f backend

# Reiniciar un servicio
docker compose --env-file .env -f docker-compose.yml -f docker-compose.prod.yml restart backend

# Actualizar código
git pull origin main
docker compose --env-file .env -f docker-compose.yml -f docker-compose.prod.yml up -d --build

# Backup base de datos
docker exec fiscalizacion-hse-db pg_dump -U postgres fiscalizacion_hse > backup_$(date +%Y%m%d).sql
```

---

## Rollback / detener

```bash
cd /opt/fiscalizacion-hse
docker compose --env-file .env -f docker-compose.yml -f docker-compose.prod.yml down
```

Los volúmenes `postgres_data` y `uploads_data` conservan datos.

---

## Solución de problemas

**502 Bad Gateway en Nginx**
- Verifique que el contenedor frontend esté up: `docker ps | grep fiscalizacion`
- Pruebe directo: `curl http://127.0.0.1:8005`

**Backend no arranca**
- Logs: `docker logs fiscalizacion-hse-api`
- Verifique `DB_PASSWORD` = `POSTGRES_PASSWORD` en `.env`

**CORS en app móvil**
- Incluya el dominio en `CORS_ALLOWED_ORIGINS=https://hse.improvement-solution.com`

**Puerto ocupado**
```bash
ss -tlnp | grep 8005
# Detenga el proceso conflictivo o cambie FRONTEND_HOST_PORT en .env
```

**504 Gateway Timeout al subir PDF**

La subida procesa el PDF de forma síncrona (extracción de texto + IA) y puede tardar **varios minutos**. Nginx y Nginx Proxy Manager cortan por defecto a ~60 s.

1. **Contenedor frontend** — `frontend/nginx.conf` ya incluye timeouts de 30 min. Tras actualizar el código:
   ```bash
   cd /opt/fiscalizacion-hse
   git pull origin main
   docker compose --env-file .env -f docker-compose.yml -f docker-compose.prod.yml up -d --build frontend
   ```

2. **Nginx Proxy Manager** (dominio HTTPS) — en el Proxy Host de `hse.improvement-solution.com`, pestaña **Advanced**, pegue:
   ```nginx
   client_max_body_size 105M;
   proxy_connect_timeout 75s;
   proxy_send_timeout 1800s;
   proxy_read_timeout 1800s;
   send_timeout 1800s;
   ```
   Guarde y pruebe de nuevo la subida.

3. **Verificar que el backend sigue procesando** (no es error de la app si el 504 es solo del proxy):
   ```bash
   docker logs -f fiscalizacion-hse-api
   ```
   Debe verse `Paso 1/5` … `Paso 5/5`. Si el documento aparece en la lista tras el timeout, el proxy era el cuello de botella.
