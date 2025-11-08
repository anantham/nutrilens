# Phase 2A: Garmin Integration - Design Document

**Author:** Claude Code (with user validation)
**Date:** 2025-11-08
**Status:** Design Complete - Ready for Implementation
**Timeline:** After Phase 3 completion (PRs #5, #7 merged)

---

## Executive Summary

Integrate Garmin Connect API to sync health and fitness data (HRV, sleep, exercise, activity) into NutriLens. Enable users to correlate nutrition patterns with health outcomes through visualizations and manual annotations. Foundation for future statistical analysis and AI-generated insights.

**Key Features:**
- OAuth 2.0 connection to Garmin account
- On-demand sync of health metrics (sleep, HRV, RHR, steps, exercise)
- Time-series visualizations (7/30/90 day charts)
- Daily health notes with tagging
- Location redundancy (Garmin exercise GPS validates meal location detection)

**User Value:**
- "How do late-night meals affect my sleep quality?"
- "Does workout performance change with high-carb vs high-protein days?"
- "Track HRV trends vs plant diversity in meals"

---

## Requirements Summary

### Functional Requirements

**Must Have (MVP):**
- ✅ Connect Garmin account via OAuth
- ✅ Sync health data on-demand (user-triggered)
- ✅ Display metrics: HRV, sleep quality/duration, RHR, steps, active calories
- ✅ Time-series charts (7/30/90 day views)
- ✅ Add daily health notes with tags
- ✅ Exercise session tracking (duration, type, GPS location)

**Should Have (Phase 2A.1):**
- 📊 Export health data (CSV/JSON)
- 🔄 Background sync (daily automated pull)
- 📍 Exercise location → meal correlation ("post-workout meal at Chipotle")

**Could Have (Phase 2B - Future):**
- 📈 Statistical correlations (Pearson/Spearman)
- 🤖 AI-generated insights ("HRV 15% higher with >8 plant species")
- 📊 Dual-axis charts (nutrition + health metrics overlaid)

### Non-Functional Requirements

- **Performance:** Sync 30 days of data in <10 seconds
- **Reliability:** OAuth token refresh >99% success rate
- **Security:** Tokens stored securely, never exposed to client
- **Privacy:** User can disconnect and delete health data anytime
- **Extensibility:** Schema supports future wearables (Apple Health, Strava, Whoop)
- **Data Integrity:** Prevent duplicate metrics with UNIQUE constraints

### User Constraints

- **Timeline:** Implement after Phase 3 PRs merged (clean slate)
- **Storage:** Keep all data indefinitely (no retention limits)
- **Sync:** On-demand only (user control, no background jobs)
- **Analytics:** Start with visualizations + manual notes (extensible for correlations later)

---

## Architecture

### High-Level System Design

```
┌─────────────────────────────────────────────────────────────────┐
│                     Flutter App (Frontend)                       │
├─────────────────────────────────────────────────────────────────┤
│  • HealthConnectionsScreen (OAuth flow, sync trigger)           │
│  • HealthDashboardScreen (metrics cards, charts, notes)         │
│  • HealthMetricsChartWidget (reusable time-series charts)       │
│  • HealthNoteDialog (daily annotations)                         │
└─────────────────────────────────────────────────────────────────┘
                              ↕ HTTPS (JWT Auth)
┌─────────────────────────────────────────────────────────────────┐
│                   Spring Boot Backend (Java 17)                  │
├─────────────────────────────────────────────────────────────────┤
│  Controllers:                                                    │
│    • GarminController (/api/garmin/auth, /sync)                │
│    • HealthMetricsController (/api/health/metrics, /notes)     │
│                                                                  │
│  Services:                                                       │
│    • GarminOAuthService (OAuth flow, token management)          │
│    • GarminDataSyncService (fetch & transform Garmin data)     │
│    • HealthMetricsService (store/query metrics)                 │
│    • HealthNotesService (manage annotations)                    │
└─────────────────────────────────────────────────────────────────┘
                              ↕ OAuth 2.0 / REST API
┌─────────────────────────────────────────────────────────────────┐
│              Garmin Connect API (External Service)               │
│  • OAuth endpoints (authorization, token exchange)              │
│  • Wellness API: /dailies, /sleeps, /activities                │
│  • Returns: HRV, sleep stages, exercise sessions, steps, etc.  │
└─────────────────────────────────────────────────────────────────┘
                              ↕
┌─────────────────────────────────────────────────────────────────┐
│                    PostgreSQL Database                           │
├─────────────────────────────────────────────────────────────────┤
│  Tables:                                                         │
│    • oauth_connections (tokens for Garmin, Apple Health, etc.) │
│    • health_metrics (time-series data: HRV, sleep, steps)      │
│    • health_notes (daily annotations with tags)                 │
│    • users, meals, meal_ingredients (existing tables)           │
└─────────────────────────────────────────────────────────────────┘
```

### Technology Stack

**Backend:**
- Spring Boot 3.2, Java 17 (existing)
- PostgreSQL 15 with Flyway migrations (existing)
- OkHttp 4.11 (HTTP client for Garmin API)
- Auth0 JWT 4.4 (if Garmin uses JWT tokens)

**Frontend:**
- Flutter 3.24, Dart 3.5 (existing)
- fl_chart 0.65 (time-series charts)
- webview_flutter 4.4 (OAuth web view)
- Riverpod (state management - existing)
- Hive (local caching - existing)

**External APIs:**
- Garmin Health API (wellness-api/rest)
- Garmin OAuth 2.0 (authorization)

---

## Database Schema

### Migration: `V13__add_garmin_health_integration.sql`

```sql
-- OAuth connections table (supports multiple wearables)
CREATE TABLE oauth_connections (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider VARCHAR(50) NOT NULL,  -- 'garmin', 'apple_health', 'strava', etc.
    access_token TEXT NOT NULL,
    refresh_token TEXT,
    token_expires_at TIMESTAMP WITH TIME ZONE,
    scopes TEXT[],  -- Permissions: ['activities', 'sleep', 'wellness']
    external_user_id VARCHAR(255),  -- Garmin's user ID
    connected_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    last_synced_at TIMESTAMP WITH TIME ZONE,
    is_active BOOLEAN DEFAULT TRUE,
    metadata JSONB,  -- Provider-specific config
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    UNIQUE(user_id, provider)
);

CREATE INDEX idx_oauth_user_provider ON oauth_connections(user_id, provider);

-- Health metrics table (flexible schema for all metric types)
CREATE TABLE health_metrics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    source VARCHAR(50) NOT NULL,  -- 'garmin', 'apple_health', 'manual', etc.
    metric_type VARCHAR(100) NOT NULL,  -- 'hrv', 'resting_hr', 'sleep_score', 'steps', etc.
    value DECIMAL(10, 2) NOT NULL,
    unit VARCHAR(50),  -- 'ms', 'bpm', 'score', 'count', 'hours', etc.
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL,  -- When metric was recorded
    synced_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),  -- When we imported it
    metadata JSONB,  -- Extra fields (e.g., sleep: {deep_min, light_min, rem_min})
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    UNIQUE(user_id, source, metric_type, recorded_at)  -- Prevent duplicates
);

CREATE INDEX idx_health_user_type_time ON health_metrics(user_id, metric_type, recorded_at DESC);
CREATE INDEX idx_health_recorded_at ON health_metrics(recorded_at);

-- Health notes table (daily annotations)
CREATE TABLE health_notes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    note_date DATE NOT NULL,
    note_text TEXT NOT NULL,
    tags TEXT[],  -- e.g., ['energy', 'mood', 'digestion', 'brain_fog']
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_health_notes_user_date ON health_notes(user_id, note_date DESC);
```

### Schema Design Rationale

**Why single `health_metrics` table?**
- **Flexibility:** Add new metric types without schema changes
- **Simplicity:** Fewer tables to manage, joins, migrations
- **Query patterns:** Most queries filter by `user_id + metric_type + date_range` → single index covers
- **Extensibility:** Works for any wearable (Garmin, Apple, Whoop) without code changes

**Why JSONB `metadata` field?**
- Each wearable returns different extra fields
- Example: Sleep has `deep_min`, `light_min`, `rem_min` (not worth separate columns)
- Example: Exercise has `sport_type`, `avg_hr`, `max_hr`, `gps_track`
- PostgreSQL JSONB is queryable and indexed when needed

**Why UNIQUE constraint on (user_id, source, metric_type, recorded_at)?**
- Prevents duplicate syncs (same data pulled twice)
- `recorded_at` is Garmin's timestamp (not our insert time)
- Insert will silently fail on duplicate → idempotent sync

---

## Backend Services

### 1. GarminOAuthService.java

**Purpose:** Handle OAuth 2.0 flow with Garmin Connect

**Methods:**

```java
public class GarminOAuthService {

    /**
     * Generate OAuth authorization URL for user to approve access
     * @return URL string to open in WebView
     */
    public String getAuthorizationUrl(UUID userId) {
        // Build URL: https://connect.garmin.com/oauthConfirm
        // Params: client_id, redirect_uri, response_type=code, scope=activities+sleep+wellness
        // Include state parameter (userId encrypted) for CSRF protection
    }

    /**
     * Exchange authorization code for access/refresh tokens
     * @param authCode - Code from Garmin OAuth callback
     * @param userId - User initiating auth
     * @return OAuthConnection entity (saved to DB)
     */
    public OAuthConnection exchangeCodeForTokens(String authCode, UUID userId) {
        // POST to Garmin token endpoint
        // Parse response: access_token, refresh_token, expires_in, scope
        // Save to oauth_connections table
        // Set connected_at = NOW()
    }

    /**
     * Refresh expired access token using refresh token
     * @param userId - User whose token needs refresh
     * @return Updated OAuthConnection with new access token
     */
    public OAuthConnection refreshAccessToken(UUID userId) {
        // Fetch refresh_token from oauth_connections
        // POST to Garmin token endpoint with grant_type=refresh_token
        // Update access_token and token_expires_at in DB
    }

    /**
     * Revoke Garmin connection and delete tokens
     * @param userId - User disconnecting
     */
    public void revokeConnection(UUID userId) {
        // Optional: Call Garmin revoke endpoint
        // Delete from oauth_connections WHERE user_id = ? AND provider = 'garmin'
    }

    /**
     * Check if access token is expired
     */
    private boolean isTokenExpired(OAuthConnection conn) {
        return conn.getTokenExpiresAt().isBefore(Instant.now());
    }
}
```

**Configuration:**
```properties
garmin.oauth.client.id=${GARMIN_CLIENT_ID}
garmin.oauth.client.secret=${GARMIN_CLIENT_SECRET}
garmin.oauth.callback.url=${GARMIN_CALLBACK_URL}
garmin.oauth.auth.url=https://connect.garmin.com/oauthConfirm
garmin.oauth.token.url=https://connectapi.garmin.com/oauth-service/oauth/access_token
```

---

### 2. GarminDataSyncService.java

**Purpose:** Fetch health data from Garmin API and transform to our schema

**Methods:**

```java
public class GarminDataSyncService {

    /**
     * Sync all health metrics for a date range
     * @param userId - User to sync
     * @param startDate - Start of date range
     * @param endDate - End of date range (max 90 days)
     * @return SyncResult (metrics synced count, errors)
     */
    public SyncResult syncUserData(UUID userId, LocalDate startDate, LocalDate endDate) {
        // 1. Get access token (refresh if expired)
        // 2. Call Garmin API endpoints in parallel:
        //    - syncSleepData()
        //    - syncHRVData()
        //    - syncDailyActivity()
        //    - syncExercises()
        // 3. Aggregate metrics and save via HealthMetricsService
        // 4. Update oauth_connections.last_synced_at
        // 5. Return summary
    }

    /**
     * Fetch sleep data from Garmin
     */
    private List<HealthMetric> syncSleepData(UUID userId, LocalDate startDate, LocalDate endDate) {
        // GET https://apis.garmin.com/wellness-api/rest/sleeps
        // Parse response: sleep_start, sleep_end, total_sleep_seconds, deep_sleep_seconds, etc.
        // Transform to HealthMetric objects:
        //   - metric_type='sleep_score', value=82, unit='score'
        //   - metric_type='sleep_duration', value=7.5, unit='hours'
        //   - metadata={deep_min: 90, light_min: 180, rem_min: 120, awake_min: 30}
    }

    /**
     * Fetch HRV data from Garmin
     */
    private List<HealthMetric> syncHRVData(UUID userId, LocalDate startDate, LocalDate endDate) {
        // GET https://apis.garmin.com/wellness-api/rest/dailies
        // Parse: hrvValue (night-time HRV average)
        // Transform: metric_type='hrv', value=65, unit='ms'
    }

    /**
     * Fetch daily activity summary (steps, calories, RHR)
     */
    private List<HealthMetric> syncDailyActivity(UUID userId, LocalDate startDate, LocalDate endDate) {
        // GET https://apis.garmin.com/wellness-api/rest/dailies
        // Parse: totalSteps, activeCalories, restingHeartRate
        // Transform to multiple HealthMetric objects per day
    }

    /**
     * Fetch exercise sessions
     */
    private List<HealthMetric> syncExercises(UUID userId, LocalDate startDate, LocalDate endDate) {
        // GET https://apis.garmin.com/wellness-api/rest/activities
        // Parse: activityType, durationSeconds, averageHeartRate, startLatitude, startLongitude
        // Transform: metric_type='exercise_duration', metadata={sport: 'running', gps: {...}}
    }

    /**
     * Handle Garmin API rate limits and retries
     */
    private <T> T callGarminAPI(String endpoint, Class<T> responseClass) {
        // OkHttp with exponential backoff
        // Retry on 429 (rate limit), 5xx (server error)
        // Log errors for debugging
    }
}
```

**Garmin API Endpoints:**
- `/wellness-api/rest/dailies` → Daily summaries (HRV, RHR, steps, calories)
- `/wellness-api/rest/sleeps` → Sleep sessions with stages
- `/wellness-api/rest/activities` → Exercise sessions with GPS

---

### 3. HealthMetricsService.java

**Purpose:** Business logic for health data storage and retrieval

**Methods:**

```java
public class HealthMetricsService {

    /**
     * Save batch of health metrics (handles deduplication)
     * @param userId - User ID
     * @param source - 'garmin', 'apple_health', 'manual'
     * @param metrics - List of metrics to save
     * @return Number of metrics inserted (ignores duplicates)
     */
    public int saveMetrics(UUID userId, String source, List<HealthMetric> metrics) {
        // Batch insert with ON CONFLICT DO NOTHING (UNIQUE constraint)
        // Return inserted count
    }

    /**
     * Query specific metric type over time
     * @param userId - User ID
     * @param metricType - 'hrv', 'sleep_score', 'steps', etc.
     * @param startDate - Start of range
     * @param endDate - End of range
     * @return List of HealthMetric ordered by recorded_at ASC
     */
    public List<HealthMetric> getMetricsByType(UUID userId, String metricType,
                                                 LocalDate startDate, LocalDate endDate);

    /**
     * Get all metrics for a specific date (dashboard summary)
     * @return Map of metric_type → HealthMetric
     */
    public Map<String, HealthMetric> getMetricsSummary(UUID userId, LocalDate date);

    /**
     * Get daily averages for charting
     * @return List of DailyAverage(date, avgValue)
     */
    public List<DailyAverage> getDailyAverages(UUID userId, String metricType,
                                                 LocalDate startDate, LocalDate endDate);

    /**
     * Get data for correlation analysis (future Phase 2B)
     * @param nutritionField - 'protein_grams', 'plant_count', 'meal_time_hour'
     * @param healthMetric - 'hrv', 'sleep_score', 'resting_hr'
     * @return List of CorrelationDataPoint(date, nutritionValue, healthValue)
     */
    public List<CorrelationDataPoint> getCorrelationData(UUID userId, String nutritionField,
                                                           String healthMetric, int days);
}
```

---

### 4. HealthNotesService.java

**Purpose:** Manage daily health annotations

**Methods:**

```java
public class HealthNotesService {

    /**
     * Create daily health note
     */
    public HealthNote createNote(UUID userId, LocalDate date, String text, List<String> tags);

    /**
     * Update existing note
     */
    public HealthNote updateNote(UUID noteId, String text, List<String> tags);

    /**
     * Retrieve notes for date range
     */
    public List<HealthNote> getNotesByDateRange(UUID userId, LocalDate startDate, LocalDate endDate);

    /**
     * Search notes by tags
     */
    public List<HealthNote> searchNotesByTag(UUID userId, List<String> tags);
}
```

---

## Backend Controllers

### GarminController.java (`/api/garmin`)

**Endpoints:**

```java
@RestController
@RequestMapping("/api/garmin")
public class GarminController {

    /**
     * GET /api/garmin/auth/start
     * Returns OAuth authorization URL
     */
    @GetMapping("/auth/start")
    public ResponseEntity<AuthUrlResponse> startAuth(@AuthenticationPrincipal User user) {
        String authUrl = garminOAuthService.getAuthorizationUrl(user.getId());
        return ResponseEntity.ok(new AuthUrlResponse(authUrl));
    }

    /**
     * POST /api/garmin/auth/callback
     * Exchange auth code for tokens
     * Body: {"auth_code": "xyz123"}
     */
    @PostMapping("/auth/callback")
    public ResponseEntity<ConnectionStatusResponse> handleCallback(
            @RequestBody AuthCallbackRequest request,
            @AuthenticationPrincipal User user) {
        OAuthConnection conn = garminOAuthService.exchangeCodeForTokens(
                request.getAuthCode(), user.getId());
        return ResponseEntity.ok(new ConnectionStatusResponse(conn));
    }

    /**
     * DELETE /api/garmin/disconnect
     * Revoke Garmin connection
     */
    @DeleteMapping("/disconnect")
    public ResponseEntity<Void> disconnect(@AuthenticationPrincipal User user) {
        garminOAuthService.revokeConnection(user.getId());
        return ResponseEntity.noContent().build();
    }

    /**
     * GET /api/garmin/status
     * Check connection status
     * Returns: {connected: true, last_synced_at: "2025-01-08T10:30:00Z"}
     */
    @GetMapping("/status")
    public ResponseEntity<ConnectionStatusResponse> getStatus(@AuthenticationPrincipal User user);

    /**
     * POST /api/garmin/sync
     * Trigger on-demand sync
     * Query params: ?start_date=2025-01-01&end_date=2025-01-08
     * Returns: {metrics_synced: 56, date_range: "Jan 1-8"}
     */
    @PostMapping("/sync")
    public ResponseEntity<SyncResultResponse> syncData(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @AuthenticationPrincipal User user) {
        SyncResult result = garminDataSyncService.syncUserData(user.getId(), startDate, endDate);
        return ResponseEntity.ok(new SyncResultResponse(result));
    }
}
```

---

### HealthMetricsController.java (`/api/health`)

**Endpoints:**

```java
@RestController
@RequestMapping("/api/health")
public class HealthMetricsController {

    /**
     * GET /api/health/metrics/{type}?start_date=2025-01-01&end_date=2025-01-08
     * Fetch specific metric time series
     */
    @GetMapping("/metrics/{type}")
    public ResponseEntity<List<HealthMetricResponse>> getMetrics(
            @PathVariable String type,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @AuthenticationPrincipal User user);

    /**
     * GET /api/health/summary?date=2025-01-08
     * All metrics for a specific day
     */
    @GetMapping("/summary")
    public ResponseEntity<Map<String, HealthMetricResponse>> getSummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @AuthenticationPrincipal User user);

    /**
     * GET /api/health/daily-averages/{type}?start_date&end_date
     * Aggregated data for charts
     */
    @GetMapping("/daily-averages/{type}")
    public ResponseEntity<List<DailyAverageResponse>> getDailyAverages(
            @PathVariable String type,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @AuthenticationPrincipal User user);

    /**
     * POST /api/health/notes
     * Create health note
     * Body: {date: "2025-01-08", text: "...", tags: ["energy", "mood"]}
     */
    @PostMapping("/notes")
    public ResponseEntity<HealthNoteResponse> createNote(
            @RequestBody CreateNoteRequest request,
            @AuthenticationPrincipal User user);

    /**
     * GET /api/health/notes?start_date&end_date
     * Retrieve notes for date range
     */
    @GetMapping("/notes")
    public ResponseEntity<List<HealthNoteResponse>> getNotes(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @AuthenticationPrincipal User user);
}
```

---

## Flutter Frontend

### New Screens

**1. HealthConnectionsScreen** (`health_connections_screen.dart`)

Purpose: Manage wearable integrations

UI Components:
- App bar: "Health Connections"
- List of providers (Garmin, Apple Health - future, Strava - future)
- Each provider card shows:
  - Logo/icon
  - Connection status badge (✅ Connected, ⚠️ Expired, ➕ Not connected)
  - Last synced timestamp
  - Action buttons: "Connect" / "Sync Now" / "Disconnect"
- OAuth WebView modal for Garmin login

State Management:
- `ConnectionStatus` model (provider, isConnected, lastSyncedAt)
- Riverpod provider: `garminConnectionProvider`

---

**2. HealthDashboardScreen** (`health_dashboard_screen.dart`)

Purpose: Main health data visualization hub

UI Layout:
- Tab bar: Overview | Charts | Notes | (Correlations - future)

**Overview Tab:**
- Cards for today's metrics:
  - HRV: 65 ms (vs 7-day avg: 62 ms) ↑
  - Sleep Score: 82/100 (7.5 hours)
  - Resting HR: 58 bpm
  - Steps: 8,234 / 10,000 goal
- "Last synced: 2 hours ago" subtitle
- "Sync Now" button

**Charts Tab:**
- Dropdown: Select metric type (HRV, Sleep, RHR, Steps, etc.)
- Date range picker: 7 days | 30 days | 90 days
- Line chart (HealthMetricsChartWidget)
- Tap data point → Show details modal

**Notes Tab:**
- Date-filtered list of health notes
- Tag filter chips
- Floating action button: "Add Note"

State Management:
- `selectedMetricType` (String)
- `selectedDateRange` (DateTimeRange)
- Riverpod: `healthMetricsProvider(type, range)`
- Riverpod: `healthNotesProvider(range)`

---

**3. HealthMetricsChartWidget** (`health_metrics_chart_widget.dart`)

Purpose: Reusable time-series chart component

Props:
- `metricType` (String) - Which metric to display
- `dateRange` (DateTimeRange)
- `showMealMarkers` (bool) - Overlay meal times as vertical lines

Implementation:
- Uses `fl_chart` package (LineChart widget)
- X-axis: Dates
- Y-axis: Metric value with unit label
- Tooltip on tap: Date, value, metadata (if any)
- Responsive: Adapts to screen width

Future Enhancement:
- Dual-axis mode (nutrition metric + health metric overlaid)
- Zoom/pan gestures

---

**4. HealthNoteDialog** (`health_note_dialog.dart`)

Purpose: Add/edit daily health notes

UI:
- Date picker (defaults to today)
- Multi-line text field (character limit: 500)
- Tag chips (predefined: energy, mood, digestion, brain fog, stress, workout)
- Custom tag creation (+ button)
- Save / Cancel buttons

Validation:
- Date cannot be in future
- Text required (min 5 chars)
- Max 10 tags

---

### New Models

**`oauth_connection.dart`**
```dart
@JsonSerializable()
class OAuthConnection {
  final String id;
  final String provider;  // 'garmin', 'apple_health', etc.
  final DateTime connectedAt;
  final DateTime? lastSyncedAt;
  final bool isActive;

  bool get needsReauth {
    // Check if lastSyncedAt is >7 days ago (might indicate token expired)
    if (lastSyncedAt == null) return false;
    return DateTime.now().difference(lastSyncedAt!) > Duration(days: 7);
  }

  String get displayName {
    switch (provider) {
      case 'garmin': return 'Garmin Connect';
      case 'apple_health': return 'Apple Health';
      case 'strava': return 'Strava';
      default: return provider;
    }
  }

  // JSON serialization
}
```

**`health_metric.dart`**
```dart
@JsonSerializable()
class HealthMetric {
  final String id;
  final String source;  // 'garmin', 'apple_health', 'manual'
  final HealthMetricType type;
  final double value;
  final String unit;
  final DateTime recordedAt;
  final Map<String, dynamic>? metadata;

  // JSON serialization
}

enum HealthMetricType {
  hrv,
  restingHeartRate,
  sleepScore,
  sleepDuration,
  deepSleepMinutes,
  remSleepMinutes,
  steps,
  activeCalories,
  exerciseDuration,
  // Extensible for future metrics
}
```

**`health_note.dart`**
```dart
@JsonSerializable()
class HealthNote {
  final String id;
  final DateTime date;
  final String text;
  final List<String> tags;
  final DateTime createdAt;
  final DateTime updatedAt;

  // JSON serialization
}
```

**`daily_average.dart`**
```dart
@JsonSerializable()
class DailyAverage {
  final DateTime date;
  final double averageValue;
  final String unit;

  // For chart rendering
}
```

---

### New Services (Flutter)

**`garmin_service.dart`**
```dart
class GarminService {
  final Dio _dio;

  Future<String> getAuthUrl() async {
    // GET /api/garmin/auth/start
    final response = await _dio.get('/garmin/auth/start');
    return response.data['auth_url'];
  }

  Future<void> completeAuth(String authCode) async {
    // POST /api/garmin/auth/callback
    await _dio.post('/garmin/auth/callback', data: {'auth_code': authCode});
  }

  Future<OAuthConnection?> getConnectionStatus() async {
    // GET /api/garmin/status
    final response = await _dio.get('/garmin/status');
    if (response.data['connected'] == false) return null;
    return OAuthConnection.fromJson(response.data);
  }

  Future<SyncResult> syncData({DateTime? startDate, DateTime? endDate}) async {
    // POST /api/garmin/sync?start_date=...&end_date=...
    final response = await _dio.post('/garmin/sync', queryParameters: {
      'start_date': startDate?.toIso8601String().split('T')[0],
      'end_date': endDate?.toIso8601String().split('T')[0],
    });
    return SyncResult.fromJson(response.data);
  }

  Future<void> disconnect() async {
    // DELETE /api/garmin/disconnect
    await _dio.delete('/garmin/disconnect');
  }
}
```

**`health_metrics_service.dart`**
```dart
class HealthMetricsService {
  final Dio _dio;

  Future<List<HealthMetric>> getMetrics(
      HealthMetricType type, DateTimeRange range) async {
    // GET /api/health/metrics/{type}?start_date&end_date
    final response = await _dio.get('/health/metrics/${type.name}', queryParameters: {
      'start_date': range.start.toIso8601String().split('T')[0],
      'end_date': range.end.toIso8601String().split('T')[0],
    });
    return (response.data as List)
        .map((json) => HealthMetric.fromJson(json))
        .toList();
  }

  Future<Map<HealthMetricType, HealthMetric>> getTodaySummary() async {
    // GET /api/health/summary?date=today
    final response = await _dio.get('/health/summary', queryParameters: {
      'date': DateTime.now().toIso8601String().split('T')[0],
    });
    // Parse and return map
  }

  Future<List<DailyAverage>> getDailyAverages(
      HealthMetricType type, DateTimeRange range) async {
    // GET /api/health/daily-averages/{type}?start_date&end_date
    final response = await _dio.get('/health/daily-averages/${type.name}', queryParameters: {
      'start_date': range.start.toIso8601String().split('T')[0],
      'end_date': range.end.toIso8601String().split('T')[0],
    });
    return (response.data as List)
        .map((json) => DailyAverage.fromJson(json))
        .toList();
  }
}
```

**`health_notes_service.dart`**
```dart
class HealthNotesService {
  final Dio _dio;

  Future<HealthNote> createNote(DateTime date, String text, List<String> tags) async {
    // POST /api/health/notes
    final response = await _dio.post('/health/notes', data: {
      'date': date.toIso8601String().split('T')[0],
      'text': text,
      'tags': tags,
    });
    return HealthNote.fromJson(response.data);
  }

  Future<HealthNote> updateNote(String noteId, String text, List<String> tags) async {
    // PUT /api/health/notes/{id}
    final response = await _dio.put('/health/notes/$noteId', data: {
      'text': text,
      'tags': tags,
    });
    return HealthNote.fromJson(response.data);
  }

  Future<List<HealthNote>> getNotes(DateTimeRange range) async {
    // GET /api/health/notes?start_date&end_date
    final response = await _dio.get('/health/notes', queryParameters: {
      'start_date': range.start.toIso8601String().split('T')[0],
      'end_date': range.end.toIso8601String().split('T')[0],
    });
    return (response.data as List)
        .map((json) => HealthNote.fromJson(json))
        .toList();
  }
}
```

---

## Data Flows

### Flow 1: OAuth Connection

```
1. User taps "Connect Garmin" in HealthConnectionsScreen
   ↓
2. Flutter: GarminService.getAuthUrl()
   Backend: GarminOAuthService.getAuthorizationUrl()
   Returns: "https://connect.garmin.com/oauthConfirm?client_id=...&redirect_uri=..."
   ↓
3. Flutter: Opens WebView with Garmin login page
   User: Enters Garmin credentials, authorizes app
   ↓
4. Garmin: Redirects to callback URL with auth code
   Example: "myapp://garmin/callback?code=abc123&state=xyz"
   ↓
5. Flutter: Captures redirect in WebView, extracts auth code
   ↓
6. Flutter: GarminService.completeAuth("abc123")
   Backend: GarminOAuthService.exchangeCodeForTokens()
   - POST to Garmin token endpoint
   - Parse access_token, refresh_token, expires_in
   - Save to oauth_connections table
   ↓
7. Backend: Returns OAuthConnection (connected_at, last_synced_at=null)
   ↓
8. Flutter: Updates UI → Shows "Connected ✅" badge
   ↓
9. Flutter: Auto-triggers initial sync (last 7 days)
```

---

### Flow 2: Data Sync

```
1. User taps "Sync Now" button (or app auto-syncs on connection)
   ↓
2. Flutter: Shows loading indicator
   GarminService.syncData(startDate: 7 days ago, endDate: today)
   ↓
3. Backend: GarminDataSyncService.syncUserData()
   a. Fetch access_token from oauth_connections
   b. Check if token expired → Call refreshAccessToken() if needed
   c. Parallel API calls to Garmin:
      - GET /wellness-api/rest/dailies → HRV, RHR, steps, calories
      - GET /wellness-api/rest/sleeps → Sleep sessions with stages
      - GET /wellness-api/rest/activities → Exercise sessions
   d. Parse responses, transform to HealthMetric objects
   e. HealthMetricsService.saveMetrics() → Batch insert
      (UNIQUE constraint prevents duplicates)
   f. Update oauth_connections.last_synced_at = NOW()
   ↓
4. Backend: Returns SyncResult {metrics_synced: 56, errors: []}
   ↓
5. Flutter: Hides loading, shows success Snackbar
   "Synced 56 metrics from Jan 1-8"
   ↓
6. Flutter: Refreshes HealthDashboardScreen (invalidates Riverpod providers)
```

---

### Flow 3: Viewing Charts

```
1. User navigates to HealthDashboardScreen → Charts tab
   ↓
2. Flutter: Default view → HRV for last 30 days
   ↓
3. Flutter: HealthMetricsService.getDailyAverages(
      type: HealthMetricType.hrv,
      range: DateTimeRange(start: 30 days ago, end: today)
   )
   ↓
4. Backend: HealthMetricsService.getDailyAverages()
   SQL:
     SELECT DATE(recorded_at) as date, AVG(value) as avg_hrv
     FROM health_metrics
     WHERE user_id = ? AND metric_type = 'hrv'
       AND recorded_at BETWEEN ? AND ?
     GROUP BY DATE(recorded_at)
     ORDER BY date ASC
   ↓
5. Backend: Returns List<DailyAverage>:
   [{date: "2024-12-09", avgHrv: 62}, {date: "2024-12-10", avgHrv: 58}, ...]
   ↓
6. Flutter: Renders HealthMetricsChartWidget
   - X-axis: Dates (formatted as "Dec 9", "Dec 10", ...)
   - Y-axis: HRV values (ms)
   - Line chart with dots at each data point
   ↓
7. User taps data point (Dec 15)
   ↓
8. Flutter: Shows modal:
   "Dec 15, 2024
    HRV: 65 ms
    Trend: ↑ 7% vs 7-day avg"
```

---

### Flow 4: Adding Health Note

```
1. User taps "Add Note" FAB on Notes tab
   ↓
2. Flutter: Opens HealthNoteDialog
   - Date picker defaults to today
   - Empty text field
   - Predefined tag chips
   ↓
3. User enters: "Felt energetic all day, great workout"
   Selects tags: [energy, mood]
   ↓
4. User taps "Save"
   ↓
5. Flutter: HealthNotesService.createNote(
      date: today,
      text: "Felt energetic...",
      tags: ["energy", "mood"]
   )
   ↓
6. Backend: HealthNotesService.createNote()
   INSERT INTO health_notes (user_id, note_date, note_text, tags)
   VALUES (?, ?, ?, ARRAY['energy', 'mood'])
   ↓
7. Backend: Returns saved HealthNote object
   ↓
8. Flutter: Dismisses dialog, shows Snackbar "Note saved"
   Refreshes notes list → New note appears
```

---

### Flow 5: Location Redundancy (Garmin GPS)

```
1. User completes run, Garmin records exercise with GPS track
   ↓
2. User syncs Garmin data
   Backend: GarminDataSyncService.syncExercises()
   Garmin API returns:
   {
     activityType: "running",
     startTime: "2025-01-08T07:30:00Z",
     endTime: "2025-01-08T08:00:00Z",
     startLatitude: 37.7749,
     startLongitude: -122.4194,
     ...
   }
   ↓
3. Backend: Saves to health_metrics
   metric_type: 'exercise_duration'
   metadata: {
     sport: 'running',
     start_gps: {lat: 37.7749, lng: -122.4194},
     end_gps: {lat: 37.7800, lng: -122.4150}
   }
   ↓
4. User logs meal with photo at 8:15 AM
   Photo GPS: (37.7755, -122.4200) - Chipotle
   ↓
5. Backend (future enhancement - Phase 2B):
   - Detect meal timestamp within 30 min of exercise end
   - Calculate distance between exercise end GPS and meal GPS
   - If <500m → Tag meal as "post-workout"
   - Enhance meal context: "Post-run refuel at Chipotle"
   ↓
6. UI: Meal card shows badge "🏃 Post-Workout"
   Analytics: Track nutrition patterns after exercise
```

---

## Testing Strategy

### Backend Unit Tests

**`GarminOAuthServiceTest.java`**
```java
@Test
void testGetAuthorizationUrl_containsCorrectParams() {
    String url = garminOAuthService.getAuthorizationUrl(userId);
    assertThat(url).contains("client_id=");
    assertThat(url).contains("redirect_uri=");
    assertThat(url).contains("scope=activities+sleep+wellness");
}

@Test
void testExchangeCodeForTokens_savesToDatabase() {
    // Mock Garmin API response
    mockGarminTokenEndpoint(accessToken, refreshToken, expiresIn);

    OAuthConnection conn = garminOAuthService.exchangeCodeForTokens(authCode, userId);

    assertThat(conn.getAccessToken()).isEqualTo(accessToken);
    assertThat(conn.getProvider()).isEqualTo("garmin");
    // Verify saved to DB
}

@Test
void testRefreshAccessToken_updatesToken() {
    // Setup: Expired token in DB
    OAuthConnection oldConn = createExpiredConnection();

    OAuthConnection refreshed = garminOAuthService.refreshAccessToken(userId);

    assertThat(refreshed.getAccessToken()).isNotEqualTo(oldConn.getAccessToken());
    assertThat(refreshed.getTokenExpiresAt()).isAfter(Instant.now());
}
```

**`GarminDataSyncServiceTest.java`**
```java
@Test
void testSyncSleepData_transformsCorrectly() {
    // Mock Garmin API response
    String garminJson = """
        {
            "sleeps": [{
                "sleepStartTime": "2025-01-08T23:00:00",
                "sleepEndTime": "2025-01-09T06:30:00",
                "deepSleepSeconds": 5400,
                "lightSleepSeconds": 10800,
                "remSleepSeconds": 7200
            }]
        }
    """;
    mockGarminAPI("/sleeps", garminJson);

    List<HealthMetric> metrics = garminDataSyncService.syncSleepData(userId, startDate, endDate);

    assertThat(metrics).hasSize(2); // sleep_duration + sleep_score
    HealthMetric duration = metrics.stream()
        .filter(m -> m.getMetricType().equals("sleep_duration"))
        .findFirst().orElseThrow();
    assertThat(duration.getValue()).isEqualTo(7.5); // hours
}

@Test
void testSyncUserData_preventsDuplicates() {
    // Sync same date range twice
    garminDataSyncService.syncUserData(userId, startDate, endDate);
    int firstCount = healthMetricsRepository.countByUserId(userId);

    garminDataSyncService.syncUserData(userId, startDate, endDate);
    int secondCount = healthMetricsRepository.countByUserId(userId);

    assertThat(firstCount).isEqualTo(secondCount); // No duplicates
}
```

**`HealthMetricsServiceTest.java`**
```java
@Test
void testGetDailyAverages_aggregatesCorrectly() {
    // Setup: Multiple HRV readings per day
    saveMetric(userId, "hrv", 60, "2025-01-08T06:00:00");
    saveMetric(userId, "hrv", 64, "2025-01-08T14:00:00");
    saveMetric(userId, "hrv", 62, "2025-01-09T06:00:00");

    List<DailyAverage> averages = healthMetricsService.getDailyAverages(
        userId, "hrv", LocalDate.parse("2025-01-08"), LocalDate.parse("2025-01-09"));

    assertThat(averages).hasSize(2);
    assertThat(averages.get(0).getAverageValue()).isEqualTo(62.0); // (60+64)/2
    assertThat(averages.get(1).getAverageValue()).isEqualTo(62.0);
}
```

---

### Backend Integration Tests

**`GarminIntegrationTest.java`**
```java
@SpringBootTest
@Transactional
class GarminIntegrationTest {

    @Test
    void testCompleteOAuthFlow() {
        // 1. Get auth URL
        String authUrl = garminController.startAuth(testUser).getBody().getAuthUrl();
        assertThat(authUrl).startsWith("https://connect.garmin.com");

        // 2. Simulate OAuth callback (mock Garmin token endpoint)
        mockGarminTokenEndpoint();
        garminController.handleCallback(new AuthCallbackRequest(authCode), testUser);

        // 3. Verify connection saved
        OAuthConnection conn = oauthConnectionsRepository
            .findByUserIdAndProvider(testUser.getId(), "garmin").orElseThrow();
        assertThat(conn.getAccessToken()).isNotNull();
    }

    @Test
    void testEndToEndSync() {
        // Setup: Connected Garmin account
        createGarminConnection(testUser);
        mockGarminAPI("/dailies", garminDailiesJson);
        mockGarminAPI("/sleeps", garminSleepsJson);

        // Sync data
        SyncResultResponse result = garminController.syncData(
            LocalDate.parse("2025-01-01"),
            LocalDate.parse("2025-01-08"),
            testUser
        ).getBody();

        assertThat(result.getMetricsSynced()).isGreaterThan(0);

        // Query data
        List<HealthMetric> hrv = healthMetricsService.getMetricsByType(
            testUser.getId(), "hrv", LocalDate.parse("2025-01-01"), LocalDate.parse("2025-01-08"));
        assertThat(hrv).isNotEmpty();
    }
}
```

---

### Flutter Widget Tests

**`health_dashboard_screen_test.dart`**
```dart
testWidgets('HealthDashboardScreen shows metric cards', (tester) async {
  // Mock health metrics
  when(mockHealthMetricsService.getTodaySummary())
      .thenAnswer((_) async => {
        HealthMetricType.hrv: HealthMetric(value: 65, unit: 'ms'),
        HealthMetricType.sleepScore: HealthMetric(value: 82, unit: 'score'),
      });

  await tester.pumpWidget(makeTestableWidget(HealthDashboardScreen()));

  expect(find.text('HRV'), findsOneWidget);
  expect(find.text('65 ms'), findsOneWidget);
  expect(find.text('Sleep Score'), findsOneWidget);
  expect(find.text('82'), findsOneWidget);
});

testWidgets('Shows empty state when not connected', (tester) async {
  when(mockGarminService.getConnectionStatus())
      .thenAnswer((_) async => null);

  await tester.pumpWidget(makeTestableWidget(HealthDashboardScreen()));

  expect(find.text('Connect Garmin to see health data'), findsOneWidget);
  expect(find.byType(ElevatedButton), findsOneWidget); // Connect button
});
```

**`health_metrics_chart_widget_test.dart`**
```dart
testWidgets('Chart renders with data', (tester) async {
  final metrics = [
    DailyAverage(date: DateTime(2025, 1, 1), averageValue: 60),
    DailyAverage(date: DateTime(2025, 1, 2), averageValue: 62),
    DailyAverage(date: DateTime(2025, 1, 3), averageValue: 65),
  ];

  await tester.pumpWidget(makeTestableWidget(
    HealthMetricsChartWidget(
      metricType: HealthMetricType.hrv,
      data: metrics,
    ),
  ));

  expect(find.byType(LineChart), findsOneWidget);
  expect(find.text('HRV (ms)'), findsOneWidget); // Y-axis label
});
```

---

### Manual Testing Checklist

**OAuth Flow:**
- [ ] Connect Garmin account with valid credentials
- [ ] Verify redirect to app after authorization
- [ ] Check connection status shows "Connected ✅"
- [ ] Test disconnect → Tokens removed from DB
- [ ] Test reconnect after disconnect

**Data Sync:**
- [ ] Sync last 7 days → Verify metrics appear in DB
- [ ] Sync last 30 days → Verify no duplicates
- [ ] Sync with no new data → Verify graceful handling
- [ ] Test sync with expired token → Auto-refresh works
- [ ] Test sync with revoked token → Error message shown

**Dashboard:**
- [ ] Overview tab shows today's metrics correctly
- [ ] Metrics match Garmin Connect app values (±5%)
- [ ] Empty states handled (no sleep data, no exercise)
- [ ] Charts render for 7/30/90 day ranges
- [ ] Tap data point shows detail modal

**Health Notes:**
- [ ] Add note for today → Saves correctly
- [ ] Add note for past date → Works
- [ ] Edit existing note → Updates persist
- [ ] Filter by tag → Shows only matching notes
- [ ] Delete note → Removed from list

**Edge Cases:**
- [ ] User wears Garmin inconsistently (gaps in data)
- [ ] Offline sync attempt → Queued or error shown
- [ ] Rapid consecutive syncs → No crashes or dupes
- [ ] Token expiry during sync → Refreshes and continues
- [ ] Garmin API down → Error handling graceful

---

## Success Metrics

### Phase 2A MVP Acceptance Criteria

**Functionality:**
- ✅ User can connect Garmin account (OAuth success rate >95%)
- ✅ User can sync 30 days of data in <10 seconds
- ✅ Dashboard displays HRV, sleep, RHR, steps, exercise
- ✅ Charts render correctly for 7/30/90 day views
- ✅ User can add/edit health notes with tags
- ✅ No crashes, no data corruption, no duplicate metrics

**User Engagement (30 days post-launch):**
- 60%+ of users who connect Garmin sync weekly
- Users view health dashboard 3+ times per week
- Users add health notes on 20%+ of days

**Technical KPIs:**
- API response time <500ms (p95) for dashboard queries
- Token refresh success rate >99%
- Zero duplicate metrics in health_metrics table
- Database query performance: <100ms for 90-day aggregations

### Future Enhancement Triggers

**When to build Phase 2B (Statistical Correlations):**
- 100+ users have >30 days of combined nutrition + health data
- User feedback: "I want to know what affects my sleep/HRV"
- Manual pattern spotting becomes tedious (users request automation)

**When to add AI Insights (Phase 2C):**
- Users have >60 days of dense data (nutrition + health + notes)
- Patterns are consistent enough for AI to detect
- User asks: "What should I change to improve X?"

---

## Security & Privacy

### Token Security

**Storage:**
- Access tokens stored in `oauth_connections` table (PostgreSQL)
- Consider encrypting tokens at rest (Phase 2A.1 enhancement)
- Tokens NEVER sent to Flutter app (backend-only)

**Lifecycle:**
- Access tokens expire after 1 hour (Garmin default)
- Refresh tokens used to get new access tokens automatically
- Expired tokens refreshed transparently during sync
- Revoked tokens → User must reconnect

**Transport:**
- All OAuth callbacks use HTTPS
- JWT authentication required for all API endpoints
- CORS configured for Flutter app domain only

---

### Data Privacy

**User Control:**
- User can disconnect Garmin anytime (deletes tokens)
- Optional: Delete all health data on disconnect (confirm dialog)
- Export health data via existing `/api/export` endpoint (GDPR compliance)

**Access Control:**
- All health_metrics rows scoped by `user_id`
- JPA queries ALWAYS filter by authenticated user
- No sharing of health data between users

**Data Retention:**
- No automatic deletion (user chose "store indefinitely")
- User can manually delete metrics via UI (future feature)

---

### API Security

**Rate Limiting:**
- Sync endpoint: Max 1 sync per minute per user
- Prevents abuse and Garmin API rate limit violations

**Input Validation:**
- Date ranges validated (max 90 days per sync)
- Future dates rejected
- Metric values validated (e.g., HRV >0, sleep_duration <24 hours)

**Error Handling:**
- Sensitive errors logged server-side only (don't expose internals to client)
- Generic error messages to user ("Sync failed, please try again")

---

## Migration & Deployment

### Prerequisites

**Before Implementation:**
1. ✅ Merge PR #5 (Phase 3A ingredient decomposition)
2. ✅ Merge PR #7 (Phase 3B ingredient learning bugfix)
3. Optional: Complete Phase 3C (ingredient auto-fill) or defer

**Garmin Developer Setup:**
1. Create Garmin Developer account (https://developer.garmin.com)
2. Register OAuth application
3. Configure redirect URI (e.g., `https://api.analyze.food/garmin/callback`)
4. Note `client_id` and `client_secret`
5. Request access to Garmin Health API (may require approval)

---

### Environment Configuration

**Backend `.env` additions:**
```env
# Garmin OAuth
GARMIN_CLIENT_ID=your-client-id
GARMIN_CLIENT_SECRET=your-client-secret
GARMIN_CALLBACK_URL=https://api.analyze.food/api/garmin/callback
```

**Flutter `.env` additions:**
```env
# No changes needed (uses existing API_BASE_URL)
```

---

### Deployment Steps

**1. Database Migration**
```bash
# Migration runs automatically on boot (Flyway)
# V13__add_garmin_health_integration.sql creates 3 tables
./gradlew bootRun  # or deploy to production
```

**2. Backend Deployment**
```bash
# Build and deploy backend with new services
./gradlew build
# Deploy JAR to server (existing process)
```

**3. Flutter Deployment**
```bash
cd frontend/nutritheous_app
flutter pub get
flutter pub run build_runner build --delete-conflicting-outputs
flutter build apk --release  # Android
flutter build ios --release  # iOS
# Deploy to app stores (existing process)
```

**4. Smoke Testing (Staging)**
- [ ] Complete OAuth flow with test Garmin account
- [ ] Sync 7 days of data
- [ ] Verify metrics in dashboard
- [ ] Check database for duplicates (should be zero)
- [ ] Test token refresh (set expiry to 1 min in code)

**5. Production Launch**
- [ ] Deploy backend
- [ ] Deploy Flutter app (staged rollout: 10% → 50% → 100%)
- [ ] Monitor logs for errors
- [ ] Monitor Garmin API rate limits

---

### Rollback Plan

**If critical bug discovered:**
1. Database rollback NOT needed (tables backward compatible)
2. Revert backend to previous version (old code ignores new tables)
3. Revert Flutter app or disable Health nav item via feature flag

**If Garmin API issues:**
- Disable sync endpoint via application.properties flag
- Show maintenance banner in app
- Investigate and fix, then re-enable

---

## Open Questions & Future Work

### Questions for User

1. **Privacy:** Should we add encryption at rest for OAuth tokens? (Adds complexity)
2. **Data Export:** Include health metrics in existing CSV export, or separate export?
3. **Correlations:** Which nutrition → health correlations are most interesting?
   - Plant diversity → HRV?
   - Meal timing → Sleep quality?
   - Protein intake → Resting HR?

---

### Future Enhancements (Phase 2B+)

**Phase 2B: Statistical Correlations**
- Calculate Pearson/Spearman correlations
- Visualize scatter plots (e.g., protein grams vs HRV)
- Statistical significance testing (p-values, confidence intervals)

**Phase 2C: AI-Generated Insights**
- Weekly reports: "Your HRV was 15% higher on days with >8 plant species"
- Anomaly detection: "Your sleep score dropped 20% this week"
- Recommendations: "Try eating dinner before 7pm to improve sleep"

**Phase 2D: Advanced Features**
- Multi-wearable support (Apple Health, Strava, Whoop)
- CGM integration (Dexcom, Freestyle Libre)
- N-of-1 experiments (structured self-testing)

---

## Appendix

### Garmin API Reference

**Base URL:** `https://apis.garmin.com/wellness-api/rest`

**Key Endpoints:**
- `GET /dailies` → Daily summaries (HRV, RHR, steps, calories)
- `GET /sleeps` → Sleep sessions with stages
- `GET /activities` → Exercise sessions with GPS
- `GET /moveIQ` → Auto-detected activities

**Authentication:** OAuth 2.0 with access/refresh tokens

**Rate Limits:**
- 100 requests per hour per user (check headers for current usage)

**Documentation:** https://developer.garmin.com/health-api/overview

---

### Dependencies to Add

**Backend (build.gradle):**
```gradle
dependencies {
    // Existing dependencies...

    // Garmin integration
    implementation 'com.squareup.okhttp3:okhttp:4.11.0'
    implementation 'com.auth0:java-jwt:4.4.0'  // If Garmin uses JWT tokens
}
```

**Flutter (pubspec.yaml):**
```yaml
dependencies:
  # Existing dependencies...

  # Health integration
  fl_chart: ^0.65.0        # Time-series charts
  webview_flutter: ^4.4.2  # OAuth web view
```

---

## Summary

This design document provides a complete blueprint for Phase 2A Garmin integration:

✅ **Database schema** (3 tables: oauth_connections, health_metrics, health_notes)
✅ **Backend services** (OAuth, data sync, metrics storage, notes)
✅ **REST API** (8 endpoints for auth, sync, query, notes)
✅ **Flutter UI** (4 screens, 3 models, 3 services)
✅ **Data flows** (OAuth, sync, charts, notes, location redundancy)
✅ **Testing strategy** (unit, integration, manual)
✅ **Security & privacy** (token management, data access control)
✅ **Deployment plan** (prerequisites, steps, rollback)

**Ready for implementation after Phase 3 completion.**

**Next Steps:**
1. Finish Phase 3 (merge PRs #5, #7)
2. Set up Garmin Developer account
3. Create implementation plan (detailed tasks)
4. Begin coding Phase 2A
