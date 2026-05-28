#!/bin/bash
# =============================================================================
# Despliegue VPS — Fiscalización HSE
# Ejecutar EN EL SERVIDOR Linux (como root o usuario con docker)
# Repo: https://github.com/AngelJavier060/Fiscalizacion_HSE
# =============================================================================
set -euo pipefail

APP_DIR="/opt/fiscalizacion-hse"
REPO_URL="https://github.com/AngelJavier060/Fiscalizacion_HSE.git"
COMPOSE_FILES="-f docker-compose.yml -f docker-compose.prod.yml"

echo "🚀 Despliegue Fiscalización HSE en VPS"
echo "======================================="

# --- Docker / Compose ---
if docker compose version &>/dev/null; then
  COMPOSE="docker compose"
elif command -v docker-compose &>/dev/null; then
  COMPOSE="docker-compose"
else
  echo "❌ Instala Docker y Docker Compose primero."
  exit 1
fi

# --- Verificar puertos libres (no chocar con otros programas) ---
echo "🔍 Verificando puertos 8005 y 8090..."
for PORT in 8005 8090; do
  if ss -tlnp 2>/dev/null | grep -q ":${PORT} " || netstat -tlnp 2>/dev/null | grep -q ":${PORT} "; then
    echo "❌ Puerto ${PORT} ya está en uso. Revise DEPLOY.md (mapa de puertos)."
    ss -tlnp | grep ":${PORT} " || true
    exit 1
  fi
done
echo "✅ Puertos 8005 y 8090 libres"

# --- Clonar o actualizar ---
if [ ! -d "$APP_DIR" ]; then
  echo "📁 Creando $APP_DIR..."
  sudo mkdir -p "$APP_DIR"
  sudo chown "$(whoami):$(whoami)" "$APP_DIR"
  git clone "$REPO_URL" "$APP_DIR"
else
  echo "📁 Actualizando código en $APP_DIR..."
fi
cd "$APP_DIR"
git pull origin main

# --- .env de producción ---
if [ ! -f .env ]; then
  echo "⚙️  Creando .env desde plantilla..."
  cp .env.vps.example .env
  echo ""
  echo "⚠️  IMPORTANTE: Edite .env con secretos reales antes de continuar:"
  echo "   nano $APP_DIR/.env"
  echo ""
  echo "   Cambie: POSTGRES_PASSWORD, DB_PASSWORD, JWT_SECRET,"
  echo "           SUPER_ADMIN_PASSWORD, DEEPSEEK_API_KEY, CORS_ALLOWED_ORIGINS"
  exit 0
fi

# --- Validar que no queden placeholders ---
if grep -q "CAMBIAR_PASSWORD\|GENERAR_SECRETO" .env; then
  echo "❌ .env aún tiene valores de ejemplo. Edítelo:"
  echo "   nano $APP_DIR/.env"
  exit 1
fi

# --- Build y levantar ---
echo "🐳 Deteniendo contenedores anteriores (si existen)..."
$COMPOSE --env-file .env $COMPOSE_FILES down 2>/dev/null || true

echo "🐳 Construyendo e iniciando (prod)..."
$COMPOSE --env-file .env $COMPOSE_FILES up -d --build

echo "⏳ Esperando arranque del backend..."
sleep 20

echo ""
echo "📊 Estado:"
$COMPOSE --env-file .env $COMPOSE_FILES ps

echo ""
echo "✅ Despliegue completado"
SERVER_IP=$(hostname -I 2>/dev/null | awk '{print $1}' || echo "TU-IP")
echo ""
echo "🌐 URLs (antes de configurar dominio SSL):"
echo "   Frontend:  http://${SERVER_IP}:8005"
echo "   Backend:   http://${SERVER_IP}:8090/api/v1"
echo ""
echo "📋 Siguiente paso — Nginx + SSL:"
echo "   sudo cp nginx-vps.example.conf /etc/nginx/sites-available/hse.improvement-solution.com"
echo "   sudo ln -sf /etc/nginx/sites-available/hse.improvement-solution.com /etc/nginx/sites-enabled/"
echo "   sudo certbot certonly --nginx -d hse.improvement-solution.com"
echo "   sudo nginx -t && sudo systemctl reload nginx"
echo ""
echo "📱 App móvil (producción): api_config.dart → Enviroment.production"
echo "   URL: https://hse.improvement-solution.com/api/v1"
