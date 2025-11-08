# Local Development Setup Guide

Complete guide to run Nutritheous locally on your Mac with Android WiFi debugging.

## Prerequisites

- macOS
- Homebrew installed
- Java 21 (already installed)
- Android phone on same WiFi network

---

## Step 1: Install PostgreSQL

```bash
# Install PostgreSQL via Homebrew
brew install postgresql@15

# Start PostgreSQL service
brew services start postgresql@15

# Add psql to PATH (add to ~/.zshrc or ~/.bash_profile)
echo 'export PATH="/opt/homebrew/opt/postgresql@15/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc

# Verify installation
psql --version
```

## Step 2: Create Database

```bash
# Connect to PostgreSQL as superuser
psql postgres

# In the psql prompt, run:
CREATE DATABASE nutritheous;
CREATE USER nutritheous_user WITH PASSWORD 'nutritheous_dev_password';
GRANT ALL PRIVILEGES ON DATABASE nutritheous TO nutritheous_user;

# Grant schema permissions (needed for migrations)
\c nutritheous
GRANT ALL ON SCHEMA public TO nutritheous_user;

# Exit psql
\q
```

## Step 3: Configure Environment

```bash
cd /Users/aditya/Documents/Ongoing\ Local/nutritheous/backend

# Copy the example .env file
cp .env.example .env

# Edit .env with your actual values
nano .env
```

**Required values in .env:**
```bash
# Database (use values from Step 2)
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/nutritheous
SPRING_DATASOURCE_USERNAME=nutritheous_user
SPRING_DATASOURCE_PASSWORD=nutritheous_dev_password

# OpenRouter API
OPENAI_API_KEY=sk-or-v1-YOUR-ACTUAL-OPENROUTER-KEY-HERE
OPENAI_API_URL=https://openrouter.ai/api/v1/chat/completions
OPENAI_API_MODEL=anthropic/claude-3.5-sonnet

# Server
SERVER_PORT=8080

# JWT (use this for testing, change in production)
JWT_SECRET=local-dev-secret-key-change-in-production-make-it-very-long-and-random-123456
JWT_EXPIRATION=86400000

# Optional: Google Maps (leave empty for now, location features will be disabled)
GOOGLE_MAPS_API_KEY=
```

**Get your OpenRouter API key:**
1. Go to https://openrouter.ai/
2. Sign up / Log in
3. Go to Keys: https://openrouter.ai/keys
4. Create a new key
5. Copy and paste into .env file

## Step 4: Run Database Migrations

```bash
cd backend

# Run Flyway migrations
./gradlew flywayMigrate

# You should see: "Successfully applied 15 migrations"
```

## Step 5: Build and Start Backend

```bash
# Build the backend
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
./gradlew build -x test

# Start the server
./gradlew bootRun
```

**Expected output:**
```
Started NutritheousApplication in X seconds
OpenAI Vision Service initialized with URL: https://openrouter.ai/api/v1/chat/completions
```

**Test the server:**
Open http://localhost:8080/actuator/health in your browser
- Should return: `{"status":"UP"}`

---

## Step 6: Get Your Mac's Local IP Address

```bash
# Find your local IP (usually 192.168.x.x)
ifconfig | grep "inet " | grep -v 127.0.0.1
```

Example output: `inet 192.168.1.100` ← This is your IP

**Note this IP address** - you'll need it for Flutter config.

---

## Step 7: Configure Flutter for Local Backend

```bash
cd ../frontend  # Navigate to Flutter app

# Find the API configuration file
find lib -name "*config*" -o -name "*api*" -o -name "*environment*" | head -10
```

Look for files like:
- `lib/config/api_config.dart`
- `lib/services/api_service.dart`
- `lib/core/constants.dart`

**Update the base URL to your Mac's IP:**
```dart
// Change from:
static const String baseUrl = 'https://api.nutritheous.com';

// To (use YOUR IP from Step 6):
static const String baseUrl = 'http://192.168.1.100:8080';
```

---

## Step 8: Android WiFi Debugging Setup

### Enable WiFi Debugging on Your Phone

**Method A: USB First (Easiest)**

1. Enable Developer Options on Android:
   - Go to Settings → About Phone
   - Tap "Build Number" 7 times
   - Developer Options now enabled

2. Enable USB Debugging:
   - Settings → Developer Options → USB Debugging (ON)

3. Connect phone via USB to Mac

4. On your Mac:
```bash
# Verify phone is connected
adb devices

# Should show your device like: "ABC123456 device"

# Enable wireless debugging
adb tcpip 5555

# Find phone's IP (in phone: Settings → About Phone → Status → IP address)
# Or use:
adb shell ip addr show wlan0 | grep inet

# Connect via WiFi (replace with your phone's IP)
adb connect 192.168.1.XXX:5555

# Disconnect USB cable now!

# Verify wireless connection
adb devices
# Should still show your device with ":5555" at the end
```

**Method B: Android 11+ (No USB needed)**

1. Settings → Developer Options → Wireless Debugging (ON)
2. Tap "Wireless Debugging"
3. Tap "Pair device with pairing code"
4. Note the IP:Port and 6-digit code

On your Mac:
```bash
# Pair (use the IP:Port and code from phone)
adb pair 192.168.1.XXX:XXXXX

# Enter the 6-digit pairing code when prompted

# Connect
adb connect 192.168.1.XXX:5555
```

---

## Step 9: Build and Install Flutter App

```bash
cd frontend

# Make sure phone is connected
flutter devices
# Should show your Android device

# Build and install (first time takes ~5 minutes)
flutter run

# Or for release build:
flutter build apk
flutter install
```

**Expected:** App installs and launches on your phone!

---

## Step 10: Test the Full Flow

### Create an Account

1. Open app → Sign Up
2. Enter email, password
3. Should create account successfully

### Test Meal Photo Analysis

1. Take a photo of food (or use existing)
2. Add optional description
3. Submit
4. Wait for AI analysis (OpenRouter/Claude)
5. Should see nutrition breakdown!

### Test Ingredient Decomposition

1. After meal analysis, tap "View Ingredients"
2. Should see individual ingredients (e.g., rice, curry, etc.)
3. Tap an ingredient to edit quantity
4. Save changes
5. Verify update persists

---

## Troubleshooting

### Backend won't start

**Check PostgreSQL is running:**
```bash
brew services list | grep postgresql
# Should show "started"

# If not:
brew services restart postgresql@15
```

**Check .env file exists and has correct values:**
```bash
cat .env | grep OPENAI_API_KEY
# Should NOT be empty
```

**Check logs for errors:**
```bash
./gradlew bootRun --info
```

### Flutter can't connect to backend

**Test from phone's browser:**
Open Chrome on your phone → `http://192.168.1.XXX:8080/actuator/health`
- Should show `{"status":"UP"}`
- If not, firewall may be blocking. Check Mac Firewall settings.

**Verify both devices on same WiFi:**
```bash
# On Mac
ifconfig | grep "inet " | grep -v 127.0.0.1

# On phone: Settings → WiFi → Tap network name → IP address
# First 3 numbers should match (e.g., both 192.168.1.X)
```

### ADB connection issues

```bash
# Kill and restart ADB server
adb kill-server
adb start-server

# Reconnect
adb connect 192.168.1.XXX:5555
```

### App crashes on startup

```bash
# Check logs
flutter logs

# Or
adb logcat | grep flutter
```

---

## Useful Commands

```bash
# Backend
./gradlew bootRun                    # Start server
./gradlew flywayMigrate             # Run migrations
./gradlew flywayClean               # Drop all tables (DANGER!)
./gradlew test                      # Run tests

# Flutter
flutter run                          # Build and install
flutter logs                         # View app logs
flutter clean                        # Clean build cache
flutter pub get                      # Install dependencies

# PostgreSQL
psql -U nutritheous_user -d nutritheous   # Connect to DB
\dt                                        # List tables
\q                                         # Quit

# ADB
adb devices                          # List connected devices
adb connect IP:5555                  # Connect via WiFi
adb disconnect                       # Disconnect
adb logcat                           # View device logs
```

---

## Next Steps After Testing

Once everything works:

1. **Deploy backend to cloud** (Railway, Render, Fly.io)
2. **Update Flutter to use cloud URL**
3. **Build release APK** for installation
4. **Set up CI/CD** for automated deployments

For now, enjoy testing on WiFi! 🚀
