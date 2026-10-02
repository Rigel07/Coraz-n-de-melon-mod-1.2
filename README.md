# Corazón de Melon

Mod de **Minecraft Forge 1.20.1** (Forge 47.3.0+, Java 17).

Encuentra **corazones mágicos**, sube tu nivel de **Inocencia** (de *Bruto* a *Santo*) y los mobs te tratarán cada vez con más cariño.
Conoce a **Mao**, un pequeño ángel inmortal que puede ser tu compañero y asistente (con IA opcional): encuentra minerales y estructuras, guarda lugares y te teletransporta, rompe bloques y cultiva.

## Cómo conseguir el .jar (GitHub)

1. Crea un repositorio en GitHub y sube todo este proyecto (`git init`, `git add .`, `git commit`, `git push`).
2. GitHub Actions compilará el mod automáticamente (pestaña **Actions** → último *Build* → artefacto `corazon-de-melon` → contiene el `.jar`).
3. Para publicar una *release* con el `.jar` adjunto: `git tag v1.0.0 && git push origin v1.0.0`.
4. Mete el `.jar` en la carpeta `mods/` de Minecraft Forge 1.20.1 (cliente y servidor).

### Compilar en tu PC
Necesitas JDK 17 y Gradle 8.x: `gradle wrapper` (una vez) y luego `./gradlew build`. El `.jar` queda en `build/libs/`.

## Corazones mágicos

| Corazón | Color | Inocencia | Rareza |
|---|---|---|---|
| Corazón mágico | Rosa | +1 | Común |
| Corazón mágico azul | Azul | +3 | Poco común |
| Corazón mágico dorado | Dorado | +8 | Raro |
| Corazón mágico violeta | Violeta | +20 | Épico |

- Aparecen **tirados en el suelo del Overworld** cerca de los jugadores, con un brillo para poder localizarlos (configurable).
- También pueden aparecer en **cofres** de estructuras (30 % de los cofres de vanilla).
- Al **recogerlos del suelo** se absorben al instante. Los que salen de cofres se absorben con **clic derecho**.
- **Buenas acciones** también suben la barra: cosechar cultivos maduros (+0.12), comerciar con aldeanos (+0.4), criar animales (+0.3), domesticar (+1.5).

## Niveles de Inocencia

La barra aparece unos segundos al ganar experiencia, o mientras mantienes **I** (configurable en Controles). Cada nivel cuesta más que el anterior.

| Nv | Nombre | XP total |
|---|---|---|
| 1 | Bruto | 0 |
| 2 | Salvaje | 8 |
| 3 | Arisco | 25 |
| 4 | Tranquilo | 55 |
| 5 | Amable | 105 |
| 6 | Gentil | 180 |
| 7 | Bondadoso | 290 |
| 8 | Puro | 450 |
| 9 | Angelical | 700 |
| 10 | Santo | 1100 |

Efectos:

- **Nv 5+**: los mobs hostiles que te persiguen pueden retirarse (probabilidad creciente; si los atacas, no se calman). Los animales pasivos te regalan objetos y efectos (vaca → cuero + regeneración, abeja → panal + velocidad, etc.).
- **Nv 3+**: puedes domesticar a Mao (los Maos salvajes ya no huyen de ti).
- **Nv 7+**: aura de Suerte (Suerte II en nivel 10).
- **Nv 8+**: lobos, gatos, loros y caballos salvajes **se domestican solos** (los lobos sueltan un hueso) y los hostiles calmados **te defienden** de otros hostiles durante unos segundos. Los aldeanos te dan esmeraldas con *Héroe de la aldea*.
- **Nv 9+**: los hostiles calmados a veces te dejan un objeto (pólvora, hueso, hilo...).
- Los jefes (Wither, Ender Dragon) y el Warden son inmunes a la calma.

## Mao, el pequeño ángel

- **Varios Maos salvajes** viven repartidos por el Overworld (aparecen con el mundo y, de día, cerca de los jugadores; hasta 4 salvajes alrededor de cada jugador, configurable con `maoMaxNearby`).
- Si tu Inocencia es **menor de 3** (Bruto/Salvaje) huye de ti. Desde el **nivel 3** se acerca y puedes **domesticarlo con una zanahoria dorada** (no siempre sale a la primera; más Inocencia = más probabilidad). Si tienes 6+ te regala objetos. Huevo de aparición en el creativo.
- **Inmortal** una vez domesticado: ignora todo el daño (fuego, lava, caídas, ahogamiento, mobs, tu propio golpe...). Si cae al vacío vuelve contigo. Solo un `/kill` de un admin puede eliminarlo.
- Domesticado: te sigue, te defiende, te cura cuando estás por debajo del 50 % de vida. **Clic derecho (mano vacía)** lo sienta o lo hace seguirte. Una zanahoria dorada lo cura.

### Mao, tu asistente (escribe en el chat `Mao, <mensaje>` o `/mao <mensaje>`)

Estas órdenes funcionan **al instante y sin clave de IA**:

| Dile... | Qué hace |
|---|---|
| `Mao, quédate` / `quieto` / `espera` | Se queda quieto |
| `Mao, sígueme` / `vamos` | Vuelve a seguirte |
| `Mao, ven` | Se teletransporta a tu lado |
| `Mao, dime los minerales cercanos` | Lista de minerales alrededor (32 bloques) con cantidad, dirección y coordenadas |
| `Mao, ¿hay diamantes cerca?` / `busca hierro` | Busca solo ese mineral |
| `Mao, ¿qué estructuras hay cerca?` | Aldeas, templos, minas, fortalezas... **ya generadas** cerca de ti |
| `Mao, rompe la piedra` / `pica el hierro` / `tala los árboles` | Rompe hasta 32 bloques de ese tipo (radio 16) y te trae los objetos |
| `Mao, rompe ese bloque` | Rompe el bloque al que miras |
| `Mao, cultiva` / `cosecha` | Cosecha los cultivos maduros (radio 10), los replanta y siembra tierra vacía con tus semillas |
| `Mao, guarda este sitio como mi casa` | Recuerda el lugar (hasta 40; también la dimensión) |
| `Mao, llévame a casa` / `teletranspórtame a casa` | Te teletransporta a ese sitio (y Mao viene contigo si no lo has sentado) |
| `Mao, ¿qué lugares tengo?` / `¿dónde está mi casa?` | Lista de sitios guardados |
| `Mao, olvida el sitio casa` | Borra un sitio |
| `Mao, cúrame` | Te cura un poco |

Además, Mao **te avisa solo** cuando ve una estructura nueva cerca (`announceStructures`).
Con la IA configurada, puedes pedirlo con tus propias palabras ("oye Mao, ¿me guardas esto como la mina de hierro?") y también charlar con él, pedirle consejos de Minecraft, etc.
Si Mao está lejos o en otra dimensión, también te oye: `Mao, ven` lo trae.

Cosas que Mao **no** rompe: bedrock, cofres, hornos y demás bloques con inventario, spawners ni líquidos. Respeta la protección de spawn y los claims de otros mods. Se puede desactivar romper y teletransportar en el `.toml` (`allowBreakBlocks`, `allowTeleport`).

### Configurar la IA (dentro del juego)

1. Pulsa la tecla **K** (o ve a **Mods → Corazón de Melon → Config**).
2. Elige el proveedor (Groq, xAI, Anthropic, Ollama o Personalizado), pega tu clave de API con **Ctrl+V** y pulsa **Guardar**.
3. Si el nombre del modelo ya no existe, cámbialo por uno de la lista de tu proveedor.

Funciona al momento, sin reiniciar. Solo vale en tu mundo o LAN; en un servidor dedicado edita `config/corazondemelon-common.toml`.
También puedes usar la variable de entorno `MAO_API_KEY`. **No subas tu clave a GitHub.**

Sin clave, Mao entiende todas las órdenes de la tabla de arriba, pero no charla.

## Comandos

- `/inocencia` — muestra tu nivel.
- `/inocencia nivel <1-10>` y `/inocencia dar <xp>` — para probar (requieren OP).
- `/mao <mensaje>` — hablar con tu Mao.

## Ajustes de dificultad

En el mismo `.toml`: `xpMultiplier`, `heartSpawnIntervalSeconds`, `heartSpawnChance`, `maxHeartsNearby`, `heartsGlow`, `maoSpawnChance`, `maoMaxNearby` y, en la sección `assistant`, `scanRadius`, `breakRadius`, `breakMaxBlocks`, `farmRadius`, `structureRadiusChunks`, `allowTeleport`, `allowBreakBlocks`, `announceStructures`.

## Texturas

Las texturas se generaron con `tools_gen_textures.py` (Pillow). Puedes reemplazar los PNG de `src/main/resources/assets/corazondemelon/textures/` por tu propio arte.
