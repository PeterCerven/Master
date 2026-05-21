# Master

Full-stack aplikácia na generovanie grafov cestnej siete z GPS trajektórií (GPX súbory) a výpočet rozmiestnenia nabíjacích staníc. Spracovateľský pipeline zahŕňa predspracovanie GPS bodov, map matching pomocou GraphHopper voči OpenStreetMap, vloženie trajektórií do grafu a H3 priestorové zlučovanie. Zameranie na región Slovenska.

## Tech Stack

- **Backend**: Java 25, Spring Boot 4.0.3, Maven, PostgreSQL, JPA/Hibernate, Spring Security (JWT)
- **Frontend**: Angular 21, TypeScript, Tailwind CSS + DaisyUI, Angular Material, Google Maps API
- **Infraštruktúra**: Docker Compose (backend + frontend + PostgreSQL)
- **Kľúčové knižnice**: GraphHopper, JGraphT, JTS, H3, JPX, deck.gl, Transloco (SK/EN)

## Funkcionalita

- Import GPS dát z formátov **GPX, GeoJSON/JSON, CSV**
- Import cestnej siete mesta priamo z OpenStreetMap (cez Nominatim + GraphHopper)
- Vizualizácia grafu na Google Maps
- Výpočet rozmiestnenia nabíjacích staníc — algoritmy **Random**, **Greedy**, **GRASP**
- Per-user konfigurácia pipeline parametrov
- Ukladanie a načítavanie grafov z databázy
- Autentifikácia s JWT (access + refresh token), správa používateľov (ADMIN/USER)
- Dvojjazyčné UI (slovenčina / angličtina), tmavý režim

## Predpoklady

- **Docker** a **Docker Compose** (pre spustenie cez Docker)
- Pri lokálnom vývoji bez Dockera: **JDK 25**, **Maven**, **Node.js 20+**, **PostgreSQL 16**
- OSM PBF súbor pre GraphHopper umiestnený v `infrastructure/data/merged_sk_cz_au.osm.pbf`
- Google Maps API kľúč v `frontend/src/api-keys/google.js`

## Spustenie cez Docker (odporúčané)

1. Vytvor `.env` súbory podľa vzorov:
   - `infrastructure/.env` (skopíruj z `infrastructure/.env.example`)
   - `infrastructure/database/.env` (skopíruj z `infrastructure/database/.env.example`)

   Premenné na nastavenie: `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_DB`, `JWT_SECRET`, `FRONTEND_URL`, `ADMIN_EMAIL`, `ADMIN_PASSWORD` (a `DOMAIN` pre produkciu).

2. Stiahni OSM PBF súbor a ulož ho do `infrastructure/data/`.

3. Spusti stack:

   ```bash
   cd infrastructure
   docker-compose -f docker-compose-dev.yml up
   ```

   Pre produkciu (s Caddy reverse proxy):
   ```bash
   docker-compose -f docker-compose-prod.yml up
   ```

4. Otvor aplikáciu:
   - **Frontend**: http://localhost:4200
   - **Backend API**: http://localhost:8080
   - **PostgreSQL**: localhost:5432

5. Prihlás sa s predvoleným admin účtom: `admin@master.sk` / `admin123`.

## Lokálny vývoj (bez Dockera)

### Databáza

Spusti samotnú databázu cez Docker:
```bash
cd infrastructure
docker-compose -f docker-compose.db.yml up
```

### Backend

```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Backend beží na `http://localhost:8080`. Dev profil očakáva PostgreSQL na `localhost:5432` a OSM dáta v `infrastructure/data/`.

### Frontend

```bash
cd frontend
npm install
npm start
```

Frontend beží na `http://localhost:4200`.

## Užitočné príkazy

```bash
# Backend testy
cd backend && ./mvnw test

# Produkčný build backendu
cd backend && ./mvnw clean package

# Frontend formátovanie
cd frontend && npx prettier --write .

# Produkčný build frontendu
cd frontend && npm run build
```

## Štruktúra projektu

```
.
├── backend/          # Spring Boot aplikácia
├── frontend/         # Angular aplikácia
└── infrastructure/   # Docker Compose, databáza, OSM dáta

```

