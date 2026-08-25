# Galaxy Buds2 BLE Scanner

İlk sürüm: Android BLE tarama ve GATT servis keşfi.

## V1

- BLE scan
- cihaz adı / adres / RSSI
- Galaxy Buds benzeri cihazı otomatik bağlama
- GATT service discovery
- characteristic UUID ve properties
- READ callback logu
- NOTIFY callback logu
- HEX veri görüntüleme

## Amaç

Bu proje Galaxy Buds2 üzerinde önce iletişim yapısını gözlemlemek, daha sonra güvenli biçimde Buds2 kontrol özelliklerini araştırmak için hazırlanmıştır.

## Build

Android Studio veya Gradle ile `assembleDebug` çalıştırılabilir.

Android 12+ için Nearby devices izinleri gerekir.
