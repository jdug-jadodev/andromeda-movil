# andromeda-movil

App acompañante de Andrómeda. **Repo independiente**: no contiene el motor, las luces,
la agenda, las alarmas ni el residente. Solo pide cosas a la casa por Tailscale.

## Estado

Esqueleto v0.1.0. Todavía no empareja ni habla; la pantalla es un marcador de posición.
El contrato v1 ya está vendorizado y verificado.

## Requisitos

- Android 10 o superior.
- Tailscale instalado y conectado en el teléfono.
- El Moto (`andromeda`) conectado al mismo tailnet.
- Para compilar: JDK 21, Android SDK 36 y Gradle 8.13.

## Compilar

```powershell
.\gradlew.bat :app:assembleDebug
```

El APK sale en `app\build\outputs\apk\debug\app-debug.apk`.

Para instalarlo:

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

## Permisos

Solo estos:

- `INTERNET`
- `RECORD_AUDIO` (para la voz)
- `POST_NOTIFICATIONS`

No pide cámara, Bluetooth, ubicación, `WRITE_SETTINGS` ni acceso a archivos.
La cámara se **ve**, no se captura desde este teléfono.

## Contrato

`contract/` es una copia vendorizada de `andromeda-contrato` v1.0.0.
No se edita a mano. Se actualiza copiando una nueva versión y verificando:

```powershell
python tools\verify_contract.py
```

Los tests de JVM comprueban que las constantes de la app coinciden con
`contract/home-api-v1/policies.json` y `limits.json`.

## Seguridad

- El gateway de la casa usa TLS con certificado fijado por SHA-256.
- El token del dispositivo se guarda cifrado con `EncryptedSharedPreferences`.
- No hay tokens ni claves en el repo.
- No hay backend en la nube: el canal es Tailscale + la casa.
- El certificado se fija; el hostname no decide la identidad.

## Estructura

```
app/
  src/main/java/local/doja/companion/
    MainActivity.kt
    Contract.kt
    TokenStore.kt
    net/GatewayClient.kt
  src/main/assets/contract/     (se copia en el build)
  src/test/java/.../ContractTest.kt
contract/                       copia vendorizada del contrato
tools/verify_contract.py
```

## Siguiente

1. Emparejamiento con QR/PIN.
2. Voz remota con Vosk local + push-to-talk.
3. Mensajes de voz.
4. Visor de cámara en vivo.
