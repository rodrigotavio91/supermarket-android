# Location Detection - Quick Reference

## 🚀 Quick Start

### Setup Google Places API Key

1. Get API key from [Google Cloud Console](https://console.cloud.google.com/)
2. Enable Places API
3. Replace in `AndroidManifest.xml`:
   ```xml
   <meta-data
       android:name="com.google.android.geo.API_KEY"
       android:value="YOUR_ACTUAL_KEY_HERE" />
   ```

### Test the Implementation

```bash
# Clean install
adb uninstall com.barcodescanner.app
./gradlew installDebug

# Clear app data (reset to first launch)
adb shell pm clear com.barcodescanner.app

# Monitor logs
adb logcat | grep -E "(Location|Places)"
```

## 📁 File Structure

```
app/src/main/java/com/barcodescanner/app/
├── data/
│   ├── location/
│   │   ├── LocationManager.kt      ← Location requests + caching
│   │   ├── PlacesManager.kt        ← Places API integration
│   │   └── LocationRepository.kt   ← Coordinator + state
│   └── model/
│       └── Product.kt              ← Updated PriceInfo model
└── ui/
    └── location/
        ├── LocationLoadingFragment.kt  ← UI + permission
        └── LocationViewModel.kt        ← State management

app/src/main/res/
├── layout/
│   └── fragment_location_loading.xml   ← Location UI layout
└── navigation/
    └── mobile_navigation.xml           ← Updated navigation

app/
├── build.gradle.kts                ← Added dependencies
└── src/main/AndroidManifest.xml    ← Added permissions + API key
```

## 🔑 Key Classes

### LocationRepository
```kotlin
// Get current store (with caching)
repository.getCurrentStore()
    .collect { state ->
        when (state) {
            is LocationState.Loading -> /* show loading */
            is LocationState.Success -> /* show store: ${state.storeName} */
            is LocationState.Error -> /* show error */
            is LocationState.PermissionDenied -> /* request permission */
        }
    }

// Get cached store (synchronous)
val store = repository.getCachedStore() // Returns String? or null

// Check if first launch
val isFirst = repository.isFirstLaunch() // Returns Boolean
```

### Usage in ProductDetailFragment (Future)
```kotlin
val repository = LocationRepository(requireContext())

// When adding a price
val priceInfo = PriceInfo(
    storeName = repository.getCachedStore() ?: "Unknown Store",
    price = userPrice,
    timestamp = System.currentTimeMillis()
)
```

## 🔧 Common Tasks

### Force Location Refresh
```bash
# Clear cache
adb shell run-as com.barcodescanner.app rm /data/data/com.barcodescanner.app/shared_prefs/location_prefs.xml

# Or programmatically
repository.clearCache()
```

### Test Different States
```kotlin
// Simulate permission denied
viewModel.onPermissionResult(false)

// Simulate location update
viewModel.requestLocationUpdate()
```

### Debug Location Issues
```bash
# Check location services
adb shell settings get secure location_mode

# Check permissions
adb shell dumpsys package com.barcodescanner.app | grep permission

# View SharedPreferences
adb shell run-as com.barcodescanner.app cat /data/data/com.barcodescanner.app/shared_prefs/location_prefs.xml
```

## 📊 Cache Strategy

| Scenario | Behavior |
|----------|----------|
| First launch | Request location + store name |
| < 30 min since last | Use cached store name |
| > 30 min since last | Re-request location |
| Permission denied | Show permission UI |
| No store found | Use "Unknown Store" |

## 🎯 Testing Checklist

### First Launch
- [ ] Shows location loading screen
- [ ] Requests permission
- [ ] Detects location
- [ ] Shows success with store name
- [ ] Navigates to main app

### Cached Location
- [ ] Skips location screen
- [ ] Goes directly to scan
- [ ] Uses cached store name

### Error Cases
- [ ] Permission denied → shows rationale
- [ ] No location → shows error + retry
- [ ] No network → shows error + retry
- [ ] No store found → uses "Unknown Store"

## 🐛 Troubleshooting

| Problem | Solution |
|---------|----------|
| Build fails | `./gradlew clean build` |
| Stuck on loading | Check API key + billing |
| Permission not requested | Clear app data |
| Always "Unknown Store" | Check API restrictions |
| Navigation fails | Verify navigation graph |

## 📖 Documentation

- **LOCATION_SETUP.md**: Full API setup guide
- **ARCHITECTURE.md**: Technical architecture
- **QUICK_FIX.md**: Troubleshooting guide
- **IMPLEMENTATION_SUMMARY.md**: What was implemented

## 🔐 Security Notes

⚠️ **API Key Restrictions** (Google Cloud Console):
- Application restrictions: Android apps
- Package name: `com.barcodescanner.app`
- SHA-1: Get with `./gradlew signingReport`

## 💰 Cost Optimization

Current implementation:
- ✅ 30-minute cache (96% reduction in API calls)
- ✅ Only basic fields requested
- ✅ Single request per detection
- ✅ No background tracking

Estimated: **~$3/month** for 1000 users (within free tier)

## 🔄 State Flow

```
LocationLoadingFragment
    ↓
LocationViewModel
    ↓
LocationRepository
    ↓
Flow<LocationState>
    ↓
UI Updates
    ↓
Navigate to ScanFragment
```

## 📝 Quick Code Snippets

### Check if location is cached
```kotlin
val locationRepository = LocationRepository(context)
if (locationRepository.getCachedStore() != null) {
    // Use cached location
} else {
    // Need to request location
}
```

### Get store name for price tagging
```kotlin
val currentStore = LocationRepository(context).getCachedStore() ?: "Unknown Store"
```

### Clear cache (for testing)
```kotlin
LocationRepository(context).clearCache()
```

### Reset to first launch
```bash
adb shell pm clear com.barcodescanner.app
```

---

**Questions?** See full documentation in `LOCATION_SETUP.md`
