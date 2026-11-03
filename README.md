# Shader Launcher

Launcher de **Minecraft: Java Edition para Android**, basado en
[PojavLauncher](https://github.com/PojavLauncherTeam/PojavLauncher) (LGPL-3.0).
El README original está en [README_PojavLauncher.md](README_PojavLauncher.md).

## Cambios respecto a PojavLauncher

- **Solo tres tipos de instalación:** Vanilla, OptiFine y Forge.
- **Interfaz azul:** tonos que van del cian claro al azul marino oscuro.
- **Pestaña "Mods, shaders y más"** (menú principal), con contenido de [Modrinth](https://modrinth.com):
  - Mods, shaders, modpacks y resource packs, con buscador.
  - Filtros por versión de Minecraft, categoría y orden. Para los shaders se puede elegir OptiFine o Iris/Oculus.
  - Los mods y modpacks son **solo de Forge**.
  - Los archivos se descargan en el perfil que elijas (`mods/`, `shaderpacks/` o `resourcepacks/`).
  - Al descargar un mod se descargan también sus **dependencias obligatorias** para la misma versión y para Forge.
  - Los modpacks crean una instancia nueva e instalan Forge automáticamente.
- **Carpeta `ShaderLauncher` en el almacenamiento principal** (`/storage/emulated/0/ShaderLauncher`),
  usando el permiso "Acceso a todos los archivos". Si no se concede, se usa la carpeta privada de la app.
- **Micrófono:** se pide al iniciar, para los mods de chat de voz.
- **Las cuentas locales no se han tocado:** se pueden crear sin iniciar sesión con Microsoft,
  con las mismas reglas que en PojavLauncher.
- Usa su propio `applicationId` (`com.shader.launcher`), así que se puede instalar junto a PojavLauncher.

## Compilar

GitHub Actions compila el APK en cada push (`.github/workflows/android.yml`).
Se descarga desde la pestaña **Actions → ejecución → Artifacts → ShaderLauncher-debug**.

## Licencia

LGPL-3.0, igual que PojavLauncher (ver [LICENSE](LICENSE)).
