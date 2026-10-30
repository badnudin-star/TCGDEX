# Release Keystore & Backup Guide

## 1. Generating a Release Keystore with Keytool

Run the following command in your terminal to create a release keystore:

```bash
keytool -genkeypair -v \
  -keystore release-key.jks \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -alias tcg_sammlung_key
```

You will be prompted to enter a keystore password and organizational details.

## 2. Configuring `keystore.properties`

Create a file named `keystore.properties` in the root directory of the project (this file is excluded from Git in `.gitignore`):

```properties
storeFile=../release-key.jks
storePassword=YOUR_STORE_PASSWORD
keyAlias=tcg_sammlung_key
keyPassword=YOUR_KEY_PASSWORD
```

## 3. WARNING: Changing Signatures (Debug to Release)

> **IMPORTANT WARNING**:
> Changing the signing key from the default Android Debug Certificate to a custom Release Keystore (or changing keys between builds) will cause Android to block app updates over existing installations due to signature mismatch.
>
> **Required Action Before Changing Keystores or Re-installing:**
> 1. Open the app and go to **Sammlung Exporte** (Collection Export).
> 2. Perform a complete **JSON Export** of your collection (`tcg_sammlung_export.json`) using the Storage Access Framework.
> 3. Save the JSON file to your device or Google Drive.
> 4. Uninstall the debug app and install the release signed APK.
> 5. Open the app and use **Sammlung Import** to restore all user cards and collection data safely.
