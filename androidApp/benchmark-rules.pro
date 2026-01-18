# ProGuard rules for benchmark build type
# These rules ensure the app is not obfuscated or optimized during baseline profile generation

# Disable obfuscation - keeps class and method names readable for profiling
-dontobfuscate

# Disable optimization - ensures all code paths are preserved for profiling
-dontoptimize
