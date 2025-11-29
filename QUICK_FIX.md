# Quick Reference: Places API Setup

## What Changed
Updated from legacy `findCurrentPlace()` to modern Places API (New) with:
- ✅ 500m radius filtering
- ✅ Place type filtering (supermarket, grocery_store)
- ✅ Distance-based closest store selection
- ✅ Efficient server-side filtering

## Files Modified
1. `app/src/main/java/com/barcodescanner/app/data/location/PlacesManager.kt`
2. `app/src/main/java/com/barcodescanner/app/data/location/LocationRepository.kt`

## Enable Places API (New)

### Google Cloud Console
1. Go to: https://console.cloud.google.com/
2. Select your project
3. Navigate to: **APIs & Services** → **Library**
4. Search for: **"Places API (New)"**
5. Click **Enable**

### API Key Configuration
Your API key must have:
- ✅ Places API (New) enabled
- ✅ No restrictions OR Application restrictions set to your app's package name
- ✅ Billing enabled on the project

### Check Current API Key
Location: `app/src/main/AndroidManifest.xml`
```xml
<meta-data
    android:name="com.google.android.geo.API_KEY"
    android:value="YOUR_API_KEY_HERE" />
```

## Testing the Implementation

### 1. Check Logs
```bash
adb logcat | grep -E "PlacesManager|LocationRepository"
```

Expected logs:
```
PlacesManager: Found nearby store: Store Name (123.45m away)
LocationRepository: Using cached store: Store Name
```

### 2. Test Location Flow
1. Launch app
2. Grant location permission
3. Wait for location detection
4. Check if store name appears

### 3. Debug Issues

**No stores found:**
- Check if stores exist within 500m of test location
- Verify API key has Places API (New) enabled
- Check network logs for API errors

**API errors:**
- Check billing is enabled
- Verify API key restrictions
- Check network connectivity

**Permission errors:**
- Grant location permissions in app settings
- Check AndroidManifest.xml has location permissions

## Network Inspection

### View API Requests
Enable verbose logging in `PlacesManager.kt`:
```kotlin
loggingInterceptor.level = HttpLoggingInterceptor.Level.BODY
```

### Expected Request
```json
{
  "includedTypes": ["supermarket", "grocery_store"],
  "maxResultCount": 10,
  "locationRestriction": {
    "circle": {
      "center": {
        "latitude": 37.7749,
        "longitude": -122.4194
      },
      "radius": 500.0
    }
  }
}
```

### Expected Response
```json
{
  "places": [
    {
      "displayName": {
        "text": "Safeway"
      },
      "location": {
        "latitude": 37.7750,
        "longitude": -122.4195
      }
    }
  ]
}
```

## Adjustable Parameters

### Search Radius
Change in `PlacesManager.kt`:
```kotlin
private const val SEARCH_RADIUS_METERS = 500.0  // Increase if needed
```

### Place Types
Change in `PlacesManager.kt`:
```kotlin
includedTypes = listOf(
    "supermarket", 
    "grocery_store",
    "convenience_store"  // Add more types
)
```

### Max Results
Change in `PlacesManager.kt`:
```kotlin
maxResultCount = 10  // Increase for more options
```

## Cost Estimation

Places API (New) - Search Nearby:
- **Cost**: $0.032 per request (as of 2024)
- **Monthly Free**: $200 credit ≈ 6,250 requests/month
- **With Caching**: ~30-60 requests/user/month (30min cache)

## Rollback Plan

If issues occur, revert to old implementation:
```bash
git checkout HEAD~1 -- app/src/main/java/com/barcodescanner/app/data/location/PlacesManager.kt
git checkout HEAD~1 -- app/src/main/java/com/barcodescanner/app/data/location/LocationRepository.kt
```

## Support Links

- [Places API (New) Documentation](https://developers.google.com/maps/documentation/places/web-service/search-nearby)
- [Android Places SDK](https://developers.google.com/maps/documentation/places/android-sdk/overview)
- [API Key Best Practices](https://developers.google.com/maps/api-security-best-practices)
