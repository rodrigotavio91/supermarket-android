# Location Detection Architecture

## Component Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                        MainActivity                              │
│  - Checks first launch status                                   │
│  - Conditionally navigates to LocationLoadingFragment           │
└────────────────────────┬────────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────────┐
│                 LocationLoadingFragment                          │
│  - Requests location permission                                 │
│  - Shows loading/success/error/permission states                │
│  - Auto-navigates to main app on success                        │
└────────────────────────┬────────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────────┐
│                   LocationViewModel                              │
│  - Exposes LiveData<LocationState>                              │
│  - Methods: requestLocationUpdate(), onPermissionResult()       │
└────────────────────────┬────────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────────┐
│                  LocationRepository                              │
│  - Coordinates LocationManager + PlacesManager                  │
│  - Implements caching logic                                      │
│  - Exposes Flow<LocationState>                                   │
│  - Manages first launch detection                               │
└──────────────┬────────────────────────────┬─────────────────────┘
               │                            │
               ▼                            ▼
┌──────────────────────────┐    ┌──────────────────────────┐
│    LocationManager       │    │     PlacesManager        │
│ - FusedLocationProvider  │    │ - Google Places SDK      │
│ - 30-min cache timeout   │    │ - Find Current Place API │
│ - SharedPreferences      │    │ - Filter supermarkets    │
└──────────────────────────┘    └──────────────────────────┘
```

## Data Flow

### First Launch Flow
```
1. User opens app
2. MainActivity checks isFirstLaunch() → true
3. Navigation graph shows LocationLoadingFragment
4. Fragment requests location permission
5. User grants permission
6. ViewModel calls requestLocationUpdate()
7. Repository emits LocationState.Loading
8. Fragment shows loading indicator
9. Repository calls LocationManager.getLastLocation()
10. LocationManager uses FusedLocationProviderClient
11. Repository calls PlacesManager.findNearbyStores()
12. PlacesManager uses Find Current Place API
13. Repository receives store name
14. Repository saves to cache and emits LocationState.Success
15. Fragment shows success state with store name
16. After 1 second, navigates to ScanFragment
17. Repository marks first launch complete
```

### Subsequent Launch Flow (Cached)
```
1. User opens app
2. MainActivity checks isFirstLaunch() → false
3. MainActivity checks getCachedStore() → "Store Name"
4. MainActivity navigates directly to ScanFragment
5. User scans products immediately
```

### Subsequent Launch Flow (Cache Expired)
```
1. User opens app
2. MainActivity checks cache → expired (>30 min)
3. Navigation shows LocationLoadingFragment
4. Permission already granted (no prompt)
5. Repository detects location and updates cache
6. Navigates to main app
```

## State Management

### LocationState Sealed Class
```kotlin
sealed class LocationState {
    object Loading            // Detecting location
    data class Success        // Store detected
    data class Error          // Detection failed
    object PermissionDenied   // User denied permission
}
```

### UI State Mapping
```
LocationState.Loading       → Show progress bar + "Detecting..."
LocationState.Success       → Show checkmark + store name
LocationState.Error         → Show warning + error message + retry
LocationState.PermissionDenied → Show explanation + grant button
```

## Caching Strategy

### SharedPreferences Storage
```
Key: last_store_name     Value: "SupermarketName"
Key: last_location_time  Value: timestamp (Long)
Key: is_first_launch     Value: true/false
```

### Cache Validation
```kotlin
fun shouldRequestLocation(): Boolean {
    val lastLocationTime = prefs.getLong("last_location_time", 0L)
    val currentTime = System.currentTimeMillis()
    val timeSinceLastUpdate = currentTime - lastLocationTime
    
    return timeSinceLastUpdate > 30 * 60 * 1000L // 30 minutes
}
```

### Cache Usage
```
First Launch        → No cache → Request location
Within 30 min       → Use cache → Skip detection
After 30 min        → Cache expired → Request location
```

## Permission Flow

### Permission States
```
Not Requested → Request → Granted ────────────► Detect Location
                         │
                         └─► Denied ──► Show Explanation
                                         │
                                         └─► Retry
```

### Permission Request
```kotlin
// Modern approach using ActivityResultContracts
private val requestPermissionLauncher = registerForActivityResult(
    ActivityResultContracts.RequestPermission()
) { isGranted: Boolean ->
    viewModel.onPermissionResult(isGranted)
}

requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
```

## Error Handling

### Error Scenarios
```
1. Location permission denied     → Show permission rationale
2. Location services disabled     → Show error + retry
3. No location available          → Show error + retry
4. Network error (Places API)     → Show error + retry
5. No supermarket found           → Use "Unknown Store"
6. API key missing/invalid        → Show error + fallback
7. API quota exceeded             → Show error + cache fallback
```

### Graceful Degradation
```
Best case:  Detected store name
Okay case:  "Unknown Store"
Worst case: App still works, just no store tagging
```

## Performance Optimizations

### API Call Reduction
```
Without cache: ~480 API calls/day (1 per launch, 20 launches/day, 24 users)
With cache:    ~19 API calls/day (1 per 30-min window)
Reduction:     96%
```

### Battery Optimization
```
- No continuous location tracking
- Single location request on demand
- PRIORITY_BALANCED_POWER_ACCURACY
- Cancellable coroutines
- Lifecycle-aware components
```

### Network Optimization
```
- Cache-first strategy
- Minimal Places API fields requested
- Single Find Current Place request
- No polling or background updates
```

## Integration Points

### ProductDetailFragment Integration (Future)
```kotlin
// When adding a price, include location data
val priceInfo = PriceInfo(
    storeName = locationRepository.getCachedStore() ?: "Unknown Store",
    price = userEnteredPrice,
    timestamp = System.currentTimeMillis(),
    storeId = null,  // Future: from Places API
    latitude = null, // Future: from location
    longitude = null // Future: from location
)
```

### Catalog Integration (Future)
```kotlin
// Filter prices by current store
val currentStore = locationRepository.getCachedStore()
val localPrices = product.prices.filter { it.storeName == currentStore }
```

## Testing Strategy

### Unit Tests (Future)
- LocationManager cache logic
- LocationRepository state emissions
- ViewModel permission handling
- Store name filtering

### Integration Tests (Future)
- End-to-end location flow
- Permission request flow
- Cache expiration logic
- Error handling scenarios

### Manual Testing (Current)
- First launch detection
- Permission request/denial
- Location detection
- Cache usage
- Error states
- Navigation flow

## Security Considerations

### API Key Security
```
⚠️  Current: API key in AndroidManifest.xml (visible in APK)
✅  Recommended: Restrict API key in Google Cloud Console
    - Limit to Android apps only
    - Restrict to your package name
    - Restrict to your SHA-1 fingerprint
    
🔒 Production: Consider backend proxy for Places API
    - API key stays server-side
    - Rate limiting per user
    - Better cost control
```

### Location Privacy
```
✅ Request only when needed (first launch + cache expiration)
✅ No background location tracking
✅ Clear explanation of why permission is needed
✅ User can deny and app still works
✅ Location data not stored (only store name)
```

## Cost Estimation

### Google Places API Pricing
```
Find Current Place: ~$0.004 per request (with basic fields)

Estimated monthly cost (1000 users, 20 launches/month):
- Without cache: 20,000 requests × $0.004 = $80
- With cache:       800 requests × $0.004 = $3.20
- Savings: $76.80/month (96% reduction)
```

### Free Tier
```
Google provides $200/month free credit
Our implementation: ~$3.20/month
Effectively free for most use cases
```

## Future Enhancements

### Phase 2: Manual Store Selection
- Add fallback UI for store selection
- Store database with verified locations
- User preference for default store

### Phase 3: Geofencing
- Create geofences around known stores
- Automatic detection when entering store
- Background location updates (opt-in)

### Phase 4: Price Intelligence
- Price comparison between stores
- Price history graphs
- Best price alerts
- Store recommendations

### Phase 5: Offline Support
- Download store database
- Offline geocoding
- Sync when online
- Local-first architecture
