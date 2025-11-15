# Barcode Scanner App

A native Android barcode scanning app built with Kotlin, optimized for performance on limited hardware.

## Features

- **Fast barcode scanning** using ML Kit
- **Optimized for low-end devices** with CameraX and ProGuard
- **Material Design 3** UI with bottom navigation
- **GTIN code detection** (EAN-8, EAN-13, UPC-A, UPC-E)
- **Real-time camera preview** with scanner overlay

## Tech Stack

- **Kotlin** - Modern Android development
- **CameraX** - Efficient camera API with lifecycle awareness
- **ML Kit Barcode Scanning** - On-device barcode detection
- **Material Design 3** - Modern Material You components
- **Navigation Component** - Type-safe fragment navigation
- **ViewBinding** - Efficient view access

## Project Structure

```
app/src/main/java/com/barcodescanner/app/
├── MainActivity.kt
├── ui/
│   ├── scan/
│   │   ├── ScanFragment.kt
│   │   ├── BarcodeAnalyzer.kt
│   │   └── ScannerOverlayView.kt
│   ├── catalog/
│   │   └── CatalogFragment.kt
│   └── profile/
│       └── ProfileFragment.kt
```

## Getting Started

### Prerequisites

- Android Studio (latest version)
- JDK 8 or higher
- Android SDK with API 24+ (Android 7.0+)

### Running the App

1. **Open in Android Studio:**
   - File → Open → Select the `BarcodeScanner` folder

2. **Sync Gradle:**
   - Android Studio will automatically sync Gradle dependencies
   - Or click "Sync Project with Gradle Files" in the toolbar

3. **Run on Emulator:**
   - Make sure you have an Android emulator configured
   - Click the "Run" button (green play icon)
   - Select your emulator
   - **Note:** For camera testing, use a physical device for best results

4. **Run on Physical Device:**
   - Enable Developer Options on your Android device
   - Enable USB Debugging
   - Connect your device via USB
   - Click "Run" and select your device

### Building from Command Line

```bash
# Debug build
./gradlew assembleDebug

# Release build (optimized)
./gradlew assembleRelease

# Install on connected device
./gradlew installDebug
```

## App Screens

### 1. Scan Screen (Default)
- Full-screen camera preview
- Square scanner frame with corner accents
- Automatic GTIN code detection
- Shows toast notification when barcode is detected

### 2. Catálogo Screen
- Placeholder for future product catalog

### 3. Perfil Screen
- Placeholder for user profile

## Permissions

The app requires camera permission for barcode scanning. Permission is requested at runtime when accessing the Scan screen.

## Performance Optimizations

1. **Gradle Configuration:**
   - ProGuard enabled for release builds
   - Code shrinking and resource optimization
   - Parallel builds enabled

2. **CameraX:**
   - Hardware-accelerated camera processing
   - Automatic lifecycle management
   - Optimized preview resolution

3. **ML Kit:**
   - On-device processing (no network required)
   - Efficient barcode detection
   - Battery-friendly implementation

4. **Code Best Practices:**
   - ViewBinding for efficient view access
   - Lifecycle-aware components
   - Single-threaded executor for image analysis
   - Debounced barcode detection

## Next Steps

- Implement product lookup after barcode detection
- Add product details screen
- Implement shopping cart functionality
- Add price comparison features
- Create catalog and profile screens

## License

This project is for educational and commercial use.
