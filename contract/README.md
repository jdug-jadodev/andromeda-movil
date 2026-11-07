# Copia vendorizada del contrato

Esta carpeta es una copia de **andromeda-contrato v1.0.0**.
No se edita a mano.

- `home-api-v1/` contiene la API, las políticas, los límites y los errores.
- `VERSION` es la versión del contrato.
- `SHA256SUMS` permite detectar cualquier edición accidental.

Verificar:

```powershell
python tools\verify_contract.py
```

Actualizar:

1. Copiar la nueva versión desde `andromeda-contrato`.
2. Actualizar `Contract.kt` si cambian las políticas o los límites.
3. Correr `python tools\verify_contract.py` y los tests de JVM.
