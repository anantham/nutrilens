# NutriLens

**Track your meals with AI. Snap a photo, get 25+ nutrition insights instantly.**

## What is this?

NutriLens is a "compound interest app" for nutrition tracking - it automatically gets better as AI models improve, without code changes. Take a photo of your meal and GPT-4 Vision analyzes it, extracting not just calories but also food quality metrics, location context, and health markers.

The magic: your photos include GPS metadata that identifies restaurants, and the AI adjusts estimates based on where and when you ate. Home-cooked meals get different assumptions than restaurant meals. No extra input required.

The project has two parts:
- **Backend**: Spring Boot REST API (Java 17)
- **Frontend**: Flutter app (iOS, Android, Web)

## Screenshots

<table>
  <tr>
    <td><img src="screenshots/Screenshot_20251024_002055.jpg" width="250"/></td>
    <td><img src="screenshots/Screenshot_20251024_002204.jpg" width="250"/></td>
    <td><img src="screenshots/Screenshot_20251024_002213.jpg" width="250"/></td>
  </tr>
  <tr>
    <td align="center">Daily tracking</td>
    <td align="center">Weekly calendar</td>
    <td align="center">Meal upload</td>
  </tr>
  <tr>
    <td><img src="screenshots/Screenshot_20251024_002221.jpg" width="250"/></td>
    <td><img src="screenshots/Screenshot_20251024_002227.jpg" width="250"/></td>
    <td></td>
  </tr>
  <tr>
    <td align="center">Nutrition trends</td>
    <td align="center">Analytics dashboard</td>
    <td></td>
  </tr>
</table>

## How it works

```
You take a photo → Upload to backend → Extract GPS from EXIF → Google Maps identifies location
                                                ↓
                        Stored in GCS → OpenAI analyzes with context
                                                ↓
Frontend displays nutrition ← API returns 25+ fields ← Saved to PostgreSQL
                    ↓
            Shows location badge: 🍽️ Chipotle or 🏠 Home-cooked
```

**Enhanced AI Analysis:**
- **Basic macros:** Calories, protein, carbs, fat, fiber, sugar, sodium
- **Food quality:** NOVA score (1-4), processing level, cooking method
- **Glycemic data:** Estimated GI/GL for blood sugar impact
- **Plant diversity:** Count and list of unique plant species
- **Fat quality:** Saturated/unsaturated/trans classification
- **Location context:** Restaurant vs home, cuisine type, price level

You can also just type a description if you don't have a photo ("black coffee", "2 scrambled eggs"), and it'll estimate the nutrition.

## Quick Start

### What you need

- Java 17+
- Flutter 3.24+
- PostgreSQL 15+
- OpenAI API key (GPT-4 Vision)
- Google Cloud Storage bucket
- Google Maps API key (for location intelligence)

### Setup

1. Clone this repo
```bash
git clone https://github.com/yourusername/nutritheous-server.git
cd nutritheous-server
```

2. Create your `.env` file
```bash
cp .env.example .env
```

Edit `.env` with your actual credentials:
```env
DB_HOST=localhost
DB_PORT=5432
DB_NAME=nutritheous
DB_USERNAME=nutritheous
DB_PASSWORD=nutritheous

# GCS credentials as JSON string
GCS_PROJECT_ID=your-project-id
GCS_CREDENTIALS_JSON={"type":"service_account",...}
GCS_BUCKET_NAME=your-bucket-name

# Get from platform.openai.com
OPENAI_API_KEY=sk-proj-...

# Get from Google Cloud Console (Maps API)
GOOGLE_MAPS_API_KEY=AIza...

# Generate with: openssl rand -base64 32
JWT_SECRET=your-secret-key
```

3. Start PostgreSQL
```bash
make docker-up
```

4. Run the backend
```bash
make run
```

5. Run the Flutter app
```bash
cd frontend/nutritheous_app
flutter pub get
flutter run
```

That's it. Backend runs on `localhost:8081`.

## Project Structure

```
nutritheous-server/
├── backend/              # Spring Boot API
│   ├── src/
│   │   └── main/java/com/nutritheous/
│   │       ├── auth/     # Login, registration
│   │       ├── meal/     # Meal CRUD
│   │       ├── storage/  # GCS integration
│   │       └── analyzer/ # OpenAI Vision
│   └── README.md
│
├── frontend/
│   └── nutritheous_app/  # Flutter app
│       ├── lib/
│       │   ├── models/
│       │   ├── services/
│       │   ├── state/    # Riverpod
│       │   └── ui/
│       └── README.md
│
├── Makefile              # Shortcuts for common commands
├── docker-compose.yml
└── .env.example
```

## Tech Stack

**Backend**
- Spring Boot 3.2, Java 17
- PostgreSQL 15 with Flyway migrations
- Google Cloud Storage for images
- OpenAI GPT-4 Vision for AI analysis (25+ fields)
- Google Maps API (geocoding + places) for location intelligence
- JWT auth
- Metadata Extractor (EXIF GPS extraction)

**Frontend**
- Flutter 3.24, Dart 3.5
- Riverpod for state management
- Dio for HTTP
- Hive for local storage
- Material Design 3
- JSON serialization with build_runner

**AI & Data Pipeline**
- Photo → EXIF extraction → GPS coordinates
- GPS → Google Maps → Restaurant/home identification
- Context + photo → GPT-4 Vision → 25+ nutrition fields
- Results stored in PostgreSQL for analytics

## Makefile Commands

I added a Makefile because typing `./gradlew bootRun` gets old fast.

```bash
make help           # Show all commands
make run            # Start backend
make fresh          # Reset database and start fresh
make build          # Build backend
make test           # Run tests
make db-status      # Check database
make flutter-build  # Build Flutter APK
make flutter-run    # Run Flutter app
```

## API Docs

When the backend is running:
- Swagger UI: http://localhost:8081/swagger-ui.html
- OpenAPI spec: http://localhost:8081/v3/api-docs

## Key Features

**For Users:**
- 📸 **Photo-based meal tracking** - Just snap a picture
- 🤖 **25+ AI-extracted fields** - Macros, food quality, glycemic data, plant diversity
- 📍 **Location intelligence** - Automatic restaurant detection with GPS
- 🏠 **Context-aware analysis** - Home vs restaurant, cuisine type, price level
- 🎯 **Accuracy boost** - 20-30% better estimates with location context
- 📊 **Daily goals** - Personalized calorie targets
- 📈 **Analytics** - Weekly/monthly trends and insights
- ✍️ **Text-only option** - No photo needed

**Phase 1 Features (Shipped Oct 2024):**
- **Enhanced AI (1A):** NOVA score, GI/GL, plant diversity, fat quality, cooking method
- **Location intelligence (1B):** GPS extraction, Google Maps integration, restaurant detection
- **Context-aware AI (1B.2):** Prompts adjusted based on where/when you ate
- **Flutter UI (1D):** Location badges (🍽️ Chipotle, 🏠 Home-cooked)

**For Developers:**
- Clean separation of backend/frontend
- Environment-based config
- Docker support
- Comprehensive API docs with Swagger
- Flyway migrations for schema versioning
- Future-proof: Gets better as AI models improve

## Configuration Notes

### GCP Credentials

You have two options:

**Option 1: JSON in .env (recommended)**
```env
GCS_CREDENTIALS_JSON={"type":"service_account","project_id":"..."}
```

**Option 2: File path (local dev)**
```env
GCS_CREDENTIALS_PATH=backend/src/main/resources/gcp-credentials.json
```

The code checks for JSON first, then falls back to file path.

### Flutter .env

The Flutter app needs its own `.env`:
```bash
cd frontend/nutritheous_app
cp .env.example .env
```

Edit with your backend URL:
```env
API_BASE_URL=http://localhost:8081/api
```

For Android emulator, use `http://10.0.2.2:8081/api` instead.

## Database

Uses PostgreSQL 15 with Flyway for migrations. Schema changes go in `backend/src/main/resources/db/migration/`.

To reset the database:
```bash
make fresh
```

## Deployment

The app is currently deployed at https://api.analyze.food

For your own deployment:
1. Make sure all secrets are in environment variables (not hardcoded)
2. Set a strong JWT_SECRET
3. Configure CORS for your frontend domain
4. Use proper SSL/TLS
5. Set up backups for PostgreSQL

## Development

### Documentation

**Implementation Guides:**
- [ROADMAP.md](ROADMAP.md) - Full project roadmap and future phases
- [IMPLEMENTATION_PHASE_1A.md](IMPLEMENTATION_PHASE_1A.md) - Enhanced AI nutrition extraction
- [IMPLEMENTATION_PHASE_1B.md](IMPLEMENTATION_PHASE_1B.md) - Photo metadata + location intelligence
- [IMPLEMENTATION_PHASE_1B2.md](IMPLEMENTATION_PHASE_1B2.md) - Context-aware AI prompts
- [IMPLEMENTATION_PHASE_1C.md](IMPLEMENTATION_PHASE_1C.md) - Backend API exposure
- [IMPLEMENTATION_PHASE_1D.md](IMPLEMENTATION_PHASE_1D.md) - Flutter location UI

**Component Documentation:**
- [Backend Documentation](backend/README.md)
- [Frontend Documentation](frontend/nutritheous_app/README.md)

### Code Generation (Flutter)

After pulling changes or modifying models:
```bash
cd frontend/nutritheous_app
flutter pub run build_runner build --delete-conflicting-outputs
```

This generates JSON serialization code for Dart models.

## Future Roadmap

**Phase 2: Holistic Health Integration (Q1 2025)**
- Wearable integration (Apple Health, Garmin, Google Fit)
- Track HRV, sleep, exercise alongside meals
- Correlate food with energy levels and recovery
- Micronutrient tracking
- Hydration logging

**Phase 3: Advanced Analytics (Q2-Q3 2025)**
- CGM integration (Dexcom, Freestyle Libre)
- Personal N-of-1 experiments ("Does dairy cause bloating?")
- Automated weekly health reports
- Location-based meal insights

**Phase 4: AI Model Upgrades (Ongoing)**
- Zero code changes as GPT-5 and future models release
- Existing context (GPS, time) automatically used better
- Data becomes more valuable over time (compound interest!)

See [ROADMAP.md](ROADMAP.md) for complete details.

## Cost Estimates

**Per meal with GPS:**
- OpenAI API: ~$0.02 (GPT-4 Vision with 2000 tokens)
- Google Maps API: ~$0.005-0.01 (geocoding + places)
- Total: ~$0.025-0.03 per meal

**Free tier limits:**
- Google Maps: $200/month credit = ~7,000-10,000 meals
- OpenAI: Pay as you go

**For personal use:** Very affordable (~$5-10/month for daily tracking)

## Privacy & GPS

**Current behavior:**
- GPS coordinates stored in database
- Used for restaurant identification and context-aware AI
- Displayed in Flutter app with location badges

**Planned privacy controls:**
- User opt-out for GPS storage
- Auto-delete GPS after N days
- Option to store only "restaurant/home" without exact coordinates

## License

MIT License - do whatever you want with it.
