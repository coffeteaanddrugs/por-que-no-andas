# Smartphone (teléfono para Minecraft 1.21.1)

Un teléfono estilo FiveM que aparece abajo a la derecha. Son **dos piezas**:

| Pieza | Dónde va | Qué hace |
|-------|----------|----------|
| **Plugin de Paper** (`SmartphonePlugin-1.0.0.jar`) | Carpeta `plugins` del servidor | Guarda números, mensajes, contactos, llamadas |
| **Mod de Fabric** (`smartphone-1.0.0.jar`) | Carpeta `mods` de **cada jugador** | La pantalla del teléfono, cámara y galería |

Tu servidor sigue siendo Paper y conserva todos sus plugins.

## Qué incluye
- **Mensajes**: burbujas de chat, historial completo guardado en el servidor, estados Enviado / Entregado / Leído. Si el otro está desconectado, el mensaje le espera y lo ve al entrar.
- **Número único** por jugador (formato `555-1234`). Para agregar a alguien necesitas su número.
- **Contactos (agenda)**.
- **Llamadas** con voz usando **Plasmo Voice**, con timbre, contestar, rechazar, colgar y recientes.
- **Cámara**: saca fotos (sin HUD) y se guardan en la **Galería** del teléfono (solo en el PC del jugador).
- **Mercado Pago**: ver tu saldo y transferir a otros jugadores por número o contacto (usa BankPlus).
- **Inicio** con día, temperatura y clima.
- **Ajustes**: volumen, No molestar, tonos de llamada, fondo (colores o una foto de tu galería).
- **El teléfono es un ítem**: hay que llevarlo en el inventario para usarlo.

## Instalación
**Servidor (Paper 1.21.1):**
1. Pon `SmartphonePlugin-1.0.0.jar` en `plugins`.
2. Instala **Plasmo Voice** (versión Paper) para que haya voz en las llamadas.
3. Para Mercado Pago: **BankPlus**, **Vault** y **PlaceholderAPI**. Si tu BankPlus usa otro comando de pago, cámbialo en `plugins/Smartphone/config.yml` (`pay-command`).
4. Reinicia el servidor y date un teléfono con `/phone give`.

**Cada jugador (cliente Fabric 1.21.1):**
1. Fabric Loader + **Fabric API**.
2. **Plasmo Voice** (versión Fabric).
3. `smartphone-1.0.0.jar` en la carpeta `mods`.

## Teclas (se pueden cambiar en Controles → Teléfono)
| Tecla | Acción |
|-------|--------|
| `↑` (flecha arriba) | Abrir / cerrar el teléfono (y salir del modo cámara) |
| `Y` | Contestar llamada |
| `N` | Rechazar / colgar |
| `Enter` | Tomar foto (solo en modo cámara) |

Comandos: `/phone number` te dice tu número. `/phone give [jugador]` da un teléfono (permiso `smartphone.give`, por defecto operadores).

Si ya tenías otra tecla asignada en Controles → Teléfono, Minecraft no la cambia sola: reasígnala a mano.

## Cómo hablar en una llamada
No hay nada que configurar: al contestar, **lo que hablas con tu micrófono de Plasmo Voice lo oye la otra persona**, esté donde esté.
Los dos necesitan Plasmo Voice instalado y el micrófono funcionando.

## Audio de "no disponible"
Pon tu audio en `src/main/resources/assets/smartphone/sounds/unavailable.ogg` (formato OGG) antes de compilar.
Ya trae uno de prueba (unos pitidos).

## Compilar (GitHub Actions)
Sube el contenido de esta carpeta a un repositorio. En **Actions** se compilan solos las dos piezas
y se descargan desde **Artifacts**: `mod-fabric-para-jugadores` y `plugin-paper-para-el-servidor`.

## Datos
El servidor guarda todo en `plugins/Smartphone/smartphone.json`. Haz copia de seguridad de ese archivo.
