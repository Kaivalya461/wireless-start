# Wireless Start
### Application used for connecting with a BLE Module (this hardware module will trigger Self Start Motor)

---
App Features:
1. Wireless Start
2. Similar Stop Engine Functionality
3. Wear OS Companion App
4. Engine Running Detection (Planned)


---
Commands:
1. Install apk wirelessly on android device
    ```
   adb -s 192.168.1.135:32933 install app/build/outputs/apk/debug/app-debug.apk
    ```
   
2. ADB Wireless Pair And Connect
    ```
   // Pair your phone/watch (using the phone/watch pairing code popup info)
   adb pair 192.168.1.135:33827
   
   // Connect (Note: different port number)
   adb connect 192.168.1.135:32933
   ```