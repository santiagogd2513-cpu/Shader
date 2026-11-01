# Shizuku 13.6.0 – Modo "siempre activo" (KeepAlive)

APK modificado: `Shizuku_13.6.0_KeepAlive.apk` (basado en Shizuku 13.6.0.r1086).

## Qué se cambió

### 1. Servicio vigilante en la app (`KeepAliveService`)
- Servicio en primer plano (`specialUse`, sin límite de tiempo), con una notificación fija
  "Shizuku: modo siempre activo".
- Se inicia solo cada vez que arranca el proceso de la app (al abrirla, al encender el
  teléfono si "Iniciar al arrancar" está activado, o cuando el servidor contacta a la app).
- Cada 15 s comprueba si el servicio de Shizuku sigue vivo. Si falla dos veces seguidas,
  lo vuelve a arrancar solo, con el mismo método que "Iniciar al arrancar":
  - **Root**: lo reinicia con `su`.
  - **Depuración inalámbrica (Android 13+)**: activa la depuración inalámbrica y lo
    reinicia por ADB, igual que el arranque automático original.
- `START_STICKY` y `stopWithTask=false`: si el sistema mata la app, Android la recrea.

### 2. Protecciones dentro del servidor de Shizuku (`rikka.shizuku.server.KeepAlive`)
Al arrancar, el servidor:
- Pone su `oom_score_adj` en `-1000` (el Low Memory Killer no lo mata aunque juegues
  algo pesado). **Solo funciona con root**; con ADB el kernel no lo permite y se ignora.
- Saca la app de Shizuku de todas las restricciones de batería:
  `deviceidle whitelist`, `RUN_ANY_IN_BACKGROUND`, `RUN_IN_BACKGROUND`,
  `SYSTEM_EXEMPT_FROM_POWER_RESTRICTIONS`, bucket `active` y
  `bg-restriction-level unrestricted`. Así el vigilante no se congela.

### 3. `BootCompleteReceiver`
Ahora también acepta la acción interna
`moe.shizuku.privileged.api.action.KEEPALIVE_RESTART` (receptor privado, no exportado).

## Instalación
1. **Desinstala el Shizuku original** (la firma es distinta, Android no deja actualizar encima).
2. Instala `Shizuku_13.6.0_KeepAlive.apk`.
3. Abre Shizuku, concede notificaciones y arranca el servicio como siempre (root,
   depuración inalámbrica o PC).
4. Para que el reinicio automático funcione sin root (Android 13+):
   - Empareja la depuración inalámbrica desde Shizuku al menos una vez.
   - Da el permiso `WRITE_SECURE_SETTINGS` (una vez, desde PC):
     `adb shell pm grant moe.shizuku.privileged.api android.permission.WRITE_SECURE_SETTINGS`
   - Necesitas estar conectado a una red Wi-Fi en el momento del reinicio.
5. Las apps que usan Shizuku deben volver a pedir permiso (se reinstaló la app).

## Límites reales
- Al reiniciar o apagar el teléfono el servicio se detiene (como pediste). Si activas
  "Iniciar al arrancar" en Shizuku, vuelve a arrancar solo.
- Sin root y sin Wi-Fi (Android < 13, o sin la depuración inalámbrica emparejada) no
  existe forma de volver a arrancarlo sin una PC; en ese caso solo actúan las
  protecciones de batería.
- Si pulsas "Detener" en Shizuku, el vigilante lo volverá a arrancar. Para apagarlo de
  verdad: Ajustes → Apps → Shizuku → Forzar detención.

## Reconstruir
```
apktool d Shizuku_13.6.0.r1086.apk -o dec
cp -r patch/smali/* dec/smali/ && cp patch/AndroidManifest.xml dec/
apktool b dec -o unsigned.apk
python3 scripts/zipalign.py unsigned.apk aligned.apk
javac -cp apksig-2.3.0.jar scripts/Sign.java
java --add-exports java.base/sun.security.x509=ALL-UNNAMED -cp apksig-2.3.0.jar:scripts Sign <keystore.p12> <pass> aligned.apk out.apk
```
