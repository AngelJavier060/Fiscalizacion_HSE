#!/bin/bash
# SSL Let's Encrypt para hse.improvement-solution.com
# Ejecutar EN EL VPS después de deploy-vps.sh y de apuntar el DNS al servidor.
set -euo pipefail

DOMAIN="hse.improvement-solution.com"
EMAIL="${CERTBOT_EMAIL:-javierangelmsn@outlook.es}"
NGINX_SITE="/etc/nginx/sites-available/${DOMAIN}"
APP_DIR="/opt/fiscalizacion-hse"

echo "🔒 Configurando SSL para ${DOMAIN}"

if ! command -v certbot &>/dev/null; then
  echo "📦 Instalando certbot..."
  sudo apt update
  sudo apt install -y certbot python3-certbot-nginx
fi

if [ ! -f "$NGINX_SITE" ]; then
  echo "📄 Copiando configuración Nginx..."
  sudo cp "$APP_DIR/nginx-vps.example.conf" "$NGINX_SITE"
  sudo ln -sf "$NGINX_SITE" "/etc/nginx/sites-enabled/${DOMAIN}"
fi

# Certificado (requiere DNS apuntando a esta IP)
sudo certbot certonly --nginx \
  --email "$EMAIL" \
  --agree-tos \
  --no-eff-email \
  -d "$DOMAIN"

if [ -f "/etc/letsencrypt/live/${DOMAIN}/fullchain.pem" ]; then
  echo "✅ Certificado obtenido"
  sudo nginx -t
  sudo systemctl reload nginx
  echo "🌐 https://${DOMAIN}"
else
  echo "❌ No se pudo obtener el certificado. Verifique DNS:"
  echo "   dig +short ${DOMAIN}"
  echo "   Debe apuntar a: $(curl -s ifconfig.me 2>/dev/null || echo 'IP-del-VPS')"
fi
