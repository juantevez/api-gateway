# 🚀 API Gateway - Bike Ecosystem

API Gateway centralizado para el ecosistema Bike, implementado con **Spring Cloud Gateway** y validación JWT.

---

## 📋 Tabla de Contenidos

- [Descripción](#-descripción)
- [Arquitectura](#-arquitectura)
- [Tecnologías](#-tecnologías)
- [Requisitos Previos](#-requisitos-previos)
- [Estructura del Proyecto](#-estructura-del-proyecto)
- [Configuración](#-configuración)
- [Ejecución](#-ejecución)
- [Rutas y Endpoints](#-rutas-y-endpoints)
- [Autenticación](#-autenticación)
- [Variables de Entorno](#-variables-de-entorno)
- [Docker](#-docker)
- [Troubleshooting](#-troubleshooting)

---

## 📖 Descripción

El API Gateway actúa como punto de entrada único para todos los microservicios del ecosistema Bike:

- ✅ Validación centralizada de JWT
- ✅ Extracción y propagación de `userId` a microservicios
- ✅ Ruteo dinámico a servicios backend
- ✅ Rutas públicas sin autenticación
- ✅ Logging centralizado de requests

---

## 🏗️ Arquitectura
```
                        ┌─────────────┐
                        │   Cliente   │
                        └──────┬──────┘
                               │ JWT (Authorization: Bearer xxx)
                               ▼
                    ┌──────────────────────┐
                    │   API Gateway :8000  │
                    │  • Valida JWT        │
                    │  • Extrae userId     │
                    │  • Agrega X-User-Id  │
                    │  • Rutea requests    │
                    └──────────┬───────────┘
           ┌───────────────────┼───────────────────┬────────────────────┐
           │                   │                   │                    │
           ▼                   ▼                   ▼                    ▼
    ┌────────────┐      ┌────────────┐      ┌────────────┐      ┌────────────┐
    │   auth     │      │    bike    │      │   theft    │      │   media    │
    │   :8084    │      │   :8080    │      │   :8083    │      │   :8081    │
    └────────────┘      └────────────┘      └────────────┘      └────────────┘
           │                   │                   │                    │
           └───────────────────┴───────────────────┴────────────────────┘
                                         │
                              ┌──────────▼──────────┐
                              │   PostgreSQL :5432  │
                              │  ┌──────┐ ┌──────┐  │
                              │  │ auth │ │ bike │  │
                              │  └──────┘ └──────┘  │
                              └─────────────────────┘
```

---

## 🛠️ Tecnologías

| Tecnología | Versión | Propósito |
|------------|---------|-----------|
| Java | 17 | Lenguaje principal |
| Spring Boot | 3.2.0 | Framework base |
| Spring Cloud Gateway | 2023.0.0 | Gateway reactivo |
| JJWT | 0.12.3 | Validación de JWT |
| Nimbus JOSE JWT | 9.37.3 | Carga de JWKS |
| Docker | 24.x | Containerización |

---

## 📦 Requisitos Previos

| Herramienta | Versión Mínima |
|-------------|---------------|
| Java JDK | 17 |
| Maven | 3.9.x |
| Docker | 24.x |

---

## 📁 Estructura del Proyecto
```
api-gateway/
├── docker-compose.yml
├── Dockerfile
├── pom.xml
├── README.md
└── src/
    └── main/
        ├── java/
        │   └── com/
        │       └── bikefinder/
        │           └── gateway/
        │               ├── GatewayApplication.java
        │               └── filter/
        │                   └── JwtAuthenticationFilter.java
        └── resources/
            └── application.yml
```

---

## ⚙️ Configuración

### application.yml
```yaml
server:
  port: 8000

spring:
  application:
    name: api-gateway
  cloud:
    gateway:
      routes:
        - id: auth-service
          uri: http://auth-service:8084
          predicates:
            - Path=/auth/**, /oauth2/**, /.well-known/**

        - id: bike-registration
          uri: http://bike-registration-app:8080
          predicates:
            - Path=/api/v1/bicycles/**, /api/v1/catalog/**

        - id: theft-report
          uri: http://theft-report-app:8083
          predicates:
            - Path=/api/v1/theft-reports/**, /api/v1/stolen-bikes/**, /api/v1/tips/**

        - id: media-service
          uri: http://media-service-app:8081
          predicates:
            - Path=/api/v1/media/**

        - id: location-service
          uri: http://location-service-app:8082
          predicates:
            - Path=/api/v1/locations/**

auth:
  jwks:
    url: http://auth-service:8084/.well-known/jwks.json
  jwt:
    issuer: auth-service
    audience: bike-ecosystem

gateway:
  public-paths: >
    /auth/**,
    /oauth2/**,
    /.well-known/**,
    /api/v1/catalog/**,
    /api/v1/locations/**,
    /api/v1/stolen-bikes/*/public,
    /api/v1/stolen-bikes/*/pdf,
    /api/v1/tips/**,
    /actuator/health
```

---

## 🚀 Ejecución

### Opción 1: Docker Compose (Recomendado)
```bash
# Asegurar que la red existe
docker network create bike-network 2>/dev/null || true

# Asegurar que los otros servicios estén corriendo
# (auth-service, bike-registration, etc.)

# Levantar el gateway
docker-compose up -d

# Ver logs
docker logs -f api-gateway
```

### Opción 2: Maven (desarrollo local)
```bash
# Compilar
mvn clean package -DskipTests

# Ejecutar
mvn spring-boot:run

# O ejecutar el JAR
java -jar target/api-gateway-1.0.0.jar
```

---

## 🛣️ Rutas y Endpoints

### Ruteo de Servicios

| Ruta | Servicio Destino | Puerto |
|------|------------------|--------|
| `/auth/**` | auth-service | 8084 |
| `/oauth2/**` | auth-service | 8084 |
| `/.well-known/**` | auth-service | 8084 |
| `/api/v1/bicycles/**` | bike-registration | 8080 |
| `/api/v1/catalog/**` | bike-registration | 8080 |
| `/api/v1/theft-reports/**` | theft-report | 8083 |
| `/api/v1/stolen-bikes/**` | theft-report | 8083 |
| `/api/v1/tips/**` | theft-report | 8083 |
| `/api/v1/media/**` | media-service | 8081 |
| `/api/v1/locations/**` | location-service | 8082 |

### Rutas Públicas (sin JWT)

| Ruta | Descripción |
|------|-------------|
| `/auth/**` | Login, registro, refresh token |
| `/oauth2/**` | OAuth2 social login |
| `/.well-known/**` | JWKS público |
| `/api/v1/catalog/**` | Catálogo de bicicletas |
| `/api/v1/locations/**` | Países, provincias, localidades |
| `/api/v1/stolen-bikes/*/public` | Info pública de bicis robadas |
| `/api/v1/stolen-bikes/*/pdf` | PDF público con QR |
| `/api/v1/tips/**` | Envío de tips anónimos |
| `/actuator/health` | Health check |

### Rutas Protegidas (requieren JWT)

| Ruta | Descripción |
|------|-------------|
| `/api/v1/bicycles/**` | Registro y gestión de bicicletas |
| `/api/v1/theft-reports/**` | Reportes de robo del usuario |
| `/api/v1/media/**` | Upload de fotos |

---

## 🔐 Autenticación

### Flujo de Autenticación
```
1. Cliente obtiene JWT de auth-service:
   POST /auth/login → { accessToken: "eyJ..." }

2. Cliente envía request con JWT:
   GET /api/v1/bicycles
   Header: Authorization: Bearer eyJ...

3. Gateway valida JWT:
   - Obtiene clave pública de /.well-known/jwks.json
   - Verifica firma, issuer, audience, expiración

4. Gateway propaga userId a servicios:
   Header: X-User-Id: uuid-del-usuario
   Header: X-User-Email: email@ejemplo.com

5. Servicio downstream procesa request con userId
```

### Headers Propagados

| Header | Descripción | Ejemplo |
|--------|-------------|---------|
| `X-User-Id` | UUID del usuario autenticado | `550e8400-e29b-41d4-a716-446655440000` |
| `X-User-Email` | Email del usuario | `usuario@bike.com` |

### Ejemplo de Request
```bash
# 1. Login
curl -X POST http://localhost:8000/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "test@bike.com", "password": "SecurePass123!"}'

# Response: { "accessToken": "eyJ...", "refreshToken": "xxx" }

# 2. Request protegido
curl -X POST http://localhost:8000/api/v1/bicycles/from-catalog \
  -H "Authorization: Bearer eyJ..." \
  -H "Content-Type: application/json" \
  -d '{"catalogBikeId": 5, "colorwayId": 8, "frameSize": "M"}'
```

---

## 🔧 Variables de Entorno

| Variable | Descripción | Default |
|----------|-------------|---------|
| `SERVER_PORT` | Puerto del gateway | `8000` |
| `AUTH_JWKS_URL` | URL del JWKS de auth-service | `http://auth-service:8084/.well-known/jwks.json` |
| `AUTH_JWT_ISSUER` | Issuer esperado en JWT | `auth-service` |
| `AUTH_JWT_AUDIENCE` | Audience esperado en JWT | `bike-ecosystem` |

---

## 🐳 Docker

### docker-compose.yml
```yaml
services:
  api-gateway:
    build:
      context: .
      dockerfile: Dockerfile
    image: bikefinder/api-gateway:latest
    container_name: api-gateway
    ports:
      - "8000:8000"
    environment:
      AUTH_JWKS_URL: http://auth-service:8084/.well-known/jwks.json
      AUTH_JWT_ISSUER: auth-service
      AUTH_JWT_AUDIENCE: bike-ecosystem
    networks:
      - bike-network
    restart: unless-stopped

networks:
  bike-network:
    external: true
```

### Comandos útiles
```bash
# Build
docker-compose build --no-cache

# Levantar
docker-compose up -d

# Ver logs
docker logs -f api-gateway

# Detener
docker-compose down

# Verificar red
docker network inspect bike-network
```

---

## 🔍 Troubleshooting

### Error: "Unable to load JWKS"
```
Causa: auth-service no está corriendo o no es accesible
Solución:
1. Verificar que auth-service está corriendo: docker ps | grep auth
2. Verificar red: docker network inspect bike-network
3. Probar JWKS manualmente: curl http://auth-service:8084/.well-known/jwks.json
```

### Error: "401 Unauthorized"
```
Causa: Token inválido, expirado, o issuer/audience incorrecto
Solución:
1. Verificar que el token no está expirado
2. Verificar issuer en auth-service: auth-service
3. Verificar audience: bike-ecosystem
4. Decodificar token en jwt.io para inspeccionar claims
```

### Error: "Service Unavailable"
```
Causa: Servicio destino no accesible
Solución:
1. Verificar que el servicio está corriendo
2. Verificar que está en la misma red Docker
3. Verificar nombre del contenedor en las rutas
```

### Verificar conectividad entre servicios
```bash
# Entrar al contenedor del gateway
docker exec -it api-gateway sh

# Probar conectividad
wget -qO- http://auth-service:8084/actuator/health
wget -qO- http://bike-registration-app:8080/actuator/health
```

---

## 📊 Monitoreo

### Health Check
```bash
curl http://localhost:8000/actuator/health
```

### Logs
```bash
# Tiempo real
docker logs -f api-gateway

# Últimas 100 líneas
docker logs --tail 100 api-gateway

# Filtrar errores
docker logs api-gateway 2>&1 | grep ERROR
```

---

## 🚢 Producción

### Checklist

- [ ] Configurar HTTPS/TLS
- [ ] Implementar rate limiting
- [ ] Agregar circuit breaker
- [ ] Configurar CORS correctamente
- [ ] Habilitar métricas Prometheus
- [ ] Configurar logging centralizado

### Kubernetes (ejemplo)
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: api-gateway
spec:
  replicas: 2
  template:
    spec:
      containers:
        - name: api-gateway
          image: bikefinder/api-gateway:1.0.0
          ports:
            - containerPort: 8000
          env:
            - name: AUTH_JWKS_URL
              value: "http://auth-service:8084/.well-known/jwks.json"
          resources:
            limits:
              memory: "256Mi"
              cpu: "500m"
```

---

## 📄 Licencia

Proprietary - Bike Ecosystem © 2026
