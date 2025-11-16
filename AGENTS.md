# AI Agent Guidelines for BarcodeScanner Project

This document provides architectural context and development guidelines for AI coding assistants working on this project.

## Core Philosophy: Framework-First Solutions

When solving problems in this codebase, **ALWAYS prioritize solutions that work WITH the framework, not against it.**

The Android ecosystem provides powerful, well-tested solutions for common patterns. Before implementing custom code, investigate whether the framework already handles your use case.

---

## Navigation Architecture

### Bottom Navigation with Multi-Back-Stack Support

This app uses the **Navigation Component** with the modern multi-back-stack pattern for bottom navigation.

#### Key Architectural Decisions

**✅ What We Use:**
- Single `NavHostFragment` with `setupWithNavController()` for automatic state preservation
- Nested navigation graphs for tabs with their own navigation flows
- BottomNavigationView item selection to trigger navigation
- Framework-managed state saving and restoration

**❌ What We DON'T Use:**
- Multiple `NavHostFragment` instances (outdated workaround pattern)
- Manual state saving/restoration (framework handles it automatically)
- Direct `navController.navigate()` calls for switching between tabs
- Custom back stack management

#### Why This Pattern?

- **Nested graphs** allow tabs to maintain their own navigation flows and back stacks
- **Framework handles state** automatically when using `setupWithNavController()`
- **Standard Android pattern** supported since Navigation Component 2.4.0+
- **Simpler code** with less manual management

#### Important Principles

1. **Let BottomNavigationView handle tab switching** - Don't bypass it with manual navigation calls
2. **Use nested graphs for complex tab flows** - Simple tabs can be single fragments
3. **Menu items must be enabled** - Disabled items won't respond to programmatic selection
4. **Trigger via item selection** - Let the framework apply proper NavOptions automatically

---

## Problem-Solving Guidelines

### Investigation Before Implementation

When a common UI pattern isn't working as expected:

1. **Assume the framework has a solution**
   - Android is mature; common patterns are usually built-in
   - Check the framework version in `app/build.gradle.kts` to know what features are available
   - Look for official APIs that handle the pattern automatically

2. **Research the current recommended approach**
   - Framework best practices evolve (e.g., Navigation 2.4.0 added multi-back-stack support)
   - Old Stack Overflow answers may reference outdated workarounds
   - Check official Android documentation for the specific version you're using

3. **Question complex solutions**
   - If a solution requires extensive custom code, STOP and reconsider
   - Ask: "Am I fighting the framework?"
   - Simpler is almost always better

4. **Look for configuration issues first**
   - Missing flags, disabled menu items, incorrect IDs
   - Framework bypasses (calling APIs directly instead of using helpers)
   - These are easier to fix than architectural changes

### Decision Framework

Before proposing a solution, ask:

- [ ] Is this the official recommended pattern for this framework version?
- [ ] Am I replicating functionality the framework already provides?
- [ ] Could this be solved by configuring an existing feature?
- [ ] Have I verified the dependency versions to know what's available?
- [ ] Is there a simpler fix (one-line change vs. multi-file refactor)?

### When Stuck

If a standard pattern isn't working:

1. ✅ Verify the framework version supports it
2. ✅ Check for configuration issues (disabled items, missing flags)
3. ✅ Look for framework bypasses in existing code
4. ❌ Only then consider custom implementations

---

## Android Development Patterns in This Project

### Navigation

- **Within a nested graph**: Use Navigation Component actions with Safe Args
- **Between top-level tabs**: Trigger via BottomNavigationView item selection, not direct navigation
- **Safe Args**: Type-safe navigation arguments are configured

### Camera & ML Kit

- **CameraX**: Lifecycle-aware, framework manages camera lifecycle
- **Barcode detection**: Debounced to prevent duplicate scans
- **Performance**: Optimized for low-end devices

### View Binding

- **Always enabled**: Use ViewBinding for all view access
- **Fragment pattern**: Proper lifecycle management (initialize/nullify)

### Material Design 3

- Follow Material You guidelines
- Bottom navigation with FAB overlay pattern

---

## Common Anti-Patterns to Avoid

### Navigation

❌ **Multiple NavHostFragments** for bottom nav tabs (outdated workaround, not needed with modern Navigation Component)
❌ **Bypassing BottomNavigationView helpers** by calling `navController.navigate()` directly for tab switching
❌ **Manual state saving** for framework-managed components (setupWithNavController handles it)
❌ **Custom NavOptions** for bottom nav when framework provides correct defaults

### General

❌ **Assuming old limitations** without checking current framework versions
❌ **Creating custom solutions** before researching framework-provided ones
❌ **God fragments** - Keep fragments focused and single-purpose
❌ **Manual lifecycle management** for lifecycle-aware components

---

## Project Context

### Tech Stack

Check `app/build.gradle.kts` for current versions. Key dependencies:
- **Navigation Component** - With multi-back-stack support (2.4.0+)
- **CameraX** - Lifecycle-aware camera API
- **ML Kit Barcode Scanning** - On-device detection
- **Material Components** - Material Design 3

### Performance Targets

- **Low-end device optimization**: ProGuard enabled for release builds
- **Battery efficiency**: On-device ML processing, optimized camera usage
- **Debounced scanning**: Cooldown period to prevent duplicate scans

### Navigation Structure

- Single NavHostFragment
- Bottom navigation with three tabs: Catalog, Scan, Profile
- Scan tab uses nested navigation graph for multi-screen flow
- Other tabs are simple single-screen destinations

---

## Key Learnings from This Project

### Navigation State Preservation

**Problem Pattern**: Tab state not preserved when switching between bottom navigation items (e.g., navigating deep in one tab, switching tabs, then returning resets the first tab).

**Common Root Causes**:
1. Bypassing BottomNavigationView's built-in navigation by calling `navController.navigate()` directly
2. Using custom NavOptions that don't include state saving/restoration
3. Disabled menu items preventing programmatic selection from working

**Solution Approach**:
1. Let BottomNavigationView handle tab switching via its item selection mechanism
2. Trust `setupWithNavController()` to apply the correct NavOptions automatically
3. Verify menu items are properly configured (enabled, correct IDs)

**Key Insight**: When framework helpers exist, use them. Don't bypass them with manual calls that seem equivalent but miss important internal logic.

---

## Resources

- [Navigation Component Documentation](https://developer.android.com/guide/navigation)
- [Multiple Back Stacks](https://developer.android.com/guide/navigation/multi-back-stacks)
- [Material Design 3](https://m3.material.io/)
- [CameraX Documentation](https://developer.android.com/training/camerax)

---

**Remember**: The best code is code you don't have to write. Modern Android frameworks are powerful - leverage them.
