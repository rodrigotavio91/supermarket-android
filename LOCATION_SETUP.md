# Location-Based Supermarket Detection Setup

## Google Places API Key Setup

The app uses Google Places API to detect nearby supermarkets. You need to set up an API key to enable this functionality.

### Steps to Configure API Key:

1. **Create a Google Cloud Project**
   - Go to [Google Cloud Console](https://console.cloud.google.com/)
   - Create a new project or select an existing one

2. **Enable Places API**
   - Navigate to "APIs & Services" > "Library"
   - Search for "Places API"
   - Click "Enable"

3. **Create API Key**
   - Go to "APIs & Services" > "Credentials"
   - Click "Create Credentials" > "API Key"
   - Copy your API key

4. **Restrict API Key (Recommended)**
   - Click on your API key to edit it
   - Under "Application restrictions":
     - Select "Android apps"
     - Add your package name: `com.barcodescanner.app`
     - Add your SHA-1 certificate fingerprint (get it with `./gradlew signingReport`)
   - Under "API restrictions":
     - Select "Restrict key"
     - Choose "Places API"
   - Save your changes

5. **Add API Key to App**
   - Open `app/src/main/AndroidManifest.xml`
   - Find the line: `<meta-data android:name="com.google.android.geo.API_KEY" android:value="YOUR_API_KEY_HERE" />`
   - Replace `YOUR_API_KEY_HERE` with your actual API key

### Billing Note

Google Places API requires billing to be enabled on your Google Cloud project. The API includes a free tier:
- **Current Place requests**: Free tier may be limited
- Check [Google Maps Platform Pricing](https://developers.google.com/maps/billing-and-pricing/pricing) for details

### Testing Without API Key

If you want to test the app without setting up Places API:
- The location detection will fail gracefully
- The app will fall back to "Unknown Store" as the default store name
- All other functionality will work normally

### Getting SHA-1 Certificate Fingerprint

For debug builds:
```bash
./gradlew signingReport
```

Look for the SHA-1 fingerprint under the `debug` variant.

## Features

### Location Caching
- Location is only requested once every 30 minutes
- Store name is cached in SharedPreferences
- Reduces API calls and battery usage

### First Launch Flow
1. App checks if it's the first launch
2. If yes, shows LocationLoadingFragment
3. Requests location permission
4. Detects nearby supermarket using Places API
5. Caches store name and navigates to main app
6. On subsequent launches, uses cached store name

### Permission Handling
- Uses ActivityResultContracts for modern permission handling
- Shows clear explanation when permission is denied
- Provides retry mechanism

### Error Handling
- Graceful fallback to "Unknown Store" if no supermarket found
- Clear error messages with retry options
- Works offline with cached data

## Architecture

```
data/
  location/
    LocationManager.kt       - Handles FusedLocationProviderClient
    PlacesManager.kt        - Handles Places API integration
    LocationRepository.kt   - Coordinates managers and caching

ui/
  location/
    LocationLoadingFragment.kt - First launch location UI
    LocationViewModel.kt       - ViewModel for location state
```

## Usage

After setup, the app will automatically:
1. Detect the user's location on first launch
2. Find the nearest supermarket
3. Cache the store name for 30 minutes
4. Tag all scanned product prices with the detected store name

The user doesn't need to manually select their store - it's fully automatic!
